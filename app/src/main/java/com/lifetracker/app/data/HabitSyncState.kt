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
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("habit_sync", Context.MODE_PRIVATE)

    /** Eight letters and digits like Streak's, made once and kept. */
    val deviceId: String
        get() = synchronized(LOCK) {
            prefs.getString("device", null)?.takeIf { it.isNotBlank() }
                ?: StreakBackupWriter.newDeviceId().also { id -> prefs.edit { putString("device", id) } }
        }

    /** Send changes to Streak's folder by themselves, shortly after a change and when the app opens. Off until chosen. */
    var autoSend: Boolean
        get() = prefs.getBoolean("auto_send", false)
        set(value) = prefs.edit { putBoolean("auto_send", value) }

    /** What the last automatic send did, in words, for the Settings screen. */
    var lastSendNote: String?
        get() = prefs.getString("last_note", null)
        set(value) = prefs.edit { putString("last_note", value) }

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

    /** What was deleted here (or by Streak) and when, id to milliseconds. Sent to Streak in every file. */
    val deleted: Map<String, Long>
        get() = synchronized(LOCK) { HabitChanges.parse(prefs.getString("deleted", null)) }

    private fun read() = HabitTombstones.State(
        HabitChanges.parse(prefs.getString("changes", null)),
        HabitChanges.parse(prefs.getString("deleted", null)),
    )

    private fun write(state: HabitTombstones.State, now: Long) {
        prefs.edit {
            putString("changes", HabitChanges.toJson(state.changes))
            putString("deleted", HabitChanges.toJson(HabitTombstones.forgetOld(state.deleted, now)))
        }
    }

    /** Notes that these habits or notes changed just now. A deleted one that changes is no longer deleted. */
    fun stamp(vararg ids: String, now: Long = System.currentTimeMillis()) {
        synchronized(LOCK) { write(HabitTombstones.changed(read(), ids.toList(), now), now) }
        if (autoSend) StreakSendWorker.schedule(appContext, delaySeconds = StreakSendWorker.QUIET_SECONDS)
    }

    /** Notes that these habits or notes were deleted just now. */
    fun markDeleted(vararg ids: String, now: Long = System.currentTimeMillis()) {
        synchronized(LOCK) { write(HabitTombstones.deleted(read(), ids.toList(), now), now) }
        if (autoSend) StreakSendWorker.schedule(appContext, delaySeconds = StreakSendWorker.QUIET_SECONDS)
    }

    /** Streak says [id] was deleted at [at]: remember it, without sending it back as a change made here. */
    fun learnDeleted(id: String, at: Long) {
        synchronized(LOCK) {
            val state = read()
            write(HabitTombstones.State(state.changes - id, state.deleted + (id to maxOf(at, state.deleted[id] ?: 0L))), System.currentTimeMillis())
        }
    }

    /** Streak brought [id] back after it was deleted: forget the deletion (it is not a change made here). */
    fun forgetDeleted(id: String) {
        synchronized(LOCK) {
            val state = read()
            write(HabitTombstones.State(state.changes, state.deleted - id), System.currentTimeMillis())
        }
    }

    /** The last file already read for each of Streak's devices, so a file is only read once. */
    fun lastFileOf(device: String): String? = prefs.getString("file_$device", null)

    fun setLastFileOf(device: String, name: String) {
        prefs.edit { putString("file_$device", name) }
    }

    private companion object {
        val LOCK = Any()
    }
}
