package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.Fix
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceVisitEntity
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.PlacesTracker
import com.lifetracker.app.data.PlacesWorker
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One place with the time you spent there in the chosen days. */
class PlaceRow(val place: PlaceEntity, val ms: Long, val visits: Int)

/** One listed visit. [name] is the place's name. */
class VisitRow(val visit: PlaceVisitEntity, val name: String)

class PlacesData(val rows: List<PlaceRow>, val visits: List<VisitRow>)

/** What the phone has allowed so far. */
data class PlacesAccess(val location: Boolean, val background: Boolean, val activity: Boolean) {
    val all: Boolean get() = location && background && activity
}

class PlacesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).placeDao()
    private val settings = PlacesSettings(app)

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private val rangeFlow = MutableStateFlow(RangeChoice.preset("7"))
    val range: StateFlow<RangeChoice> = rangeFlow

    private val enabledFlow = MutableStateFlow(settings.enabled)
    val enabled: StateFlow<Boolean> = enabledFlow

    private val detectFlow = MutableStateFlow(settings.detectStops)
    val detectStops: StateFlow<Boolean> = detectFlow

    private val timelineFlow = MutableStateFlow(settings.showOnTimeline)
    val showOnTimeline: StateFlow<Boolean> = timelineFlow

    private val accessFlow = MutableStateFlow(readAccess())
    val access: StateFlow<PlacesAccess> = accessFlow

    private val statusFlow = MutableStateFlow(readStatus())
    val status: StateFlow<String> = statusFlow

    private fun readAccess() = PlacesAccess(
        PlacesTracker.hasLocation(app),
        PlacesTracker.hasBackgroundLocation(app),
        PlacesTracker.hasActivityRecognition(app),
    )

    private fun readStatus(): String = buildString {
        append(settings.lastStatus.ifEmpty { "Not set up yet" })
        append("\nLast set up with Android: ").append(PlacesTracker.timeText(settings.lastRegisterMs))
        append("\nLast arrival or departure heard: ").append(PlacesTracker.timeText(settings.lastEventMs))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<PlacesData> = share(
        PlacesData(emptyList(), emptyList()),
        rangeFlow.flatMapLatest { r ->
            val zone = ZoneId.systemDefault()
            val from = r.span.startMs(zone)
            val to = r.span.endMs(zone)
            combine(dao.observePlaces(), dao.visitsBetween(from, to)) { places, visits ->
                val now = System.currentTimeMillis()
                val totals = PlaceRules.totals(visits, from, to, now).associateBy { it.placeId }
                val names = places.associate { it.id to it.name }
                PlacesData(
                    rows = places
                        .map { PlaceRow(it, totals[it.id]?.ms ?: 0L, totals[it.id]?.visits ?: 0) }
                        .sortedWith(compareBy<PlaceRow> { it.place.archived }.thenByDescending { it.ms }),
                    visits = visits.filter { PlaceRules.isShown(it, now) }.map { VisitRow(it, names[it.placeId] ?: "Place") },
                )
            }
        },
    )

    /** Stops at unnamed spots, grouped by where they were. */
    val clusters: StateFlow<List<PlaceRules.Cluster>> = share(emptyList(), dao.observeUnknown().map { PlaceRules.clusters(it) })

    fun setRange(r: RangeChoice) {
        rangeFlow.value = r
    }

    /** Looks again at what is allowed and tells Android about the places (called when the screen comes into view). */
    fun refresh() {
        accessFlow.value = readAccess()
        viewModelScope.launch {
            if (settings.enabled) runCatching { PlacesTracker.syncAll(app) }
            statusFlow.value = readStatus()
        }
    }

    fun setEnabled(on: Boolean) {
        settings.enabled = on
        enabledFlow.value = on
        if (on) PlacesWorker.schedule(app) else PlacesWorker.cancel(app)
        viewModelScope.launch {
            runCatching { PlacesTracker.syncAll(app) }
            statusFlow.value = readStatus()
        }
    }

    fun setDetectStops(on: Boolean) {
        settings.detectStops = on
        detectFlow.value = on
        viewModelScope.launch {
            runCatching { PlacesTracker.syncAll(app) }
            statusFlow.value = readStatus()
        }
    }

    fun setShowOnTimeline(on: Boolean) {
        settings.showOnTimeline = on
        timelineFlow.value = on
    }

    /** One quick position read for the "use where I am" button. */
    fun fetchHere(onResult: (Fix?) -> Unit) {
        viewModelScope.launch { onResult(PlacesTracker.currentFix(app, 15_000L)) }
    }

    /**
     * Saves a new place. If it comes from the review list, that spot's stops (and any other unnamed stop
     * inside the new circle) become visits to it.
     */
    fun addPlace(name: String, kind: String, radiusM: Int, lat: Double, lng: Double) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val place = PlaceEntity("p$now", name.trim(), kind, lat, lng, radiusM, false, now)
            dao.upsertPlace(place)
            val inside = dao.unknownOnce().filter { PlaceRules.distanceM(lat, lng, it.lat, it.lng) <= radiusM }
            if (inside.isNotEmpty()) dao.assign(inside.map { it.id }, place.id, lat, lng)
            runCatching { PlacesTracker.syncAll(app) }
            statusFlow.value = readStatus()
        }
    }

    fun updatePlace(place: PlaceEntity) {
        viewModelScope.launch {
            dao.upsertPlace(place)
            runCatching { PlacesTracker.syncAll(app) }
            statusFlow.value = readStatus()
        }
    }

    /** "Not a place": hides these stops. They are kept, only hidden. */
    fun ignore(cluster: PlaceRules.Cluster) {
        viewModelScope.launch { dao.ignore(cluster.ids) }
    }
}
