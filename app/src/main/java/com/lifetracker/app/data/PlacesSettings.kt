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

    /** Whether stops at spots you have not named are noticed. */
    var detectStops: Boolean
        get() = prefs.getBoolean("detect_stops", true)
        set(value) = prefs.edit { putBoolean("detect_stops", value) }

    /** Whether visits are drawn on the Timeline. */
    var showOnTimeline: Boolean
        get() = prefs.getBoolean("show_on_timeline", true)
        set(value) = prefs.edit { putBoolean("show_on_timeline", value) }

    /** When Android last told the app you arrived or left (0 = never). Shown so you can see it works. */
    var lastEventMs: Long
        get() = prefs.getLong("last_event", 0L)
        set(value) = prefs.edit { putLong("last_event", value) }

    /** When the places were last handed to Android, and how that went. */
    var lastRegisterMs: Long
        get() = prefs.getLong("last_register", 0L)
        set(value) = prefs.edit { putLong("last_register", value) }

    var lastStatus: String
        get() = prefs.getString("last_status", "") ?: ""
        set(value) = prefs.edit { putString("last_status", value) }

    /** The stop in progress: when the phone went still and where (position may be missing). */
    var candidate: StopCandidate?
        get() {
            val start = prefs.getLong("cand_start", 0L)
            if (start == 0L) return null
            val hasFix = prefs.getBoolean("cand_fix", false)
            return if (hasFix) {
                StopCandidate(
                    start,
                    java.lang.Double.longBitsToDouble(prefs.getLong("cand_lat", 0L)),
                    java.lang.Double.longBitsToDouble(prefs.getLong("cand_lng", 0L)),
                )
            } else {
                StopCandidate(start, null, null)
            }
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove("cand_start"); remove("cand_fix"); remove("cand_lat"); remove("cand_lng")
            } else {
                putLong("cand_start", value.startMs)
                val lat = value.lat
                val lng = value.lng
                if (lat != null && lng != null) {
                    putBoolean("cand_fix", true)
                    putLong("cand_lat", java.lang.Double.doubleToRawLongBits(lat))
                    putLong("cand_lng", java.lang.Double.doubleToRawLongBits(lng))
                } else {
                    putBoolean("cand_fix", false)
                }
            }
        }

    /** When the phone last started moving (the end of the last stop), 0 if unknown. */
    var moveStartMs: Long
        get() = prefs.getLong("move_start", 0L)
        set(value) = prefs.edit { putLong("move_start", value) }
}
