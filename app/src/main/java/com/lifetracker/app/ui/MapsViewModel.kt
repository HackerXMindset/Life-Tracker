package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.AreaSource
import com.lifetracker.app.data.AreaState
import com.lifetracker.app.data.Bounds
import com.lifetracker.app.data.MapAreaEntity
import com.lifetracker.app.data.MapCleanup
import com.lifetracker.app.data.MapDownloads
import com.lifetracker.app.data.MapScheduler
import com.lifetracker.app.data.MapSettings
import com.lifetracker.app.data.MapStyleEntity
import com.lifetracker.app.data.MapStyleStore
import com.lifetracker.app.data.MapStyles
import com.lifetracker.app.data.OfflineRules
import com.lifetracker.app.data.TileMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Maps settings page: which style the maps use, and the styles you added. */
class MapsViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Application = app
    private val db = AppDatabase.get(app)
    private val settings = MapSettings(app)

    val styles: StateFlow<List<MapStyleEntity>> =
        db.mapDao().observeStyles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedFlow = MutableStateFlow(settings.styleId)

    /** The id of the style in use. */
    val selected: StateFlow<String> = selectedFlow

    fun select(id: String) {
        settings.styleId = id
        selectedFlow.value = id
        refreshStyle()
        // Every place gets its map again in the new style.
        MapScheduler.kick(context, replace = true)
    }

    /** The provider's limit for one download in the style in use, or null when none is known. */
    private val limitFlow = MutableStateFlow<MapStyles.Limit?>(null)
    val styleLimit: StateFlow<MapStyles.Limit?> = limitFlow

    private fun refreshStyle() {
        viewModelScope.launch {
            limitFlow.value = withContext(Dispatchers.IO) { MapStyleStore.current(context, db).limit }
        }
    }

    init {
        refreshStyle()
    }

    /** The areas kept (or wanted) on the phone, newest first. */
    val areas: StateFlow<List<MapAreaEntity>> =
        db.mapDao().observeAreas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The average size of a tile from your finished areas (or the guess), and whether it is a real average. */
    val average: StateFlow<Pair<Long, Boolean>> = areas.map { list ->
        val done = list.filter { it.state == AreaState.DONE && it.tiles > 0L && it.bytes > 0L }
        TileMath.averageTileBytes(done.map { it.tiles to it.bytes }) to done.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TileMath.GUESS_TILE_BYTES to false)

    /** The switches and sliders. */
    class Prefs(
        val autoPlaces: Boolean,
        val placeReachKm: Int,
        val autoWanted: Boolean,
        val wantedReachKm: Int,
        val mobileData: Boolean,
        val limitBytes: Long,
        val autoDelete: Boolean,
        val skipUnusedPlaces: Boolean,
        val paused: Boolean,
    )

    private fun readPrefs() = Prefs(
        settings.autoPlaces, settings.placeReachKm, settings.autoWanted, settings.wantedReachKm, settings.mobileData,
        settings.limitBytes, settings.autoDelete, settings.skipUnusedPlaces, settings.paused,
    )

    private val prefsFlow = MutableStateFlow(readPrefs())
    val prefs: StateFlow<Prefs> = prefsFlow

    private fun changed(replace: Boolean = false) {
        prefsFlow.value = readPrefs()
        MapScheduler.kick(context, replace)
    }

    fun setAutoPlaces(on: Boolean) {
        settings.autoPlaces = on
        changed()
    }

    fun setPlaceReach(km: Int) {
        settings.placeReachKm = km
        changed()
    }

    fun setAutoWanted(on: Boolean) {
        settings.autoWanted = on
        changed()
    }

    fun setWantedReach(km: Int) {
        settings.wantedReachKm = km
        prefsFlow.value = readPrefs()
    }

    /** How much the maps may take together (0 is no limit). Maps over the new limit are cleaned up at once when deleting is on. */
    fun setLimit(bytes: Long) {
        settings.limitBytes = bytes
        prefsFlow.value = readPrefs()
        cleanUp()
    }

    fun setAutoDelete(on: Boolean) {
        settings.autoDelete = on
        prefsFlow.value = readPrefs()
        cleanUp()
        MapScheduler.kick(context)
    }

    fun setSkipUnusedPlaces(on: Boolean) {
        settings.skipUnusedPlaces = on
        changed()
    }

    private fun cleanUp() {
        viewModelScope.launch { withContext(Dispatchers.IO) { runCatching { MapCleanup.enforce(context, db) } } }
    }

    /** Puts all downloads on hold. What is running stops and carries on from where it was after [resume]. */
    fun pause() {
        settings.paused = true
        prefsFlow.value = readPrefs()
        MapScheduler.cancel(context)
    }

    fun resume() {
        settings.paused = false
        prefsFlow.value = readPrefs()
        MapScheduler.kick(context, replace = true)
    }

    /** Every area that did not finish goes back in the queue. */
    fun retryFailed() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val failed = db.mapDao().areas().filter { it.state == AreaState.FAILED }.map { it.id }
                if (failed.isNotEmpty()) db.mapDao().requeue(failed)
            }
            MapScheduler.kick(context, replace = true)
        }
    }

    /** What the frame window offers to jump to: your places and where you are (your last reading). */
    class FrameHelp(val places: List<Triple<String, Double, Double>>, val me: Pair<Double, Double>?)

    suspend fun frameHelp(): FrameHelp = withContext(Dispatchers.IO) {
        val places = db.placeDao().activePlaces().map { Triple(it.name.ifBlank { "A place" }, it.lat, it.lng) }
        val now = System.currentTimeMillis()
        val me = db.rawDao().between(now - 3 * 24 * 60 * 60_000L, now + 1).lastOrNull()?.let { it.lat to it.lng }
        FrameHelp(places, me)
    }

    /** How much the maps on the phone take now. */
    fun usedBytes(list: List<MapAreaEntity>): Long = list.filter { it.state == AreaState.DONE }.sumOf { it.bytes }

    fun setMobileData(on: Boolean) {
        settings.mobileData = on
        changed(replace = true)
    }

    /** Free space on the phone for map downloads. */
    fun freeBytes(): Long = MapDownloads.freeBytes(context)

    /** Where the frame map opens: the first place you watch, else your last reading, else null. */
    suspend fun startPoint(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        db.placeDao().activePlaces().firstOrNull()?.let { it.lat to it.lng }
            ?: db.rawDao().between(System.currentTimeMillis() - 7 * 24 * 60 * 60_000L, System.currentTimeMillis() + 1).lastOrNull()
                ?.let { it.lat to it.lng }
    }

    /** Adds an area you framed by hand and starts the download. [done] gets what is wrong, or null when it was queued. */
    fun addByHand(name: String, bounds: Bounds, minZ: Int, maxZ: Int, done: (String?) -> Unit) {
        viewModelScope.launch {
            val problem = withContext(Dispatchers.IO) {
                if (!bounds.valid()) return@withContext "That part of the map cannot be downloaded. Zoom in a little."
                if (minZ < 0 || maxZ > 22 || minZ > maxZ) return@withContext "The zoom levels must be between 0 and 22, lowest first."
                val style = MapStyleStore.current(context, db)
                val tiles = TileMath.count(bounds, minZ, maxZ)
                val (avg, real) = MapDownloads.averageTileBytes(db)
                val estimate = TileMath.estimateBytes(tiles, avg)
                val refusal = OfflineRules.overLimit(tiles, if (real) estimate else 0L, style.limit)
                    ?: OfflineRules.storageProblem(estimate, freeBytes())
                if (refusal != null) return@withContext refusal
                val now = System.currentTimeMillis()
                val count = db.mapDao().areas().count { it.source == AreaSource.HAND } + 1
                db.mapDao().upsertArea(
                    MapAreaEntity(
                        id = "m${now}h", name = name.trim().ifEmpty { "Area $count" },
                        styleId = style.id, styleUrl = style.url,
                        west = bounds.west, south = bounds.south, east = bounds.east, north = bounds.north,
                        minZoom = minZ, maxZoom = maxZ,
                        source = AreaSource.HAND, placeId = "", sig = "", state = AreaState.QUEUED,
                        createdMs = now, doneMs = 0L, tiles = 0L, bytes = 0L, totalTiles = 0L, regionId = -1L, note = "",
                    ),
                )
                null
            }
            if (problem == null) MapScheduler.kick(context)
            done(problem)
        }
    }

    /** Downloads the area again from nothing: its tiles are deleted and it goes back in the queue. */
    fun downloadAgain(area: MapAreaEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                MapDownloads.removeTiles(context, db, area)
                db.mapDao().upsertArea(
                    area.copy(state = AreaState.QUEUED, regionId = -1L, tiles = 0L, bytes = 0L, totalTiles = 0L, note = "", createdMs = System.currentTimeMillis()),
                )
            }
            MapScheduler.kick(context, replace = true)
        }
    }

    /** Deletes the downloaded tiles. The area stays in the list, so it is not made again by itself. */
    fun deleteArea(area: MapAreaEntity) {
        viewModelScope.launch { withContext(Dispatchers.IO) { MapDownloads.removeTiles(context, db, area) } }
    }

    /** Adds a style of your own and starts using it. Returns what is wrong, or null when it was added. */
    fun add(name: String, address: String, key: String): String? {
        val problem = MapStyles.problem(name, address, key)
        if (problem != null) return problem
        val now = System.currentTimeMillis()
        val style = MapStyleEntity(MapStyles.newId(now), name.trim(), address.trim(), key.trim(), false, now)
        viewModelScope.launch {
            db.mapDao().upsertStyle(style)
            select(style.id)
        }
        return null
    }

    /** Hiding keeps the style; it only leaves the list. The map goes back to the default if it was in use. */
    fun setHidden(style: MapStyleEntity, hidden: Boolean) {
        viewModelScope.launch {
            db.mapDao().upsertStyle(style.copy(hidden = hidden))
            if (hidden && selectedFlow.value == style.id) select(MapStyles.DEFAULT_ID)
        }
    }
}
