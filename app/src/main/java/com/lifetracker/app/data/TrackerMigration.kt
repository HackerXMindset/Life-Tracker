package com.lifetracker.app.data

import android.content.Context
import java.time.LocalDateTime
import java.util.Locale

/**
 * Custom trackers used to live in their own tables. They are habits now, so the first time the Food tab opens, every tracker
 * becomes a habit of the Habits tab (or joins the one with the same name) and its taps become checks of that habit. The old tables
 * are left as they were, and the work is done once.
 */
object TrackerMigration {
    fun fold(name: String): String = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /**
     * [existing] (the day of the habit, or null) with the tap times [minutes] added. A time that is already there is not added twice,
     * so doing this again changes nothing. Each new time is worth [amount]. The times stay in order.
     */
    fun merge(existing: CompletionEntity?, habitId: String, date: String, minutes: List<Int>, amount: Double = 1.0): CompletionEntity {
        val have = HabitTicks.times(existing).toMutableList()
        val unmatched = have.toMutableList()
        var added = 0
        for (m in minutes) {
            if (unmatched.remove(m)) continue
            have.add(m)
            added++
        }
        have.sort()
        val count = HabitTicks.round((existing?.count ?: 0.0) + added * amount)
        return CompletionEntity(habitId, date, count, have.lastOrNull(), HabitTicks.join(have), existing?.steps.orEmpty())
    }

    /** Runs once. Safe to call again: it does nothing after the first time. */
    suspend fun run(context: Context) {
        if (FoodSettings.trackersMigrated(context)) return
        val db = AppDatabase.get(context)
        val extras = db.foodExtrasDao()
        val habitDao = db.habitDao()
        val trackers = extras.allTrackers()
        val events = extras.allEvents().groupBy { it.trackerId }
        val habits = habitDao.allHabits().filter { it.deletedAt == 0L }
        val sync = HabitSyncState(context)
        val watched = FoodSettings.watchedHabits(context).toMutableList()
        val now = LocalDateTime.now()

        for (t in trackers) {
            val mine = events[t.id].orEmpty()
            if (t.archived && mine.isEmpty()) continue
            val match = habits.firstOrNull { fold(it.name) == fold(t.name) }
            val habitId = match?.id ?: HabitLink.create(context, t.name, t.icon, t.color)
            val habit = match ?: habitDao.habit(habitId) ?: continue
            val amount = HabitLink.amountOf(habit)
            for ((date, taps) in mine.groupBy { it.date }) {
                val merged = merge(habitDao.completion(habitId, date), habitId, date, taps.map { it.minuteOfDay }, amount)
                habitDao.upsertCompletion(merged)
            }
            if (t.archived) {
                if (match == null) habitDao.upsertHabit(HabitEditor.withArchived(habit, true, now))
            } else if (habitId !in watched) {
                watched.add(habitId)
            }
            sync.stamp(habitId)
        }
        FoodSettings.setWatchedHabits(context, watched)
        FoodSettings.setTrackersMigrated(context)
    }
}
