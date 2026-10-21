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
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.PlaceLookup
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceTrail
import com.lifetracker.app.data.PlaceVisitEntity
import com.lifetracker.app.data.PlaceWatchService
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.PlacesTracker
import com.lifetracker.app.data.PlacesWorker
import com.lifetracker.app.data.TripAdjust
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

/** One place with the time you spent there in the chosen days. */
class PlaceRow(val place: PlaceEntity, val ms: Long, val visits: Int)

/** One listed visit. [name] is the place's name. */
class VisitRow(val visit: PlaceVisitEntity, val name: String)

class PlacesData(val rows: List<PlaceRow>, val visits: List<VisitRow>)

/** What the phone has allowed so far. */
data class PlacesAccess(val location: Boolean, val background: Boolean, val activity: Boolean, val battery: Boolean = false) {
    val all: Boolean get() = location && background && activity
}

/** How the position readings have been arriving: how many are stored, how many each hour lately, and the longest silence. */
class ReadingsInfo(val total: Int, val hourly: List<Int>, val gapText: String)

class PlacesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.placeDao()
    private val tripDao = db.tripDao()
    private val settings = PlacesSettings(app)

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private val rangeFlow = MutableStateFlow(RangeChoice.preset("today"))
    val range: StateFlow<RangeChoice> = rangeFlow

    private val enabledFlow = MutableStateFlow(settings.enabled)
    val enabled: StateFlow<Boolean> = enabledFlow

    private val intervalFlow = MutableStateFlow(settings.intervalSec)
    val intervalSec: StateFlow<Int> = intervalFlow

    private val gpsFlow = MutableStateFlow(settings.useGps)
    val useGps: StateFlow<Boolean> = gpsFlow

    private val timelineFlow = MutableStateFlow(settings.showOnTimeline)
    val showOnTimeline: StateFlow<Boolean> = timelineFlow

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

    /** Where you were and how you got from one place to the next, oldest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trail: StateFlow<List<PlaceTrail.Row>> = share(
        emptyList(),
        rangeFlow.flatMapLatest { r ->
            val zone = ZoneId.systemDefault()
            val from = r.span.startMs(zone)
            val to = r.span.endMs(zone)
            combine(dao.observePlaces(), dao.visitsBetween(from, to), tripDao.tripsBetween(from, to)) { places, visits, trips ->
                PlaceTrail.build(visits, trips, places.associate { it.id to it.name }, System.currentTimeMillis())
            }
        },
    )

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

    /**
     * You say how you travelled. For a trip the phone recorded this is your answer and the app looks again at the trips
     * it had only guessed; for a journey with no trip it is saved as a new trip record.
     */
    fun setJourneyMode(journey: PlaceTrail.Journey, mode: String) {
        viewModelScope.launch {
            val trip = journey.trip
            if (trip != null) {
                tripDao.setMode(trip.id, mode, "you")
                TripAdjust.refresh(db, trip.id, settings.strideM())
                reguess()
            } else {
                PlaceTrail.tripFor(journey, mode)?.let { tripDao.upsertTrip(it) }
            }
        }
    }

    private suspend fun reguess() {
        val learned = tripDao.corrected().map { TripModes.learned(it) }
        for (t in tripDao.guessed()) {
            val stats = com.lifetracker.app.data.TripStats(t.distanceM, t.endMs - t.startMs, t.medianKmh, t.topKmh, TripRules.MIN_FARTHEST_M)
            val mode = TripModes.guess(stats, TripModes.parseActivity(t.activity), t.fromPlaceId, t.toPlaceId, learned)
            if (mode != t.mode) {
                tripDao.setMode(t.id, mode, "auto")
                TripAdjust.refresh(db, t.id, settings.strideM())
            }
        }
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

    fun setRange(r: RangeChoice) {
        rangeFlow.value = r
    }

    /** Looks again at what is allowed and tells Android about the places (called when the screen comes into view). */
    fun refresh() {
        accessFlow.value = readAccess()
        viewModelScope.launch {
            if (settings.enabled) runCatching { PlacesTracker.syncAll(app) }
            statusFlow.value = readStatus()
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

    /** Writes every stored reading to the file you chose, as GPX or CSV. */
    fun exportReadings(uri: Uri, csv: Boolean) {
        viewModelScope.launch {
            transferFlow.value = "Exporting..."
            transferFlow.value = runCatching { withContext(Dispatchers.IO) { writeReadings(uri, csv) } }
                .getOrElse { "Export failed: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    private suspend fun writeReadings(uri: Uri, csv: Boolean): String {
        val raw = AppDatabase.get(app).rawDao()
        val first = raw.firstMs() ?: return "No readings stored yet"
        val stream = app.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Android would not open that file for writing.")
        var count = 0
        stream.bufferedWriter(Charsets.UTF_8).use { w ->
            val gpx = if (csv) null else Gpx.Writer(w, "Life Tracker readings")
            if (gpx != null) gpx.begin() else w.append(Gpx.csvHeader())
            val chunk = 7L * 24 * 60 * 60_000L
            val end = System.currentTimeMillis() + 1
            var from = first
            while (from < end) {
                val fixes = raw.between(from, from + chunk)
                count += fixes.size
                if (gpx != null) gpx.add(fixes) else Gpx.csvRows(w, fixes)
                from += chunk
            }
            gpx?.end()
        }
        return "Exported $count reading${if (count == 1) "" else "s"} as ${if (csv) "CSV" else "GPX"}."
    }

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

    /**
     * Adds the positions in a GPX file (from GPSLogger or any other app) to the stored readings, which are only ever added to,
     * then works the visits out again from all the readings. Readings you already had (same second) are left alone.
     */
    fun importGpx(uri: Uri) {
        viewModelScope.launch {
            transferFlow.value = "Reading the file..."
            transferFlow.value = runCatching {
                withContext(Dispatchers.IO) {
                    val parsed = app.contentResolver.openInputStream(uri)?.use { Gpx.parse(it) }
                        ?: throw IOException("Android would not open that file.")
                    val usable = parsed.fixes.filter { it.acc <= PlaceRules.MAX_ACCURACY_M }
                    val raw = AppDatabase.get(app).rawDao()
                    val before = raw.count()
                    usable.chunked(2_000).forEach { raw.insertMissing(it) }
                    val added = raw.count() - before
                    if (added > 0) PlacesTracker.cleanupAll(app)
                    "Found ${parsed.fixes.size} reading${if (parsed.fixes.size == 1) "" else "s"} in the file: $added new, " +
                        "${usable.size - added} already here, ${parsed.fixes.size - usable.size} too inaccurate, ${parsed.skipped} without a position or time." +
                        if (added > 0) " Visits were worked out again." else ""
                }
            }.getOrElse { "Import failed: ${it.message ?: it.javaClass.simpleName}" }
            statusFlow.value = readStatus()
            readingsFlow.value = runCatching { readReadings() }.getOrElse { readingsFlow.value }
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
            statusFlow.value = readStatus()
        }
    }

    /** "Not a place": hides these stops. They are kept, only hidden. */
    fun ignore(cluster: PlaceRules.Cluster) {
        viewModelScope.launch { dao.ignore(cluster.ids) }
    }
}
