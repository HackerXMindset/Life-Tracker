package com.lifetracker.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** What a Google Play services call came back with: a value, an error, or neither. */
private class Outcome<T>(val value: T?, val error: Exception?)

private suspend fun <T> Task<T>.outcome(onCancel: (() -> Unit)? = null): Outcome<T> =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { onCancel?.invoke() }
        addOnCompleteListener { task ->
            if (cont.isActive) {
                cont.resume(if (task.isSuccessful) Outcome(task.result, null) else Outcome(null, task.exception))
            }
        }
    }

/**
 * Keeps places up to date. Android does the first watching: Google Play services says when you come near or leave a
 * ring around a place (geofences) and when the phone goes still or starts moving (activity transitions). Each message
 * wakes the app, which starts [PlaceWatchService] only if it needs position readings to know where you are.
 */
object PlacesTracker {
    private val lock = Mutex()

    fun hasLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasActivityRecognition(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACTIVITY_RECOGNITION)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun pending(context: Context, code: Int, receiver: Class<*>): PendingIntent {
        // Google Play services adds the event to the intent, so it has to be mutable.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, code, Intent(context, receiver), flags)
    }

    private fun geofenceIntent(context: Context) = pending(context, 11, GeofenceReceiver::class.java)
    private fun transitionIntent(context: Context) = pending(context, 12, ActivityTransitionReceiver::class.java)

