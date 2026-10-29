package com.lifetracker.app.data

/** One thing you changed that can be taken back. */
class ChangeItem(
    val key: String,
    val kind: Kind,
    val title: String,
    val detail: String,
    /** When you made the change; the list is newest first. */
    val sortMs: Long,
    /** The merge or cut to undo (joins and cuts only). */
    val editId: String? = null,
    val tripId: String? = null,
    val visitId: String? = null,
    /** False when Restore would not work from here; [why] says why. */
    val canRestore: Boolean = true,
    val why: String = "",
) {
    enum class Kind { JOIN, CUT, HIDDEN_TRIP, HIDDEN_STAY }
}

/** The Changes page: every trip you joined or cut and every trip or stay you hid, each with its way back. Nothing is ever deleted. */
object ChangesRules {
    fun items(
        edits: List<TripEditEntity>,
        trips: List<TripEntity>,
        visits: List<PlaceVisitEntity>,
        timeline: List<TimelineEditEntity>,
        placeNames: Map<String, String>,
        text: (Long) -> String,
    ): List<ChangeItem> {
        val out = ArrayList<ChangeItem>()
        val active = edits.filter { it.undoneMs == 0L }
        for (e in active) {
            when (e.kind) {
                TripEditKind.MERGE -> {
                    val pair = TripEditing.joinedPair(e, trips)
                    val blocker = pair?.let { TripEditing.blockedBy(e, active, it.first.startMs..it.first.endMs) }
                    out += ChangeItem(
                        key = "edit" + e.id, kind = ChangeItem.Kind.JOIN, title = "Trips joined",
                        detail = pair?.first?.let { tripLine(it, text) } ?: "Joined at " + text(e.atMs),
                        sortMs = e.createdMs, editId = e.id, tripId = pair?.first?.id,
                        canRestore = pair != null && blocker == null,
                        why = when {
                            pair == null -> "The trips were changed since, so this cannot be undone from here."
                            blocker != null -> "A newer change sits on top of this one. Undo that one first."
                            else -> "",
                        },
                    )
                }
                TripEditKind.SPLIT -> {
                    val pair = TripEditing.splitPair(e, trips)
                    val blocker = pair?.let { TripEditing.blockedBy(e, active, it.first.startMs..it.second.endMs) }
                    out += ChangeItem(
                        key = "edit" + e.id, kind = ChangeItem.Kind.CUT, title = "Trip cut in two",
                        detail = pair?.let { text(it.first.startMs) + " to " + text(it.second.endMs) } ?: "Cut at " + text(e.atMs),
                        sortMs = e.createdMs, editId = e.id, tripId = pair?.first?.id,
                        canRestore = pair != null && blocker == null,
                        why = when {
                            pair == null -> "The trips were changed since, so this cannot be undone from here."
                            blocker != null -> "A newer change sits on top of this one. Undo that one first."
                            else -> "",
                        },
                    )
                }
            }
        }
        for (t in timeline) {
            if (!t.hidden || TripEditing.isJoinedAway(t)) continue
            when (t.kind) {
                TimelineEdits.TRIP -> {
                    val trip = trips.firstOrNull { it.id == t.refId }
                    out += ChangeItem(
                        key = "trip" + t.refId, kind = ChangeItem.Kind.HIDDEN_TRIP, title = "Hidden trip",
                        detail = trip?.let { tripLine(it, text) } ?: text(t.startMs),
                        sortMs = t.updatedMs, tripId = t.refId,
                    )
                }
                TimelineEdits.VISIT -> {
                    val visit = visits.firstOrNull { it.id == t.refId }
                    val name = visit?.let { placeNames[it.placeId] }?.takeIf { it.isNotBlank() } ?: "an unnamed spot"
                    out += ChangeItem(
                        key = "stay" + t.refId, kind = ChangeItem.Kind.HIDDEN_STAY, title = "Hidden stay at $name",
                        detail = if (visit != null && !visit.ongoing) text(visit.startMs) + " to " + text(visit.endMs) else text(t.startMs),
                        sortMs = t.updatedMs, visitId = t.refId,
                    )
                }
            }
        }
        return out.sortedByDescending { it.sortMs }
    }

    private fun tripLine(t: TripEntity, text: (Long) -> String) =
        text(t.startMs) + " to " + text(t.endMs) + " · " + TripRules.distanceText(t.distanceM)
}
