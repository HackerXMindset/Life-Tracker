package com.lifetracker.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** What a goal counts. */
object GoalMeasures {
    const val HOURS_AT = "hours_at"
    const val VISITS = "visits"
    const val HOURS_OUT = "hours_out"
    const val DISTANCE = "distance"
    const val HOURS_TRAVEL = "hours_travel"

    /** Measure to the words offered in the editor. */
    val ALL: List<Pair<String, String>> = listOf(
        HOURS_AT to "Hours at a place",
        VISITS to "Visits to a place",
        HOURS_OUT to "Hours out of the house",
        DISTANCE to "Distance travelled",
        HOURS_TRAVEL to "Hours travelling",
    )

    fun label(measure: String) = ALL.firstOrNull { it.first == measure }?.second ?: measure
    fun usesPlace(measure: String) = measure == HOURS_AT || measure == VISITS
    fun usesMode(measure: String) = measure == DISTANCE || measure == HOURS_TRAVEL
    fun unit(measure: String) = when (measure) {
        VISITS -> "visits"
        DISTANCE -> "km"
        else -> "h"
    }
}

object GoalPeriods {
    const val DAY = "day"
    const val WEEK = "week"
    const val MONTH = "month"
    const val DAYS = "days"

    val ALL: List<Pair<String, String>> = listOf(DAY to "A day", WEEK to "A week", MONTH to "A month", DAYS to "The last days")

    fun label(goal: GoalEntity) = when (goal.period) {
        DAY -> "today"
        WEEK -> "this week"
        MONTH -> "this month"
        else -> "in the last ${goal.days} days"
    }
}

object GoalDirections {
    const val AT_LEAST = "least"
    const val AT_MOST = "most"
}

/** Where a goal stands. */
class GoalProgress(
    val value: Double,
    val target: Double,
    /** 0 to 1 for the ring (it stops at full). */
    val fraction: Double,
    /** A target that has been reached, or a limit that is still kept. */
    val good: Boolean,
    /** A limit that has been passed. */
    val over: Boolean,
)

/** Works out goals from the stays and trips you already have. No Android in it, so it can be tested. */
object GoalStats {
    class Range(val fromMs: Long, val toMs: Long)

