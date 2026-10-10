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
            PlacesSettings(context).lastStatus = "Android reported a geofence problem (code ${event.errorCode}). It will be set up again soon."
            return
        }
        val entered = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return
        }
        val ids = event.triggeringGeofences?.map { it.requestId } ?: return
        val pending = goAsync()
        receiverScope.launch {
            try {
                ids.forEach { PlacesTracker.onGeofence(context.applicationContext, it, entered) }
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
        val events = result.transitionEvents
            .filter { it.activityType == DetectedActivity.STILL }
            .sortedBy { it.elapsedRealTimeNanos }
        if (events.isEmpty()) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                for (e in events) {
                    val at = PlacesTracker.wallClock(e.elapsedRealTimeNanos)
                    if (e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        PlacesTracker.onStillStart(context.applicationContext, at)
                    } else {
                        PlacesTracker.onStillEnd(context.applicationContext, at)
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
