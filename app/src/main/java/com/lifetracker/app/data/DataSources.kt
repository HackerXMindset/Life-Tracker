package com.lifetracker.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * The three folders you choose once, and the little the app remembers about
 * them. Android keeps the permission to use a chosen folder across restarts.
 */
class DataSources(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("data_sources", Context.MODE_PRIVATE)

    enum class Slot(val key: String, val writable: Boolean) {
        Backup("backup_folder", writable = true),
        Streak("streak_folder", writable = false),
        OpenNutriTracker("nutri_folder", writable = false),
    }

    fun folder(slot: Slot): Uri? = prefs.getString(slot.key, null)?.let(Uri::parse)

    /** Remembers the folder and asks Android to keep access to it. Throws if Android refuses. */
    fun setFolder(slot: Slot, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (slot.writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        app.contentResolver.takePersistableUriPermission(uri, flags)
        val old = folder(slot)
        prefs.edit().putString(slot.key, uri.toString()).apply()
        if (old != null && old != uri) {
            // Let go of the folder that was replaced; Android allows only so many.
            runCatching { app.contentResolver.releasePersistableUriPermission(old, flags) }
        }
    }

    /** The folder's display name, or "Unavailable" if it was moved, deleted or access was lost. */
    fun folderName(uri: Uri): String =
        DocumentFile.fromTreeUri(app, uri)?.takeIf { it.exists() }?.name ?: "Unavailable"

    var lastAutoBackupDate: String?
        get() = prefs.getString("last_auto_backup_date", null)
        set(value) = prefs.edit().putString("last_auto_backup_date", value).apply()

    var lastBackupText: String?
        get() = prefs.getString("last_backup_text", null)
        set(value) = prefs.edit().putString("last_backup_text", value).apply()

    /** The `exportedAt` stamp of the newest Streak backup already imported. */
    var streakSeenExportedAt: String?
        get() = prefs.getString("streak_seen_exported_at", null)
        set(value) = prefs.edit().putString("streak_seen_exported_at", value).apply()

    var lastStreakImportText: String?
        get() = prefs.getString("last_streak_import", null)
        set(value) = prefs.edit().putString("last_streak_import", value).apply()

    /** Name and modified time of the OpenNutriTracker export already imported. */
    var ontSeenKey: String?
        get() = prefs.getString("ont_seen_key", null)
        set(value) = prefs.edit().putString("ont_seen_key", value).apply()

    var lastOntImportText: String?
        get() = prefs.getString("last_ont_import", null)
        set(value) = prefs.edit().putString("last_ont_import", value).apply()
}
