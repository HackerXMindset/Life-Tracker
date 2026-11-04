package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** The map settings, kept on the phone. */
class MapSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("map", Context.MODE_PRIVATE)

    /** The style the maps use: a built-in id or one you added. */
    var styleId: String
        get() = prefs.getString("style_id", MapStyles.DEFAULT_ID) ?: MapStyles.DEFAULT_ID
        set(value) = prefs.edit { putString("style_id", value) }

    /** Every place gets the map around it, as soon as it is made and again when it changes. */
    var autoPlaces: Boolean
        get() = prefs.getBoolean("auto_places", true)
        set(value) = prefs.edit { putBoolean("auto_places", value) }

    /** How far around a place the map is kept, in kilometres each way (a first guess of 3). */
    var placeReachKm: Int
        get() = prefs.getInt("place_reach_km", 3)
        set(value) = prefs.edit { putInt("place_reach_km", value.coerceIn(1, 50)) }

    /** A reading outside every area adds a wanted area around it. */
    var autoWanted: Boolean
        get() = prefs.getBoolean("auto_wanted", true)
        set(value) = prefs.edit { putBoolean("auto_wanted", value) }

    /** How far around such a reading the map is kept, in kilometres each way (a first guess of 2). */
    var wantedReachKm: Int
        get() = prefs.getInt("wanted_reach_km", 2)
        set(value) = prefs.edit { putInt("wanted_reach_km", value.coerceIn(1, 50)) }

    /** Map downloads may use mobile data. On to start with, because you asked for it to happen by itself. */
    var mobileData: Boolean
        get() = prefs.getBoolean("mobile_data", true)
        set(value) = prefs.edit { putBoolean("mobile_data", value) }

    /** Readings up to this time have been checked against the areas already. */
    var checkedUntilMs: Long
        get() = prefs.getLong("checked_until_ms", 0L)
        set(value) = prefs.edit { putLong("checked_until_ms", value) }

    /** How much the downloaded maps may take together, in bytes. 0 is no limit. */
    var limitBytes: Long
        get() = prefs.getLong("limit_bytes", MapLimits.DEFAULT_BYTES)
        set(value) = prefs.edit { putLong("limit_bytes", value.coerceAtLeast(0L)) }

    /** When the limit would be passed, the least used maps are deleted to make room. Off keeps every map and refuses new downloads instead. */
    var autoDelete: Boolean
        get() = prefs.getBoolean("auto_delete", true)
        set(value) = prefs.edit { putBoolean("auto_delete", value) }

    /** A place nobody went to for a long time does not get a map of its own. */
    var skipUnusedPlaces: Boolean
        get() = prefs.getBoolean("skip_unused_places", true)
        set(value) = prefs.edit { putBoolean("skip_unused_places", value) }

    /** Downloads are on hold: nothing is started, what is running stops, and it carries on when you resume. */
    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) = prefs.edit { putBoolean("paused", value) }

    /** When failed downloads that were bad luck were last queued again by themselves. */
    var lastRetryMs: Long
        get() = prefs.getLong("last_retry_ms", 0L)
        set(value) = prefs.edit { putLong("last_retry_ms", value) }

    /** Readings up to this time have been counted as use of the areas they fall in. */
    var usedUntilMs: Long
        get() = prefs.getLong("used_until_ms", 0L)
        set(value) = prefs.edit { putLong("used_until_ms", value) }

    private val use = context.applicationContext.getSharedPreferences("map_use", Context.MODE_PRIVATE)

    /** The last time a reading was inside the area [id], or 0. */
    fun usedMs(id: String): Long = use.getLong(id, 0L)

    fun markUsed(times: Map<String, Long>) {
        if (times.isEmpty()) return
        use.edit { times.forEach { (id, ms) -> if (ms > use.getLong(id, 0L)) putLong(id, ms) } }
    }
}
