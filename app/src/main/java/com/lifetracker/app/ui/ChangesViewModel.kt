package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.ChangeItem
import com.lifetracker.app.data.ChangesRules
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.TripEditor
import com.lifetracker.app.data.UndoResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Changes page: what you joined, cut or hid, and the way back for each. */
class ChangesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val settings = PlacesSettings(app)

    private val messageFlow = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = messageFlow

    fun clearMessage() {
        messageFlow.value = null
    }

    val items: StateFlow<List<ChangeItem>> = combine(
        db.tripEditDao().observeActive(),
        db.tripDao().observeAllTrips(),
        db.placeDao().visitsBetween(0L, Long.MAX_VALUE),
        db.timelineEditDao().observeAll(),
        db.placeDao().observePlaces(),
    ) { edits, trips, visits, timeline, places ->
        ChangesRules.items(edits, trips, visits, timeline, places.associate { it.id to it.name }) { formatDateTime(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(item: ChangeItem) {
        viewModelScope.launch {
            when (item.kind) {
                ChangeItem.Kind.JOIN, ChangeItem.Kind.CUT -> {
                    val result = TripEditor.undo(db, item.editId.orEmpty(), settings.strideM())
                    messageFlow.value = when (result.outcome) {
                        UndoResult.Outcome.DONE -> "Done. It is back the way it was."
                        UndoResult.Outcome.BLOCKED -> "A newer change sits on top of this one. Undo that one first."
                        UndoResult.Outcome.GONE -> "This change can no longer be undone from here, because the trips were changed since."
                    }
                }
                ChangeItem.Kind.HIDDEN_TRIP -> {
                    db.tripDao().trip(item.tripId.orEmpty())?.let { TripEditor.setHidden(db, it, false) }
                    messageFlow.value = "The trip is shown again."
                }
                ChangeItem.Kind.HIDDEN_STAY -> {
                    TripEditor.showStay(db, item.visitId.orEmpty())
                    messageFlow.value = "The stay is shown again."
                }
            }
        }
    }

    fun bringBackAllJoins() {
        viewModelScope.launch {
            val r = TripEditor.bringBackAllJoins(db, settings.strideM())
            messageFlow.value = when {
                r.restored == 0 && r.blocked == 0 -> "There are no joined trips."
                r.blocked == 0 -> "Brought back ${r.restored} joined trip${if (r.restored == 1) "" else "s"}."
                else -> "Brought back ${r.restored}. ${r.blocked} could not be, because you cut the joined trip afterwards; undo that cut first."
            }
        }
    }
}
