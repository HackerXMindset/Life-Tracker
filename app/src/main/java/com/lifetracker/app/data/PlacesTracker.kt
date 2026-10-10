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
            if (error != null) status.append("; still/moving detection failed: ").append(error.message ?: error.javaClass.simpleName)
        } else {
            runCatching { recognition.removeActivityTransitionUpdates(transitionIntent(context)) }
            status.append("; stops need the Physical activity permission")
        }
        remember(settings, now, status.toString())
    }

    private fun remember(settings: PlacesSettings, now: Long, text: String): String {
        settings.lastRegisterMs = now
        settings.lastStatus = text
        return text
    }

    /** Android said you came near, or went away from, one of the rings around your places. */
    suspend fun onGeofence(context: Context, now: Long = System.currentTimeMillis()) = trigger(context, now) { state ->
        PlaceEngine.onTrigger(state, now)
    }

    /** The phone went still at [atMs]. */
    suspend fun onStillStart(context: Context, atMs: Long) = trigger(context, atMs) { state ->
        PlaceEngine.onStill(state, atMs, System.currentTimeMillis())
    }

    /** The phone started moving at [atMs]. */
    suspend fun onMoving(context: Context, atMs: Long) = trigger(context, atMs) { state ->
        PlaceEngine.onMoving(state, atMs, System.currentTimeMillis())
    }

    private suspend fun trigger(context: Context, at: Long, change: (WatchState) -> WatchState) =
        withContext(Dispatchers.IO) {
            val settings = PlacesSettings(context)
            if (!settings.enabled) return@withContext
            val watch = lock.withLock {
                settings.lastEventMs = System.currentTimeMillis()
                val next = change(settings.engine)
                settings.engine = next
                PlaceEngine.keepWatching(next, System.currentTimeMillis())
            }
            if (watch && hasLocation(context)) PlaceWatchService.start(context)
        }

    /** One position reading from the watch service. Returns whether the readings should go on. */
    suspend fun onFix(context: Context, fix: Fix): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val settings = PlacesSettings(context)
            val dao = AppDatabase.get(context).placeDao()
            val now = System.currentTimeMillis()
            val recent = dao.visitsAround(fix.timeMs - 12 * 60 * 60_000L, fix.timeMs)
            val step = PlaceEngine.onFix(settings.engine, fix, dao.activePlaces(), recent, now)
            if (step.saves.isNotEmpty()) dao.upsertVisits(step.saves)
            settings.engine = step.state
            settings.lastFixMs = now
            step.keepWatching
        }
    }

    /** Starts the readings if the app should be checking where you are right now (used when the app is opened). */
    fun resumeIfNeeded(context: Context) {
        val settings = PlacesSettings(context)
        if (settings.enabled && hasLocation(context) && !settings.watching &&
            PlaceEngine.keepWatching(settings.engine, System.currentTimeMillis())
        ) {
            PlaceWatchService.start(context)
        }
    }

    /** Called after a restart: Android forgot the geofences. */
    suspend fun afterRestart(context: Context) {
        runCatching { syncAll(context) }
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
        return Fix(location.latitude, location.longitude, accuracy, location.time)
    }

    /** The clock time for an activity event, which Android stamps with time since the phone started. */
    fun wallClock(elapsedRealtimeNanos: Long): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L

    fun timeText(ms: Long): String =
        if (ms <= 0L) "never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
}
