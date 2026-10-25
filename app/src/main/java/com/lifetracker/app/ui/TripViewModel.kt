package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.PointNoteEntity
import com.lifetracker.app.data.RawFixEntity
import com.lifetracker.app.data.Segment
import com.lifetracker.app.data.TimelineEditEntity
import com.lifetracker.app.data.TimelineEdits
import com.lifetracker.app.data.TripAdjust
import com.lifetracker.app.data.TripEditEntity
import com.lifetracker.app.data.TripEditKind
import com.lifetracker.app.data.TripEditRules
import com.lifetracker.app.data.TripEditing
import com.lifetracker.app.data.TripEditor
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripPointEntity
import com.lifetracker.app.data.UndoResult
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One merge or split that made this trip, with a plain sentence for it. */
class TripChangeRow(val edit: TripEditEntity, val text: String)

/** Everything the trip page shows, worked out in one place. */
class TripPage(
    val trip: TripEntity,
    /** The trip's own readings, oldest first. */
    val points: List<TripPointEntity>,
    val fromName: String,
    val toName: String,
    /** Your notes on single readings of this trip, by time of the reading. */
    val notes: Map<Long, String>,
    /** The trip before and after this one (hidden ones left out), or null when there is none. */
    val previous: TripEntity?,
    val next: TripEntity?,
    /** The merges and splits that made this trip, newest first. */
    val changes: List<TripChangeRow>,
    /** True when you hid this trip. */
    val hidden: Boolean,
    /** When this trip was joined to the one before it (so it has no page of its own): that trip's id. */
    val joinedInto: String?,
)

/** The trip page: the path, the numbers, merging, splitting, notes on a point, hiding. */
class TripViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val settings = PlacesSettings(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val idFlow = MutableStateFlow<String?>(null)

    /** Opens the page for [id]. Called again with the same id it does nothing. */
    fun open(id: String) {
        if (idFlow.value != id) idFlow.value = id
    }

    private val messageFlow = MutableStateFlow<String?>(null)

    /** One line about what just happened (or why it could not), until you dismiss it. */
    val message: StateFlow<String?> = messageFlow

    fun clearMessage() {
        messageFlow.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val page: StateFlow<TripPage?> = idFlow.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            val trips = combine(db.tripDao().observeAllTrips(), db.tripDao().observePoints(id), db.placeDao().observePlaces()) { t, p, pl ->
                Triple(t, p, pl)
            }
            val extras = combine(db.timelineEditDao().observeAll(), db.pointNoteDao().observeNotes(), db.tripEditDao().observeActive()) { e, n, a ->
                Triple(e, n, a)
            }
            combine(trips, extras) { (t, p, pl), (e, n, a) -> build(id, t, p, pl, e, n, a) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(
        id: String,
        trips: List<TripEntity>,
        allPoints: List<TripPointEntity>,
        places: List<PlaceEntity>,
        editList: List<TimelineEditEntity>,
        notes: List<PointNoteEntity>,
        active: List<TripEditEntity>,
    ): TripPage? {
        val trip = trips.firstOrNull { it.id == id } ?: return null
        val edits = editList.associateBy { it.key }
        val tripEdit = edits[TimelineEdits.key(TimelineEdits.TRIP, id)]
        val shown = trips.filter { !TimelineEdits.isHidden(edits[TimelineEdits.key(TimelineEdits.TRIP, it.id)]) }
        val joinedInto = if (TripEditing.isJoinedAway(tripEdit)) {
            shown.firstOrNull { it.id != id && it.startMs <= trip.startMs && it.endMs >= trip.endMs }?.id
        } else {
            null
        }
        val names = places.associate { it.id to it.name }
        return TripPage(
            trip = trip,
            points = TripEditing.pointsOf(trip, allPoints),
            fromName = names[trip.fromPlaceId].orEmpty(),
            toName = names[trip.toPlaceId].orEmpty(),
            notes = notes.filter { it.ms in trip.startMs..trip.endMs }.associate { it.ms to it.note },
            previous = shown.lastOrNull { it.id != id && it.endMs <= trip.startMs },
            next = shown.firstOrNull { it.id != id && it.startMs >= trip.endMs },
            changes = active.filter { it.atMs in trip.startMs..trip.endMs }
                .sortedByDescending { it.createdMs }
                .map { TripChangeRow(it, changeText(it)) },
            hidden = TimelineEdits.isHidden(tripEdit) && joinedInto == null,
            joinedInto = joinedInto,
        )
    }

    private fun changeText(e: TripEditEntity): String = when (e.kind) {
        TripEditKind.MERGE -> "Two trips joined at " + clock(e.atMs)
        TripEditKind.SPLIT -> "Cut in two at " + clock(e.atMs)
        else -> "A change"
    }

    private fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).let { formatMinute(it.hour * 60 + it.minute) }

    /** True when the trip can be cut at the reading taken at [atMs] (two readings on each side). */
    fun canSplit(page: TripPage, atMs: Long): Boolean =
        TripEditRules.canSplit(Segment(page.points.map { RawFixEntity(it.ms, it.lat, it.lng, it.acc, steps = it.steps) }), atMs)

    /** Joins the trip to the one before it (the page then shows the joined trip) or to the one after it. */
    fun merge(page: TripPage, withPrevious: Boolean) {
        viewModelScope.launch {
            val first = if (withPrevious) page.previous else page.trip
            val second = if (withPrevious) page.trip else page.next
            if (first == null || second == null) return@launch
            if (TripEditor.merge(db, first.id, second.id, settings.strideM())) {
                idFlow.value = first.id
                messageFlow.value = "Joined into one trip. You can undo it under Changes."
            } else {
                messageFlow.value = "These two trips could not be joined."
            }
        }
    }

    fun split(page: TripPage, atMs: Long) {
        viewModelScope.launch {
            val ok = TripEditor.split(db, page.trip.id, atMs, settings.strideM())
            messageFlow.value = if (ok) "Cut in two. The second part is a trip of its own now. You can undo it under Changes." else "The trip cannot be cut there."
        }
    }

    fun undo(row: TripChangeRow) {
        viewModelScope.launch {
            val result = TripEditor.undo(db, row.edit.id, settings.strideM())
            messageFlow.value = when (result.outcome) {
                UndoResult.Outcome.DONE -> "Undone."
                UndoResult.Outcome.BLOCKED -> "A newer change sits on top of this one (" +
                    (result.blockedBy?.let { changeText(it).lowercase() } ?: "another change") + "). Undo that one first."
                UndoResult.Outcome.GONE -> "This change can no longer be undone from here, because the trips were changed since."
            }
        }
    }

    fun setHidden(trip: TripEntity, hidden: Boolean) {
        viewModelScope.launch { TripEditor.setHidden(db, trip, hidden) }
    }

    fun saveNote(ms: Long, text: String) {
        viewModelScope.launch { TripEditor.saveNote(db, ms, text) }
    }

    /** Your answer to "How did you travel?". */
    fun setMode(trip: TripEntity, mode: String) {
        viewModelScope.launch { TripAdjust.setMode(db, trip.id, mode, settings.strideM()) }
    }
}
