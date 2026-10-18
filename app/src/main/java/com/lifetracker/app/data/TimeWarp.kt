package com.lifetracker.app.data

import kotlin.math.max

/**
 * One map from a moment in the day to a position on the Timeline page, shared by the lines, the rows and the hour labels
 * so they can never disagree.
 *
 * Rows keep their real order and start time. A row sits at its real start (1 minute = [perMinPx]) unless the row above it
 * is taller than the time between them; then it sits just below that row, and the time in between is stretched to fit.
 * Between two rows the map is a straight line, so where nothing is crowded the scale is exact, and where it is crowded the
 * hours simply sit further apart. A moment never maps above an earlier moment.
 */
object TimeWarp {
    class Warp internal constructor(
        private val times: LongArray,
        private val ys: FloatArray,
        private val perMs: Float,
    ) {
        /** The position of [ms]: exact scale before the first row and after the last, a straight line in between. */
        fun y(ms: Long): Float {
            if (times.isEmpty()) return 0f
            val last = times.size - 1
            if (ms <= times[0]) return ys[0] + (ms - times[0]) * perMs
            if (ms >= times[last]) return ys[last] + (ms - times[last]) * perMs
            var lo = 0
            var hi = last
            while (hi - lo > 1) {
                val mid = (lo + hi) / 2
                if (times[mid] <= ms) lo = mid else hi = mid
            }
            val span = times[hi] - times[lo]
            return if (span <= 0L) ys[lo] else ys[lo] + (ys[hi] - ys[lo]) * ((ms - times[lo]).toFloat() / span)
        }
    }

    class Result(val warp: Warp, /** Where each row starts, in the order given. */ val tops: List<Float>)

    /**
     * [startsMs] are the rows' start times, oldest first; [heightsPx] how tall each row is (including the space after it).
     * [originMs] is the moment at position 0 (the hour the page starts on).
     */
    fun build(startsMs: List<Long>, heightsPx: List<Float>, originMs: Long, perMinPx: Float): Result {
        require(startsMs.size == heightsPx.size) { "one height per row" }
        val perMs = perMinPx / 60_000f
        val tops = ArrayList<Float>(startsMs.size)
        for (i in startsMs.indices) {
            val exact = if (i == 0) (startsMs[0] - originMs) * perMs else tops[i - 1] + (startsMs[i] - startsMs[i - 1]) * perMs
            val clear = if (i == 0) 0f else tops[i - 1] + heightsPx[i - 1]
            tops += max(max(exact, clear), 0f)
        }
        return Result(Warp(startsMs.toLongArray(), tops.toFloatArray(), perMs), tops)
    }
}
