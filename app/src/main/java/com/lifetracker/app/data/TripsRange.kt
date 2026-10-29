package com.lifetracker.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot

/** One way of travelling in a stretch of time. */
class ModeTotal(val mode: String, val count: Int, val distanceM: Long, val ms: Long)

/** The numbers of the Data tab. */
class TripsSummary(
    val trips: Int,
    val distanceM: Long,
    val movingMs: Long,
    val avgKmh: Int,
    val longest: TripEntity?,
    val fastest: TripEntity?,
    val byMode: List<ModeTotal>,
)

/**
 * The Trips view works on any stretch: a day, a week, a month, a year or two dates you pick. All the arithmetic is here, free of
 * Android, so it can be tested.
 */
object TripsRange {
    enum class Span(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year"), CUSTOM("Custom") }

    /** The days of [span] around [anchor] (both ends included). For [Span.CUSTOM] it is [custom] as it is. */
    fun window(span: Span, anchor: LocalDate, custom: DateSpan, weekStart: DayOfWeek = DayOfWeek.MONDAY): DateSpan = when (span) {
        Span.DAY -> DateSpan(anchor, anchor)
        Span.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(weekStart)).let { DateSpan(it, it.plusDays(6)) }
        Span.MONTH -> YearMonth.from(anchor).let { DateSpan(it.atDay(1), it.atEndOfMonth()) }
        Span.YEAR -> DateSpan(LocalDate.of(anchor.year, 1, 1), LocalDate.of(anchor.year, 12, 31))
        Span.CUSTOM -> custom
    }

    /** The first millisecond of the first day to the first millisecond after the last day. */
    fun bounds(w: DateSpan, zone: ZoneId): Pair<Long, Long> =
        w.from.atStartOfDay(zone).toInstant().toEpochMilli() to w.to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** [anchor] moved [steps] stretches (negative for earlier). A custom stretch is moved by its own length in the view model. */
    fun shift(span: Span, anchor: LocalDate, steps: Int): LocalDate = when (span) {
        Span.DAY -> anchor.plusDays(steps.toLong())
        Span.WEEK -> anchor.plusWeeks(steps.toLong())
        Span.MONTH -> anchor.plusMonths(steps.toLong())
        Span.YEAR -> anchor.plusYears(steps.toLong())
        Span.CUSTOM -> anchor
    }

    /** "Fri, 23 Oct", "20 - 26 Oct", "October 2026", "2026", "12 Oct - 25 Nov 2026". The year is added when it is not this year. */
    fun title(span: Span, w: DateSpan, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        fun f(p: String) = DateTimeFormatter.ofPattern(p, locale)
        val sameYear = w.from.year == today.year && w.to.year == today.year
        return when (span) {
            Span.DAY -> w.from.format(f(if (sameYear) "EEE, d MMM" else "EEE, d MMM yyyy"))
            Span.WEEK, Span.CUSTOM -> {
                if (w.from == w.to) {
                    w.from.format(f(if (sameYear) "EEE, d MMM" else "EEE, d MMM yyyy"))
                } else if (w.from.year == w.to.year && w.from.monthValue == w.to.monthValue) {
                    w.from.format(f("d")) + " - " + w.to.format(f(if (sameYear) "d MMM" else "d MMM yyyy"))
                } else {
                    w.from.format(f(if (w.from.year == w.to.year) "d MMM" else "d MMM yyyy")) + " - " + w.to.format(f(if (sameYear) "d MMM" else "d MMM yyyy"))
                }
            }
            Span.MONTH -> w.from.format(f("MMMM yyyy"))
            Span.YEAR -> w.from.format(f("yyyy"))
        }
    }

    /** The colours of the trips on the map, in order; the seventh trip takes the first colour again. */
    val palette: List<Int> = listOf(0x2E6FD8, 0x2E9E5B, 0xE08A00, 0xD64545, 0x8E5BD6, 0x1AA7A7, 0xC2569B, 0x7A6A4F)

    fun colorOf(index: Int): Int = palette[Math.floorMod(index, palette.size)]

    /** Trips grouped by the day each began, oldest day first. */
    fun byDay(trips: List<TripEntity>, zone: ZoneId): List<Pair<LocalDate, List<TripEntity>>> =
        trips.sortedBy { it.startMs }
            .groupBy { java.time.Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
            .toList()
            .sortedBy { it.first }

    fun summary(trips: List<TripEntity>): TripsSummary {
        val distance = trips.sumOf { it.distanceM.toLong() }
        val moving = trips.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        val avg = if (moving > 0L) ((distance / 1000.0) / (moving / 3_600_000.0)).toInt() else 0
        val modes = trips.groupBy { it.mode }.map { (mode, list) ->
            ModeTotal(mode, list.size, list.sumOf { it.distanceM.toLong() }, list.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) })
        }.sortedByDescending { it.distanceM }
        return TripsSummary(trips.size, distance, moving, avg, trips.maxByOrNull { it.distanceM }, trips.maxByOrNull { it.topKmh }, modes)
    }

    /** The trip that began in the stretch and is not hidden, oldest first. */
    fun inRange(trips: List<TripEntity>, fromMs: Long, toMs: Long, hiddenIds: Set<String>): List<TripEntity> =
        trips.filter { it.startMs in fromMs until toMs && it.id !in hiddenIds }.sortedBy { it.startMs }

    /** Readings in time order, each time once (the same reading can be stored under two trips after a join). */
    fun prepare(points: List<TripPointEntity>): List<TripPointEntity> = points.sortedBy { it.ms }.distinctBy { it.ms }

    /** The readings of [trip]: those taken between its start and end. [sorted] must come from [prepare]. */
    fun pointsOf(trip: TripEntity, sorted: List<TripPointEntity>): List<TripPointEntity> {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].ms < trip.startMs) lo = mid + 1 else hi = mid
        }
        var end = lo
        while (end < sorted.size && sorted[end].ms <= trip.endMs) end++
        return sorted.subList(lo, end)
    }

    /**
     * Drops readings closer than [minM] to the last one kept, so a month of trips draws quickly. The first and last stay.
     * Only the drawing is thinned; the readings themselves are never touched.
     */
    fun thin(path: List<Pair<Double, Double>>, minM: Double): List<Pair<Double, Double>> {
        if (path.size <= 2 || minM <= 0.0) return path
        val out = ArrayList<Pair<Double, Double>>()
        out += path.first()
        for (i in 1 until path.size - 1) {
            if (metres(out.last(), path[i]) >= minM) out += path[i]
        }
        out += path.last()
        return out
    }

    private fun metres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dLat = (b.first - a.first) * 111_320.0
        val dLng = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2.0))
        return hypot(dLat, dLng)
    }
}
