package com.lifetracker.app.data

import java.time.DayOfWeek
import java.time.LocalDate

/** The three calendar views Streak offers on every habit card. */
enum class HabitMode(val label: String) { Week("Week"), Month("Month"), Year("Year") }

/** "3 of 5 done today", for the ring at the top of the Habits screen. */
data class TodayProgress(val done: Int, val total: Int) {
    val ratio: Float get() = if (total == 0) 0f else done.toFloat() / total
    val allDone: Boolean get() = total > 0 && done == total
}

/** The shapes of Streak's week, month and year calendars, and the Today ring maths. No Android in it, so it can be tested. */
object HabitCalendar {
    fun startOfWeek(day: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        day.minusDays(((day.dayOfWeek.value - weekStart.value + 7) % 7).toLong())

    /** The seven days of the week that today is in. */
    fun weekDays(today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate> {
        val first = startOfWeek(today, weekStart)
        return (0 until 7).map { first.plusDays(it.toLong()) }
    }

    /**
     * This month as rows of seven days, starting on the first day of the week that holds the 1st and ending with the
     * week that holds the last day. Days of the neighbouring months are in the rows too; the screen leaves them blank.
     */
    fun monthWeeks(today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<List<LocalDate>> {
        val monthStart = today.withDayOfMonth(1)
        val last = today.withDayOfMonth(today.lengthOfMonth())
        val first = startOfWeek(monthStart, weekStart)
        val rows = ((startOfWeek(last, weekStart).toEpochDay() - first.toEpochDay()) / 7).toInt() + 1
        return (0 until rows).map { w -> (0 until 7).map { d -> first.plusDays((w * 7 + d).toLong()) } }
    }

    /** The last 53 weeks as columns of seven days, oldest column first, ending with this week. */
    fun yearColumns(today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY, columns: Int = 53): List<List<LocalDate>> {
        val first = startOfWeek(today, weekStart).minusWeeks((columns - 1).toLong())
        return (0 until columns).map { c -> (0 until 7).map { r -> first.plusDays((c * 7 + r).toLong()) } }
    }

    /** The columns of a year grid that start a new month, with that month's number (1 to 12). */
    fun yearMonthLabels(columns: List<List<LocalDate>>): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        columns.forEachIndexed { i, col ->
            if (i == 0 || col[0].monthValue != columns[i - 1][0].monthValue) out[i] = col[0].monthValue
        }
        return out
    }

    /** Habits that are due today and not paused are counted; one is finished when done or covered. */
    fun todayProgress(rules: List<HabitRules>, today: LocalDate): TodayProgress {
        val counted = rules.filter { !it.tracking && !it.isPaused(today) && it.isScheduled(today) }
        return TodayProgress(counted.count { it.isCompleted(today) || it.isCovered(today) }, counted.size)
    }

    /** The streak shown on a card: weeks or months are marked, days are a bare number. */
    fun streakText(current: Int, interval: Int): String = when (interval) {
        HabitPlan.WEEKLY -> "${current}w"
        HabitPlan.MONTHLY -> "${current}mo"
        else -> current.toString()
    }

    /**
     * The categories for the chips: those you ordered first, then any others in name order. Only categories that
     * some habit uses are listed.
     */
    fun categoryChips(used: Collection<String>, ordered: List<String>): List<String> {
        val present = used.filter { it.isNotBlank() }.toSet()
        val ranked = ordered.filter { it in present }
        return ranked + (present - ranked.toSet()).sorted()
    }

    /** The twelve small bars of a compact card in Year mode: how much of each month of this year was done, 0 to 1. */
    fun monthShares(rules: HabitRules, today: LocalDate): List<Float?> = (1..12).map { m ->
        val first = LocalDate.of(today.year, m, 1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        val from = if (first.isBefore(rules.startedAt)) rules.startedAt else first
        val to = if (last.isAfter(today)) today else last
        val span = to.toEpochDay() - from.toEpochDay() + 1
        if (span <= 0) null else {
            var done = 0
            var d = from
            while (!d.isAfter(to)) {
                if (rules.isCompleted(d)) done++
                d = d.plusDays(1)
            }
            (done.toFloat() / span).coerceIn(0f, 1f)
        }
    }
}
