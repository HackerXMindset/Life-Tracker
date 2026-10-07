package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.HabitStats
import com.lifetracker.app.data.Streaks
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CellState { Done, Partial, Missed, Relapse, Clean, Pending, Before }

data class DayCell(val date: LocalDate, val state: CellState)

data class HabitCard(
    val habit: HabitEntity,
    val cells: List<DayCell>,
    val streaks: Streaks,
    val todayCount: Double,
    val totalAmount: Double,
)

data class HabitGroup(val title: String, val cards: List<HabitCard>)

class HabitsViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).habitDao()

    val groups: StateFlow<List<HabitGroup>> =
        combine(dao.observeHabits(), dao.observeCompletions()) { habits, completions ->
            build(habits, completions, LocalDate.now())
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tap on a day: yes/no habits toggle, counted habits add one step, relapse habits toggle a relapse. */
    fun tap(habit: HabitEntity, date: LocalDate) {
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

    /** Press and hold on a day: clears it. */
    fun clear(habit: HabitEntity, date: LocalDate) {
        viewModelScope.launch { dao.deleteCompletion(habit.id, date.toString()) }
    }

    private fun build(habits: List<HabitEntity>, completions: List<CompletionEntity>, today: LocalDate): List<HabitGroup> {
        val byHabit = completions.groupBy { it.habitId }
        val days = (13 downTo 0).map { today.minusDays(it.toLong()) }

        val cards = habits.filter { !it.archived }.map { habit ->
            val counts = HashMap<LocalDate, Double>()
            for (c in byHabit[habit.id].orEmpty()) {
                runCatching { LocalDate.parse(c.date) }.getOrNull()?.let { counts[it] = c.count }
            }
            val startedOn = runCatching { LocalDate.parse(habit.startedOn) }.getOrDefault(today)

            if (habit.kind == HabitStats.KIND_RELAPSE) {
                val relapses = counts.keys
                HabitCard(
                    habit = habit,
                    cells = days.map { d ->
                        DayCell(
                            d,
                            when {
                                d in relapses -> CellState.Relapse
                                d.isBefore(startedOn) -> CellState.Before
                                else -> CellState.Clean
                            },
                        )
                    },
                    streaks = HabitStats.cleanStreaks(relapses, startedOn, today),
                    todayCount = counts[today] ?: 0.0,
                    totalAmount = 0.0,
                )
            } else {
                val done = counts.filter { HabitStats.reachedGoal(habit, it.value) }.keys
                HabitCard(
                    habit = habit,
                    cells = days.map { d ->
                        val count = counts[d] ?: 0.0
                        DayCell(
                            d,
                            when {
                                d in done -> CellState.Done
                                count > 0 -> CellState.Partial
                                d.isBefore(startedOn) -> CellState.Before
                                d == today -> CellState.Pending
                                else -> CellState.Missed
                            },
                        )
                    },
                    streaks = HabitStats.streaks(done, today),
                    todayCount = counts[today] ?: 0.0,
                    totalAmount = counts.values.sum(),
                )
            }
        }
        return cards.groupBy { it.habit.category.ifBlank { "Other" } }
            .map { (title, list) -> HabitGroup(title, list) }
    }
}
