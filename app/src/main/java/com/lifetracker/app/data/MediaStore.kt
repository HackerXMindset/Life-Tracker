package com.lifetracker.app.data

import android.content.Context
import org.json.JSONObject

/**
 * The accounts. Only names and Simkl's sign-in are kept, and this file is left out of backups so a restore never carries a
 * sign-in to another phone.
 */
class MediaAccounts(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var aniListUser: String
        get() = prefs.getString("anilist_user", "").orEmpty()
        set(v) { prefs.edit().putString("anilist_user", v).apply() }

    var aniListId: Int
        get() = prefs.getInt("anilist_id", 0)
        set(v) { prefs.edit().putInt("anilist_id", v).apply() }

    var malUser: String
        get() = prefs.getString("mal_user", "").orEmpty()
        set(v) { prefs.edit().putString("mal_user", v).apply() }

    var simklClientId: String
        get() = prefs.getString("simkl_client", "").orEmpty()
        set(v) { prefs.edit().putString("simkl_client", v).apply() }

    var simklToken: String
        get() = prefs.getString("simkl_token", "").orEmpty()
        set(v) { prefs.edit().putString("simkl_token", v).apply() }

    var comickClientId: String
        get() = prefs.getString("comick_client", "").orEmpty()
        set(v) { prefs.edit().putString("comick_client", v).apply() }

    var comickAccess: String
        get() = prefs.getString("comick_access", "").orEmpty()
        set(v) { prefs.edit().putString("comick_access", v).apply() }

    var comickRefresh: String
        get() = prefs.getString("comick_refresh", "").orEmpty()
        set(v) { prefs.edit().putString("comick_refresh", v).apply() }

    var comickExpiryMs: Long
        get() = prefs.getLong("comick_expiry", 0L)
        set(v) { prefs.edit().putLong("comick_expiry", v).apply() }

    var comickName: String
        get() = prefs.getString("comick_name", "").orEmpty()
        set(v) { prefs.edit().putString("comick_name", v).apply() }

    /** What the last sign-in said, for the settings page to show. */
    var comickMessage: String
        get() = prefs.getString("comick_message", "").orEmpty()
        set(v) { prefs.edit().putString("comick_message", v).apply() }

    /** The sign-in that was started and is waiting for the browser to come back. */
    var comickAttempt: ComickAuth.Attempt?
        get() {
            val state = prefs.getString("comick_state", "").orEmpty()
            val verifier = prefs.getString("comick_verifier", "").orEmpty()
            return if (state.isEmpty() || verifier.isEmpty()) null else ComickAuth.Attempt(state, verifier, prefs.getLong("comick_started", 0L))
        }
        set(v) {
            val e = prefs.edit()
            if (v == null) e.remove("comick_state").remove("comick_verifier").remove("comick_started")
            else e.putString("comick_state", v.state).putString("comick_verifier", v.verifier).putLong("comick_started", v.createdMs)
            e.commit()
        }

    /** Saves a new access token and, when one came, the new refresh token (the old one stops working once used). */
    fun saveComickTokens(tokens: MediaParsers.ComickTokens, nowMs: Long) {
        val e = prefs.edit().putString("comick_access", tokens.access).putLong("comick_expiry", nowMs + tokens.expiresInSeconds * 1000L)
        if (tokens.refresh != null) e.putString("comick_refresh", tokens.refresh)
        e.commit()
    }

    fun clearComickTokens() {
        prefs.edit().remove("comick_access").remove("comick_refresh").remove("comick_expiry").remove("comick_name").commit()
    }

    /** Calls [onChange] whenever an account setting changes (a sign-in finishing in the background). Returns the way to stop. */
    fun listen(onChange: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun connected(service: MediaService): Boolean = when (service) {
        MediaService.ANILIST -> aniListUser.isNotBlank() && aniListId > 0
        MediaService.MAL -> malUser.isNotBlank()
        MediaService.SIMKL -> simklClientId.isNotBlank() && simklToken.isNotBlank()
        MediaService.COMICK -> comickClientId.isNotBlank() && (comickAccess.isNotBlank() || comickRefresh.isNotBlank())
        MediaService.MIHON, MediaService.KOTATSU -> false
    }

    fun anyConnected(): Boolean = MediaService.accounts.any { connected(it) }

    fun disconnect(service: MediaService) {
        val e = prefs.edit()
        when (service) {
            MediaService.ANILIST -> e.remove("anilist_user").remove("anilist_id")
            MediaService.MAL -> e.remove("mal_user")
            MediaService.SIMKL -> e.remove("simkl_token")
            MediaService.COMICK -> e.remove("comick_access").remove("comick_refresh").remove("comick_expiry").remove("comick_name")
            MediaService.MIHON, MediaService.KOTATSU -> {}
        }
        e.apply()
    }

    companion object {
        const val FILE = "media_accounts"
    }
}

/** Which app goes with which service, and how long a chapter is taken to be. Kept in backups. */
class MediaSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Package name to service key. */
    fun appMap(): Map<String, MediaService> {
        val json = runCatching { JSONObject(prefs.getString("apps", "{}").orEmpty()) }.getOrDefault(JSONObject())
        val out = LinkedHashMap<String, MediaService>()
        for (key in json.keys()) MediaService.of(json.optString(key))?.let { out[key] = it }
        return out
    }

    fun setApp(pkg: String, service: MediaService?) {
        val json = runCatching { JSONObject(prefs.getString("apps", "{}").orEmpty()) }.getOrDefault(JSONObject())
        if (service == null) json.remove(pkg) else json.put(pkg, service.key)
        prefs.edit().putString("apps", json.toString()).apply()
    }

    var minutesPerChapter: Int
        get() = prefs.getInt("chapter_minutes", DEFAULT_CHAPTER_MINUTES).coerceIn(1, 60)
        set(v) { prefs.edit().putInt("chapter_minutes", v.coerceIn(1, 60)).apply() }

    /** How long an episode is taken to be when the service does not say (MyAnimeList never does). */
    var episodeMinutes: Int
        get() = prefs.getInt("episode_minutes", DEFAULT_EPISODE_MINUTES).coerceIn(5, 180)
        set(v) { prefs.edit().putInt("episode_minutes", v.coerceIn(5, 180)).apply() }

    /** Which place wins when the same chapter or episode is noted in more than one: first is the most trusted. */
    var priority: List<MediaService>
        get() = MediaMatch.completeOrder(prefs.getString("priority", "").orEmpty().split(',').mapNotNull { MediaService.of(it) })
        set(v) { prefs.edit().putString("priority", MediaMatch.completeOrder(v).joinToString(",") { it.key }).apply() }

    /** Whether how long a chapter takes is learned from the times that were really measured. */
    var learnSpeed: Boolean
        get() = prefs.getBoolean("learn_speed", true)
        set(v) { prefs.edit().putBoolean("learn_speed", v).apply() }

    /** What a chapter is taken to be when no time is known: the learned one if switched on and there are enough measured times. */
    fun chapterMinutesFor(samples: List<Int>): Int =
        (if (learnSpeed) MediaSpeed.learnedMinutes(samples) else null) ?: minutesPerChapter

    /** How far back the first check, and a backdated Simkl entry, may fill. */
    var lookbackDays: Int
        get() = prefs.getInt("lookback_days", DEFAULT_LOOKBACK_DAYS)
        set(v) { prefs.edit().putInt("lookback_days", v).apply() }

    companion object {
        const val DEFAULT_LOOKBACK_DAYS = 30
        val LOOKBACK_OPTIONS = listOf(14 to "2 weeks", 30 to "1 month", 90 to "3 months", 365 to "1 year")
        const val FILE = "media_settings"
        const val DEFAULT_CHAPTER_MINUTES = 8
        const val DEFAULT_EPISODE_MINUTES = 24
        val EPISODE_OPTIONS = listOf(20, 24, 30, 45)
        val CHAPTER_OPTIONS = listOf(4, 8, 12, 20)
    }
}

