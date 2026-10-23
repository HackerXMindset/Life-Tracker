package com.lifetracker.app.data

/**
 * Merges and splits of trips, applied on top of the trips worked out from the readings. An edit is found again by the times at
 * its boundary, not by a position in a list, so it still holds when the trips are worked out again. Readings are never changed.
 */
object TripEditRules {
    /** Each side of a split needs at least this many readings. */
    const val MIN_SIDE_POINTS = 2

    fun mergeEdit(id: String, earlier: Segment, later: Segment, nowMs: Long) =
        TripEditEntity(id, TripEditKind.MERGE, earlier.endMs, later.startMs, nowMs)

    fun splitEdit(id: String, atMs: Long, nowMs: Long) = TripEditEntity(id, TripEditKind.SPLIT, atMs, 0L, nowMs)

    /** Whether [segment] can be cut so that the reading at [atMs] starts the second part. */
    fun canSplit(segment: Segment, atMs: Long): Boolean =
        segment.points.any { it.ms == atMs } &&
            segment.points.count { it.ms < atMs } >= MIN_SIDE_POINTS &&
            segment.points.count { it.ms >= atMs } >= MIN_SIDE_POINTS

    /**
     * [edits] in force, [segments] as cut, [fixes] all the readings (a merge brings along the readings between two trips).
     * An edit that no longer fits (its boundary is gone, or a side would be too small) is skipped.
     */
    fun apply(segments: List<Segment>, fixes: List<RawFixEntity>, edits: List<TripEditEntity>): List<Segment> {
        var list = segments.sortedBy { it.startMs }
        for (e in edits.sortedWith(compareBy({ it.createdMs }, { it.id }))) {
            list = when (e.kind) {
                TripEditKind.MERGE -> merged(list, fixes, e)
                TripEditKind.SPLIT -> split(list, e)
                else -> list
            }
        }
        return list
    }

    private fun merged(list: List<Segment>, fixes: List<RawFixEntity>, e: TripEditEntity): List<Segment> {
        val i = list.indices.firstOrNull { it + 1 < list.size && list[it].endMs == e.atMs && list[it + 1].startMs == e.otherMs } ?: return list
        val a = list[i]
        val b = list[i + 1]
        val between = fixes.filter { it.ms >= a.startMs && it.ms <= b.endMs }
        val points = (a.points + b.points + between).distinctBy { it.ms }.sortedBy { it.ms }
        return list.subList(0, i) + Segment(points, a.manual || b.manual) + list.subList(i + 2, list.size)
    }

    private fun split(list: List<Segment>, e: TripEditEntity): List<Segment> {
        val i = list.indexOfFirst { canSplit(it, e.atMs) }
        if (i < 0) return list
        val s = list[i]
        val left = Segment(s.points.filter { it.ms < e.atMs }, true)
        val right = Segment(s.points.filter { it.ms >= e.atMs }, true)
        return list.subList(0, i) + left + right + list.subList(i + 1, list.size)
    }

    /** Everything together: cut the readings, apply the edits, throw out the tiny ones. */
    fun trips(
        fixes: List<RawFixEntity>,
        stays: List<BuiltStay>,
        edits: List<TripEditEntity>,
        gapMs: Long = TripCutter.GAP_MS,
        minFarthestM: Int = TripCutter.MIN_FARTHEST_M,
    ): List<Segment> = TripCutter.keep(apply(TripCutter.cut(fixes, stays, gapMs), fixes, edits), minFarthestM)
}
