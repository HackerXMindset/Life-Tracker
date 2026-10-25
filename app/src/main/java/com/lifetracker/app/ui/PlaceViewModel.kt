package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceVisitEntity
import com.lifetracker.app.data.TimelineEdits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** The place page: the place, and every stay of yours there that is shown (newest first) with the time it adds up to. */
class PlacePage(val place: PlaceEntity, val visits: List<PlaceVisitEntity>, val totalMs: Long)

class PlaceViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val idFlow = MutableStateFlow<String?>(null)

    fun open(id: String) {
        if (idFlow.value != id) idFlow.value = id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val page: StateFlow<PlacePage?> = idFlow.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(
                db.placeDao().observePlaces(),
                db.placeDao().visitsBetween(0L, Long.MAX_VALUE),
                db.timelineEditDao().observeAll(),
            ) { places, visits, edits ->
                val place = places.firstOrNull { it.id == id } ?: return@combine null
                val hidden = edits.filter { it.kind == TimelineEdits.VISIT && it.hidden }.map { it.refId }.toSet()
                val now = System.currentTimeMillis()
                val own = visits.filter { it.placeId == id && it.id !in hidden && PlaceRules.isShown(it, now) }
                PlacePage(place, own, own.sumOf { PlaceRules.lengthMs(it, now) })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
