package com.lifetracker.app.data

import kotlin.math.min

/**
 * Fast movement (a train, a car, a bus) switches recording to a dense pace by itself, so the route is drawn exactly even in
 * Gentle mode. It only ever speeds recording up, never slows it, and it holds for a few minutes after the last fast reading so
 * a station stop does not drop back to the slow pace and lose the next stretch. No Android in here, so it can be tested.
 */
object FastPace {
    /** 25 km/h: faster than anyone walks or cycles, slower than every train, bus and car on the move. */
    const val FAST_MS = 7.0f

    /** Seconds between readings while moving fast. */
    const val DENSE_SEC = 10

    /** How long the dense pace goes on after the last fast reading. */
    const val HOLD_MS = 3 * 60_000L

    /** Whether one reading, by the phone's own speed, was fast. */
    fun isFast(speedMs: Float): Boolean = speedMs >= FAST_MS

    /** The pace to use: [intervalSec] or the dense pace, whichever is quicker, while [lastFastMs] is recent. */
    fun cap(intervalSec: Int, lastFastMs: Long, nowMs: Long): Int =
        if (lastFastMs > 0L && nowMs >= lastFastMs && nowMs - lastFastMs <= HOLD_MS) min(intervalSec, DENSE_SEC) else intervalSec

    /**
     * The time of the last reading in [recent] (oldest first) that came after a fast step, or 0. Two fast steps are needed, so one
     * jumpy reading does not switch it on. A step's speed is the phone's own when it gave one, else distance over time.
     */
    fun lastFastMs(recent: List<RawFixEntity>): Long {
        var last = 0L
        var run = 0
        for (i in 1 until recent.size) {
            val a = recent[i - 1]
            val b = recent[i]
            val seconds = (b.ms - a.ms) / 1000.0
            val v = when {
                b.speed >= 0f -> b.speed.toDouble()
                seconds > 0.0 -> FixFilter.dist(a, b) / seconds
                else -> 0.0
            }
            if (v >= FAST_MS) {
                run++
                if (run >= 2) last = b.ms
            } else {
                run = 0
            }
        }
        return last
    }
}
