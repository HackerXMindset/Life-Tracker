package com.lifetracker.app.data

import androidx.room.withTransaction
import java.util.UUID

/** What happened when you tried to undo a merge or split. */
class UndoResult(val outcome: Outcome, val blockedBy: TripEditEntity? = null) {
    enum class Outcome {
        DONE,

        /** A newer merge or split sits on top of it; that one has to be undone first. */
        BLOCKED,

        /** The trips no longer meet at its boundary (something else changed them), so it cannot be undone from here. */
        GONE,
    }
}

/**
 * Merging, splitting and undoing trips, and the small things on the trip page, saved to the database. The deciding is done by
 * [TripEditing] (plain code, tested); this only reads what it needs and saves what it says, in one go, so a failure leaves nothing
 * half done. Nothing is deleted.
 */
object TripEditor {
    private fun newId() = UUID.randomUUID().toString()

    private suspend fun timelineEdits(db: AppDatabase): Map<String, TimelineEditEntity> =
        db.timelineEditDao().all().associateBy { it.key }

    private suspend fun save(db: AppDatabase, change: TripChange, nowMs: Long) {
        db.withTransaction {
            for (t in change.trips) db.tripDao().upsertTrip(t)
            if (change.points.isNotEmpty()) db.tripDao().addPoints(change.points)
            for (e in change.timelineEdits) db.timelineEditDao().upsert(e)
            change.journal?.let { db.tripEditDao().upsert(it) }
            change.undoneEditId?.let { db.tripEditDao().undo(it, nowMs) }
        }
    }

    /** Joins [laterId] to the end of [earlierId]. False when they do not follow each other. */
    suspend fun merge(db: AppDatabase, earlierId: String, laterId: String, strideM: Double, nowMs: Long = System.currentTimeMillis()): Boolean {
        val earlier = db.tripDao().trip(earlierId) ?: return false
        val later = db.tripDao().trip(laterId) ?: return false
        val visits = db.placeDao().visitsAround(earlier.endMs - TripEditing.STAY_SLACK_MS, later.startMs + TripEditing.STAY_SLACK_MS)
        val change = TripEditing.merge(
            earlier, later,
            db.tripDao().points(earlier.id), db.tripDao().points(later.id),
            db.rawDao().between(earlier.endMs, later.startMs + 1),
            visits, timelineEdits(db), strideM, nowMs, newId(),
            learned = db.tripDao().corrected().map { TripModes.learned(it) },
        ) ?: return false
        save(db, change, nowMs)
        AppLog.i("Trips", "Merged two trips into one (${LogFormat.clock(earlier.startMs)}-${LogFormat.clock(later.endMs)})")
        return true
    }

    /** Cuts [tripId] at the reading taken at [atMs]. False when a side would be too small or there is no such reading. */
    suspend fun split(db: AppDatabase, tripId: String, atMs: Long, strideM: Double, nowMs: Long = System.currentTimeMillis()): Boolean {
        val trip = db.tripDao().trip(tripId) ?: return false
        val change = TripEditing.split(trip, db.tripDao().points(trip.id), atMs, newId(), strideM, nowMs, newId()) ?: return false
        save(db, change, nowMs)
        AppLog.i("Trips", "Split a trip in two at ${LogFormat.clock(atMs)}")
        return true
    }

