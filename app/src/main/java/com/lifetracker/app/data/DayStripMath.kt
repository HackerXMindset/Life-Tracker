package com.lifetracker.app.data

import kotlin.math.abs

/** The sums behind the 24-hour strip on the Timeline: where along the line a time is, and which stretch a tap landed on. */
object DayStripMath {
    /** How far along the day [ms] is, 0 at the start and 1 at the end. Times outside the day are pulled to the edge. */
    fun fraction(ms: Long, dayStartMs: Long, dayMs: Long): Float =
        if (dayMs <= 0L) 0f else ((ms - dayStartMs).toDouble() / dayMs).toFloat().coerceIn(0f, 1f)

    /**
     * The position of the stretch that [ms] falls in, or, when it falls between stretches, the nearest one within
     * [toleranceMs] (so a thin stretch can still be hit with a finger). Null when nothing is close.
     */
    fun hit(stretches: List<Pair<Long, Long>>, ms: Long, toleranceMs: Long): Int? {
        stretches.forEachIndexed { i, (start, end) -> if (ms in start until end) return i }
        var best: Int? = null
        var bestDistance = Long.MAX_VALUE
        stretches.forEachIndexed { i, (start, end) ->
            val d = if (ms < start) start - ms else abs(ms - end)
            if (d <= toleranceMs && d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return best
    }
}
