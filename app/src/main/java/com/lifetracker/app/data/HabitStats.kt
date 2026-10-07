package com.lifetracker.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Streak numbers for one habit. For a "relapse" habit they mean days without a relapse. */
data class Streaks(val current: Int, val best: Int, val totalDays: Int)

/**
 * The streak arithmetic, kept free of the phone and database so it can be tested.
 *
 * Every habit is treated as a daily habit for now. Streak's weekly, monthly and
 * "every X days" schedules are stored but not applied yet.
 */
object HabitStats {
    const val KIND_YES_NO = 0
    const val KIND_RELAPSE = 1
    const val KIND_COUNTED = 2

    /** Did this day's count reach the habit's goal? */
    fun reachedGoal(habit: HabitEntity, count: Double): Boolean =
        if (habit.kind == KIND_COUNTED && habit.anyAmount) count > 0 else count >= habit.target - 1e-9

    /**
     * A streak of days that each reached the goal. It stays alive through today
     * even if today is not done yet, because the day is not over.
     */
    fun streaks(doneDays: Set<LocalDate>, today: LocalDate): Streaks {
        if (doneDays.isEmpty()) return Streaks(0, 0, 0)
        var cursor = if (today in doneDays) today else today.minusDays(1)
        var current = 0
        while (cursor in doneDays) {
            current++
            cursor = cursor.minusDays(1)
        }
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in doneDays.sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            if (run > best) best = run
            previous = day
        }
        return Streaks(current, best, doneDays.size)
    }

    /**
     * Days without a relapse. `current` counts days since the last relapse (0 if
     * it happened today), or since the habit started if there has been none.
     * `totalDays` is the number of relapses.
     */
    fun cleanStreaks(relapseDays: Set<LocalDate>, startedOn: LocalDate, today: LocalDate): Streaks {
        val start = if (startedOn.isAfter(today)) today else startedOn
        val sorted = relapseDays.filter { !it.isAfter(today) }.sorted()
        if (sorted.isEmpty()) {
            val days = (ChronoUnit.DAYS.between(start, today) + 1).toInt()
            return Streaks(days, days, 0)
        }
        val current = ChronoUnit.DAYS.between(sorted.last(), today).toInt()
        var best = ChronoUnit.DAYS.between(start, sorted.first()).toInt().coerceAtLeast(0)
        for (i in 1 until sorted.size) {
            best = maxOf(best, ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]).toInt() - 1)
        }
        return Streaks(current, maxOf(best, current), sorted.size)
    }
}
