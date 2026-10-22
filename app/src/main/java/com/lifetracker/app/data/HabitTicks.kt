package com.lifetracker.app.data

import java.time.LocalDate
import java.util.Locale

/**
 * The checks of one day of a habit, with their times. Streak calls the times "marks": the minute of the day of every
 * check, in order. A day is still one record (its amount, its last time and its marks), so Streak reads it the same way.
 * No Android in it, so it can be tested.
 */
object HabitTicks {
    /** "480,615,1300" to [480, 615, 1300]. Anything that is not a minute of the day is left out. */
    fun parse(marks: String): List<Int> =
        marks.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..1439 }

    fun join(minutes: List<Int>): String = minutes.joinToString(",")

    /** The times of a day: its list, or the one old time of a day from before lists existed, or none. */
    fun times(day: CompletionEntity?): List<Int> {
        if (day == null) return emptyList()
        val list = parse(day.marks)
        if (list.isNotEmpty()) return list
        return listOfNotNull(day.minuteOfDay)
    }

    /** Amounts are kept to three decimals so 0.1 + 0.2 stays 0.3. */
    fun round(value: Double): Double = Math.round(value * 1000.0) / 1000.0

    private fun make(habitId: String, date: String, count: Double, minutes: List<Int>) =
        CompletionEntity(habitId, date, count, minutes.lastOrNull(), join(minutes))

    /**
     * One more check of [amount]. [nowMinute] is the time to note, or null when the day is not today and the time is not
     * known. An old single time becomes the first in the list.
     */
    fun add(old: CompletionEntity?, habitId: String, date: String, amount: Double, nowMinute: Int?): CompletionEntity {
        val minutes = times(old) + listOfNotNull(nowMinute)
        return make(habitId, date, round((old?.count ?: 0.0) + amount), minutes)
    }

    /** The day with its last check taken back by [amount]; null when nothing is left, so the day is cleared. */
    fun undo(old: CompletionEntity, amount: Double): CompletionEntity? {
        val left = round(old.count - amount)
        if (left <= 1e-9) return null
        return make(old.habitId, old.date, left, times(old).dropLast(1))
    }

    /** The day with one time changed ([from] null or not found: the time is added). The list stays in time order. */
    fun retime(old: CompletionEntity, from: Int?, to: Int): CompletionEntity {
        val minutes = times(old).toMutableList()
        val at = if (from == null) -1 else minutes.indexOf(from)
        if (at < 0) minutes.add(to) else minutes[at] = to
        minutes.sort()
        return make(old.habitId, old.date, old.count, minutes)
    }

    /** The day with its total replaced. The times stay; a day with none gets [nowMinute], if known. Zero or less clears the day (null). */
    fun withTotal(old: CompletionEntity?, habitId: String, date: String, total: Double, nowMinute: Int?): CompletionEntity? {
        val value = round(total)
        if (value <= 0.0) return null
        val kept = times(old)
        val minutes = if (kept.isEmpty()) listOfNotNull(nowMinute) else kept
        return make(habitId, date, value, minutes)
    }

    fun clock(minute: Int): String = "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)

    /** "Today · 14:05", "Yesterday", "12 Oct · 09:30" or "Never". */
    fun lastCheckText(last: Pair<LocalDate, Int?>?, today: LocalDate): String {
        if (last == null) return "Never"
        val (day, minute) = last
        val when_ = when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> "${day.dayOfMonth} ${day.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())}"
        }
        return if (minute == null) when_ else "$when_ · ${clock(minute)}"
    }
}
