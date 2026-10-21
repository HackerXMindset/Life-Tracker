package com.lifetracker.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Google Play services calls this when you arrive at or leave a named place. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            AppLog.e("Geofence", "Android reported a geofence problem (code ${event.errorCode})")
            PlacesSettings(context).lastStatus = "Android reported a geofence problem (code ${event.errorCode}). It will be set up again soon."
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            return
        }
        val enter = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
        val rings = event.triggeringGeofences.orEmpty().map { it.requestId }.distinct()
        val pending = goAsync()
        receiverScope.launch {
            try {
                val names = runCatching { AppDatabase.get(context.applicationContext).placeDao().activePlaces().associate { it.id to it.name } }
                    .getOrDefault(emptyMap())
                AppLog.i(
                    "Geofence",
                    "Android says you " + (if (enter) "ARRIVED near" else "LEFT") + " the ring of " + rings.joinToString { names[it] ?: it },
                )
                PlacesTracker.onGeofence(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Google Play services calls this when the phone goes still or starts moving. */
class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val events = result.transitionEvents.sortedBy { it.elapsedRealTimeNanos }
        if (events.isEmpty()) return
        AppLog.i("Activity", "Android sent ${events.size} activity message(s)")
        val pending = goAsync()
        receiverScope.launch {
            try {
                for (e in events) {
                    val at = PlacesTracker.wallClock(e.elapsedRealTimeNanos)
                    val entering = e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
                    val name = when (e.activityType) {
                        DetectedActivity.STILL -> "STILL"
                        DetectedActivity.WALKING -> "WALKING"
                        DetectedActivity.RUNNING -> "RUNNING"
                        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
                        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
                        else -> continue
                    }
                    val context = context.applicationContext
                    when {
                        name == "STILL" && entering -> PlacesTracker.onStillStart(context, at)
                        name == "STILL" -> PlacesTracker.onMoving(context, at)
                        entering -> PlacesTracker.onActivity(context, name, at)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** After the phone restarts or the app is updated Android has forgotten the geofences, so they are set up again. */
class PlacesBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i("App", "Broadcast received: ${intent.action}")
        val pending = goAsync()
        receiverScope.launch {
            try {
                PlacesTracker.afterRestart(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
