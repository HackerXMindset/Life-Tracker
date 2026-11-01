package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/** Two-phone sync switches that belong to this phone only (never copied). */
class SyncSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    /** Sync is off until it is switched on from the Sync screen. While off, nothing is noted and nothing is sent. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    /** A name for this phone in the mailbox, made once. */
    val deviceId: String
        get() = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { id -> prefs.edit { putString("device_id", id) } }
}
