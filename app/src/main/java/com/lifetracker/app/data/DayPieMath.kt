package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.min

/**
 * The sums behind the Timeline's pie: time per task (the inner pie) and per place (the outer ring), where a tap landed,
 * and which places a task happened at (and the other way round). No Android in it, so it can be tested.
 */
object DayPieMath {
    /** One stretch of time with a name and a colour: a task, or a stay at a place. */
    class Piece(val label: String, val argb: Int, val startMs: Long, val endMs: Long) {
        val lengthMs: Long get() = endMs - startMs
    }

    /** All the time under one name. */
    class Slice(val label: String, val argb: Int, val ms: Long)

    /** Time per name, longest first, with the colour of the first stretch of that name. */
    fun slices(pieces: List<Piece>): List<Slice> =
        pieces.filter { it.lengthMs > 0 }
            .groupBy { it.label }
            .map { (label, list) -> Slice(label, list.first().argb, list.sumOf { it.lengthMs }) }
            .sortedByDescending { it.ms }

    fun totalMs(slices: List<Slice>): Long = slices.sumOf { it.ms }

    /** Whole percent of [total] that [ms] is. */
    fun percent(ms: Long, total: Long): Int = if (total <= 0L) 0 else Math.round(ms * 100.0 / total).toInt()

    /** For each slice: where it starts and how far it goes, in degrees clockwise from the top. */
    fun sweeps(slices: List<Slice>): List<Pair<Float, Float>> {
        val total = totalMs(slices).toDouble()
        if (total <= 0.0) return emptyList()
        var start = 0.0
        return slices.map {
            val sweep = it.ms / total * 360.0
            val out = start.toFloat() to sweep.toFloat()
            start += sweep
            out
        }
    }

    /** The slice an angle (degrees clockwise from the top) falls in, or null for an empty pie. */
    fun hit(slices: List<Slice>, angleDeg: Float): Int? {
        val sw = sweeps(slices)
        if (sw.isEmpty()) return null
        val a = ((angleDeg % 360f) + 360f) % 360f
        sw.forEachIndexed { i, (start, sweep) -> if (a >= start && a < start + sweep) return i }
        return sw.lastIndex
    }

    private fun overlap(a: Piece, b: Piece): Long = max(0L, min(a.endMs, b.endMs) - max(a.startMs, b.startMs))

    /**
     * Time shared between everything called [label] in [mine] and each name in [others], longest first.
     * For a task, [others] are the places; for a place, [others] are the tasks.
     */
    fun related(label: String, mine: List<Piece>, others: List<Piece>): List<Pair<String, Long>> {
        val own = mine.filter { it.label == label }
        return others.groupBy { it.label }
            .map { (name, list) -> name to own.sumOf { m -> list.sumOf { o -> overlap(m, o) } } }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
    }
}
