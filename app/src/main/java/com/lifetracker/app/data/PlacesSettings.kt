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

    /** A name found on the internet for a spot, kept so each spot is only looked up once. */
    fun suggestion(key: String): String? = prefs.getString("sug_$key", null)

    fun setSuggestion(key: String, value: String) = prefs.edit { putString("sug_$key", value) }
}
