package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** Places settings and the small bits of state the background code keeps, all on the phone. */
class PlacesSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("places", Context.MODE_PRIVATE)

    /** Whether places are watched at all. Off until you switch it on (after allowing location). */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    /** Your height in cm, or 0 when you have not said. Step length starts from it. */
    var heightCm: Int
        get() = prefs.getInt("height_cm", 0)
        set(value) = prefs.edit {
            putInt("height_cm", value)
            // A new height starts the step length again.
            putFloat("stride_m", 0f)
        }

    /** The step length learned from your own walks, in metres, or 0 when nothing is learned yet. */
    var learnedStrideM: Float
        get() = prefs.getFloat("stride_m", 0f)
        set(value) = prefs.edit { putFloat("stride_m", value) }

    /** The step length to use now: the learned one, else from your height, else a typical 0.70 m. */
    fun strideM(): Double = when {
        learnedStrideM > 0f -> learnedStrideM.toDouble()
        heightCm > 0 -> TripRules.strideFromHeightCm(heightCm)
        else -> TripRules.DEFAULT_STRIDE_M
    }

    /** Seconds between position readings while the app is checking where you are. */
    var intervalSec: Int
        get() = prefs.getInt("interval_sec", 60)
        set(value) = prefs.edit { putInt("interval_sec", value) }

    /** Whether trips (the way between two stays) are recorded. */
    var recordTrips: Boolean
        get() = prefs.getBoolean("record_trips", true)
        set(value) = prefs.edit { putBoolean("record_trips", value) }

    /** Seconds between position readings while you are travelling. */
    var tripIntervalSec: Int
        get() = prefs.getInt("trip_interval_sec", 30)
        set(value) = prefs.edit { putInt("trip_interval_sec", value) }

    /** Always-on mode: the position readings run all the time (more complete, more battery) instead of only when needed. */
    var alwaysOn: Boolean
        get() = prefs.getBoolean("always_on", false)
        set(value) = prefs.edit { putBoolean("always_on", value) }

    /** Seconds between readings in always-on mode while you are not moving. */
    var alwaysOnIntervalSec: Int
        get() = prefs.getInt("always_on_interval_sec", 120)
        set(value) = prefs.edit { putInt("always_on_interval_sec", value) }

    /** The last time the readings were seen running (a reading arrived or the service started). */
    var lastBeatMs: Long
        get() = prefs.getLong("last_beat", 0L)
        set(value) = prefs.edit { putLong("last_beat", value) }

    /** How many times the 15 minute check had to start the readings again because Android had stopped them, and when last. */
    var watchdogStarts: Int
        get() = prefs.getInt("watchdog_starts", 0)
        set(value) = prefs.edit { putInt("watchdog_starts", value) }

    var lastWatchdogMs: Long
        get() = prefs.getLong("last_watchdog", 0L)
        set(value) = prefs.edit { putLong("last_watchdog", value) }

    /** When the readings were last looked at again to tidy visits and trips, and what it found. */
    var lastCleanupMs: Long
        get() = prefs.getLong("last_cleanup", 0L)
        set(value) = prefs.edit { putLong("last_cleanup", value) }

    var lastCleanupNote: String
        get() = prefs.getString("last_cleanup_note", "") ?: ""
        set(value) = prefs.edit { putString("last_cleanup_note", value) }

    /** Whether trips are drawn on the Timeline. */
    var showTripsOnTimeline: Boolean
        get() = prefs.getBoolean("show_trips_on_timeline", true)
        set(value) = prefs.edit { putBoolean("show_trips_on_timeline", value) }

    /** Whether readings use GPS (most accurate, more battery) or only Wi-Fi and mobile network. */
    var useGps: Boolean
        get() = prefs.getBoolean("use_gps", true)
        set(value) = prefs.edit { putBoolean("use_gps", value) }

    /** Whether visits are drawn on the Timeline. */
    var showOnTimeline: Boolean
        get() = prefs.getBoolean("show_on_timeline", true)
        set(value) = prefs.edit { putBoolean("show_on_timeline", value) }

    /** When Android last told the app something about places or stillness (0 = never). Shown so you can see it works. */
    var lastEventMs: Long
        get() = prefs.getLong("last_event", 0L)
        set(value) = prefs.edit { putLong("last_event", value) }

    /** When the last position reading arrived (0 = never). */
    var lastFixMs: Long
        get() = prefs.getLong("last_fix", 0L)
        set(value) = prefs.edit { putLong("last_fix", value) }

    /** When the places were last handed to Android, and how that went. */
    var lastRegisterMs: Long
        get() = prefs.getLong("last_register", 0L)
        set(value) = prefs.edit { putLong("last_register", value) }

    var lastStatus: String
        get() = prefs.getString("last_status", "") ?: ""
        set(value) = prefs.edit { putString("last_status", value) }

    /** True while the position readings are running (the notification is showing). */
    var watching: Boolean
        get() = prefs.getBoolean("watching", false)
        set(value) = prefs.edit { putBoolean("watching", value) }

    /** What the stay detector remembers between readings. */
    var engine: WatchState
        get() = WatchState.fromJson(prefs.getString("engine", "") ?: "")
        set(value) = prefs.edit { putString("engine", value.toJson()) }

    /** The last reading that was believed (not a glitch), used to spot position jumps. Null if none yet. */
    var lastGood: RawFixEntity?
        get() {
            val p = (prefs.getString("last_good", "") ?: "").split('|')
            if (p.size != 5) return null
            return try {
                RawFixEntity(p[0].toLong(), p[1].toDouble(), p[2].toDouble(), p[3].toFloat(), p[4].toFloat())
            } catch (e: NumberFormatException) {
                null
            }
        }
        set(value) = prefs.edit { putString("last_good", if (value == null) "" else "${value.ms}|${value.lat}|${value.lng}|${value.acc}|${value.speed}") }

    /** How many readings in a row were thrown out as glitches just now, and how many in all. */
    var glitchRun: Int
        get() = prefs.getInt("glitch_run", 0)
        set(value) = prefs.edit { putInt("glitch_run", value) }

    var glitches: Int
        get() = prefs.getInt("glitches", 0)
        set(value) = prefs.edit { putInt("glitches", value) }

    /** What the motion sensor last said: "still", "moving" or "" if it has not said anything. And since when, and how often. */
    var motionState: String
        get() = prefs.getString("motion_state", "") ?: ""
        set(value) = prefs.edit { putString("motion_state", value) }

    var motionSinceMs: Long
        get() = prefs.getLong("motion_since", 0L)
        set(value) = prefs.edit { putLong("motion_since", value) }

    var motionChanges: Int
        get() = prefs.getInt("motion_changes", 0)
        set(value) = prefs.edit { putInt("motion_changes", value) }

    /** Whether the phone has a motion sensor the service could use the last time it started: "yes", "no" or "" (not tried). */
    var motionSensor: String
        get() = prefs.getString("motion_sensor", "") ?: ""
        set(value) = prefs.edit { putString("motion_sensor", value) }

    /** A name found on the internet for a spot, kept so each spot is only looked up once. */
    fun suggestion(key: String): String? = prefs.getString("sug_$key", null)

    fun setSuggestion(key: String, value: String) = prefs.edit { putString("sug_$key", value) }
}
