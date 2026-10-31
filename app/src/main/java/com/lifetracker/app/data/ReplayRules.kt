package com.lifetracker.app.data

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * The rules of the day replay, with no Android in them so they can be tested. The idea is the one games use to show smooth
 * movement from snapshots: keep the readings as they are, run a clock of your own through the day, and for every clock time work
 * out where you were between the two readings either side of it. The speed only changes how fast the clock runs.
 */
object ReplayRules {
    /** One reading kept for the replay. */
    class Sample(val ms: Long, val lat: Double, val lng: Double)

    /** Something that happens at a moment of the day (arriving at a place, starting a logged activity). */
    class Event(val ms: Long, val title: String, val subtitle: String, val kind: Kind)

    enum class Kind { ARRIVE, LEAVE, LOGGED }

    /** Between two readings further apart than this the phone did not know where you were, so the dot stays at the first. */
    const val MAX_GAP_MS = 20 * 60_000L

    /** Staying within this distance for [QUIET_MIN_MS] is a quiet stretch (sleep, a long stay). */
    const val QUIET_RADIUS_M = 100.0
    const val QUIET_MIN_MS = 30 * 60_000L

    /** While skipping, quiet stretches go at least this fast (minutes of the day for each real second). */
    const val SKIP_MIN_PER_S = 240

    /** The speeds on offer, in minutes of the day for each real second. */
    val SPEEDS = listOf(1, 2, 5, 10, 20, 30, 60, 120, 240, 480, 1440)

    /** "5 min/s", "1 h/s". */
    fun speedLabel(minPerS: Int): String = when {
        minPerS >= 1440 && minPerS % 1440 == 0 -> "${minPerS / 1440} day/s"
        minPerS >= 60 && minPerS % 60 == 0 -> "${minPerS / 60} h/s"
        else -> "$minPerS min/s"
    }

    /** A span of the day with nothing worth watching. */
    class Quiet(val startMs: Long, val endMs: Long)

    /** Where you were at [ms]: between the two nearest readings, or null before the first and after the last. */
    fun positionAt(samples: List<Sample>, ms: Long): Pair<Double, Double>? {
        if (samples.isEmpty() || ms < samples.first().ms || ms > samples.last().ms) return null
        val i = indexAtOrBefore(samples, ms)
        val a = samples[i]
        if (a.ms == ms || i == samples.lastIndex) return a.lat to a.lng
        val b = samples[i + 1]
        if (b.ms - a.ms > MAX_GAP_MS) return a.lat to a.lng
        val f = (ms - a.ms).toDouble() / (b.ms - a.ms)
        return (a.lat + (b.lat - a.lat) * f) to (a.lng + (b.lng - a.lng) * f)
    }