    /** The period a goal looks at on [today]: the day, the week (Monday to Sunday), the month, or the last N days up to today. */
    fun range(goal: GoalEntity, today: LocalDate, zone: ZoneId): Range {
        val (first, lastExclusive) = when (goal.period) {
            GoalPeriods.DAY -> today to today.plusDays(1)
            GoalPeriods.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(7) }
            GoalPeriods.MONTH -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
            else -> today.minusDays((goal.days.coerceIn(1, 366) - 1).toLong()) to today.plusDays(1)
        }
        return Range(first.atStartOfDay(zone).toInstant().toEpochMilli(), lastExclusive.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    private fun overlap(aStart: Long, aEnd: Long, from: Long, to: Long): Long = max(0L, min(aEnd, to) - max(aStart, from))

    private fun placeMatches(goal: GoalEntity, visit: PlaceVisitEntity, places: List<PlaceEntity>): Boolean = when {
        goal.placeId.isNotEmpty() -> visit.placeId == goal.placeId
        goal.category.isNotEmpty() -> places.firstOrNull { it.id == visit.placeId }?.kind == goal.category
        else -> true
    }

    private fun modeMatches(goal: GoalEntity, trip: TripEntity) = goal.mode.isEmpty() || trip.mode == goal.mode

    /** The measure over the period of [goal] on [today], up to [nowMs]. Hours, kilometres or a count. */
    fun value(goal: GoalEntity, visits: List<PlaceVisitEntity>, trips: List<TripEntity>, places: List<PlaceEntity>, nowMs: Long, today: LocalDate, zone: ZoneId): Double {
        val r = range(goal, today, zone)
        val to = min(r.toMs, nowMs)
        val shown = visits.filter { PlaceStats.isShown(it, nowMs) }
        fun visitMs(v: PlaceVisitEntity) = overlap(v.startMs, v.startMs + PlaceStats.lengthMs(v, nowMs), r.fromMs, to)
        return when (goal.measure) {
            GoalMeasures.HOURS_AT -> shown.filter { placeMatches(goal, it, places) }.sumOf { visitMs(it) } / HOUR_MS
            GoalMeasures.VISITS -> shown.count { placeMatches(goal, it, places) && it.startMs >= r.fromMs && it.startMs < r.toMs }.toDouble()
            GoalMeasures.HOURS_OUT -> {
                val home = goal.category.ifEmpty { "home" }
                val away = shown.filter { v -> places.firstOrNull { it.id == v.placeId }?.kind != home }.sumOf { visitMs(it) }
                val travelling = trips.sumOf { overlap(it.startMs, it.endMs, r.fromMs, to) }
                (away + travelling) / HOUR_MS
            }
            GoalMeasures.DISTANCE -> trips.filter { modeMatches(goal, it) && it.startMs >= r.fromMs && it.startMs < r.toMs }.sumOf { it.distanceM } / 1000.0
            GoalMeasures.HOURS_TRAVEL -> trips.filter { modeMatches(goal, it) }.sumOf { overlap(it.startMs, it.endMs, r.fromMs, to) } / HOUR_MS
            else -> 0.0
        }
    }

    private const val HOUR_MS = 3_600_000.0

    fun progress(goal: GoalEntity, value: Double): GoalProgress {
        val target = goal.target
        val fraction = if (target <= 0.0) (if (goal.direction == GoalDirections.AT_MOST) (if (value > 0) 1.0 else 0.0) else 1.0) else (value / target).coerceIn(0.0, 1.0)
        return if (goal.direction == GoalDirections.AT_MOST) {
            GoalProgress(value, target, fraction, good = value <= target, over = value > target)
        } else {
            GoalProgress(value, target, fraction, good = value >= target, over = false)
        }
    }

    private fun number(x: Double): String =
        if (x >= 100 || x == Math.floor(x)) String.format(Locale.ROOT, "%.0f", x) else String.format(Locale.ROOT, "%.1f", x)

    fun amountText(measure: String, x: Double): String = number(x) + " " + GoalMeasures.unit(measure)

    /** "6.5 of 10 h this week", or for a limit "3 h, at most 5 h this week". */
    fun lineText(goal: GoalEntity, p: GoalProgress): String {
        val unit = GoalMeasures.unit(goal.measure)
        val period = GoalPeriods.label(goal)
        return if (goal.direction == GoalDirections.AT_MOST) {
            number(p.value) + " " + unit + ", at most " + number(p.target) + " " + unit + " " + period
        } else {
            number(p.value) + " of " + number(p.target) + " " + unit + " " + period
        }
    }

    /** One short word for the state: "Done", "Kept", "Over" or the percent so far. */
    fun stateText(goal: GoalEntity, p: GoalProgress): String = when {
        p.over -> "Over"
        goal.direction == GoalDirections.AT_MOST -> "Kept"
        p.good -> "Done"
        else -> Math.round(p.fraction * 100).toString() + "%"
    }

    /** A name made from the settings when you leave the name empty: "Hours at Gym". [where] is the place or kind, or "". */
    fun defaultName(goal: GoalEntity, where: String): String = when {
        goal.measure == GoalMeasures.HOURS_AT -> "Hours at " + where.ifEmpty { "places" }
        goal.measure == GoalMeasures.VISITS -> "Visits to " + where.ifEmpty { "places" }
        else -> GoalMeasures.label(goal.measure)
    }

    /** The goals that are switched on, in order. */
    fun active(goals: List<GoalEntity>) = goals.filter { it.enabled }

    /** The earliest moment any of [goals] looks at, to read only the stays and trips that are needed. */
    fun earliest(goals: List<GoalEntity>, today: LocalDate, zone: ZoneId): Long =
        goals.minOfOrNull { range(it, today, zone).fromMs } ?: Instant.now().toEpochMilli()
}
