package com.lifetracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.FixStats
import com.lifetracker.app.data.Gpx
import com.lifetracker.app.data.KeepAlive
import com.lifetracker.app.data.Fix
import com.lifetracker.app.data.PlaceCategoryEntity
import com.lifetracker.app.data.DefaultPlaceCategories
import com.lifetracker.app.data.PauseSettings
import com.lifetracker.app.data.MapScheduler
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.PlaceLookup
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceWatchService
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.PlacesTracker
import com.lifetracker.app.data.PlacesWorker
import com.lifetracker.app.data.RecordingMode
import com.lifetracker.app.data.TrackEngine
import com.lifetracker.app.data.TrackService
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the phone has allowed so far. */
data class PlacesAccess(val location: Boolean, val background: Boolean, val activity: Boolean, val battery: Boolean = false) {
    val all: Boolean get() = location && background && activity
}

/** How the position readings have been arriving: how many are stored, how many each hour lately, and the longest silence. */
class ReadingsInfo(val total: Int, val hourly: List<Int>, val gapText: String)

class PlacesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.placeDao()
    private val settings = PlacesSettings(app)

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)


    private val enabledFlow = MutableStateFlow(settings.enabled)
    val enabled: StateFlow<Boolean> = enabledFlow

    private val intervalFlow = MutableStateFlow(settings.intervalSec)
    val intervalSec: StateFlow<Int> = intervalFlow

    private val gpsFlow = MutableStateFlow(settings.useGps)
    val useGps: StateFlow<Boolean> = gpsFlow

    private val timelineFlow = MutableStateFlow(settings.showOnTimeline)
    val showOnTimeline: StateFlow<Boolean> = timelineFlow

    private fun readRecording() = RecordingUi(
        mode = settings.mode,
        intervalSec = settings.trackIntervalSec,
        minMoveM = settings.trackMinMoveM,
        accuracyMode = settings.trackAccuracyMode,
        accuracyLimitM = settings.trackAccuracyLimitM,
        paused = settings.manualPause,
        status = settings.trackStatus,
        kept = settings.trackKept,
        refused = settings.trackRefused,
    )

    private val recordingFlow = MutableStateFlow(readRecording())

    /** How places and trips are recorded (Gentle or GPS all day) and the GPS-all-day settings. */
    val recording: StateFlow<RecordingUi> = recordingFlow

    /** Switches between Gentle and GPS all day, starting the right service and stopping the other one. */
    fun setMode(mode: String) {
        if (settings.mode == mode) return
        settings.mode = mode
        settings.manualPause = false
        settings.trackKept = 0
        settings.trackRefused = 0
        settings.trackStatus = ""
        TrackEngine.reset()
        val canRun = settings.enabled && PlacesTracker.hasLocation(app)
        if (mode == RecordingMode.ALL_DAY) {
            runCatching { app.stopService(android.content.Intent(app, PlaceWatchService::class.java)) }
            if (canRun) TrackService.start(app)
        } else {
            TrackService.stop(app)
            if (canRun && settings.keepRunning) PlaceWatchService.start(app)
        }
        recordingFlow.value = readRecording()
        statusFlow.value = readStatus()
    }

    fun setTrackIntervalSec(seconds: Int) {
        settings.trackIntervalSec = seconds
        recordingFlow.value = readRecording()
    }

    fun setTrackMinMoveM(metres: Int) {
        settings.trackMinMoveM = metres
        recordingFlow.value = readRecording()
    }

    fun setTrackAccuracyMode(mode: String) {
        settings.trackAccuracyMode = mode
        recordingFlow.value = readRecording()
    }

    fun setTrackAccuracyLimitM(metres: Int) {
        settings.trackAccuracyLimitM = metres
        recordingFlow.value = readRecording()
    }

    fun setTrackPaused(paused: Boolean) {
        TrackService.setPaused(app, paused)
        recordingFlow.value = readRecording()
    }

    private val alwaysOnFlow = MutableStateFlow(settings.alwaysOn)
    val alwaysOn: StateFlow<Boolean> = alwaysOnFlow

    private val alwaysOnIntervalFlow = MutableStateFlow(settings.alwaysOnIntervalSec)
    val alwaysOnIntervalSec: StateFlow<Int> = alwaysOnIntervalFlow

    private val readingsFlow = MutableStateFlow(ReadingsInfo(0, List(24) { 0 }, ""))
    val readings: StateFlow<ReadingsInfo> = readingsFlow

    private val rebuildFlow = MutableStateFlow(false)

    /** True while the whole history is being tidied from the stored readings. */
    val rebuilding: StateFlow<Boolean> = rebuildFlow

    private val accessFlow = MutableStateFlow(readAccess())
    val access: StateFlow<PlacesAccess> = accessFlow

    private val statusFlow = MutableStateFlow(readStatus())
    val status: StateFlow<String> = statusFlow

    private fun readAccess() = PlacesAccess(
        PlacesTracker.hasLocation(app),
        PlacesTracker.hasBackgroundLocation(app),
        PlacesTracker.hasActivityRecognition(app),
        KeepAlive.isExempt(app),
    )

    private fun readStatus(): String = buildString {
        append(settings.lastStatus.ifEmpty { "Not set up yet" })
        append("\nLast set up with Android: ").append(PlacesTracker.timeText(settings.lastRegisterMs))
        append("\nLast message from Android (near a place, still, moving): ").append(PlacesTracker.timeText(settings.lastEventMs))
        append("\nLast position reading: ").append(PlacesTracker.timeText(settings.lastFixMs))
        append("\nChecking now: ").append(if (settings.watching) "yes" else "no")
        append("\nAlways-on mode: ").append(if (settings.alwaysOn) "on" else "off")
        append("\nStarted again by the 15 minute check: ").append(settings.watchdogStarts).append(" time(s)")
        if (settings.watchdogStarts > 0) append(", last ").append(PlacesTracker.timeText(settings.lastWatchdogMs))
        append("\nMotion sensor: ").append(
            when {
                settings.motionSensor == "no" -> "this phone has none"
                settings.motionState.isEmpty() -> if (settings.motionSensor == "yes") "listening, nothing felt yet" else "starts with the readings"
                else -> settings.motionState + " since " + PlacesTracker.timeText(settings.motionSinceMs) + " (" + settings.motionChanges + " changes)"
            },
        )
        append("\nPosition jumps thrown out while live: ").append(settings.glitches)
        append("\nLast tidy-up from the stored readings: ").append(PlacesTracker.timeText(settings.lastCleanupMs))
        if (settings.lastCleanupNote.isNotEmpty()) append("\n  ").append(settings.lastCleanupNote)
    }

    private suspend fun readReadings(): ReadingsInfo {
        val dao = AppDatabase.get(app).rawDao()
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60_000L
        val fixes = dao.between(now - day, now + 1)
        val gap = FixStats.longestGap(fixes, now - day, now)
        val text = when {
            gap == null -> "No readings in the last 24 hours."
            else -> "Longest silence in the last 24 hours: " + PlaceRules.durationText(gap.lengthMs) + ", from " + PlacesTracker.timeText(gap.startMs) + "."
        }
        return ReadingsInfo(dao.count(), FixStats.hourly(fixes, now), text)
    }

    /** Your places, the ones still watched first. */
    val places: StateFlow<List<PlaceEntity>> = share(emptyList(), dao.observePlaces())

    private val recordTripsFlow = MutableStateFlow(settings.recordTrips)
    val recordTrips: StateFlow<Boolean> = recordTripsFlow

    private val heartbeatFlow = MutableStateFlow(settings.heartbeat)
    val heartbeat: StateFlow<Boolean> = heartbeatFlow

    fun setHeartbeat(on: Boolean) {
        settings.heartbeat = on
        heartbeatFlow.value = on
    }

    private val tripPaceFlow = MutableStateFlow(settings.tripIntervalSec)
    val tripIntervalSec: StateFlow<Int> = tripPaceFlow

    private val tripsTimelineFlow = MutableStateFlow(settings.showTripsOnTimeline)
    val showTripsOnTimeline: StateFlow<Boolean> = tripsTimelineFlow

    private val heightFlow = MutableStateFlow(settings.heightCm)
    val heightCm: StateFlow<Int> = heightFlow

    private val strideFlow = MutableStateFlow(settings.strideM())

    /** The step length in use, in metres (learned, else from your height, else a typical 0.70). */
    val strideM: StateFlow<Double> = strideFlow

    private val learnedFlow = MutableStateFlow(settings.learnedStrideM > 0f)
    val strideLearned: StateFlow<Boolean> = learnedFlow

    /** Your height in cm (0 for not said). Step length starts again from it. */
    fun setHeightCm(cm: Int) {
        settings.heightCm = cm.coerceIn(0, 250)
        heightFlow.value = settings.heightCm
        strideFlow.value = settings.strideM()
        learnedFlow.value = false
    }

    /** Forgets the learned step length so it is learned again from your next walks. */
    fun resetStride() {
        settings.learnedStrideM = 0f
        strideFlow.value = settings.strideM()
        learnedFlow.value = false
    }

    fun setRecordTrips(on: Boolean) {
        settings.recordTrips = on
        recordTripsFlow.value = on
    }

    fun setTripIntervalSec(seconds: Int) {
        settings.tripIntervalSec = seconds
        tripPaceFlow.value = seconds
    }

    fun setShowTripsOnTimeline(on: Boolean) {
        settings.showTripsOnTimeline = on
        tripsTimelineFlow.value = on
    }

    /** Stops at unnamed spots, grouped by where they were. */
    val clusters: StateFlow<List<PlaceRules.Cluster>> = share(emptyList(), dao.observeUnknown().map { PlaceRules.clusters(it) })

    private val suggestionFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Names found on the internet, by spot key (see [suggestionKey]). */
    val suggestions: StateFlow<Map<String, String>> = suggestionFlow

    private var looking = false

    fun suggestionKey(cluster: PlaceRules.Cluster): String = PlaceLookup.key(cluster.lat, cluster.lng)

    /** Looks up a name for the first few unnamed spots, one at a time and only once each (results are kept). */
    fun lookUp(list: List<PlaceRules.Cluster>) {
        if (looking) return
        looking = true
        viewModelScope.launch {
            try {
                var asked = 0
                for (cluster in list) {
                    val key = suggestionKey(cluster)
                    val cached = settings.suggestion(key)
                    if (cached != null) {
                        if (cached.isNotEmpty()) suggestionFlow.value = suggestionFlow.value + (key to cached)
                        continue
                    }
                    if (asked >= 5) break
                    if (asked > 0) kotlinx.coroutines.delay(1_200L)
                    asked++
                    val found = PlaceLookup.suggest(cluster.lat, cluster.lng)
                    if (found != null) {
                        settings.setSuggestion(key, found)
                        suggestionFlow.value = suggestionFlow.value + (key to found)
                    }
                }
            } finally {
                looking = false
            }
        }
    }

    /** Looks again at what is allowed and tells Android about the places (called when the screen comes into view). */
    fun refresh() {
        accessFlow.value = readAccess()
        viewModelScope.launch {
            if (settings.enabled) runCatching { PlacesTracker.syncAll(app) }
            statusFlow.value = readStatus()
            recordingFlow.value = readRecording()
            readingsFlow.value = runCatching { readReadings() }.getOrElse { readingsFlow.value }
            // Look at the last days' readings again now and then, so opening the app keeps the visits tidy.
            if (settings.enabled && System.currentTimeMillis() - settings.lastCleanupMs > 60 * 60_000L) {
                PlacesTracker.cleanupRecent(app)
                statusFlow.value = readStatus()
            }
        }
    }

    fun setAlwaysOn(on: Boolean) {
        settings.alwaysOn = on
        alwaysOnFlow.value = on
        if (on && settings.enabled && PlacesTracker.hasLocation(app)) PlaceWatchService.start(app)
        statusFlow.value = readStatus()
    }

    fun setAlwaysOnIntervalSec(seconds: Int) {
        settings.alwaysOnIntervalSec = seconds
        alwaysOnIntervalFlow.value = seconds
    }

    /** Looks at every stored reading again and tidies all the visits and trips. Nothing is deleted. */
    fun rebuildAll() {
        if (rebuildFlow.value) return
        rebuildFlow.value = true
        viewModelScope.launch {
            try {
                PlacesTracker.cleanupAll(app)
            } finally {
                rebuildFlow.value = false
                statusFlow.value = readStatus()
                readingsFlow.value = runCatching { readReadings() }.getOrElse { readingsFlow.value }
            }
        }
    }

    private val transferFlow = MutableStateFlow("")

    /** What the last export or import did (or failed to do), shown under the Readings buttons. */
    val transfer: StateFlow<String> = transferFlow

    /** Writes every trip, one track each, to the file you chose. */
    fun exportTrips(uri: Uri) {
        viewModelScope.launch {
            transferFlow.value = "Exporting..."
            transferFlow.value = runCatching {
                withContext(Dispatchers.IO) {
                    val tripDao = AppDatabase.get(app).tripDao()
                    val trips = tripDao.allTrips().sortedBy { it.startMs }
                    val points = tripDao.allPoints().groupBy { it.tripId }
                    val zone = ZoneId.systemDefault()
                    val tracks = trips.map { t ->
                        val day = Instant.ofEpochMilli(t.startMs).atZone(zone).toLocalDate()
                        "$day ${TripModes.label(t.mode)} ${TripRules.distanceText(t.distanceM)}" to (points[t.id] ?: emptyList())
                    }
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Android would not open that file for writing.")
                    stream.bufferedWriter(Charsets.UTF_8).use { Gpx.writeTrips(it, tracks) }
                    "Exported ${trips.size} trip${if (trips.size == 1) "" else "s"} as GPX."
                }
            }.getOrElse { "Export failed: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    fun setEnabled(on: Boolean) {
        settings.enabled = on
        enabledFlow.value = on
        if (on) PlacesWorker.schedule(app) else PlacesWorker.cancel(app)
        viewModelScope.launch {
            runCatching { PlacesTracker.syncAll(app) }
            runCatching { MapScheduler.kick(app) }
            statusFlow.value = readStatus()
        }
    }

    fun setIntervalSec(seconds: Int) {
        settings.intervalSec = seconds
        intervalFlow.value = seconds
    }

    fun setUseGps(on: Boolean) {
        settings.useGps = on
        gpsFlow.value = on
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
    fun addPlace(name: String, kind: String, radiusM: Int, lat: Double, lng: Double, pause: PauseSettings = PauseSettings()) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val place = PlaceEntity(
                "p$now", name.trim(), kind, lat, lng, radiusM, false, now,
                pauseHere = pause.pauseHere, pauseWifi = pause.wifi, pauseMotionless = pause.motionless,
                heartbeatMin = pause.heartbeatMin, entryDelaySec = pause.entryDelaySec,
            )
            dao.upsertPlace(place)
            val inside = dao.unknownOnce().filter { PlaceRules.distanceM(lat, lng, it.lat, it.lng) <= radiusM }
            if (inside.isNotEmpty()) dao.assign(inside.map { it.id }, place.id, lat, lng)
            runCatching { PlacesTracker.syncAll(app) }
            runCatching { MapScheduler.kick(app) }
            statusFlow.value = readStatus()
        }
    }

    /** Every place category, hidden ones last. */
    val categories: StateFlow<List<PlaceCategoryEntity>> =
        share(DefaultPlaceCategories.list, db.placeCategoryDao().observeAll())

    /** Saves a place category. A new one (sortOrder -1) goes to the end of the list. */
    fun saveCategory(category: PlaceCategoryEntity) {
        viewModelScope.launch {
            val dao = db.placeCategoryDao()
            dao.upsert(if (category.sortOrder < 0) category.copy(sortOrder = dao.maxSortOrder() + 1) else category)
        }
    }

    fun updatePlace(place: PlaceEntity) {
        viewModelScope.launch {
            dao.upsertPlace(place)
            runCatching { PlacesTracker.syncAll(app) }
            runCatching { MapScheduler.kick(app) }
            statusFlow.value = readStatus()
        }
    }

    /** "Not a place": hides these stops. They are kept, only hidden. */
    fun ignore(cluster: PlaceRules.Cluster) {
        viewModelScope.launch { dao.ignore(cluster.ids) }
    }
}

/** What the Recording card shows. */
data class RecordingUi(
    val mode: String,
    val intervalSec: Int,
    val minMoveM: Int,
    val accuracyMode: String,
    val accuracyLimitM: Int,
    val paused: Boolean,
    val status: String,
    val kept: Int,
    val refused: Int,
)
