package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.lifetracker.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Takes a position reading every minute (or as often as you chose) while the app does not know where you are, and
 * stops by itself when you are at a named place or moving. Android only allows readings that often from a
 * foreground service, which is why the notification shows while it runs. It is started by Android's own
 * "arrived near a place" and "phone went still / moved" messages, never at other times.
 */
class PlaceWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var callback: LocationCallback? = null
    private var currentIntervalSec = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must come first: Android crashes the app if a started foreground service does not announce itself quickly.
        showNotification()
        beginUpdates()
        return START_NOT_STICKY
    }

    private fun showNotification() {
        ensureChannel(this)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Checking where you are")
            .setContentText("Only while you are somewhere the app does not know. It stops by itself.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun beginUpdates() {
        if (callback != null) return
        val settings = PlacesSettings(this)
        if (!settings.enabled || !PlacesTracker.hasLocation(this)) {
            stopSelf()
            return
        }
        settings.watching = true
        requestUpdates(PlaceEngine.intervalSec(settings.engine, settings.intervalSec, settings.tripIntervalSec))
    }

    /** Starts (or restarts, if the pace changed) the position readings. */
    @SuppressLint("MissingPermission")
    private fun requestUpdates(intervalSec: Int) {
        val client = LocationServices.getFusedLocationProviderClient(this)
        callback?.let { runCatching { client.removeLocationUpdates(it) } }
        val settings = PlacesSettings(this)
        val interval = intervalSec.coerceAtLeast(10) * 1000L
        currentIntervalSec = intervalSec
        val request = LocationRequest.Builder(
            if (settings.useGps) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            interval,
        )
            .setMinUpdateIntervalMillis(interval / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    val fix = Fix(
                        location.latitude,
                        location.longitude,
                        if (location.hasAccuracy()) location.accuracy else 0f,
                        location.time,
                    )
                    scope.launch {
                        val watch = runCatching { PlacesTracker.onFix(applicationContext, fix) }.getOrNull()
                        withContext(Dispatchers.Main) {
                            when {
                                watch == null -> Unit
                                !watch.keep -> stopWatching()
                                watch.intervalSec != currentIntervalSec && callback != null -> requestUpdates(watch.intervalSec)
                            }
                        }
                    }
                }
            }
        }
        callback = cb
        runCatching { client.requestLocationUpdates(request, cb, Looper.getMainLooper()) }.onFailure { stopWatching() }
    }

    private fun stopWatching() {
        callback?.let { runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) } }
        callback = null
        PlacesSettings(this).watching = false
        stopSelf()
    }

    override fun onDestroy() {
        callback?.let { runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) } }
        callback = null
        PlacesSettings(this).watching = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "places_watch"
        private const val NOTIFICATION_ID = 4101

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Checking where you are")
                .setDescription("Shown while the app takes position readings at a place it does not know.")
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        /** Starts the readings. Safe to call when they are already running. */
        fun start(context: Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, PlaceWatchService::class.java))
            }
        }
    }
}