    /**
     * Hands the named places to Android (replacing what it had) and asks it to report when the phone goes still.
     * Safe to call again and again. Returns a line saying how it went.
     */
    suspend fun syncAll(context: Context, now: Long = System.currentTimeMillis()): String = withContext(Dispatchers.IO) {
        val settings = PlacesSettings(context)
        val geofences = LocationServices.getGeofencingClient(context)
        val recognition = ActivityRecognition.getClient(context)
        val status = StringBuilder()

        if (!settings.enabled) {
            runCatching { geofences.removeGeofences(geofenceIntent(context)) }
            runCatching { recognition.removeActivityTransitionUpdates(transitionIntent(context)) }
            AppLog.i("Places", "Hand-over to Android skipped: places are switched off")
            return@withContext "Off"
        }
        if (!hasLocation(context)) {
            AppLog.w("Places", "Hand-over to Android failed: no location permission")
            return@withContext remember(settings, now, "Needs location permission")
        }

        val places = AppDatabase.get(context).placeDao().activePlaces().take(PlaceRules.MAX_PLACES)
        runCatching { geofences.removeGeofences(geofenceIntent(context)).outcome() }
        if (places.isEmpty()) {
            status.append("No places to watch yet")
        } else {
            val list = places.map {
                Geofence.Builder()
                    .setRequestId(it.id)
                    .setCircularRegion(it.lat, it.lng, maxOf(it.radiusM, PlaceRules.GEOFENCE_MIN_M).toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(list)
                .build()
            val result = runCatching { geofences.addGeofences(request, geofenceIntent(context)).outcome() }
            val error = result.exceptionOrNull() ?: result.getOrNull()?.error
            if (error == null) {
                status.append("Watching ${places.size} place${if (places.size == 1) "" else "s"}")
                if (!hasBackgroundLocation(context)) status.append(" (set location to \"Allow all the time\" so it works with the app closed)")
            } else {
                status.append("Android would not watch the places: ").append(error.message ?: error.javaClass.simpleName)
            }
        }

        if (hasActivityRecognition(context)) {
            val transitions = buildList {
                add(ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build())
                add(ActivityTransition.Builder().setActivityType(DetectedActivity.STILL).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build())
                listOf(DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_BICYCLE, DetectedActivity.IN_VEHICLE).forEach { type ->
                    add(ActivityTransition.Builder().setActivityType(type).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build())
                }
            }
            val request = ActivityTransitionRequest(transitions)
            val result = runCatching { recognition.requestActivityTransitionUpdates(request, transitionIntent(context)).outcome() }
            val error = result.exceptionOrNull() ?: result.getOrNull()?.error
            if (error != null) status.append("; still/moving detection failed: ").append(error.message ?: error.javaClass.simpleName)
        } else {
            runCatching { recognition.removeActivityTransitionUpdates(transitionIntent(context)) }
            status.append("; stops need the Physical activity permission")
        }
        AppLog.i("Places", "Handed the places to Android: $status")
        remember(settings, now, status.toString())
    }

    private fun remember(settings: PlacesSettings, now: Long, text: String): String {
        settings.lastRegisterMs = now
        settings.lastStatus = text
        return text
    }

    /** What was last written to the diary about each saved visit, so only a start, a change of place or an end is written. */
    private val loggedVisits = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun logVisits(saves: List<PlaceVisitEntity>, names: Map<String, String>) {
        for (v in saves) {
            val where = if (v.placeId.isEmpty()) "an unknown spot" else names[v.placeId] ?: v.placeId
            val sig = "${v.placeId}|${v.startMs}|${v.ongoing}"
            if (loggedVisits.put(v.id, sig) == sig) continue
            AppLog.i(
                "Visits",
                (if (v.ongoing) "Visit at $where is going on since ${LogFormat.clock(v.startMs)}" else "Visit at $where ended: ${LogFormat.clock(v.startMs)} to ${LogFormat.clock(v.endMs)}") +
                    " (${(maxOf(v.endMs, v.startMs) - v.startMs) / 60_000L} min saved so far)",
            )
        }
    }

    /** One short line saying what the app believes right now, for the diary. */
    fun describe(s: WatchState, names: Map<String, String> = emptyMap()): String = buildString {
        val stay = s.stay
        if (stay == null) {
            append("no stay")
        } else {
            append("stay since ").append(LogFormat.clock(stay.startMs))
            append(if (stay.placeId.isEmpty()) " at an unknown spot" else " at ${names[stay.placeId] ?: stay.placeId.take(6)}")
            if (stay.ignored) append(" (ignored)")
        }
        append("; ").append(if (s.moving) "moving" else "still")
        s.trip?.let { append("; trip since ").append(LogFormat.clock(it.startMs)).append(" (").append(it.farthestM.toInt()).append(" m from the start)") }
        if (s.activity.isNotEmpty()) append("; Android says ").append(s.activity)
    }

    /** Android said you came near, or went away from, one of the rings around your places. */
    suspend fun onGeofence(context: Context, now: Long = System.currentTimeMillis()) = trigger(context, now, "geofence") { state ->
        PlaceEngine.onTrigger(state, now)
    }

    /** The phone went still at [atMs]. */
    suspend fun onStillStart(context: Context, atMs: Long) = trigger(context, atMs, "Android: went still") { state ->
        PlaceEngine.onStill(state, atMs, System.currentTimeMillis())
    }

    /** The phone started moving at [atMs]. */
    suspend fun onMoving(context: Context, atMs: Long) = trigger(context, atMs, "Android: started moving") { state ->
        PlaceEngine.onMoving(state, atMs, System.currentTimeMillis())
    }

    private suspend fun trigger(context: Context, at: Long, what: String, change: (WatchState) -> WatchState) =
        withContext(Dispatchers.IO) {
            val settings = PlacesSettings(context)
            if (!settings.enabled) {
                AppLog.w("Places", "$what at ${LogFormat.clock(at)} ignored: places are switched off")
                return@withContext
            }
            val names = runCatching { AppDatabase.get(context).placeDao().activePlaces().associate { it.id to it.name } }.getOrDefault(emptyMap())
            var before = ""
            var after = ""
            val watch = lock.withLock {
                settings.lastEventMs = System.currentTimeMillis()
                before = describe(settings.engine, names)
                val next = change(settings.engine)
                settings.engine = next
                after = describe(next, names)
                PlaceEngine.keepWatching(next, System.currentTimeMillis())
            }
            val start = (watch || settings.keepRunning) && hasLocation(context)
            AppLog.i(
                "Places",
                "$what (at ${LogFormat.clock(at)}): $after" + (if (before != after) " [was: $before]" else "") +
                    "; readings " + (if (start) "needed, starting them" else "not needed"),
            )
            if (start) PlaceWatchService.start(context)
        }

    /** What the watch service should do after a reading: keep going or stop, how often to read, and whether to use GPS. */
    class Watch(val keep: Boolean, val intervalSec: Int, val highAccuracy: Boolean)

    /** Seconds between readings while you are standing still: the always-on pace in always-on mode, else the usual one. */
    fun baseIntervalSec(settings: PlacesSettings): Int =
        if (settings.allDay) settings.trackIntervalSec else if (settings.alwaysOn) settings.alwaysOnIntervalSec else settings.intervalSec

    /** GPS is used when you chose it, except in always-on mode while you are standing still (Wi-Fi and mobile network then). */
    fun useHighAccuracy(settings: PlacesSettings, state: WatchState): Boolean =
        settings.useGps && (!settings.alwaysOn || state.moving || state.trip != null)

    /**
     * The motion sensor says the phone went still ([still] true) or started moving, since [atMs]. It only adds to what Android's
     * own detection says: moving always counts, but "still" is ignored on a trip (a smooth ride feels still) or when the phone
     * is already known to be still.
     */
    suspend fun onMotion(context: Context, still: Boolean, atMs: Long) {
        val settings = PlacesSettings(context)
        settings.motionState = if (still) "still" else "moving"
        settings.motionSinceMs = atMs
        settings.motionChanges = settings.motionChanges + 1
        trigger(context, atMs, "Motion sensor: " + (if (still) "still" else "moving")) { state ->
            when {
                still && state.moving && state.trip == null -> PlaceEngine.onStill(state, atMs, System.currentTimeMillis())
                !still && !state.moving -> PlaceEngine.onMoving(state, atMs, System.currentTimeMillis())
                else -> state
            }
        }
    }

    /** Android says you are now walking, running, cycling or in a vehicle. */
    suspend fun onActivity(context: Context, name: String, atMs: Long) = trigger(context, atMs, "Android: $name") { state ->
        PlaceEngine.onActivity(state, name, atMs, System.currentTimeMillis())
    }

    /** One position reading from the watch service. */
    suspend fun onFix(context: Context, fix: Fix): Watch = withContext(Dispatchers.IO) {
        lock.withLock {
            val settings = PlacesSettings(context)
            val db = AppDatabase.get(context)
            val dao = db.placeDao()
            val now = System.currentTimeMillis()
            val recent = dao.visitsAround(fix.timeMs - 12 * 60 * 60_000L, fix.timeMs)
            if (fix.accuracyM > PlaceRules.MAX_ACCURACY_M) {
                AppLog.w("Fixes", "Reading at ${LogFormat.clock(fix.timeMs)} ignored: accuracy ${fix.accuracyM.toInt()} m is worse than ${PlaceRules.MAX_ACCURACY_M.toInt()} m")
            }
            if (fix.accuracyM <= PlaceRules.MAX_ACCURACY_M) {
                // Every reading is kept for good, glitches too: the tidy-up filters them again when it looks back.
                val raw = RawFixEntity(fix.timeMs, fix.lat, fix.lng, fix.accuracyM, fix.speedMs)
                db.rawDao().add(raw)
                val before = settings.lastGood
                val glitch = before != null && settings.glitchRun < 3 && FixFilter.isJump(before, raw)
                if (glitch) {
                    // A jump the phone's own speed does not explain: do not let it end a stay or start a trip.
                    settings.glitchRun = settings.glitchRun + 1
                    settings.glitches = settings.glitches + 1
                    AppLog.w("Fixes", "Reading at ${LogFormat.clock(fix.timeMs)} looks like a jump and was not used (${settings.glitchRun} in a row)")
                    settings.lastFixMs = now
                    return@withLock Watch(
                        PlaceEngine.keepWatching(settings.engine, now) || settings.alwaysOn,
                        PlaceEngine.intervalSec(settings.engine, baseIntervalSec(settings), settings.tripIntervalSec),
                        useHighAccuracy(settings, settings.engine),
                    )
                }
                settings.glitchRun = 0
                settings.lastGood = raw
            }
            val places = dao.activePlaces()
            val names = places.associate { it.id to it.name }
            val beforeState = describe(settings.engine, names)
            val step = PlaceEngine.onFix(settings.engine, fix, places, recent, now, settings.recordTrips)
            val afterState = describe(step.state, names)
            AppLog.i(
                "Fixes",
                "Reading ${LogFormat.clock(fix.timeMs)}: accuracy ${fix.accuracyM.toInt()} m" +
                    (if (fix.speedMs >= 0f) ", speed ${"%.1f".format(fix.speedMs * 3.6f)} km/h" else ""),
            )
            logVisits(step.saves, names)
            if (beforeState != afterState) AppLog.i("Places", "Now: $afterState [was: $beforeState]")
            if (step.saves.isNotEmpty()) {
                // The current stay in the engine is authoritative. A previous app/process state may
                // have left another visit marked open; close those rows when a newer active visit is saved.
                val activeVisit = step.saves.filter { it.ongoing }.maxByOrNull { it.startMs }
                if (activeVisit == null) {
                    dao.upsertVisits(step.saves)
                } else {
                    val idsSavedThisStep = step.saves.mapTo(HashSet()) { it.id }
                    val staleOpen = dao.openVisits()
                        .filter { it.id != activeVisit.id && it.id !in idsSavedThisStep }
                        .map { old ->
                            old.copy(
                                endMs = maxOf(old.startMs, minOf(old.endMs, activeVisit.startMs)),
                                ongoing = false,
                            )
                        }
                    if (staleOpen.isNotEmpty()) {
                        AppLog.w("Visits", "Closing ${staleOpen.size} stale open visit(s) before ${LogFormat.clock(activeVisit.startMs)}")
                        dao.upsertVisits(staleOpen)
                    }
                    dao.upsertVisits(step.saves)
                }
            }
            if (step.points.isNotEmpty()) {
                // Note the step counter on the readings taken now (not on one placed back at the start of a stay).
                val counter = StepCounterSync.counterNow(context)
                db.tripDao().addPoints(
                    if (counter == null) step.points else step.points.map { if (it.ms >= fix.timeMs - 90_000L) it.copy(steps = counter) else it },
                )
            }
            step.finish?.let { finishTrip(db, it, settings) }
            if (!step.keepWatching && !settings.alwaysOn) AppLog.i("Service", "Readings no longer needed: $afterState")
            settings.engine = step.state
            settings.lastFixMs = now
            Watch(
                step.keepWatching || settings.alwaysOn,
                PlaceEngine.intervalSec(step.state, baseIntervalSec(settings), settings.tripIntervalSec),
                useHighAccuracy(settings, step.state),
            )
        }
    }

    /** Works out the distance and speeds of a trip that has ended and saves it, unless it was only GPS drift. */
    private suspend fun finishTrip(db: AppDatabase, f: TripFinish, settings: PlacesSettings) {
        val dao = db.tripDao()
        val points = dao.points(f.id)
        val stats = TripRules.stats(points, f.startMs, f.endMs)
        if (!TripRules.isRealTrip(stats)) {
            AppLog.w(
                "Trips",
                "Trip ${LogFormat.clock(f.startMs)}-${LogFormat.clock(f.endMs)} DROPPED, not a real trip: farthest ${stats.farthestM.toInt()} m " +
                    "(needs ${TripRules.MIN_FARTHEST_M} m), lasted ${stats.durationMs / 1000} s (needs 60 s), ${points.size} readings",
            )
            dao.dropPoints(f.id)
            return
        }
        val guess = TripModes.guess(stats, f.acts, f.fromPlaceId, f.toPlaceId, dao.corrected().map { TripModes.learned(it) })
        val steps = TripRules.stepsBetween(points)
        // Learn your step length from walks where the GPS and the steps can be compared, before the distance is settled.
        TripRules.strideSample(stats.distanceM, steps, stats.medianKmh, guess)?.let {
            settings.learnedStrideM = TripRules.learnStride(settings.learnedStrideM.toDouble(), it, settings.strideM()).toFloat()
        }
        val distance = TripRules.adjust(stats.distanceM, stats.durationMs, guess, steps, settings.strideM())
        AppLog.i(
            "Trips",
            "Trip ${LogFormat.clock(f.startMs)}-${LogFormat.clock(f.endMs)} saved: ${distance.toInt()} m, guess $guess, ${points.size} readings, $steps steps",
        )
        dao.upsertTrip(
            TripEntity(
                id = f.id,
                startMs = f.startMs,
                endMs = f.endMs,
                fromPlaceId = f.fromPlaceId,
                toPlaceId = f.toPlaceId,
                distanceM = distance,
                medianKmh = stats.medianKmh,
                topKmh = stats.topKmh,
                activity = TripModes.activityText(f.acts),
                mode = guess,
                modeSource = "auto",
            ),
        )
    }

    /** Whether the readings should be running right now and are not. */
    private fun needsStarting(settings: PlacesSettings, now: Long): Boolean =
        PlacesWatchdog.shouldStart(
            enabled = settings.enabled,
            alwaysOn = settings.keepRunning,
            wanted = PlaceEngine.keepWatching(settings.engine, now),
            watching = settings.watching,
            lastBeatMs = settings.lastBeatMs,
            nowMs = now,
            intervalSec = PlaceEngine.intervalSec(settings.engine, baseIntervalSec(settings), settings.tripIntervalSec),
        )

    /** Starts the readings if the app should be checking where you are right now (used when the app is opened). */
    fun resumeIfNeeded(context: Context) {
        val settings = PlacesSettings(context)
        if (hasLocation(context) && needsStarting(settings, System.currentTimeMillis())) PlaceWatchService.start(context)
    }

    /**
     * The 15 minute check: if Android stopped the readings while they should be running, start them again and count it,
     * so the Places screen shows how often the phone is doing that.
     */
    fun watchdog(context: Context, now: Long = System.currentTimeMillis()) {
        val settings = PlacesSettings(context)
        if (!hasLocation(context)) {
            AppLog.w("Watchdog", "15-minute check: no location permission")
            return
        }
        if (!needsStarting(settings, now)) {
            AppLog.i(
                "Watchdog",
                "15-minute check: fine (readings " + (if (settings.watching) "running, last one ${(now - settings.lastBeatMs) / 1000} s ago" else "not needed right now") + ")",
            )
            return
        }
        if (PlaceWatchService.start(context)) {
            settings.watchdogStarts = settings.watchdogStarts + 1
            settings.lastWatchdogMs = now
            AppLog.w("Watchdog", "15-minute check: the readings had stopped (last one ${(now - settings.lastBeatMs) / 1000} s ago), started them again")
        }
    }

    /**
     * The safety net (see [PlacesHeartbeat]): one position, checked by the 15-minute check when the continuous readings are not
     * running. It goes through the same rules as any reading, so a stay is confirmed or a move is noticed even when Android sent
     * no message. If the rules then want continuous readings, they start.
     */
    suspend fun heartbeat(context: Context, now: Long = System.currentTimeMillis()) {
        val settings = PlacesSettings(context)
        val running = PlacesWatchdog.alive(
            settings.watching,
            settings.lastBeatMs,
            now,
            PlaceEngine.intervalSec(settings.engine, baseIntervalSec(settings), settings.tripIntervalSec),
        )
        if (!PlacesHeartbeat.due(settings.enabled, settings.heartbeat, hasLocation(context), running, now, settings.lastHeartbeatMs)) return
        settings.lastHeartbeatMs = now
        val fix = currentFix(context, 20_000L)
        if (fix == null) {
            AppLog.w("Heartbeat", "No usable position within 20 s (indoors, location off, no signal, or too inaccurate)")
            return
        }
        if (!PlacesHeartbeat.usable(fix.timeMs, now)) {
            AppLog.w("Heartbeat", "Only an old position (${(now - fix.timeMs) / 60_000L} min old); not used")
            return
        }
        AppLog.i("Heartbeat", "Position check: accuracy ${fix.accuracyM.toInt()} m")
        val watch = onFix(context, fix)
        if (watch.keep && hasLocation(context)) PlaceWatchService.start(context)
    }

    /** Called after a restart: Android forgot the geofences. */
    suspend fun afterRestart(context: Context) {
        AppLog.i("App", "Phone restarted or app updated: handing the places to Android again")
        runCatching { syncAll(context) }
        val settings = PlacesSettings(context)
        if (settings.enabled && settings.keepRunning && hasLocation(context)) PlaceWatchService.start(context)
    }

    /** What a tidy-up changed. */
    class Cleanup(val added: Int = 0, val stretched: Int = 0, val trips: Int = 0, val scored: Int = 0, val built: Int = 0) {
        operator fun plus(o: Cleanup) = Cleanup(added + o.added, stretched + o.stretched, trips + o.trips, scored + o.scored, built + o.built)

        fun text(): String =
            "Added $added visit${if (added == 1) "" else "s"}, stretched $stretched, corrected $trips trip${if (trips == 1) "" else "s"}, " +
                (if (built > 0) "built $built trip${if (built == 1) "" else "s"}, " else "") +
                "checked $scored visit${if (scored == 1) "" else "s"}"
    }

    /**
     * Looks at the readings between [fromMs] and [toMs] again and tidies the visits and trips (see [PlaceCleanup]).
     * Readings from the last half hour are left to the live detector.
     */
    private suspend fun cleanup(context: Context, fromMs: Long, toMs: Long, now: Long): Cleanup =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val db = AppDatabase.get(context)
                val end = minOf(toMs, now - PlaceCleanup.SETTLE_MS)
                if (end <= fromMs) return@withLock Cleanup()
                val margin = 6 * 60 * 60_000L
                val readings = db.rawDao().between(fromMs - margin, end)
                val allStays = StayBuilder.build(FixFilter.clean(readings))
                val stays = allStays.filter { it.endMs >= fromMs }
                val places = db.placeDao().activePlaces()
                val saved = db.placeDao().visitsAround(fromMs - 24 * 60 * 60_000L, end + 24 * 60 * 60_000L)
                val plan = PlaceCleanup.plan(stays, places, saved)
                val rows = plan.adds + plan.updates
                if (rows.isNotEmpty()) db.placeDao().upsertVisits(rows)

                var trips = 0
                for (t in db.tripDao().tripsIn(fromMs, end)) {
                    val fixed = PlaceCleanup.refreshTrip(t, db.tripDao().points(t.id), PlacesSettings(context).strideM()) ?: continue
                    db.tripDao().setStats(fixed.id, fixed.distanceM, fixed.medianKmh, fixed.topKmh)
                    trips++
                }
                // With GPS all day, trips that the live detector did not write down are built from the readings (never over a trip that exists).
                var built = 0
                val settings = PlacesSettings(context)
                if (settings.allDay) {
                    val saved2 = db.placeDao().visitsAround(fromMs - 24 * 60 * 60_000L, end + 24 * 60 * 60_000L)
                    val learned = db.tripDao().corrected().map { TripModes.learned(it) }
                    val fresh = TripBuilder.plan(
                        readings, allStays, db.tripDao().allTrips(), saved2, db.tripEditDao().active(), learned,
                        settings.strideM(), settledBeforeMs = end,
                    // A trip that began before this stretch but ends in it is built too (a week-long rebuild would otherwise skip it).
                    ).filter { it.trip.endMs >= fromMs }
                    if (fresh.isNotEmpty()) {
                        db.withTransaction {
                            db.tripDao().insertMissingTrips(fresh.map { it.trip })
                            db.tripDao().insertMissingPoints(fresh.flatMap { it.points })
                        }
                        built = fresh.size
                        AppLog.i("Trips", "Built $built trip${if (built == 1) "" else "s"} from the readings that no trip covered")
                    }
                }
                // How sure the app is of each finished visit, from the readings taken during it.
                var scored = 0
                for (v in db.placeDao().visitsAround(fromMs, end)) {
                    if (v.ongoing) continue
                    val fixes = FixFilter.clean(db.rawDao().between(v.startMs, v.endMs + 1))
                    val confidence = StayConfidence.score(v.startMs, v.endMs, fixes, v.placeId.isNotEmpty())
                    if (confidence != v.confidence) {
                        db.placeDao().setConfidence(v.id, confidence)
                        scored++
                    }
                }
                Cleanup(plan.adds.size, plan.updates.size, trips, scored, built)
            }
        }

