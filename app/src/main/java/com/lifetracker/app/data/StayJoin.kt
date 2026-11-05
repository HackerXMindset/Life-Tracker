package com.lifetracker.app.data

import androidx.room.withTransaction

/**
 * Joining two stays that sit next to each other on the Timeline into one (you drag one onto the other). No Android in the
 * rules, so they can be tested.
 *
 * Nothing is deleted. The stay you keep is stretched over both (your time change, saved as a Timeline edit on it), the other is
 * hidden, and so is any walk or trip that lay between them (it becomes part of the stay). The hidden ones remember which stay they
 * went into, so "Separate again" on the Changes page puts everything back.
 */
object StayJoin {
    /** The reason written on the stay that was taken into the other one. The id of the kept stay and its earlier times follow after "|". */
    const val STAYS_LABEL = "Joined to the stay next to it"

    /** The reason written on a trip between two joined stays. The id of the kept stay follows after "|". */
    const val WALK_LABEL = "Between two joined stays"

    private const val SEP = "|"

    /** A trip counts as "between" the two stays when it lies between them give or take this much. */
    private const val SLACK_MS = 2 * 60_000L

    fun isJoined(edit: TimelineEditEntity?): Boolean =
        edit != null && edit.hidden && (edit.label.startsWith(STAYS_LABEL) || edit.label.startsWith(WALK_LABEL))

    /** Who must be the one kept, or null when you should choose: the stay still going, else the one with a name, else null for two different names. */
    fun forcedKeeper(earlier: PlaceVisitEntity, later: PlaceVisitEntity): PlaceVisitEntity? = when {
        later.ongoing -> later
        earlier.ongoing -> earlier
        earlier.placeId.isEmpty() && later.placeId.isNotEmpty() -> later
        later.placeId.isEmpty() -> earlier
        earlier.placeId == later.placeId -> earlier
        else -> null
    }

    /** What a join saves: Timeline edits to write. */
    class Change(val edits: List<TimelineEditEntity>)

    private fun hidden(old: TimelineEditEntity?, kind: String, id: String, startMs: Long, label: String, now: Long) =
        TimelineEdits.edit(
            kind, id,
            startMs = old?.startMs?.takeIf { it >= 0 } ?: startMs,
            endMs = old?.endMs ?: -1L,
            activityId = old?.activityId.orEmpty(),
            hidden = true, now = now, label = label,
        )

    /**
     * Joins the stay [drop] into [keep] (they are next to each other). The times are the ones shown on the Timeline ([keepTimes],
     * [dropTimes]); [trips] are the trips of the day with their shown times. Null when they cannot be joined.
     */
    fun join(
        keep: PlaceVisitEntity,
        drop: PlaceVisitEntity,
        keepTimes: Pair<Long, Long>,
        dropTimes: Pair<Long, Long>,
        trips: List<TripEntity>,
        edits: Map<String, TimelineEditEntity>,
        nowMs: Long,
    ): Change? {
        if (keep.id == drop.id) return null
        // The one still going is always the kept one: a stay in progress cannot end somewhere else.
        if (drop.ongoing && !keep.ongoing) return null
        val start = minOf(keepTimes.first, dropTimes.first)
        val end = maxOf(keepTimes.second, dropTimes.second)
        val keepFirst = keepTimes.first <= dropTimes.first
        val firstEnd = if (keepFirst) keepTimes.second else dropTimes.second
        val secondStart = if (keepFirst) dropTimes.first else keepTimes.first

        val keepOld = edits[TimelineEdits.key(TimelineEdits.VISIT, keep.id)]
        val dropOld = edits[TimelineEdits.key(TimelineEdits.VISIT, drop.id)]
        val out = ArrayList<TimelineEditEntity>()
        out += TimelineEdits.edit(
            TimelineEdits.VISIT, keep.id,
            startMs = start,
            endMs = if (keep.ongoing) -1L else end,
            activityId = keepOld?.activityId.orEmpty(),
            hidden = false, now = nowMs, label = keepOld?.label.orEmpty(),
        )
        val memory = listOf(
            STAYS_LABEL, keep.id, (keepOld?.startMs ?: -1L).toString(), (keepOld?.endMs ?: -1L).toString(),
        ).joinToString(SEP)
        out += hidden(dropOld, TimelineEdits.VISIT, drop.id, drop.startMs, memory, nowMs)
        for (t in trips) {
            if (t.startMs < firstEnd - SLACK_MS || t.endMs > secondStart + SLACK_MS) continue
            val old = edits[TimelineEdits.key(TimelineEdits.TRIP, t.id)]
            if (old?.hidden == true) continue
            out += hidden(old, TimelineEdits.TRIP, t.id, t.startMs, WALK_LABEL + SEP + keep.id, nowMs)
        }
        return Change(out)
    }

