package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.CategoryEntity
import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.HabitCalendar
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.HabitForm
import com.lifetracker.app.data.HabitMode
import com.lifetracker.app.data.HabitPlan
import com.lifetracker.app.data.HabitRules
import com.lifetracker.app.data.HabitStats
import com.lifetracker.app.data.HabitsSettings
import com.lifetracker.app.data.TodayProgress
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One habit as the Habits screen draws it. */
data class HabitRow(
    val habit: HabitEntity,
    val rules: HabitRules,
    val streakText: String,
    val strength: Float,
    val consistency: Int,
    val todayCount: Double,
    /** Done today. For a "relapse" habit it means no relapse today. */
    val doneToday: Boolean,
    val paused: Boolean,
)

data class HabitsUi(
    val today: LocalDate,
    val rows: List<HabitRow>,
    val hasAny: Boolean,
    val progress: TodayProgress,
    val chips: List<String>,
    /** Every category name there is, for the habit form. */
    val allCategories: List<String>,
    /** Hidden habits, which can be shown again. */
    val archived: List<HabitEntity>,
    /** Empty means All. */
    val category: String,
    val mode: HabitMode,
    val compact: Boolean,
)

private class Source(
    val habits: List<HabitEntity>,
    val completions: List<CompletionEntity>,
    val categories: List<CategoryEntity>,
)

class HabitsViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).habitDao()
    private val settings = HabitsSettings(app)

    private val mode = MutableStateFlow(settings.mode)
    private val compact = MutableStateFlow(settings.compact)
    private val category = MutableStateFlow(settings.category)

    private val source = combine(dao.observeHabits(), dao.observeCompletions(), dao.observeCategories()) { h, c, k ->
        Source(h, c, k)
    }

    val state: StateFlow<HabitsUi> =
        combine(source, mode, compact, category) { src, m, small, picked ->
            build(src, m, small, picked, LocalDate.now())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HabitsUi(
                    LocalDate.now(), emptyList(), false, TodayProgress(0, 0), emptyList(), emptyList(), emptyList(), "",
                    settings.mode, settings.compact,
                ),
            )

    fun setMode(value: HabitMode) {
        settings.mode = value
        mode.value = value
    }

    fun toggleCompact() {
        val next = !compact.value
        settings.compact = next
        compact.value = next
    }

    fun setCategory(value: String) {
        settings.category = value
        category.value = value
    }

    /** Tap on a day: yes/no habits toggle, counted habits add one step, relapse habits toggle a relapse. */
    fun tap(habit: HabitEntity, date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            val key = date.toString()
            val existing = dao.completion(habit.id, key)
            if (habit.kind == HabitStats.KIND_COUNTED) {
                dao.upsertCompletion(
                    CompletionEntity(habit.id, key, (existing?.count ?: 0.0) + habit.step, existing?.minuteOfDay),
                )
            } else if (existing == null) {
                dao.upsertCompletion(CompletionEntity(habit.id, key, maxOf(1.0, habit.target), null))
            } else {
                dao.deleteCompletion(habit.id, key)
            }
        }
    }

    /** Saves a new habit ([existing] null) or the changes to one. */
    fun save(form: HabitForm, existing: HabitEntity?) {
        viewModelScope.launch {
            val id = existing?.id ?: HabitEditor.newId()
            val order = if (existing == null) dao.maxHabitOrder() + 1 else existing.sortOrder
            val habit = HabitEditor.build(form, existing, id, order, LocalDate.now(), LocalDateTime.now())
            val known = dao.allCategories()
            if (habit.category.isNotEmpty() && known.none { it.name == habit.category }) {
                dao.upsertCategories(
                    listOf(CategoryEntity(HabitEditor.newId(), habit.category, habit.color, (known.maxOfOrNull { it.sortOrder } ?: -1) + 1)),
                )
            }
            dao.upsertHabit(habit)
        }
    }

    /** Hides a habit, or shows it again. Everything it recorded stays. */
    fun setArchived(habit: HabitEntity, archived: Boolean) {
        viewModelScope.launch { dao.upsertHabit(HabitEditor.withArchived(habit, archived, LocalDateTime.now())) }
    }

    /** Press and hold on a day: clears it. */
    fun clear(habit: HabitEntity, date: LocalDate) {
        viewModelScope.launch { dao.deleteCompletion(habit.id, date.toString()) }
    }

    private fun build(src: Source, m: HabitMode, small: Boolean, picked: String, today: LocalDate): HabitsUi {
        val byHabit = src.completions.groupBy { it.habitId }
        val all = src.habits.filter { !it.archived }.map { habit ->
            val counts = HashMap<LocalDate, Double>()
            for (c in byHabit[habit.id].orEmpty()) {
                runCatching { LocalDate.parse(c.date) }.getOrNull()?.let { counts[it] = c.count }
            }
            val rules = HabitRules.of(habit, counts, today)
            val plan = HabitPlan.fromRaw(habit.raw)
            HabitRow(
                habit = habit,
                rules = rules,
                streakText = HabitCalendar.streakText(rules.currentStreak(), plan.interval),
                strength = rules.strength().toFloat(),
                consistency = rules.consistency(),
                todayCount = counts[today] ?: 0.0,
                doneToday = rules.isCompleted(today) || rules.isCovered(today),
                paused = rules.isPaused(today),
            )
        }
        val chips = HabitCalendar.categoryChips(all.map { it.habit.category }, src.categories.map { it.name })
        val selected = picked.takeIf { it in chips } ?: ""
        val shown = if (selected.isEmpty()) all else all.filter { it.habit.category == selected }
        return HabitsUi(
            today = today,
            rows = shown,
            hasAny = all.isNotEmpty(),
            progress = HabitCalendar.todayProgress(all.map { it.rules }, today),
            chips = chips,
            allCategories = (src.categories.map { it.name } + all.map { it.habit.category }).filter { it.isNotBlank() }.distinct(),
            archived = src.habits.filter { it.archived },
            category = selected,
            mode = m,
            compact = small,
        )
    }
}
