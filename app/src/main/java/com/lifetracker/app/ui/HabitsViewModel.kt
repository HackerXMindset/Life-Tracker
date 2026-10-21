package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.CategoryEntity
import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.HabitCalendar
import com.lifetracker.app.data.HabitClears
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.HabitForm
import com.lifetracker.app.data.HabitMode
import com.lifetracker.app.data.HabitNotes
import com.lifetracker.app.data.HabitPlan
import com.lifetracker.app.data.HabitRules
import com.lifetracker.app.data.HabitStats
import com.lifetracker.app.data.HabitSyncState
import com.lifetracker.app.data.HabitsSettings
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.data.TodayProgress
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val planDao = AppDatabase.get(app).planDao()
    private val settings = HabitsSettings(app)
    /** Remembers what changed here, for sending to Streak later (H5b). */
    private val sync = HabitSyncState(app)

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

    /** Every note, newest first. */
    val notes: StateFlow<List<NoteEntity>> =
        planDao.observeNotes()
            .map { HabitNotes.sorted(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Habits and notes that were deleted: hidden, kept, and listed in Settings so they can be restored. */
    val deletedHabits: StateFlow<List<HabitEntity>> =
        dao.observeDeletedHabits().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deletedNotes: StateFlow<List<NoteEntity>> =
        planDao.observeDeletedNotes().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Habit names by id, archived habits included, for labelling notes. */
    val habitNames: StateFlow<Map<String, String>> =
        dao.observeHabits()
            .map { list -> list.associate { it.id to it.name } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Writes a new note on a habit and day. A blank text writes nothing. */
    fun addNote(habitId: String, date: LocalDate, text: String) {
        val note = HabitNotes.create(HabitEditor.newId(), habitId, date, text, LocalDateTime.now()) ?: return
        viewModelScope.launch {
            planDao.upsertNote(note)
            sync.stamp(note.id)
        }
    }

    /** Changes a note's text. A blank text changes nothing: notes are never removed here. */
    fun editNote(note: NoteEntity, text: String) {
        viewModelScope.launch {
            val current = planDao.note(note.id) ?: return@launch
            HabitNotes.edited(current, text)?.let {
                planDao.upsertNote(it)
                sync.stamp(it.id)
            }
        }
    }

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
            sync.stamp(habit.id)
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
            sync.stamp(habit.id)
        }
    }

    /** Hides a habit, or shows it again. Everything it recorded stays. */
    fun setArchived(habit: HabitEntity, archived: Boolean) {
        viewModelScope.launch {
            dao.upsertHabit(HabitEditor.withArchived(habit, archived, LocalDateTime.now()))
            sync.stamp(habit.id)
        }
    }

    /**
     * Deletes a habit: it is hidden and kept, with its notes, and Streak is told it was deleted. Nothing is erased, and
     * Settings > Habits > Deleted habits and notes brings it back.
     */
    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val noteIds = planDao.allNotes().filter { it.habitId == habit.id && it.deletedAt == 0L }.map { it.id }
            dao.setHabitDeleted(habit.id, now)
            planDao.deleteNotesOfHabit(habit.id, now)
            sync.markDeleted(habit.id, *noteIds.toTypedArray(), now = now)
        }
    }

    /** Brings back a deleted habit and the notes that were deleted with it. Streak reads this as a change. */
    fun restoreHabit(habit: HabitEntity) {
        viewModelScope.launch {
            val at = habit.deletedAt
            val noteIds = planDao.allNotes().filter { it.habitId == habit.id && it.deletedAt == at }.map { it.id }
            dao.setHabitDeleted(habit.id, 0L)
            planDao.restoreNotesOfHabit(habit.id, at)
            sync.stamp(habit.id, *noteIds.toTypedArray())
        }
    }

    /** Deletes one note: hidden and kept. */
    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            planDao.setNoteDeleted(note.id, System.currentTimeMillis())
            sync.markDeleted(note.id)
        }
    }

    fun restoreNote(note: NoteEntity) {
        viewModelScope.launch {
            planDao.setNoteDeleted(note.id, 0L)
            sync.stamp(note.id)
        }
    }

    /** "Clear all progress": from now on only days after today count. Every day stays stored. */
    fun clearAllProgress(habit: HabitEntity) {
        viewModelScope.launch {
            dao.setHabitCleared(habit.id, System.currentTimeMillis())
            sync.stamp(habit.id)
        }
    }

    /** Sets the exact amount for a day (counted habits). Zero or less clears the day. */
    fun setAmount(habit: HabitEntity, date: LocalDate, amount: Double) {
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            val key = date.toString()
            if (amount <= 0) {
                dao.deleteCompletion(habit.id, key)
            } else {
                val existing = dao.completion(habit.id, key)
                dao.upsertCompletion(CompletionEntity(habit.id, key, amount, existing?.minuteOfDay))
            }
            sync.stamp(habit.id)
        }
    }

    /** Why a day was missed. A blank reason removes it. */
    fun setMissReason(habit: HabitEntity, date: LocalDate, reason: String?) {
        viewModelScope.launch {
            val current = dao.habit(habit.id) ?: return@launch
            dao.upsertHabit(HabitEditor.withMissReason(current, date, reason))
            sync.stamp(habit.id)
        }
    }

    /** Clears a day: the one way a day's record is removed. */
    fun clear(habit: HabitEntity, date: LocalDate) {
        viewModelScope.launch {
            dao.deleteCompletion(habit.id, date.toString())
            sync.stamp(habit.id)
        }
    }

    private fun build(src: Source, m: HabitMode, small: Boolean, picked: String, today: LocalDate): HabitsUi {
        val byHabit = src.completions.groupBy { it.habitId }
        val all = src.habits.filter { !it.archived }.map { habit ->
            val counts = HashMap<LocalDate, Double>()
            for (c in HabitClears.visible(byHabit[habit.id].orEmpty(), habit.clearedAt)) {
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
