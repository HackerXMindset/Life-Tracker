package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.data.TripStats
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One listed trip. [from] and [to] are place names, or "" for a spot with no name. */
class TripRow(val trip: TripEntity, val from: String, val to: String)

/** Distance and time on one way of travelling. */
class ModeTotal(val mode: String, val distanceM: Long, val ms: Long, val trips: Int)

class TripsData(val rows: List<TripRow>, val modes: List<ModeTotal>, val distanceM: Long, val ms: Long)

class TripsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.tripDao()
    private val settings = PlacesSettings(app)

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private val rangeFlow = MutableStateFlow(RangeChoice.preset("7"))
    val range: StateFlow<RangeChoice> = rangeFlow

    private val recordFlow = MutableStateFlow(settings.recordTrips)
    val recordTrips: StateFlow<Boolean> = recordFlow

    private val paceFlow = MutableStateFlow(settings.tripIntervalSec)
    val tripIntervalSec: StateFlow<Int> = paceFlow

    private val timelineFlow = MutableStateFlow(settings.showTripsOnTimeline)
    val showOnTimeline: StateFlow<Boolean> = timelineFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<TripsData> = share(
        TripsData(emptyList(), emptyList(), 0L, 0L),
        rangeFlow.flatMapLatest { r ->
            val zone = ZoneId.systemDefault()
            combine(dao.tripsBetween(r.span.startMs(zone), r.span.endMs(zone)), db.placeDao().observePlaces()) { trips, places ->
                val names = places.associate { it.id to it.name }
                val byMode = trips.groupBy { it.mode }
                TripsData(
                    rows = trips.map { TripRow(it, names[it.fromPlaceId] ?: "", names[it.toPlaceId] ?: "") },
                    modes = byMode.map { (mode, list) ->
                        ModeTotal(mode, list.sumOf { it.distanceM.toLong() }, list.sumOf { it.endMs - it.startMs }, list.size)
                    }.sortedByDescending { it.distanceM },
                    distanceM = trips.sumOf { it.distanceM.toLong() },
                    ms = trips.sumOf { it.endMs - it.startMs },
                )
            }
        },
    )

    fun setRange(r: RangeChoice) {
        rangeFlow.value = r
    }

    fun setRecordTrips(on: Boolean) {
        settings.recordTrips = on
        recordFlow.value = on
    }

    fun setTripIntervalSec(seconds: Int) {
        settings.tripIntervalSec = seconds
        paceFlow.value = seconds
    }

    fun setShowOnTimeline(on: Boolean) {
        settings.showTripsOnTimeline = on
        timelineFlow.value = on
    }

    /** You say how you travelled. The app then looks again at the trips it had guessed, using what you taught it. */
    fun setMode(trip: TripEntity, mode: String) {
        viewModelScope.launch {
            dao.setMode(trip.id, mode, "you")
            reguess()
        }
    }

    private suspend fun reguess() {
        val learned = dao.corrected().map { TripModes.learned(it) }
        for (t in dao.guessed()) {
            val stats = TripStats(t.distanceM, t.endMs - t.startMs, t.medianKmh, t.topKmh, TripRules.MIN_FARTHEST_M)
            val mode = TripModes.guess(stats, TripModes.parseActivity(t.activity), t.fromPlaceId, t.toPlaceId, learned)
            if (mode != t.mode) dao.setMode(t.id, mode, "auto")
        }
    }
}