    /** The kept stay's id written on a joined-away stay or trip, or null. */
    fun keeperOf(edit: TimelineEditEntity): String? =
        if (!isJoined(edit)) null else edit.label.split(SEP).getOrNull(1)?.takeIf { it.isNotEmpty() }

    /**
     * Takes a join apart again, given the hidden stay [dropEdit]: the stay comes back, the kept one gets the times it had before,
     * and every trip that was hidden into it is shown again. Null when [dropEdit] is not a joined stay.
     */
    fun separate(dropEdit: TimelineEditEntity, edits: List<TimelineEditEntity>, nowMs: Long): Change? {
        if (!dropEdit.hidden || !dropEdit.label.startsWith(STAYS_LABEL)) return null
        val parts = dropEdit.label.split(SEP)
        val keepId = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val prevStart = parts.getOrNull(2)?.toLongOrNull() ?: -1L
        val prevEnd = parts.getOrNull(3)?.toLongOrNull() ?: -1L
        val out = ArrayList<TimelineEditEntity>()
        out += dropEdit.copy(hidden = false, label = "", updatedMs = nowMs)
        edits.firstOrNull { it.kind == TimelineEdits.VISIT && it.refId == keepId }?.let { k ->
            out += k.copy(startMs = prevStart, endMs = prevEnd, updatedMs = nowMs)
        }
        for (e in edits) {
            if (e.kind == TimelineEdits.TRIP && e.hidden && e.label == WALK_LABEL + SEP + keepId) out += e.copy(hidden = false, label = "Trip", updatedMs = nowMs)
        }
        return Change(out)
    }

    /** True when an edit says nothing any more (all original, no category, not hidden), so the row can go. */
    fun isEmpty(e: TimelineEditEntity): Boolean = e.startMs < 0 && e.endMs < 0 && e.activityId.isEmpty() && !e.hidden

    // ---- saving

    suspend fun apply(db: AppDatabase, change: Change) {
        db.withTransaction {
            for (e in change.edits) {
                if (e.kind == TimelineEdits.VISIT && isEmpty(e)) db.timelineEditDao().remove(e.kind, e.refId) else db.timelineEditDao().upsert(e)
            }
        }
    }

    /** Joins two stays by their ids and saves it. False when it cannot be done. */
    suspend fun join(
        db: AppDatabase, keepId: String, dropId: String,
        keepTimes: Pair<Long, Long>, dropTimes: Pair<Long, Long>, trips: List<TripEntity>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val keep = db.placeDao().visitById(keepId) ?: return false
        val drop = db.placeDao().visitById(dropId) ?: return false
        val edits = db.timelineEditDao().all().associateBy { it.key }
        val change = join(keep, drop, keepTimes, dropTimes, trips, edits, nowMs) ?: return false
        apply(db, change)
        AppLog.i("Places", "Joined two stays into one")
        return true
    }

    /** Takes the join of the hidden stay [dropId] apart again. False when there is nothing to take apart. */
    suspend fun separate(db: AppDatabase, dropId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val all = db.timelineEditDao().all()
        val drop = all.firstOrNull { it.kind == TimelineEdits.VISIT && it.refId == dropId } ?: return false
        val change = separate(drop, all, nowMs) ?: return false
        apply(db, change)
        AppLog.i("Places", "Separated two joined stays")
        return true
    }
}
