package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.min

/** Removes the position readings that cannot be right, so stays and routes are worked out from good ones. */
object FixFilter {
    /** A jump faster than this (252 km/h) is a bad reading. */
    const val MAX_SPEED_MS = 70.0

    /** A reading this far from both its neighbours, while they are close to each other, is a glitch. */
    const val SPIKE_M = 300.0
    const val NEIGHBOURS_M = 100.0

    /** After this many readings in a row rejected as too fast, the reading they were measured against was the bad one. */
    private const val RECOVER_AFTER = 3

    fun clean(fixes: List<RawFixEntity>): List<RawFixEntity> {
        val sorted = fixes
            .filter { it.acc <= PlaceRules.MAX_ACCURACY_M }
            .sortedBy { it.ms }
            .distinctBy { it.ms }
        val n = sorted.size
        val noSpikes = sorted.filterIndexed { i, f ->
            when {
                n < 3 -> true
                // A bad first or last reading: far from the next one, while the two after it agree with each other.
                i == 0 -> !(dist(f, sorted[1]) > SPIKE_M && dist(sorted[1], sorted[2]) <= NEIGHBOURS_M)
                i == n - 1 -> !(dist(f, sorted[n - 2]) > SPIKE_M && dist(sorted[n - 2], sorted[n - 3]) <= NEIGHBOURS_M)
                else -> {
                    val a = sorted[i - 1]
                    val b = sorted[i + 1]
                    val away = min(dist(a, f), dist(f, b))
                    !(away > SPIKE_M && dist(a, b) <= NEIGHBOURS_M)
                }
            }
        }
        val kept = ArrayList<RawFixEntity>()
        var rejected = 0
        for (f in noSpikes) {
            val last = kept.lastOrNull()
            if (last == null) {
                kept.add(f)
                continue
            }
            val seconds = (f.ms - last.ms) / 1000.0
            val d = dist(last, f)
            // Faster than a car and also further than the readings' own error could explain.
            val tooFast = seconds > 0.0 && d / seconds > MAX_SPEED_MS && d > 200.0
            if (tooFast && rejected < RECOVER_AFTER) {
                rejected++
            } else {
                rejected = 0
                kept.add(f)
            }
        }
        return kept
    }

    internal fun dist(a: RawFixEntity, b: RawFixEntity): Double = PlaceRules.distanceM(a.lat, a.lng, b.lat, b.lng)
}

/** A stay found by looking at all of a stretch of readings together. */
class BuiltStay(val startMs: Long, val endMs: Long, val lat: Double, val lng: Double, val n: Int, val bridgedMs: Long)

/**
 * Finds stays in a stretch of readings with hindsight (the live detector, [PlaceEngine], only sees one reading at a time).
 * Readings close together in space are a run; a run can wander with its average but never further than 1.5 times the
 * radius from where it began. Runs at the same place either side of a silence are one stay (the phone was idle there),
 * but a silence that ends somewhere else is left as a hole and never turned into a visit.
 */
object StayBuilder {
    const val RADIUS_M = PlaceEngine.STAY_RADIUS_M
    const val DRIFT_CAP = 1.5

    /** A silence longer than this ends a run; bridging may then join it to the next run. */
    const val SWEEP_GAP_MS = 60 * 60_000L

    /** The longest silence at one place that still counts as one stay. */
    const val BRIDGE_CAP_MS = 24 * 60 * 60_000L

    private class Run(first: RawFixEntity) {
        val points = arrayListOf(first)
        var sumLat = first.lat
        var sumLng = first.lng
        val firstFix = first
        var bridgedMs = 0L
        val lat get() = sumLat / points.size
        val lng get() = sumLng / points.size
        val startMs get() = points.first().ms
        val endMs get() = points.last().ms
        fun add(p: RawFixEntity) {
            points.add(p)
            sumLat += p.lat
            sumLng += p.lng
        }
    }

