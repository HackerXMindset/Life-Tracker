package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationAvailability
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
    private var currentHigh = true
    private var motion: MotionSensor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must come first: Android crashes the app if a started foreground service does not announce itself quickly.
        if (callback == null) {
            AppLog.i("Service", "Readings service started" + (if (intent == null) " by Android after it had been stopped" else ""))
        }
        runCatching { showNotification() }.onFailure { AppLog.e("Service", "Could not show the notification Android needs for the readings", it) }
        if (!PlacesSettings(this).enabled) {
            AppLog.i("Service", "Places are switched off: the readings service stops")
            stopSelf()
            return START_NOT_STICKY
        }
        if (PlacesSettings(this).allDay) {
            // GPS all day has its own service; this old one hands over (after announcing itself, as Android requires).
            AppLog.i("Service", "GPS all day is on: handing over to its own service")
            TrackService.start(this)
            stopSelf()
            return START_NOT_STICKY
        }
        beginUpdates()
        // In always-on mode Android brings the service back by itself if it is stopped.
        return if (PlacesSettings(this).alwaysOn) START_STICKY else START_NOT_STICKY
    }

    /** You swiped the app away: ask Android to start the readings again in a few seconds, if they should be running. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val settings = PlacesSettings(this)
        val again = settings.enabled && (settings.alwaysOn || settings.watching)
        AppLog.w("Service", "The app was swiped away" + (if (again) "; asked Android to start the readings again in 5 seconds" else ""))
        if (again) scheduleRestart(this)
        super.onTaskRemoved(rootIntent)
    }

    private fun showNotification() {
        ensureChannel(this)
        val always = PlacesSettings(this).alwaysOn
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(if (always) "Following your places and trips" else "Checking where you are")
            .setContentText(
                if (always) "Always-on mode. Switch it off on the Places screen to save battery."
                else "Only while you are somewhere the app does not know. It stops by itself.",
            )
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
            AppLog.w("Service", "Readings not started: " + (if (!settings.enabled) "places are switched off" else "no location permission"))
            stopSelf()
            return
        }
        settings.watching = true
        settings.lastBeatMs = System.currentTimeMillis()
        startMotion(settings)
        requestUpdates(
            PlaceEngine.intervalSec(settings.engine, PlacesTracker.baseIntervalSec(settings), settings.tripIntervalSec, System.currentTimeMillis()),
            PlacesTracker.useHighAccuracy(settings, settings.engine),
        )
    }

    /** Listens to the motion sensor too, so going still or moving is noticed even when Android's own detection is late. */
    private fun startMotion(settings: PlacesSettings) {
        if (motion != null) return
        val sensor = MotionSensor(applicationContext) { still, since ->
            val now = System.currentTimeMillis()
            // A time that does not make sense (a different clock on some phones) is replaced by now.
            val at = if (since in (now - 2 * 60 * 60_000L)..now) since else now
            scope.launch { runCatching { PlacesTracker.onMotion(applicationContext, still, at) } }
        }
        if (sensor.start()) {
            motion = sensor
            settings.motionSensor = "yes"
        } else {
            settings.motionSensor = "no"
            AppLog.w("Motion", "This phone has no usable motion sensor; only Android's own still/moving messages are used")
        }
    }

    private fun stopMotion() {
        motion?.stop()
        motion = null
    }

    /** Starts (or restarts, if the pace changed) the position readings. */
    @SuppressLint("MissingPermission")
    private fun requestUpdates(intervalSec: Int, highAccuracy: Boolean) {
        val client = LocationServices.getFusedLocationProviderClient(this)
        callback?.let { runCatching { client.removeLocationUpdates(it) } }
        val settings = PlacesSettings(this)
        val interval = intervalSec.coerceAtLeast(10) * 1000L
        currentIntervalSec = intervalSec
        currentHigh = highAccuracy
        val request = LocationRequest.Builder(
            if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            interval,
        )
            .setMinUpdateIntervalMillis(interval / 2)
            .setWaitForAccurateLocation(false)
            .build()
        AppLog.i("Service", "Asking for a reading every ${interval / 1000} s using " + (if (highAccuracy) "GPS and network" else "network only"))
        val cb = object : LocationCallback() {
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    AppLog.w("Service", "Android says no position is available right now (location off, no signal, or indoors)")
                }
            }

            override fun onLocationResult(result: LocationResult) {
                PlacesSettings(applicationContext).lastBeatMs = System.currentTimeMillis()
                if (result.locations.isEmpty()) AppLog.w("Service", "A position update arrived with no readings in it")
                for (location in result.locations) {
                    val fix = Fix(
                        location.latitude,
                        location.longitude,
                        if (location.hasAccuracy()) location.accuracy else 0f,
                        location.time,
                        if (location.hasSpeed()) location.speed else -1f,
                    )
                    scope.launch {
                        val watch = runCatching { PlacesTracker.onFix(applicationContext, fix) }
                            .onFailure { AppLog.e("Fixes", "Could not process the reading at ${LogFormat.clock(fix.timeMs)}", it) }
                            .getOrNull()
                        withContext(Dispatchers.Main) {
                            when {
                                watch == null -> Unit
                                !watch.keep -> stopWatching()
                                (watch.intervalSec != currentIntervalSec || watch.highAccuracy != currentHigh) && callback != null ->
                                    requestUpdates(watch.intervalSec, watch.highAccuracy)
                            }
                        }
                    }
                }
            }
        }
        callback = cb
        runCatching { client.requestLocationUpdates(request, cb, Looper.getMainLooper()) }.onFailure {
            AppLog.e("Service", "Android refused the position request", it)
            stopWatching()
        }
    }

    private fun stopWatching() {
        AppLog.i("Service", "Readings stopped: not needed any more")
        stopMotion()
        callback?.let { runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) } }
        callback = null
        PlacesSettings(this).watching = false
        stopSelf()
    }

    override fun onDestroy() {
        AppLog.i("Service", "Readings service destroyed")
        stopMotion()
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

        /** Stops the gentle readings service (the all-day one has its own stop). */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PlaceWatchService::class.java)) }
        }

        /** Starts the readings. Safe to call when they are already running. Returns false if Android would not allow it. */
        fun start(context: Context): Boolean {
            if (PlacesSettings(context).allDay) return TrackService.start(context)
            return startGentle(context)
        }

        private fun startGentle(context: Context): Boolean =
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, PlaceWatchService::class.java))
            }.onFailure {
                AppLog.e("Service", "Android would NOT let the readings start", it)
            }.isSuccess

        /**
         * Asks Android to start the service again in a few seconds. The alarm lives in the system, so it survives this
         * process being killed, and "allow while idle" is what lets the restarted service become a foreground one.
         */
        private fun scheduleRestart(context: Context) {
            runCatching {
                val alarms = context.getSystemService(AlarmManager::class.java)
                val restart = PendingIntent.getForegroundService(
                    context,
                    21,
                    Intent(context, PlaceWatchService::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 5_000L, restart)
            }
        }
    }
}
