package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** Call settings kept on the phone. */
class CallsSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("calls", Context.MODE_PRIVATE)

    /** Whether calls are drawn on the Timeline. */
    var showOnTimeline: Boolean
        get() = prefs.getBoolean("show_on_timeline", true)
        set(value) = prefs.edit { putBoolean("show_on_timeline", value) }

    /** The Timeline only shows calls you talked for at least this many minutes. */
    var minTalkMinutes: Int
        get() = prefs.getInt("min_talk_minutes", CallStats.DEFAULT_MIN_TALK_MINUTES)
        set(value) = prefs.edit { putInt("min_talk_minutes", value) }

    /** When the call log was last copied, as text for the screen. Empty if never. */
    var lastSync: String
        get() = prefs.getString("last_sync", "") ?: ""
        set(value) = prefs.edit { putString("last_sync", value) }

    /** Set when you tap "Not now" on the Timeline card that asks for call log access. */
    var promptDismissed: Boolean
        get() = prefs.getBoolean("prompt_dismissed", false)
        set(value) = prefs.edit { putBoolean("prompt_dismissed", value) }
}
