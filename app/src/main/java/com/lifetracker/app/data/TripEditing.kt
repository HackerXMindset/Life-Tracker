package com.lifetracker.app.data

import kotlin.math.max

/**
 * Everything one merge, split or undo changes, worked out without the database so it can be tested. The one that applies it
 * saves the rows and the readings; nothing here removes a reading, a trip or a stay (a trip that is joined to another is only
 * hidden, and comes back with Undo).
 */
class TripChange(
    /** Trips to save (replacing the row with the same id). */
    val trips: List<TripEntity>,
    /** Readings to add to trips. They are copies: the readings a trip already had stay where they are. */
    val points: List<TripPointEntity>,
    /** What you changed on the Timeline to save (a trip or a stay hidden, or shown again). */
    val timelineEdits: List<TimelineEditEntity>,
    /** The entry to add to the list of merges and splits, or null for an undo. */
    val journal: TripEditEntity?,
    /** The merge or split this undoes (it is marked undone, never removed), or null. */
    val undoneEditId: String?,
)

/**
 * Merging two trips into one, splitting one into two, and undoing either. A merge keeps the earlier trip's row (and so its id,
 * its "How did you go?" answer and its place at the start), a split keeps the first part under the old id. The distance and
 * speeds are always worked out again from the readings.
 */
object TripEditing {
    /** The reason written on a trip that was joined to the one before it. */
    const val JOINED_TRIP_LABEL = "Joined to the trip before it"

    /** The reason written on a stay that lies inside a joined trip. */
    const val JOINED_STAY_LABEL = "Stay inside a joined trip"

    /** A stay counts as "between" two trips when it lies between them give or take this much (a stay lasts at least 3 minutes). */
    const val STAY_SLACK_MS = 2 * 60_000L

    /** True for something hidden by a merge. It comes back with Undo on the trip page, not with "Bring back" on the Timeline. */
    fun isJoinedAway(edit: TimelineEditEntity?): Boolean =
        edit != null && edit.hidden && (edit.label == JOINED_TRIP_LABEL || edit.label == JOINED_STAY_LABEL)

    // ---- activity text ("WALKING=60000,IN_VEHICLE=540000")

    fun addActivity(a: String, b: String): String {
        val sum = TripModes.parseActivity(a).toMutableMap()
        for ((k, v) in TripModes.parseActivity(b)) sum[k] = (sum[k] ?: 0L) + v
        return TripModes.activityText(sum)
    }

    fun subtractActivity(a: String, b: String): String {
        val rest = TripModes.parseActivity(a).toMutableMap()
        for ((k, v) in TripModes.parseActivity(b)) rest[k] = max(0L, (rest[k] ?: 0L) - v)
        return TripModes.activityText(rest)
    }

    // ---- the numbers of a trip

    /** The readings that belong to [trip]: those between its start and end, once each, oldest first. */
    fun pointsOf(trip: TripEntity, points: List<TripPointEntity>): List<TripPointEntity> =
        points.filter { it.ms in trip.startMs..trip.endMs }.sortedBy { it.ms }.distinctBy { it.ms }

    private fun measure(trip: TripEntity, own: List<TripPointEntity>): TripStats {
        val clean = FixFilter.clean(own.map { RawFixEntity(it.ms, it.lat, it.lng, it.acc, steps = it.steps) })
            .map { TripPointEntity(trip.id, it.ms, it.lat, it.lng, it.acc, it.steps) }
        return TripRules.stats(clean, trip.startMs, trip.endMs)
    }

    /** [trip] with its distance and speeds worked out again from [points] (only the ones between its start and end count). */
    fun withStats(trip: TripEntity, points: List<TripPointEntity>, strideM: Double): TripEntity {
        val own = pointsOf(trip, points)
        if (own.size < 2) return trip.copy(distanceM = 0, medianKmh = 0, topKmh = 0)
        val stats = measure(trip, own)
        val distance = TripRules.adjust(stats.distanceM, stats.durationMs, trip.mode, TripRules.stepsBetween(own), strideM)
        return trip.copy(distanceM = distance, medianKmh = stats.medianKmh, topKmh = stats.topKmh)
    }

    // ---- hiding and showing (the same rows the Timeline uses, so the Timeline obeys at once)

