package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** Phone usage settings kept on the phone. */
class UsageSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE)

    /** Whether app use is drawn on the Timeline. */
    var showOnTimeline: Boolean
        get() = prefs.getBoolean("show_on_timeline", true)
        set(value) = prefs.edit { putBoolean("show_on_timeline", value) }

    /** Stretches of app use shorter than this many minutes are left off the Timeline (they still count in totals). */
    var minBlockMinutes: Int
        get() = prefs.getInt("min_block_minutes", 2)
        set(value) = prefs.edit { putInt("min_block_minutes", value) }

    /** When the app history was last copied, as text for the screen. Empty if never. */
    var lastSync: String
        get() = prefs.getString("last_sync", "") ?: ""
        set(value) = prefs.edit { putString("last_sync", value) }

    /** Set when you tap "Not now" on the Timeline card that asks for usage access. */
    var promptDismissed: Boolean
        get() = prefs.getBoolean("prompt_dismissed", false)
        set(value) = prefs.edit { putBoolean("prompt_dismissed", value) }
}
