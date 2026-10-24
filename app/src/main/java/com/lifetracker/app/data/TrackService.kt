package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
 * GPS all day. A foreground service (that is why its notification stays) that asks the phone for position readings at the
 * pace the plan from [TrackBrain] says, and changes the pace, switches the readings off or takes a heartbeat reading when the
 * plan changes. What to do with each reading is decided in [TrackEngine]; this class only talks to Android.
 */
class TrackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var callback: LocationCallback? = null
    private var requestedInterval = 0
    private var requestedMode = ""
    private var motion: MotionSensor? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var bluetooth: BluetoothWatch.Watcher? = null
    private var begun = false
    private var lastShown = ""
    private var lastPlan: Plan? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must come first: Android crashes the app if a started foreground service does not announce itself quickly.
        runCatching { showNotification(PlacesSettings(this).trackStatus.ifEmpty { "Starting" }) }
            .onFailure { AppLog.e("Track", "Could not show the notification Android needs for GPS all day", it) }
        val settings = PlacesSettings(this)
        if (!settings.allDay || !settings.enabled || !PlacesTracker.hasLocation(this)) {
            AppLog.w("Track", "GPS all day not started: " + (if (!settings.allDay) "it is switched off" else if (!settings.enabled) "places are switched off" else "no location permission"))
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> settings.manualPause = true
            ACTION_RESUME -> settings.manualPause = false
        }
        if (!begun) begin(settings, intent == null)
        look()
        return START_STICKY
    }

    private fun begin(settings: PlacesSettings, byAndroid: Boolean) {
        begun = true
        AppLog.i("Track", "GPS all day started" + (if (byAndroid) " by Android after it had been stopped" else ""))
        settings.watching = true
        settings.lastBeatMs = System.currentTimeMillis()
        startSensors()
    }

    /** You swiped the app away: ask Android to start this again in a few seconds. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val settings = PlacesSettings(this)
        val again = settings.enabled && settings.allDay
        AppLog.w("Track", "The app was swiped away" + (if (again) "; asked Android to start GPS all day again in 5 seconds" else ""))
        if (again) scheduleRestart(this)
        super.onTaskRemoved(rootIntent)
    }

    private fun showNotification(text: String) {
        ensureChannel(this)
        val notification = buildNotification(text)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        lastShown = shownKey(text)
    }

    /** What the notification shows now: the line and which button it has. */
    private fun shownKey(text: String) = text + "|" + PlacesSettings(this).manualPause

    private fun buildNotification(text: String): Notification {
        val paused = PlacesSettings(this).manualPause
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, TrackService::class.java).setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Tracking your places and trips")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, if (paused) "Resume" else "Pause", toggle)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(text: String) {
        if (shownKey(text) == lastShown) return
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(text)) }
        lastShown = shownKey(text)
    }

    /** The motion sensor, the Wi-Fi, the charger and car mode: any change makes the plan be looked at again. */
    private fun startSensors() {
        val settings = PlacesSettings(this)
        val sensor = MotionSensor(applicationContext) { still, _ ->
            TrackEngine.motionless = still
            settings.motionState = if (still) "still" else "moving"
            look()
        }
        if (sensor.start()) motion = sensor else AppLog.w("Motion", "This phone has no usable motion sensor; pausing when you are still will not work")

        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = look()
                override fun onLost(network: Network) = look()
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = look()
            }
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb)
            networkCallback = cb
        }.onFailure { AppLog.w("Track", "Could not watch the Wi-Fi: ${it.message}") }

        val watcher = BluetoothWatch.Watcher(applicationContext) { look() }
        if (watcher.start()) bluetooth = watcher

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction("android.app.action.ENTER_CAR_MODE")
            addAction("android.app.action.EXIT_CAR_MODE")
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = look()
        }
        runCatching { ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
            .onSuccess { receiver = r }
            .onFailure { AppLog.w("Track", "Could not watch the charger: ${it.message}") }
    }

    private fun stopSensors() {
        motion?.stop()
        motion = null
        networkCallback?.let { cb -> runCatching { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb) } }
        networkCallback = null
        receiver?.let { r -> runCatching { unregisterReceiver(r) } }
        receiver = null
        bluetooth?.stop()
        bluetooth = null
    }

    /** Looks at the plan again (no new reading) and does what it says. */
    private fun look() {
        scope.launch {
            val outcome = runCatching { TrackEngine.reevaluate(applicationContext) }
                .onFailure { AppLog.e("Track", "Could not look at the plan", it) }
                .getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { apply(outcome) }
        }
    }

    private fun apply(outcome: TrackEngine.Outcome) {
        val plan = outcome.plan
        lastPlan = plan
        updateNotification(plan.status)
        val settings = PlacesSettings(this)
        settings.lastBeatMs = System.currentTimeMillis()
        if (plan.gpsOff) {
            stopUpdates()
        } else if (callback == null || plan.intervalSec != requestedInterval || plan.accuracyMode != requestedMode) {
            requestUpdates(plan)
        }
        handler.removeCallbacks(tick)
        if (outcome.heartbeatNow) takeHeartbeat()
        TrackBrain.waitMs(plan, System.currentTimeMillis())?.let { handler.postDelayed(tick, it) }
    }

    private val tick = Runnable { look() }

    @SuppressLint("MissingPermission")
    private fun takeHeartbeat() {
        scope.launch {
            val fix = runCatching { PlacesTracker.currentFix(applicationContext, 20_000L) }.getOrNull()
            if (fix == null) {
                AppLog.w("Heartbeat", "No usable position within 20 s for the heartbeat reading")
            } else {
                runCatching { TrackEngine.onHeartbeat(applicationContext, fix) }
                    .onFailure { AppLog.e("Heartbeat", "Could not save the heartbeat reading", it) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(plan: Plan) {
        val client = LocationServices.getFusedLocationProviderClient(this)
        callback?.let { runCatching { client.removeLocationUpdates(it) } }
        val interval = plan.intervalSec.coerceAtLeast(1) * 1000L
        requestedInterval = plan.intervalSec
        requestedMode = plan.accuracyMode
        val priority = when (plan.accuracyMode) {
            AccuracyMode.LOW_POWER -> Priority.PRIORITY_LOW_POWER
            AccuracyMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else -> Priority.PRIORITY_HIGH_ACCURACY
        }
        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setWaitForAccurateLocation(false)
            .build()
        AppLog.i("Track", "Asking for a reading every ${interval / 1000} s, ${AccuracyMode.label(plan.accuracyMode)}")
        val cb = object : LocationCallback() {
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    AppLog.w("Track", "Android says no position is available right now (location off, no signal, or indoors)")
                }
            }

            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    scope.launch {
                        val outcome = runCatching { TrackEngine.onLocation(applicationContext, location) }
                            .onFailure { AppLog.e("Fixes", "Could not process the reading at ${LogFormat.clock(location.time)}", it) }
                            .getOrNull() ?: return@launch
                        withContext(Dispatchers.Main) { apply(outcome) }
                    }
                }
            }
        }
        callback = cb
        runCatching { client.requestLocationUpdates(request, cb, Looper.getMainLooper()) }.onFailure {
            AppLog.e("Track", "Android refused the position request", it)
            callback = null
        }
    }

    private fun stopUpdates() {
        val cb = callback ?: return
        runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(cb) }
        callback = null
        requestedInterval = 0
        AppLog.i("Track", "Position readings switched off (${lastPlan?.status ?: "paused"})")
    }

    override fun onDestroy() {
        AppLog.i("Track", "GPS all day service destroyed")
        handler.removeCallbacks(tick)
        stopUpdates()
        stopSensors()
        PlacesSettings(this).watching = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "track_all_day"
        private const val NOTIFICATION_ID = 4102
        const val ACTION_PAUSE = "com.lifetracker.app.TRACK_PAUSE"
        const val ACTION_RESUME = "com.lifetracker.app.TRACK_RESUME"

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("GPS all day")
                .setDescription("Shown while the app records your position all day.")
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        /** Starts GPS all day. Safe to call when it is already running. Returns false if Android would not allow it. */
        fun start(context: Context): Boolean =
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, TrackService::class.java))
            }.onFailure {
                AppLog.e("Track", "Android would NOT let GPS all day start", it)
            }.isSuccess

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TrackService::class.java)) }
        }

        /** Pause or resume from the settings page (the notification button does the same through the service). */
        fun setPaused(context: Context, paused: Boolean) {
            PlacesSettings(context).manualPause = paused
            if (PlacesSettings(context).allDay) start(context)
        }

        private fun scheduleRestart(context: Context) {
            runCatching {
                val alarms = context.getSystemService(AlarmManager::class.java)
                val restart = PendingIntent.getForegroundService(
                    context, 22, Intent(context, TrackService::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 5_000L, restart)
            }
        }
    }
}
