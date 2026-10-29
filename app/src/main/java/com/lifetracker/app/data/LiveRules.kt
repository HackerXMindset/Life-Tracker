package com.lifetracker.app.data

import kotlin.math.max

/**
 * What the phone is doing right now, worked out from the latest position readings: recording or not, still or on the move,
 * since when, how far so far. Free of Android so it can be tested. The live map and the "On the move" row both read this.
 */
object LiveRules {
    enum class State { OFF, PAUSED, WAITING, STILL, MOVING }

    class Live(
        val state: State,
        /** When the newest reading was taken, or 0 when there is none. */
        val lastFixMs: Long = 0L,
        /** When the current still or moving stretch began (0 for the other states). */
        val sinceMs: Long = 0L,
        /** Metres walked, cycled or driven since [sinceMs] while moving (0 otherwise). */
        val distanceM: Int = 0,
        /** Speed over the last half minute, in km/h. */
        val kmh: Int = 0,
        /** How exact the newest reading is, in metres (0 when there is none). */
        val accuracyM: Int = 0,
    )

    /** Readings further apart than this mean they stopped (still, or the phone was off) and began again. */
    const val GAP_MS = 5 * 60_000L

    /** Being still for this long, inside the circle below, means a stop. */
    const val STILL_MS = 2 * 60_000L

    /** Looking back this far decides whether you are moving. */
    const val WINDOW_MS = 3 * 60_000L

    /** The phone's own speed reading above this (m/s) counts as moving even before the distance shows. */
    const val MOVING_SPEED_MS = 2.0f

    /** How old the newest reading may be before we say "waiting for a position". */
    fun staleAfterMs(intervalSec: Int): Long = max(3 * 60_000L, intervalSec * 3_000L)

    private fun radiusM(f: RawFixEntity): Double = max(40.0, 2.0 * f.acc)

    private fun dist(a: RawFixEntity, b: RawFixEntity): Double = PlaceRules.distanceM(a.lat, a.lng, b.lat, b.lng)

    /** [fixes] are the readings of the last few hours, oldest first. */
    fun read(fixes: List<RawFixEntity>, nowMs: Long, tracking: Boolean, paused: Boolean, intervalSec: Int): Live {
        val c = FixFilter.clean(fixes.sortedBy { it.ms })
        val last = c.lastOrNull()
        val lastMs = last?.ms ?: 0L
        val acc = last?.acc?.toInt() ?: 0
        if (!tracking) return Live(State.OFF, lastMs, accuracyM = acc)
        if (paused) return Live(State.PAUSED, lastMs, accuracyM = acc)
        if (last == null || nowMs - last.ms > staleAfterMs(intervalSec)) return Live(State.WAITING, lastMs, accuracyM = acc)

        val recent = c.filter { it.ms >= last.ms - WINDOW_MS }
        val spread = recent.maxOf { dist(it, last) }
        val moving = recent.size >= 2 && (spread > radiusM(last) || last.speed >= MOVING_SPEED_MS)
        val kmh = speedKmh(c)
        if (!moving) {
            var k = c.size - 1
            while (k > 0 && dist(c[k - 1], last) <= radiusM(last) && c[k].ms - c[k - 1].ms < GAP_MS) k--
            return Live(State.STILL, lastMs, sinceMs = c[k].ms, kmh = 0, accuracyM = acc)
        }
        val start = runStart(c)
        var total = 0.0
        for (i in start + 1 until c.size) {
            val d = dist(c[i - 1], c[i])
            if (d >= 2.0) total += d
        }
        return Live(State.MOVING, lastMs, sinceMs = c[start].ms, distanceM = total.toInt(), kmh = kmh, accuracyM = acc)
    }

    /** The first reading of the current run: just after the latest stop, or the oldest one when there is no stop in the list. */
    private fun runStart(c: List<RawFixEntity>): Int {
        for (i in c.size - 1 downTo 1) {
            if (c[i].ms - c[i - 1].ms >= GAP_MS) return i
            if (stillAt(c, i)) return i
        }
        return 0
    }

    /** True when every reading in the two minutes up to [i] lies inside [i]'s circle, and the readings do reach back that far. */
    private fun stillAt(c: List<RawFixEntity>, i: Int): Boolean {
        val from = c[i].ms - STILL_MS
        var k = i
        while (k >= 0) {
            if (dist(c[k], c[i]) > radiusM(c[i])) return false
            if (c[k].ms <= from) return true
            k--
        }
        return false
    }

    private fun speedKmh(c: List<RawFixEntity>): Int {
        val last = c.last()
        val before = c.lastOrNull { last.ms - it.ms >= 30_000L } ?: return if (last.speed >= 0f) (last.speed * 3.6f).toInt() else 0
        val seconds = (last.ms - before.ms) / 1000.0
        return if (seconds <= 0.0) 0 else (dist(before, last) / seconds * 3.6).toInt()
    }

    /** "Moving for 6 min", "Still for 12 min". */
    fun sinceText(live: Live, nowMs: Long): String {
        val min = ((nowMs - live.sinceMs) / 60_000L).toInt().coerceAtLeast(0)
        val span = if (min < 1) "under a minute" else if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
        return when (live.state) {
            State.MOVING -> "On the move for $span"
            State.STILL -> "Still for $span"
            State.OFF -> "Recording is off"
            State.PAUSED -> "Recording is paused"
            State.WAITING -> "Waiting for a position"
        }
    }
}