    /**
     * Looks up a name on the internet for the unnamed spots that have none yet, a few at a time and politely (one a
     * second), so the hint is ready when you open the review list. Only the position of the spot is sent, once.
     * Returns how many spots were asked about.
     */
    suspend fun prefetchNames(context: Context, maxLookups: Int = 6): Int = withContext(Dispatchers.IO) {
        val settings = PlacesSettings(context)
        val clusters = PlaceRules.clusters(AppDatabase.get(context).placeDao().unknownOnce())
        var asked = 0
        for (cluster in clusters) {
            val key = PlaceLookup.key(cluster.lat, cluster.lng)
            if (settings.suggestion(key) != null) continue
            if (asked >= maxLookups) break
            if (asked > 0) delay(1_200L)
            asked++
            val found = PlaceLookup.suggest(cluster.lat, cluster.lng)
            if (found != null) settings.setSuggestion(key, found)
        }
        asked
    }

    /** The tidy-up the app does by itself: the last three days. */
    suspend fun cleanupRecent(context: Context, now: Long = System.currentTimeMillis()) {
        val note = runCatching { cleanup(context, now - 3 * 24 * 60 * 60_000L, now, now).text() }
            .getOrElse { "Stopped: ${it.message ?: it.javaClass.simpleName}" }
        val settings = PlacesSettings(context)
        settings.lastCleanupMs = now
        settings.lastCleanupNote = note
        AppLog.i("Cleanup", "Tidy-up of the last three days: $note")
        // New readings may be outside every offline map area; the map job checks and downloads what is wanted.
        runCatching { MapScheduler.kick(context) }
    }