    /** Undoes a merge or a split, if nothing newer sits on top of it. */
    suspend fun undo(db: AppDatabase, editId: String, strideM: Double, nowMs: Long = System.currentTimeMillis()): UndoResult {
        val edit = db.tripEditDao().active().firstOrNull { it.id == editId } ?: return UndoResult(UndoResult.Outcome.GONE)
        val trips = db.tripDao().allTrips()
        val active = db.tripEditDao().active()
        val edits = timelineEdits(db)
        val change = when (edit.kind) {
            TripEditKind.MERGE -> {
                val (earlier, later) = TripEditing.joinedPair(edit, trips) ?: return UndoResult(UndoResult.Outcome.GONE)
                TripEditing.blockedBy(edit, active, earlier.startMs..earlier.endMs)?.let {
                    return UndoResult(UndoResult.Outcome.BLOCKED, it)
                }
                val visits = db.placeDao().visitsAround(edit.atMs - TripEditing.STAY_SLACK_MS, edit.otherMs + TripEditing.STAY_SLACK_MS)
                TripEditing.unmerge(edit, earlier, later, db.tripDao().points(earlier.id), visits, edits, strideM, nowMs)
            }
            TripEditKind.SPLIT -> {
                val joinedAway = edits.values.filter { it.kind == TimelineEdits.TRIP && TripEditing.isJoinedAway(it) }.map { it.refId }.toSet()
                val (first, second) = TripEditing.splitPair(edit, trips, joinedAway) ?: return UndoResult(UndoResult.Outcome.GONE)
                TripEditing.blockedBy(edit, active, first.startMs..second.endMs)?.let {
                    return UndoResult(UndoResult.Outcome.BLOCKED, it)
                }
                TripEditing.unsplit(edit, first, second, db.tripDao().points(first.id), db.tripDao().points(second.id), edits, strideM, nowMs)
            }
            else -> return UndoResult(UndoResult.Outcome.GONE)
        }
        save(db, change, nowMs)
        AppLog.i("Trips", "Undid a ${edit.kind}")
        return UndoResult(UndoResult.Outcome.DONE)
    }

    /** What "bring back all joined trips" did. */
    class BringBack(val restored: Int, val blocked: Int)

    /** Undoes every merge, newest first, so a chain of merges comes apart in the right order. A merge with a cut on top of it is left for you to undo. */
    suspend fun bringBackAllJoins(db: AppDatabase, strideM: Double, nowMs: Long = System.currentTimeMillis()): BringBack {
        var restored = 0
        val skipped = HashSet<String>()
        while (true) {
            val next = db.tripEditDao().active()
                .filter { it.kind == TripEditKind.MERGE && it.id !in skipped }
                .maxWithOrNull(compareBy<TripEditEntity>({ it.createdMs }, { it.id })) ?: break
            if (undo(db, next.id, strideM, nowMs).outcome == UndoResult.Outcome.DONE) restored++ else skipped += next.id
        }
        return BringBack(restored, skipped.size)
    }

    /** Shows a stay you hid again (it was only hidden). */
    suspend fun showStay(db: AppDatabase, visitId: String, nowMs: Long = System.currentTimeMillis()) {
        val old = db.timelineEditDao().all().firstOrNull { it.kind == TimelineEdits.VISIT && it.refId == visitId } ?: return
        db.timelineEditDao().upsert(old.copy(hidden = false, updatedMs = nowMs))
    }

    /** Hides a trip from the Timeline and the lists (it is kept) or shows it again. */
    suspend fun setHidden(db: AppDatabase, trip: TripEntity, hidden: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val old = db.timelineEditDao().all().firstOrNull { it.kind == TimelineEdits.TRIP && it.refId == trip.id }
        if (hidden) {
            db.timelineEditDao().upsert(
                TimelineEdits.edit(
                    TimelineEdits.TRIP, trip.id,
                    startMs = old?.startMs?.takeIf { it >= 0 } ?: trip.startMs,
                    endMs = old?.endMs ?: -1L,
                    activityId = old?.activityId.orEmpty(),
                    hidden = true, now = nowMs, label = "Trip",
                ),
            )
        } else if (old != null) {
            db.timelineEditDao().upsert(old.copy(hidden = false, updatedMs = nowMs))
        }
    }

    /** Saves your note on the reading taken at [ms]. An empty note clears it (the empty row is kept). */
    suspend fun saveNote(db: AppDatabase, ms: Long, text: String, nowMs: Long = System.currentTimeMillis()) {
        db.pointNoteDao().upsert(PointNoteEntity(ms, text.trim(), nowMs))
    }
}
