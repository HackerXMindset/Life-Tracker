package com.lifetracker.app.data

import kotlin.math.max

/** A stretch of readings that is (or may become) one trip. [manual] is true for a piece made by a split you did by hand. */
class Segment(val points: List<RawFixEntity>, val manual: Boolean = false) {
    val startMs: Long get() = points.first().ms
    val endMs: Long get() = points.last().ms

    /** How far from where it began the stretch ever got, less each reading's own error. */
    fun farthestM(): Int {
        val first = points.first()
        var far = 0.0
        for (p in points) far = max(far, PlaceRules.awayM(first.lat, first.lng, p.lat, p.lng, p.acc))
        return far.toInt()
    }
}

/** Turns readings into trips: the readings between stays, cut again at long silences. */
object TripCutter {
    /** A silence this long ends a trip. */
    const val GAP_MS = 15 * 60_000L

    /** A trip must get this far from where it began to be kept (a trip you split by hand is exempt). */
    const val MIN_FARTHEST_M = 100

    /**
     * Every candidate stretch, short or not (merges and splits are applied to these before [keep] throws out the tiny ones).
     * Readings strictly inside a stay are left out; the stay's first reading ends the trip that arrives and its last reading starts
     * the trip that leaves. Without stays the readings are cut at silences only.
     */
    fun cut(fixes: List<RawFixEntity>, stays: List<BuiltStay> = emptyList(), gapMs: Long = GAP_MS): List<Segment> {
        val sorted = fixes.sortedBy { it.ms }.distinctBy { it.ms }
        val runs = ArrayList<MutableList<RawFixEntity>>()
        var cur: MutableList<RawFixEntity>? = null
        var lastStay = -1
        for (f in sorted) {
            val s = stays.indexOfFirst { f.ms >= it.startMs && f.ms <= it.endMs }
            val strictlyInside = s >= 0 && f.ms > stays[s].startMs && f.ms < stays[s].endMs
            if (strictlyInside) {
                cur = null
                lastStay = s
                continue
            }
            val last = cur?.last()
            val startNew = cur == null || last == null || f.ms - last.ms >= gapMs || (s >= 0 && s == lastStay)
            if (startNew) {
                cur = ArrayList()
                runs.add(cur)
            }
            cur!!.add(f)
            lastStay = s
        }
        return runs.map { Segment(it) }
    }

    /** The stretches worth showing as trips: at least two readings and far enough (or split by hand). */
    fun keep(segments: List<Segment>, minFarthestM: Int = MIN_FARTHEST_M): List<Segment> =
        segments.filter { it.points.size >= 2 && (it.manual || it.farthestM() >= minFarthestM) }
}