    /** [fixes] must already be cleaned (see [FixFilter.clean]). */
    fun build(fixes: List<RawFixEntity>): List<BuiltStay> {
        val runs = sweep(fixes.sortedBy { it.ms })
        val merged = merge(runs)
        val kept = merged.filter { it.endMs - it.startMs >= PlaceRules.MIN_STAY_MS && it.points.size >= 2 }
        // Runs dropped for being short may have been what kept two visits apart; join again if they are close.
        return merge(kept).filter { it.endMs - it.startMs >= PlaceRules.MIN_STAY_MS }.map { finalize(it) }
    }

    private fun sweep(sorted: List<RawFixEntity>): List<Run> {
        val out = ArrayList<Run>()
        var open: Run? = null
        for (p in sorted) {
            val r = open
            if (r == null) {
                open = Run(p)
                continue
            }
            val slack = min(p.acc.toDouble(), 50.0)
            val near = PlaceRules.distanceM(r.lat, r.lng, p.lat, p.lng) <= RADIUS_M + slack
            val drift = PlaceRules.distanceM(r.firstFix.lat, r.firstFix.lng, p.lat, p.lng) <= RADIUS_M * DRIFT_CAP + slack
            if (p.ms - r.endMs > SWEEP_GAP_MS || !near || !drift) {
                out.add(r)
                open = Run(p)
            } else {
                r.add(p)
            }
        }
        open?.let { out.add(it) }
        return out
    }

    private fun merge(runs: List<Run>): List<Run> {
        val out = ArrayList<Run>()
        for (run in runs) {
            val prev = out.lastOrNull()
            if (prev != null && joinable(prev, run)) {
                val gap = run.startMs - prev.endMs
                if (gap > SWEEP_GAP_MS) prev.bridgedMs += gap
                prev.bridgedMs += run.bridgedMs
                run.points.forEach { prev.add(it) }
            } else {
                out.add(run)
            }
        }
        return out
    }

    private fun joinable(a: Run, b: Run): Boolean {
        val gap = b.startMs - a.endMs
        val d = PlaceRules.distanceM(a.lat, a.lng, b.lat, b.lng)
        return (gap <= BRIDGE_CAP_MS && d <= RADIUS_M) || (gap <= PlaceRules.MERGE_GAP_MS && d <= PlaceRules.MERGE_DISTANCE_M)
    }

    /** The centre counts each reading by how accurate it was. */
    private fun finalize(run: Run): BuiltStay {
        var w = 0.0
        var lat = 0.0
        var lng = 0.0
        for (p in run.points) {
            val weight = 1.0 / max(p.acc.toDouble(), 5.0)
            lat += p.lat * weight
            lng += p.lng * weight
            w += weight
        }
        return BuiltStay(run.startMs, run.endMs, lat / w, lng / w, run.points.size, run.bridgedMs)
    }
}

/** Facts about how well the readings are arriving, so you can see whether the phone lets the app run. */
object FixStats {
    /** Readings in each of the [hours] hours before [nowMs], oldest hour first. */
    fun hourly(fixes: List<RawFixEntity>, nowMs: Long, hours: Int = 24): List<Int> {
        val counts = IntArray(hours)
        for (f in fixes) {
            val ago = nowMs - f.ms
            if (ago < 0 || ago >= hours * 3_600_000L) continue
            counts[hours - 1 - (ago / 3_600_000L).toInt()]++
        }
        return counts.toList()
    }

    class Gap(val startMs: Long, val lengthMs: Long)

    /** The longest stretch without a reading between [fromMs] and [toMs], counting the ends. Null if there are no readings at all. */
    fun longestGap(fixes: List<RawFixEntity>, fromMs: Long, toMs: Long): Gap? {
        val times = fixes.map { it.ms }.filter { it in fromMs..toMs }.sorted()
        if (times.isEmpty()) return null
        var best = Gap(fromMs, times.first() - fromMs)
        for (i in 1 until times.size) {
            if (times[i] - times[i - 1] > best.lengthMs) best = Gap(times[i - 1], times[i] - times[i - 1])
        }
        if (toMs - times.last() > best.lengthMs) best = Gap(times.last(), toMs - times.last())
        return best
    }
}
