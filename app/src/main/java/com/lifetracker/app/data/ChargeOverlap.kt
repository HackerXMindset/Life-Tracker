package com.lifetracker.app.data

/** Which parts of the day the phone was on charge, and which rows of the Timeline overlap them. */
object ChargeOverlap {
    /** At least this much (or the whole row, if it is shorter) has to be on charge for the small bolt to show. */
    const val MIN_MS = 60_000L

    /** How much of [startMs, endMs] lies inside the charging [spans] (start, end), which may touch but should not overlap each other. */
    fun overlapMs(startMs: Long, endMs: Long, spans: List<Pair<Long, Long>>): Long {
        val end = maxOf(endMs, startMs + 1)
        var total = 0L
        for ((a, b) in spans) {
            val lo = maxOf(a, startMs)
            val hi = minOf(b, end)
            if (hi > lo) total += hi - lo
        }
        return total
    }

    /** True when the phone was charging for at least [MIN_MS] of the row (or all of it when it is shorter than that). */
    fun during(startMs: Long, endMs: Long, spans: List<Pair<Long, Long>>): Boolean {
        if (spans.isEmpty()) return false
        val length = maxOf(endMs - startMs, 1L)
        return overlapMs(startMs, endMs, spans) >= minOf(MIN_MS, length)
    }
}