    /** Tidies everything the readings cover, a week at a time. Returns a line saying what it did. */
    suspend fun cleanupAll(context: Context, now: Long = System.currentTimeMillis()): String {
        val first = AppDatabase.get(context).rawDao().firstMs() ?: return "No readings stored yet"
        val week = 7 * 24 * 60 * 60_000L
        var from = first
        var total = Cleanup()
        val note = try {
            while (from < now) {
                total += cleanup(context, from, from + week, now)
                from += week
            }
            "Whole history: " + total.text()
        } catch (e: Exception) {
            "Stopped: ${e.message ?: e.javaClass.simpleName}"
        }
        val settings = PlacesSettings(context)
        settings.lastCleanupMs = now
        settings.lastCleanupNote = note
        return note
    }

    /** One position reading using the best source available (GPS if it can). Null if none arrives in time. */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(context: Context, timeoutMs: Long = 15_000L): Fix? {
        if (!hasLocation(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val source = CancellationTokenSource()
        val fresh = withTimeoutOrNull(timeoutMs) {
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token)
                    .outcome { source.cancel() }.value
            }.getOrNull()
        }
        val location = fresh ?: runCatching { client.lastLocation.outcome().value }.getOrNull() ?: return null
        val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
        if (accuracy > PlaceRules.MAX_ACCURACY_M) return null
        return Fix(location.latitude, location.longitude, accuracy, location.time, if (location.hasSpeed()) location.speed else -1f)
    }

    /** The clock time for an activity event, which Android stamps with time since the phone started. */
    fun wallClock(elapsedRealtimeNanos: Long): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L

    fun timeText(ms: Long): String =
        if (ms <= 0L) "never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
}
