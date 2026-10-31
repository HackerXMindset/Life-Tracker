package com.lifetracker.app.data

import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

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

    /** While skipping, quiet stretches go at least this fast (seconds of the day for each real second: 4 hours a second). */
    const val SKIP_SPEED = 14_400.0

    /** The speeds on offer, in seconds of the day for each real second: 1x is real time. */
    val SPEEDS = listOf(1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1800.0, 3600.0, 14_400.0, 86_400.0)

    /** "10x" (ten times real time), "5 min/s", "1 h/s", "1 day/s". */
    fun speedLabel(speed: Double): String {
        val n = speed.roundToLong().coerceAtLeast(1L)
        return when {
            n >= 86_400L && n % 86_400L == 0L -> "${n / 86_400L} day/s"
            n >= 3_600L && n % 3_600L == 0L -> "${n / 3_600L} h/s"
            n >= 60L && n % 60L == 0L -> "${n / 60L} min/s"
            else -> "${n}x"
        }
    }

    /** How busy a moment is for smart speed: how much the clock slows down when busy and speeds up when quiet. */
    class Smart(val busySlow: Double, val quietBoost: Double)

    /** Within this long of an event the moment counts as busy. */
    const val EVENT_NEAR_MS = 2 * 60_000L

    /** How far each side of the clock the wandering of the path is looked at. */
    const val WANDER_WINDOW_MS = 3 * 60_000L

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

    /** How fast you moved along the path (not just from the start to the end of the window), in metres a second. */
    fun pathSpeedMs(samples: List<Sample>, ms: Long): Double {
        if (samples.size < 2) return 0.0
        val from = indexAtOrBefore(samples, ms - SPEED_WINDOW_MS)
        val to = indexAtOrBefore(samples, ms + SPEED_WINDOW_MS)
        if (to <= from) return 0.0
        var path = 0.0
        for (i in from + 1..to) {
            if (samples[i].ms - samples[i - 1].ms > MAX_GAP_MS) continue
            path += PlaceRules.distanceM(samples[i - 1].lat, samples[i - 1].lng, samples[i].lat, samples[i].lng)
        }
        val seconds = (samples[to].ms - samples[from].ms) / 1000.0
        return if (seconds <= 0.0) 0.0 else path / seconds
    }

    /** The dot takes about this long to cross the screen at the most, whatever the replay speed. */
    const val LOOK_SECONDS = 5.0

    /** Room left round everything that should be on the screen. */
    const val VIEW_MARGIN = 1.15

    /** The map is never narrower than this (a few streets), so a slow walk is not zoomed in on a single house. */
    const val MIN_VIEW_M = 400.0

    /** The widest picture of the ground the map is asked to show (about a region): faster than this, the clock is slowed instead. */
    const val MAX_VIEW_M = 1_300_000.0

    const val MIN_ZOOM = 4.5
    const val MAX_ZOOM = 17.5

    /**
     * How wide a stretch of ground the map should show at [ms] when the clock runs at [rate] (milliseconds of the day for each
     * real millisecond): the dot in the middle and everything it passes in the next and last few real seconds on screen. So the
     * faster the replay, the wider the picture, and the dot always seems to move at the same calm pace.
     */
    fun viewWidthM(samples: List<Sample>, ms: Long, rate: Double): Double {
        val here = positionAt(samples, ms) ?: return MIN_VIEW_M
        val span = (LOOK_SECONDS * 1000.0 * rate).toLong().coerceAtLeast(0L)
        val from = max(ms - span, samples.first().ms)
        val to = min(ms + span, samples.last().ms)
        var r = 0.0
        fun consider(p: Pair<Double, Double>?) {
            if (p != null) r = max(r, PlaceRules.distanceM(here.first, here.second, p.first, p.second))
        }
        consider(positionAt(samples, from))
        consider(positionAt(samples, to))
        for (i in indexAtOrBefore(samples, from)..indexAtOrBefore(samples, to)) {
            val s = samples[i]
            if (s.ms in from..to) consider(s.lat to s.lng)
        }
        return max(MIN_VIEW_M, 2.0 * r * VIEW_MARGIN).coerceAtMost(MAX_VIEW_M)
    }

    /** The map zoom that shows [widthM] metres of ground across a screen [widthDp] wide at latitude [lat]. */
    fun zoomForWidth(widthM: Double, widthDp: Double, lat: Double): Double {
        val metresPerDpAtZoom0 = 78_271.5 * kotlin.math.cos(Math.toRadians(lat))
        return (ln(metresPerDpAtZoom0 * widthDp / max(widthM, 1.0)) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** The fastest the clock may run while moving at [groundSpeedMs], so that the dot never seems to race across the widest map. */
    fun rateCap(groundSpeedMs: Double): Double = (MAX_VIEW_M / (2.0 * LOOK_SECONDS * VIEW_MARGIN)) / max(groundSpeedMs, 0.5)

    /** One step of easing the shown zoom towards [target], so the picture glides instead of jumping. */
    fun easeZoom(current: Double, target: Double): Double = current + (target - current) * 0.1

    /** The path so far at [ms]: every reading up to it, ending at the in-between position. Sides of a long gap are not joined. */
    fun trail(samples: List<Sample>, ms: Long, sinceMs: Long = Long.MIN_VALUE): List<List<Pair<Double, Double>>> {
        val here = positionAt(samples, ms) ?: return emptyList()
        val upTo = indexAtOrBefore(samples, ms)
        val parts = ArrayList<MutableList<Pair<Double, Double>>>()
        var current = ArrayList<Pair<Double, Double>>()
        val first = if (sinceMs == Long.MIN_VALUE) 0 else indexAtOrBefore(samples, sinceMs)
        for (i in first..upTo) {
            if (i > first && samples[i].ms - samples[i - 1].ms > MAX_GAP_MS) {
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

    /** How many milliseconds of the day pass for each real millisecond at [clockMs]; [factor] is what smart speed multiplies it by. */
    fun rate(clockMs: Long, speed: Double, skip: Boolean, quiet: List<Quiet>, factor: Double = 1.0): Double {
        val base = speed * factor
        val inQuiet = skip && quiet.any { clockMs >= it.startMs && clockMs < it.endMs }
        return if (inQuiet) max(base, SKIP_SPEED) else base
    }

    /** The clock after [realMs] of real time, held at [endMs]. */
    fun advance(
        clockMs: Long, realMs: Long, speed: Double, skip: Boolean, quiet: List<Quiet>, endMs: Long,
        factor: Double = 1.0, maxRate: Double = Double.MAX_VALUE,
    ): Long {
        val next = clockMs + (realMs * min(rate(clockMs, speed, skip, quiet, factor), maxRate)).toLong()
        return min(next, endMs)
    }

    /** 0 for a quiet moment to 1 for a busy one: walking about, turning a lot, or close to something that happened. */
    fun busyness(samples: List<Sample>, events: List<Event>, ms: Long): Double {
        var b = 0.0
        val v = groundSpeedMs(samples, ms)
        val onTheMove = pathSpeedMs(samples, ms)
        // Moving on, or walking about in one area (the path is long even though you end up where you began).
        if (v >= 0.4 || onTheMove >= 1.0) {
            // Slow movement on foot is the busiest; the faster you go, the less there is to see in each minute.
            val footLike = (1.0 - (max(v, onTheMove) - 3.0) / 20.0).coerceIn(0.25, 1.0)
            b = 0.6 * footLike + 0.4 * wander(samples, ms)
        }
        if (events.any { kotlin.math.abs(it.ms - ms) <= EVENT_NEAR_MS }) b += 0.5
        return b.coerceIn(0.0, 1.0)
    }

    /** 0 for a straight line to 1 for going round in circles, over a few minutes either side of [ms]. */
    fun wander(samples: List<Sample>, ms: Long): Double {
        if (samples.size < 3) return 0.0
        val from = indexAtOrBefore(samples, ms - WANDER_WINDOW_MS)
        val to = indexAtOrBefore(samples, ms + WANDER_WINDOW_MS)
        if (to - from < 2) return 0.0
        var path = 0.0
        for (i in from + 1..to) {
            if (samples[i].ms - samples[i - 1].ms > MAX_GAP_MS) continue
            path += PlaceRules.distanceM(samples[i - 1].lat, samples[i - 1].lng, samples[i].lat, samples[i].lng)
        }
        val straight = PlaceRules.distanceM(samples[from].lat, samples[from].lng, samples[to].lat, samples[to].lng)
        if (path < 20.0) return 0.0
        if (straight < 5.0) return 1.0
        return ((path / straight - 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    /** What smart speed multiplies the speed by at [ms]: [Smart.quietBoost] when quiet, [Smart.busySlow] when busy. */
    fun smartFactor(smart: Smart, samples: List<Sample>, events: List<Event>, ms: Long): Double {
        val busy = busyness(samples, events, ms)
        return smart.quietBoost * (smart.busySlow / smart.quietBoost).pow(busy)
    }

    /** One step of easing the factor towards [target] over [dtMs], so the clock speeds up and slows down smoothly. */
    fun easeFactor(current: Double, target: Double, dtMs: Long): Double =
        current + (target - current) * (1.0 - exp(-dtMs / 600.0))

    /** The distance covered up to each reading, in metres (jumps across a long silence are not counted). */
    fun cumulative(samples: List<Sample>): DoubleArray {
        val out = DoubleArray(samples.size)
        for (i in 1 until samples.size) {
            val gap = samples[i].ms - samples[i - 1].ms
            val step = if (gap > MAX_GAP_MS) 0.0 else PlaceRules.distanceM(samples[i - 1].lat, samples[i - 1].lng, samples[i].lat, samples[i].lng)
            out[i] = out[i - 1] + step
        }
        return out
    }

    /** How far you had gone by [ms], using [cumulative]. */
    fun distanceAt(samples: List<Sample>, cumulative: DoubleArray, ms: Long): Double {
        if (samples.isEmpty() || cumulative.size != samples.size || ms < samples.first().ms) return 0.0
        val i = indexAtOrBefore(samples, ms)
        if (i >= samples.lastIndex) return cumulative[i]
        val gap = samples[i + 1].ms - samples[i].ms
        if (gap > MAX_GAP_MS || gap <= 0L) return cumulative[i]
        val f = ((ms - samples[i].ms).toDouble() / gap).coerceIn(0.0, 1.0)
        return cumulative[i] + (cumulative[i + 1] - cumulative[i]) * f
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
