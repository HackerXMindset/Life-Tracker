package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** Steps and sleep settings kept on the phone. */
class HealthSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("health", Context.MODE_PRIVATE)

    /** Whether the phone's own step counter is used when Health Connect has no steps. */
    var useStepCounter: Boolean
        get() = prefs.getBoolean("use_step_counter", false)
        set(value) = prefs.edit { putBoolean("use_step_counter", value) }

    /** The step counter's value the last time we looked, or -1. It counts from the phone's last restart. */
    var lastCounter: Long
        get() = prefs.getLong("last_counter", -1L)
        set(value) = prefs.edit { putLong("last_counter", value) }

    /** True once Health Connect has been read from the beginning of its history. Later reads only look at the last few days. */
    var healthConnectFirstDone: Boolean
        get() = prefs.getBoolean("hc_first_done", false)
        set(value) = prefs.edit { putBoolean("hc_first_done", value) }

    /** Whether sleep is worked out from phone use, and drawn on the Timeline. */
    var estimateSleep: Boolean
        get() = prefs.getBoolean("estimate_sleep", true)
        set(value) = prefs.edit { putBoolean("estimate_sleep", value) }

    var showOnTimeline: Boolean
        get() = prefs.getBoolean("show_on_timeline", true)
        set(value) = prefs.edit { putBoolean("show_on_timeline", value) }

    /** When steps and sleep were last updated, as text for the screen. Empty if never. */
    var lastSync: String
        get() = prefs.getString("last_sync", "") ?: ""
        set(value) = prefs.edit { putString("last_sync", value) }
}