/** What was seen on the last check, so the next one only reports what moved. Left out of backups; the entries are marked in the database. */
class MediaSyncState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var lastSyncMs: Long
        get() = prefs.getLong("last_sync", 0L)
        set(v) { prefs.edit().putLong("last_sync", v).apply() }

    var lastLine: String
        get() = prefs.getString("last_line", "").orEmpty()
        set(v) { prefs.edit().putString("last_line", v).apply() }

    var simklStamp: String
        get() = prefs.getString("simkl_stamp", "").orEmpty()
        set(v) { prefs.edit().putString("simkl_stamp", v).apply() }

    fun snapshot(service: MediaService): Map<String, Int> {
        val json = runCatching { JSONObject(prefs.getString("snap_" + service.key, "{}").orEmpty()) }.getOrDefault(JSONObject())
        return json.keys().asSequence().associateWith { json.optInt(it) }
    }

    fun saveSnapshot(service: MediaService, map: Map<String, Int>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString("snap_" + service.key, json.toString()).apply()
    }

    /** The name, time and size of the backup file already read for [service], so an unchanged file is not read again. */
    fun backupSeen(service: MediaService): String = prefs.getString("seen_" + service.key, "").orEmpty()

    fun setBackupSeen(service: MediaService, key: String) {
        prefs.edit().putString("seen_" + service.key, key).apply()
    }

    fun clear(service: MediaService) {
        prefs.edit().remove("snap_" + service.key).remove("seen_" + service.key)
            .also { if (service == MediaService.SIMKL) it.remove("simkl_stamp") }.apply()
    }

    companion object {
        const val FILE = "media_sync"
    }
}
