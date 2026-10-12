package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.min

/**
 * Turns what is known about a day into non-overlapping stretches for the 24-hour bar and the pie, with no Android in it
 * so it can be tested. Each stretch is either something you logged yourself or something the phone collected (app use,
 * sleep, meals, trips). Where they overlap, what you logged wins.
 */
object DaySegments {
    class Seg(
        val startMs: Long,
        val endMs: Long,
        val argb: Int,
        val label: String,
        val logged: Boolean,
    ) {
        val lengthMs: Long get() = endMs - startMs
    }

    /** Cuts every stretch to [dayStartMs, dayEndMs) and removes overlaps: logged beats collected, then the later start wins. */
    fun flatten(segs: List<Seg>, dayStartMs: Long, dayEndMs: Long): List<Seg> {
        val clipped = segs.map { Seg(max(it.startMs, dayStartMs), min(it.endMs, dayEndMs), it.argb, it.label, it.logged) }
            .filter { it.endMs > it.startMs }
        val cuts = clipped.flatMap { listOf(it.startMs, it.endMs) }.distinct().sorted()
        val out = ArrayList<Seg>()
        for (i in 0 until cuts.size - 1) {
            val a = cuts[i]
            val b = cuts[i + 1]
            val pick = clipped
                .filter { it.startMs <= a && it.endMs >= b }
                .minWithOrNull(compareBy<Seg>({ !it.logged }, { -it.startMs })) ?: continue
            val last = out.lastOrNull()
            if (last != null && last.endMs == a && last.label == pick.label && last.argb == pick.argb && last.logged == pick.logged) {
                out[out.size - 1] = Seg(last.startMs, b, last.argb, last.label, last.logged)
            } else {
                out += Seg(a, b, pick.argb, pick.label, pick.logged)
            }
        }
        return out
    }

    fun totalMs(segs: List<Seg>): Long = segs.sumOf { it.lengthMs }

    /** Time per label, longest first, with the colour of the first stretch of that label. */
    fun byLabel(segs: List<Seg>): List<Triple<String, Int, Long>> =
        segs.groupBy { it.label }
            .map { (label, list) -> Triple(label, list.first().argb, list.sumOf { it.lengthMs }) }
            .sortedByDescending { it.third }
}
