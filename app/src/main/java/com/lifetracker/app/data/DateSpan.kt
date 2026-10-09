package com.lifetracker.app.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A stretch of whole days, both ends included: "15 March to 26 April", or one day when
 * [from] and [to] are the same. Used by the Calls and Phone usage screens. Kept free of Android so it can be tested.
 */
data class DateSpan(val from: LocalDate, val to: LocalDate) {
    val isSingleDay: Boolean get() = from == to

    /** Number of days, both ends included. */
    val dayCount: Long get() = to.toEpochDay() - from.toEpochDay() + 1

    /** First millisecond of [from]. */
    fun startMs(zone: ZoneId): Long = from.atStartOfDay(zone).toInstant().toEpochMilli()

    /** First millisecond after [to] (so a query can say "before this"). */
    fun endMs(zone: ZoneId): Long = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Moves the whole span [direction] times its own length (-1 earlier, +1 later). A single day moves one day. */
    fun shifted(direction: Int): DateSpan {
        val n = dayCount * direction
        return DateSpan(from.plusDays(n), to.plusDays(n))
    }

    /** Same span with a new first day; the last day moves too if it would otherwise come before it. */
    fun withFrom(day: LocalDate): DateSpan = DateSpan(day, if (to.isBefore(day)) day else to)

    /** Same span with a new last day; the first day moves too if it would otherwise come after it. */
    fun withTo(day: LocalDate): DateSpan = DateSpan(if (from.isAfter(day)) day else from, day)

    /** "26 May 2024", "15 Mar to 26 Apr" (this year) or "24 Mar 2026 to 9 Oct 2026". */
    fun label(today: LocalDate): String {
        fun one(d: LocalDate, withYear: Boolean) =
            d.format(DateTimeFormatter.ofPattern(if (withYear) "d MMM yyyy" else "d MMM", Locale.ENGLISH))
        val sameYear = from.year == today.year && to.year == today.year
        return if (isSingleDay) one(from, !sameYear) else one(from, !sameYear) + " to " + one(to, !sameYear)
    }

    companion object {
        fun ofDays(a: LocalDate, b: LocalDate) = if (b.isBefore(a)) DateSpan(b, a) else DateSpan(a, b)
    }
}

/** The quick choices above the date pickers. [CUSTOM] is not a preset: it means you picked dates yourself. */
object RangePresets {
    const val CUSTOM = "custom"

    class Preset(val id: String, val label: String)

    val list = listOf(
        Preset("today", "Today"),
        Preset("yesterday", "Yesterday"),
        Preset("7", "Last 7 days"),
        Preset("30", "Last 30 days"),
        Preset("90", "Last 90 days"),
        Preset("year", "This year"),
        Preset("all", "All time"),
    )

    /** The earliest day "All time" starts from. */
    val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)

    fun span(id: String, today: LocalDate): DateSpan = when (id) {
        "yesterday" -> DateSpan(today.minusDays(1), today.minusDays(1))
        "7" -> DateSpan(today.minusDays(6), today)
        "30" -> DateSpan(today.minusDays(29), today)
        "90" -> DateSpan(today.minusDays(89), today)
        "year" -> DateSpan(LocalDate.of(today.year, 1, 1), today)
        "all" -> DateSpan(EARLIEST, today)
        else -> DateSpan(today, today)
    }
}
