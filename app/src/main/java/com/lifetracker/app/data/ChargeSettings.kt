package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** Charging settings kept on the phone. */
class ChargeSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("charging", Context.MODE_PRIVATE)

    /** Whether charging sessions are recorded at all. */
    var track: Boolean
        get() = prefs.getBoolean("track", true)
        set(value) = prefs.edit { putBoolean("track", value) }

    /** Whether a banner pops up when you plug in. */
    var showBanner: Boolean
        get() = prefs.getBoolean("show_banner", true)
        set(value) = prefs.edit { putBoolean("show_banner", value) }

    /** Whether charging sessions are drawn on the Timeline. */
    var showOnTimeline: Boolean
        get() = prefs.getBoolean("show_on_timeline", true)
        set(value) = prefs.edit { putBoolean("show_on_timeline", value) }
}