    private fun hide(old: TimelineEditEntity?, kind: String, refId: String, startMs: Long, label: String, nowMs: Long) =
        TimelineEdits.edit(
            kind, refId,
            startMs = old?.startMs?.takeIf { it >= 0 } ?: startMs,
            endMs = old?.endMs ?: -1L,
            activityId = old?.activityId.orEmpty(),
            hidden = true,
            now = nowMs,
            label = label,
        )

    private fun show(old: TimelineEditEntity, label: String, nowMs: Long) = old.copy(hidden = false, label = label, updatedMs = nowMs)

    private fun editOf(edits: Map<String, TimelineEditEntity>, kind: String, id: String) = edits[TimelineEdits.key(kind, id)]

    /** The stays that lie between two trips: those inside the stretch from where [earlierEndMs] to [laterStartMs]. */
    fun staysBetween(visits: List<PlaceVisitEntity>, earlierEndMs: Long, laterStartMs: Long): List<PlaceVisitEntity> =
        visits.filter { !it.ongoing && it.startMs >= earlierEndMs - STAY_SLACK_MS && it.endMs <= laterStartMs + STAY_SLACK_MS }

    // ---- merge

    /**
     * Joins [later] to the end of [earlier]. The readings of both come along, and so do the readings taken between them
     * ([between]). [later] and the stays between the two are hidden, not removed. Null when the two do not follow each other.
     */
    fun merge(
        earlier: TripEntity,
        later: TripEntity,
        earlierPoints: List<TripPointEntity>,
        laterPoints: List<TripPointEntity>,
        between: List<RawFixEntity>,
        visits: List<PlaceVisitEntity>,
        edits: Map<String, TimelineEditEntity>,
        strideM: Double,
        nowMs: Long,
        editId: String,
    ): TripChange? {
        if (earlier.id == later.id || later.startMs < earlier.endMs) return null
        val all = (
            pointsOf(earlier, earlierPoints) +
                pointsOf(later, laterPoints) +
                between.filter { it.ms in earlier.endMs..later.startMs }.map { TripPointEntity(earlier.id, it.ms, it.lat, it.lng, it.acc, it.steps) }
            ).map { it.copy(tripId = earlier.id) }.sortedBy { it.ms }.distinctBy { it.ms }
        val joined = withStats(
            earlier.copy(endMs = later.endMs, toPlaceId = later.toPlaceId, activity = addActivity(earlier.activity, later.activity)),
            all,
            strideM,
        )
        val hidden = ArrayList<TimelineEditEntity>()
        hidden += hide(editOf(edits, TimelineEdits.TRIP, later.id), TimelineEdits.TRIP, later.id, later.startMs, JOINED_TRIP_LABEL, nowMs)
        for (v in staysBetween(visits, earlier.endMs, later.startMs)) {
            hidden += hide(editOf(edits, TimelineEdits.VISIT, v.id), TimelineEdits.VISIT, v.id, v.startMs, JOINED_STAY_LABEL, nowMs)
        }
        return TripChange(
            trips = listOf(joined),
            points = all,
            timelineEdits = hidden,
            journal = TripEditEntity(editId, TripEditKind.MERGE, earlier.endMs, later.startMs, nowMs),
            undoneEditId = null,
        )
    }

    /** The trips a merge joined, found again by the times at its boundary: the one that now holds both, and the one that was hidden. */
    fun joinedPair(edit: TripEditEntity, trips: List<TripEntity>): Pair<TripEntity, TripEntity>? {
        if (edit.kind != TripEditKind.MERGE) return null
        val later = trips.firstOrNull { it.startMs == edit.otherMs && it.startMs >= edit.atMs } ?: return null
        val earlier = trips.firstOrNull { it.id != later.id && it.startMs < edit.atMs && it.endMs == later.endMs } ?: return null
        return earlier to later
    }

    /** Undoes a merge: the earlier trip ends where it did, the later one and the stays between come back. */
    fun unmerge(
        edit: TripEditEntity,
        earlier: TripEntity,
        later: TripEntity,
        earlierPoints: List<TripPointEntity>,
        visits: List<PlaceVisitEntity>,
        edits: Map<String, TimelineEditEntity>,
        strideM: Double,
        nowMs: Long,
    ): TripChange {
        val restored = withStats(
            earlier.copy(endMs = edit.atMs, toPlaceId = later.fromPlaceId, activity = subtractActivity(earlier.activity, later.activity)),
            earlierPoints,
            strideM,
        )
        val shown = ArrayList<TimelineEditEntity>()
        editOf(edits, TimelineEdits.TRIP, later.id)?.let { shown += show(it, "Trip", nowMs) }
        for (v in staysBetween(visits, edit.atMs, edit.otherMs)) {
            val old = editOf(edits, TimelineEdits.VISIT, v.id)
            if (isJoinedAway(old)) shown += show(old!!, "", nowMs)
        }
        return TripChange(listOf(restored), emptyList(), shown, null, edit.id)
    }

