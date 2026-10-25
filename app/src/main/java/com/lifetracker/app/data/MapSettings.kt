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
}
