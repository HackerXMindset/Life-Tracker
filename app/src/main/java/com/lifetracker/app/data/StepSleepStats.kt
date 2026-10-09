package com.lifetracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Step arithmetic and wording. Kept free of Android so it can be tested. */
object StepStats {
    const val HEALTH_CONNECT = "healthconnect"
    const val SENSOR = "sensor"

    /** A day's steps never go down: if Health Connect later reports less (its own history was cleared), the bigger number stays. */
    fun merge(existing: StepDayEntity?, date: String, steps: Int, source: String): StepDayEntity =
        if (existing == null || steps >= existing.steps) StepDayEntity(date, steps, source) else existing

    /**
     * The phone's step counter counts from the last restart. Returns how many steps happened since [previous],
     * or 0 if there was no earlier reading. If the number went down the phone restarted, so all of [counter] is new.
     */
    fun sensorDelta(previous: Long?, counter: Long): Long = when {
        previous == null || previous < 0 -> 0L
        counter < previous -> counter.coerceAtLeast(0)
        else -> counter - previous
    }

    fun text(steps: Int): String = String.format(Locale.ENGLISH, "%,d", steps)

    data class Totals(val days: Int, val total: Long, val average: Int)

    /** Only days with steps count towards the average, so days before you started recording do not drag it down. */
    fun totals(list: List<StepDayEntity>): Totals {
        val counted = list.filter { it.steps > 0 }
        val total = counted.sumOf { it.steps.toLong() }
        return Totals(counted.size, total, if (counted.isEmpty()) 0 else (total / counted.size).toInt())
    }
}

/** Sleep arithmetic, wording and the rules for which version of a night wins. */
object SleepStats {
    const val ESTIMATE = "estimate"
    const val HEALTH_CONNECT = "healthconnect"
    const val MANUAL = "manual"
    const val SKIPPED = "skipped"

    fun label(source: String): String = when (source) {
        ESTIMATE -> "estimated from phone use"
        HEALTH_CONNECT -> "from Health Connect"
        MANUAL -> "edited by you"
        SKIPPED -> "not sleep"
        else -> ""
    }

    /** Higher wins. Your own edit beats everything, then Health Connect, then the estimate. */
    private fun rank(source: String): Int = when (source) {
        SKIPPED -> 4
        MANUAL -> 3
        HEALTH_CONNECT -> 2
        else -> 1
    }

    /** Whether a night found with [newSource] may replace the saved one. An estimate can refresh an earlier estimate only. */
    fun mayReplace(existing: SleepNightEntity?, newSource: String): Boolean =
        existing == null || rank(newSource) >= rank(existing.source)

    fun minutes(n: SleepNightEntity): Int =
        if (n.source == SKIPPED) 0 else ((n.endMs - n.startMs) / 60_000L).toInt().coerceAtLeast(0)

    /** Average minutes per counted night. */
    fun averageMinutes(list: List<SleepNightEntity>): Int {
        val counted = list.filter { it.source != SKIPPED && minutes(it) > 0 }
        return if (counted.isEmpty()) 0 else counted.sumOf { minutes(it) } / counted.size
    }

    /**
     * Turns the times you picked into a night. [startMinute] and [endMinute] are minutes after midnight.
     * You wake up on [date]; if you fell asleep at a later clock time than you woke up, that was the evening before.
     * Returns null if both times are the same.
     */
    fun build(date: LocalDate, startMinute: Int, endMinute: Int, zone: ZoneId): Pair<Long, Long>? {
        if (startMinute == endMinute) return null
        val end = date.atStartOfDay(zone).plusMinutes(endMinute.toLong())
        val startDay = if (startMinute < endMinute) date else date.minusDays(1)
        val start = startDay.atStartOfDay(zone).plusMinutes(startMinute.toLong())
        return start.toInstant().toEpochMilli() to end.toInstant().toEpochMilli()
    }

    /**
     * From sleep sessions that Health Connect holds ([start, end] pairs), keeps one per wake-up date:
     * the longest one that lasted at least 2 hours, so a nap does not replace the night.
     */
    fun nightly(sessions: List<Pair<Long, Long>>, zone: ZoneId): Map<LocalDate, Pair<Long, Long>> =
        sessions
            .filter { it.second - it.first >= 2 * 60 * 60 * 1000L }
            .groupBy { Instant.ofEpochMilli(it.second).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.maxBy { it.second - it.first } }
}

/**
 * Works out when you slept from when you stopped using the phone. It looks for the longest quiet stretch
 * (3 to 14 hours) that began between 6 pm the evening before and 10 am, and ended by 6 pm. Very short
 * phone checks in the middle of the night (under 5 minutes, between 12:30 and 6 am) do not count as waking up.
 * It is a guess: reading in bed without touching the phone looks the same as sleeping, so you can edit any night.
 */
object SleepEstimator {
    private const val MIN_MS = 3 * 60 * 60 * 1000L
    private const val MAX_MS = 14 * 60 * 60 * 1000L
    private const val BLIP_MS = 5 * 60 * 1000L

    class Night(val startMs: Long, val endMs: Long)

    /** The sessions needed for [date]'s night start at noon the day before and end at 8 pm on [date]. */
    fun usageWindow(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.minusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli() to
            date.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()

    fun estimate(date: LocalDate, usage: List<UsageSessionEntity>, zone: ZoneId): Night? {
        val windowStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val latestStart = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val latestWake = date.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

        val merged = ArrayList<LongArray>()
        for (u in usage.filter { !isNightBlip(it, zone) }.sortedBy { it.startMs }) {
            val last = merged.lastOrNull()
            if (last != null && u.startMs <= last[1]) last[1] = maxOf(last[1], u.endMs) else merged.add(longArrayOf(u.startMs, u.endMs))
        }
        var best: Night? = null
        for (i in 0 until merged.size - 1) {
            val start = merged[i][1]
            val end = merged[i + 1][0]
            val length = end - start
            if (length < MIN_MS || length > MAX_MS) continue
            if (start < windowStart || start > latestStart || end > latestWake) continue
            if (best == null || length > best.endMs - best.startMs) best = Night(start, end)
        }
        return best
    }

    private fun isNightBlip(u: UsageSessionEntity, zone: ZoneId): Boolean {
        if (u.endMs - u.startMs >= BLIP_MS) return false
        val t = Instant.ofEpochMilli(u.startMs).atZone(zone).toLocalTime()
        return !t.isBefore(LocalTime.of(0, 30)) && t.isBefore(LocalTime.of(6, 0))
    }
}