    /** The index of the last reading at or before [ms] (the first when [ms] is earlier than all). */
    fun indexAtOrBefore(samples: List<Sample>, ms: Long): Int {
        var lo = 0
        var hi = samples.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (samples[mid].ms <= ms) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** How far each side of the clock the ground speed is looked at. */
    const val SPEED_WINDOW_MS = 60_000L

    /** How fast you were really moving around [ms], in metres a second (0 before, after and across silences). */
    fun groundSpeedMs(samples: List<Sample>, ms: Long): Double {
        if (samples.size < 2) return 0.0
        val from = max(ms - SPEED_WINDOW_MS, samples.first().ms)
        val to = min(ms + SPEED_WINDOW_MS, samples.last().ms)
        if (to <= from) return 0.0
        val a = positionAt(samples, from) ?: return 0.0
        val b = positionAt(samples, to) ?: return 0.0
        return PlaceRules.distanceM(a.first, a.second, b.first, b.second) / ((to - from) / 1000.0)
    }

    /**
     * The map zoom that suits moving at [speedMs] metres a second: close in when walking, further out as you go faster, so the
     * stretch just covered and the stretch just ahead both stay on the screen.
     */
    fun autoZoom(speedMs: Double): Double {
        val v = max(0.0, speedMs)
        return (17.0 - 1.3 * (ln(1.0 + v / 3.0) / ln(2.0))).coerceIn(5.0, 17.0)
    }

    /** One step of easing the shown zoom towards [target], so the picture glides instead of jumping. */
    fun easeZoom(current: Double, target: Double): Double = current + (target - current) * 0.1

    /** The path so far at [ms]: every reading up to it, ending at the in-between position. Sides of a long gap are not joined. */
    fun trail(samples: List<Sample>, ms: Long): List<List<Pair<Double, Double>>> {
        val here = positionAt(samples, ms) ?: return emptyList()
        val upTo = indexAtOrBefore(samples, ms)
        val parts = ArrayList<MutableList<Pair<Double, Double>>>()
        var current = ArrayList<Pair<Double, Double>>()
        for (i in 0..upTo) {
            if (i > 0 && samples[i].ms - samples[i - 1].ms > MAX_GAP_MS) {
                parts += current
                current = ArrayList()
            }
            current += samples[i].lat to samples[i].lng
        }
        if (here != (samples[upTo].lat to samples[upTo].lng)) current += here
        parts += current
        return parts.filter { it.size >= 2 }
    }

    /** Stretches where you stayed put, or where there were no readings at all, for at least [QUIET_MIN_MS]. */
    fun quietSpans(samples: List<Sample>, startMs: Long, endMs: Long): List<Quiet> {
        val found = ArrayList<Quiet>()
        fun add(a: Long, b: Long) {
            if (b - a >= QUIET_MIN_MS) found += Quiet(a, b)
        }
        if (samples.isEmpty()) {
            add(startMs, endMs)
            return found
        }
        add(startMs, samples.first().ms)
        var anchor = 0
        for (i in 1 until samples.size) {
            add(samples[i - 1].ms, samples[i].ms)
            if (PlaceRules.distanceM(samples[anchor].lat, samples[anchor].lng, samples[i].lat, samples[i].lng) > QUIET_RADIUS_M) {
                add(samples[anchor].ms, samples[i - 1].ms)
                anchor = i
            }
        }
        add(samples[anchor].ms, samples.last().ms)
        add(samples.last().ms, endMs)
        // Overlapping or touching stretches are one.
        val out = ArrayList<Quiet>()
        for (q in found.sortedBy { it.startMs }) {
            val last = out.lastOrNull()
            if (last != null && q.startMs <= last.endMs) out[out.size - 1] = Quiet(last.startMs, max(last.endMs, q.endMs)) else out += q
        }
        return out
    }

    /** How many milliseconds of the day pass for each real millisecond at [clockMs]. */
    fun rate(clockMs: Long, minPerS: Int, skip: Boolean, quiet: List<Quiet>): Double {
        val base = minPerS * 60_000.0 / 1000.0
        val inQuiet = skip && quiet.any { clockMs >= it.startMs && clockMs < it.endMs }
        return if (inQuiet) max(base, SKIP_MIN_PER_S * 60_000.0 / 1000.0) else base
    }

    /** The clock after [realMs] of real time, held at [endMs]. */
    fun advance(clockMs: Long, realMs: Long, minPerS: Int, skip: Boolean, quiet: List<Quiet>, endMs: Long): Long {
        val next = clockMs + (realMs * rate(clockMs, minPerS, skip, quiet)).toLong()
        return min(next, endMs)
    }

    /** Events between [fromMs] (not included) and [toMs] (included), oldest first. */
    fun crossed(events: List<Event>, fromMs: Long, toMs: Long): List<Event> =
        events.filter { it.ms > fromMs && it.ms <= toMs }.sortedBy { it.ms }

    /** The next or previous event time for a step button: null when there is none. */
    fun stepTo(events: List<Event>, clockMs: Long, forward: Boolean): Long? =
        if (forward) events.map { it.ms }.filter { it > clockMs }.minOrNull() else events.map { it.ms }.filter { it < clockMs }.maxOrNull()

    /** "07:05" for the clock in the replay. */
    fun clockText(ms: Long, zone: java.time.ZoneId): String =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}