    // ---- split

    /**
     * Cuts [trip] in two at the reading taken at [atMs]. The first part keeps the trip's row and id (so your answer to "How did you
     * go?" stays with it), the second part is a new trip with the id [newId] that gets its own guess. Each part needs at least two
     * readings. The phone's walking and driving times cannot be cut by the clock, so they stay with the first part.
     */
    fun split(
        trip: TripEntity,
        points: List<TripPointEntity>,
        atMs: Long,
        newId: String,
        strideM: Double,
        nowMs: Long,
        editId: String,
    ): TripChange? {
        val own = pointsOf(trip, points)
        val asFixes = own.map { RawFixEntity(it.ms, it.lat, it.lng, it.acc, steps = it.steps) }
        if (!TripEditRules.canSplit(Segment(asFixes), atMs)) return null
        val firstPoints = own.filter { it.ms <= atMs }
        val secondPoints = own.filter { it.ms >= atMs }.map { it.copy(tripId = newId) }
        val first = withStats(trip.copy(endMs = atMs, toPlaceId = ""), firstPoints, strideM)
        val secondBase = TripEntity(newId, atMs, trip.endMs, "", trip.toPlaceId, 0, 0, 0, "", "vehicle", "auto")
        val guess = TripModes.guess(measure(secondBase, secondPoints), emptyMap(), "", "", emptyList())
        val second = withStats(secondBase.copy(mode = guess), secondPoints, strideM)
        return TripChange(
            trips = listOf(first, second),
            points = secondPoints,
            timelineEdits = emptyList(),
            journal = TripEditEntity(editId, TripEditKind.SPLIT, atMs, 0L, nowMs),
            undoneEditId = null,
        )
    }

    /** The two trips a split made, found again by the time of the cut. */
    fun splitPair(edit: TripEditEntity, trips: List<TripEntity>): Pair<TripEntity, TripEntity>? {
        if (edit.kind != TripEditKind.SPLIT) return null
        val first = trips.firstOrNull { it.endMs == edit.atMs && it.startMs < edit.atMs } ?: return null
        val second = trips.firstOrNull { it.id != first.id && it.startMs == edit.atMs && it.endMs > edit.atMs } ?: return null
        return first to second
    }

    /** Undoes a split: the first part takes the second back, which is hidden (kept) like any trip that was joined. */
    fun unsplit(
        edit: TripEditEntity,
        first: TripEntity,
        second: TripEntity,
        firstPoints: List<TripPointEntity>,
        secondPoints: List<TripPointEntity>,
        edits: Map<String, TimelineEditEntity>,
        strideM: Double,
        nowMs: Long,
    ): TripChange {
        val all = (pointsOf(first, firstPoints) + pointsOf(second, secondPoints).map { it.copy(tripId = first.id) })
            .sortedBy { it.ms }.distinctBy { it.ms }
        val joined = withStats(
            first.copy(endMs = second.endMs, toPlaceId = second.toPlaceId, activity = addActivity(first.activity, second.activity)),
            all,
            strideM,
        )
        val hidden = hide(editOf(edits, TimelineEdits.TRIP, second.id), TimelineEdits.TRIP, second.id, second.startMs, JOINED_TRIP_LABEL, nowMs)
        return TripChange(listOf(joined), all, listOf(hidden), null, edit.id)
    }

    // ---- which merge or split can be undone

    /**
     * The newer merge or split that sits on top of [edit] and has to be undone first, or null. [region] is the stretch of time of
     * the trip (or trips) the edit made: a newer change that touches it is on top.
     */
    fun blockedBy(edit: TripEditEntity, active: List<TripEditEntity>, region: LongRange): TripEditEntity? =
        active.firstOrNull {
            it.id != edit.id && it.undoneMs == 0L &&
                (it.createdMs > edit.createdMs || (it.createdMs == edit.createdMs && it.id > edit.id)) &&
                (it.atMs in region || (it.kind == TripEditKind.MERGE && it.otherMs in region))
        }
}
