package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit
import com.lifetracker.app.data.streak.StreakBackupWriter
import org.json.JSONObject

/** The "what changed here, and when" list (Habits H5b), kept apart from Android so it can be tested. */
object HabitChanges {
    /** Reads the saved list. Anything that is not an id with a time is ignored. */
    fun parse(text: String?): Map<String, Long> {
        if (text.isNullOrBlank()) return emptyMap()
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, Long>()
        for (key in o.keys()) {
            val at = o.optLong(key, -1L)
            if (at >= 0) out[key] = at
        }
        return out
    }

    fun toJson(changes: Map<String, Long>): String {
        val o = JSONObject()
        changes.forEach { (id, at) -> o.put(id, at) }
        return o.toString()
    }

    /** The list with [ids] changed at [now]. A time never goes backwards, and blank ids are ignored. */
    fun stamp(changes: Map<String, Long>, ids: Collection<String>, now: Long): Map<String, Long> {
        val out = LinkedHashMap(changes)
        for (id in ids) {
            if (id.isBlank()) continue
            out[id] = maxOf(out[id] ?: 0L, now)
        }
        return out
    }
}

/**
 * What the app remembers for sending habit changes to Streak: which habits and notes changed here, and this app's
 * own device name for the backup files.
 */
class HabitSyncState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("habit_sync", Context.MODE_PRIVATE)

    /** Eight letters and digits like Streak's, made once and kept. */
    val deviceId: String
        get() = synchronized(LOCK) {
            prefs.getString("device", null)?.takeIf { it.isNotBlank() }
                ?: StreakBackupWriter.newDeviceId().also { id -> prefs.edit { putString("device", id) } }
        }

    /** The device name if one was already made, without making one. */
    val deviceIdOrNull: String?
        get() = synchronized(LOCK) { prefs.getString("device", null)?.takeIf { it.isNotBlank() } }

    /** A fingerprint of the last file sent (apart from its time), so the same content is not sent twice. */
    var lastSentDigest: String?
        get() = prefs.getString("last_digest", null)
        set(value) = prefs.edit { putString("last_digest", value) }

    var lastSentText: String?
        get() = prefs.getString("last_sent", null)
        set(value) = prefs.edit { putString("last_sent", value) }

    val changes: Map<String, Long>
        get() = synchronized(LOCK) { HabitChanges.parse(prefs.getString("changes", null)) }

    /** Notes that these habits or notes changed just now. */
    fun stamp(vararg ids: String, now: Long = System.currentTimeMillis()) {
        synchronized(LOCK) {
            val next = HabitChanges.stamp(HabitChanges.parse(prefs.getString("changes", null)), ids.toList(), now)
            prefs.edit { putString("changes", HabitChanges.toJson(next)) }
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
