package com.lifetracker.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The numbers and the calendar of the Location page, free of Android so they can be tested. */
object LocationDays {
    /** What one day came to. [places] counts named places only; [atPlacesMs] counts every shown stay. */
    class Summary(val distanceM: Int, val movingMs: Long, val trips: Int, val places: Int, val atPlacesMs: Long)

    /**
     * The day from [fromMs] to [toMs]: its trips are those that began in it (the same rule the Timeline uses), its stays are
     * the shown ones, counted for the part that falls inside the day.
     */
    fun summary(trips: List<TripEntity>, visits: List<PlaceVisitEntity>, fromMs: Long, toMs: Long, nowMs: Long): Summary {
        val own = trips.filter { it.startMs in fromMs until toMs }
        val totals = PlaceRules.totals(visits, fromMs, toMs, nowMs)
        return Summary(
            distanceM = own.sumOf { it.distanceM },
            movingMs = own.sumOf { it.endMs - it.startMs },
            trips = own.size,
            places = totals.count { it.placeId.isNotEmpty() },
            atPlacesMs = totals.sumOf { it.ms },
        )
    }

    /** "12.4 km · 48 min moving · 3 trips · 4 places", or a short line when nothing happened. */
    fun summaryText(s: Summary): String {
        if (s.trips == 0 && s.places == 0) return "No trips or places on this day."
        val parts = ArrayList<String>()
        if (s.trips > 0) {
            parts += TripRules.distanceText(s.distanceM)
            parts += PlaceRules.durationText(s.movingMs) + " moving"
        }
        parts += plural(s.trips, "trip")
        parts += plural(s.places, "place")
        return parts.joinToString(" · ")
    }

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    /** The days of [trips] on which you moved (by the day each trip began), in the phone's time zone. */
    fun movementDays(trips: List<TripEntity>, zone: ZoneId): Set<LocalDate> =
        trips.map { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }.toSet()

    /** The first and last millisecond of [month] as a range [from, to) in [zone]. */
    fun monthBounds(month: YearMonth, zone: ZoneId): Pair<Long, Long> =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** The month as rows of seven: empty cells (null) before the first day and after the last, so every row is full. */
    fun monthGrid(month: YearMonth, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - weekStart.value + 7) % 7
        val cells = ArrayList<LocalDate?>()
        repeat(lead) { cells += null }
        for (d in 1..month.lengthOfMonth()) cells += month.atDay(d)
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** The letters over the calendar columns, starting at [weekStart]. */
    fun weekdayLetters(weekStart: DayOfWeek = DayOfWeek.MONDAY): List<String> =
        (0 until 7).map { "MTWTFSS"[(weekStart.value - 1 + it) % 7].toString() }
}
