package com.lifetracker.app.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Writes the checks that the Food tab makes into the habits themselves, so the Habits tab, Streak and the Food tab always
 * show the same thing. Every change stamps the habit for the Streak sync, like a tap on the Habits tab does.
 */
object HabitLink {
    /** What one check is worth: a counted habit's step, otherwise 1. */
    fun amountOf(habit: HabitEntity): Double =
        if (habit.kind == HabitStats.KIND_COUNTED && habit.step > 0) habit.step else 1.0

    /** One more check on [date] at [minute] ([HabitMarks.NO_TIME] for none). */
    suspend fun addCheck(context: Context, habitId: String, date: String, minute: Int) {
        val dao = AppDatabase.get(context).habitDao()
        val habit = dao.habit(habitId) ?: return
        val next = HabitTicks.add(dao.completion(habitId, date), habitId, date, amountOf(habit), minute.takeIf { it != HabitMarks.NO_TIME })
        dao.upsertCompletion(next)
        HabitSyncState(context).stamp(habitId)
    }

    /** Takes one check away. */
    suspend fun removeCheck(context: Context, habitId: String, date: String, minute: Int) {
        val dao = AppDatabase.get(context).habitDao()
        val habit = dao.habit(habitId) ?: return
        val old = dao.completion(habitId, date) ?: return
        val next = HabitMarks.without(old, minute, amountOf(habit))
        if (next == null) dao.deleteCompletion(habitId, date) else dao.upsertCompletion(next)
        HabitSyncState(context).stamp(habitId)
    }

    /** Moves one check to another time of the same day. */
    suspend fun retimeCheck(context: Context, habitId: String, date: String, from: Int, to: Int) {
        val dao = AppDatabase.get(context).habitDao()
        val old = dao.completion(habitId, date) ?: return
        dao.upsertCompletion(HabitTicks.retime(old, from.takeIf { it != HabitMarks.NO_TIME }, to))
        HabitSyncState(context).stamp(habitId)
    }

    /** A new habit to count by tap, with no streak, for something like a headache. Returns its id. */
    suspend fun create(context: Context, name: String, icon: String, color: Long): String {
        val dao = AppDatabase.get(context).habitDao()
        val id = HabitEditor.newId()
        val form = HabitForm(
            name = name, icon = icon, color = color, kind = HabitStats.KIND_COUNTED, target = 1.0,
            unitLabel = "times", step = 1.0, anyAmount = true, tracking = true,
        )
        dao.upsertHabit(HabitEditor.build(form, null, id, dao.maxHabitOrder() + 1, LocalDate.now(), LocalDateTime.now()))
        HabitSyncState(context).stamp(id)
        return id
    }
}
