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
 * Keeps places up to date. Android does the watching, so nothing of ours runs in the background between events:
 * Google Play services reports arriving at and leaving each named place (geofences) and when the phone goes still
 * or starts moving (activity transitions). Each report wakes the app for a moment, which saves a visit.
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
            return@withContext "Off"
        }
        if (!hasLocation(context)) {
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
                    .setCircularRegion(it.lat, it.lng, it.radiusM.toFloat())
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

        if (settings.detectStops && hasActivityRecognition(context)) {
            val request = ActivityTransitionRequest(
                listOf(
                    ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.STILL)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                    ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.STILL)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build(),
                ),
            )
            val result = runCatching { recognition.requestActivityTransitionUpdates(request, transitionIntent(context)).outcome() }
            val error = result.exceptionOrNull() ?: result.getOrNull()?.error
            if (error != null) status.append("; stop detection failed: ").append(error.message ?: error.javaClass.simpleName)
        } else {
            runCatching { recognition.removeActivityTransitionUpdates(transitionIntent(context)) }
            if (settings.detectStops) status.append("; stop detection needs the Physical activity permission")
        }
        remember(settings, now, status.toString())
    }

    private fun remember(settings: PlacesSettings, now: Long, text: String): String {
        settings.lastRegisterMs = now
        settings.lastStatus = text
        return text
    }

    /** Android said you arrived at ([entered]) or left a watched place. */
    suspend fun onGeofence(context: Context, placeId: String, entered: Boolean, now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val dao = AppDatabase.get(context).placeDao()
                PlacesSettings(context).lastEventMs = now
                val open = dao.openVisits()
                val changes = if (entered) {
                    val place = dao.place(placeId) ?: return@withLock
                    PlaceRules.enter(open, place, now)
                } else {
                    PlaceRules.exit(open, placeId, now)
                }
                if (changes.isNotEmpty()) dao.upsertVisits(changes)
            }
        }

    /** The phone went still at [atMs]. Reads where it is and notes the start of a possible stop. */
    suspend fun onStillStart(context: Context, atMs: Long) = withContext(Dispatchers.IO) {
        val settings = PlacesSettings(context)
        if (!settings.enabled || !settings.detectStops) return@withContext
        val fix = currentFix(context)
        lock.withLock {
            val dao = AppDatabase.get(context).placeDao()
            if (fix != null) {
                // Android sometimes never reports leaving a place: if the phone is now still somewhere else, you left.
                val open = dao.openVisits()
                if (open.isNotEmpty()) {
                    val closed = PlaceRules.closeIfElsewhere(open, dao.allPlaces(), fix.lat, fix.lng, settings.moveStartMs, System.currentTimeMillis())
                    if (closed.isNotEmpty()) dao.upsertVisits(closed)
                }
            }
            settings.candidate = StopCandidate(atMs, fix?.lat, fix?.lng)
        }
    }

    /** The phone started moving at [atMs]: the stop (if any) ends here. */
    suspend fun onStillEnd(context: Context, atMs: Long) = withContext(Dispatchers.IO) {
        val settings = PlacesSettings(context)
        if (!settings.enabled) return@withContext
        var candidate = settings.candidate
        if (candidate != null && candidate.lat == null && atMs - candidate.startMs >= PlaceRules.MIN_STOP_MS) {
            // No position at the start: you have only just picked the phone up, so it is still about where it was.
            currentFix(context)?.let { candidate = StopCandidate(candidate!!.startMs, it.lat, it.lng) }
        }
        lock.withLock {
            settings.candidate = null
            settings.moveStartMs = atMs
            val stop = candidate ?: return@withLock
            val dao = AppDatabase.get(context).placeDao()
            val around = dao.visitsAround(stop.startMs - PlaceRules.MERGE_GAP_MS, atMs + PlaceRules.MERGE_GAP_MS)
            val changes = PlaceRules.finishStop(stop, atMs, dao.activePlaces(), around)
            if (changes.isNotEmpty()) dao.upsertVisits(changes)
        }
    }

    /** Called after a restart: Android forgot the geofences, and a stop in progress cannot be trusted. */
    suspend fun afterRestart(context: Context) {
        PlacesSettings(context).candidate = null
        runCatching { syncAll(context) }
    }

    /** One quick position read (not from the satellites, so it costs little battery). Null if none arrives in time. */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(context: Context, timeoutMs: Long = 8_000L): Fix? {
        if (!hasLocation(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val source = CancellationTokenSource()
        val fresh = withTimeoutOrNull(timeoutMs) {
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, source.token)
                    .outcome { source.cancel() }.value
            }.getOrNull()
        }
        val location = fresh ?: runCatching { client.lastLocation.outcome().value }.getOrNull() ?: return null
        val accuracy = if (location.hasAccuracy()) location.accuracy else 0f
        if (accuracy > PlaceRules.MAX_ACCURACY_M) return null
        return Fix(location.latitude, location.longitude, accuracy, location.time)
    }

    /** The clock time for an activity event, which Android stamps with time since the phone started. */
    fun wallClock(elapsedRealtimeNanos: Long): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L

    fun timeText(ms: Long): String =
        if (ms <= 0L) "never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
}
