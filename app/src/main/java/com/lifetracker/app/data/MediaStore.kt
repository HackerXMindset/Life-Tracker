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

    fun connected(service: MediaService): Boolean = when (service) {
        MediaService.ANILIST -> aniListUser.isNotBlank() && aniListId > 0
        MediaService.MAL -> malUser.isNotBlank()
        MediaService.SIMKL -> simklClientId.isNotBlank() && simklToken.isNotBlank()
    }

    fun anyConnected(): Boolean = MediaService.entries.any { connected(it) }

    fun disconnect(service: MediaService) {
        val e = prefs.edit()
        when (service) {
            MediaService.ANILIST -> e.remove("anilist_user").remove("anilist_id")
            MediaService.MAL -> e.remove("mal_user")
            MediaService.SIMKL -> e.remove("simkl_token")
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

    /** How far back the first check, and a backdated Simkl entry, may fill. */
    var lookbackDays: Int
        get() = prefs.getInt("lookback_days", DEFAULT_LOOKBACK_DAYS)
        set(v) { prefs.edit().putInt("lookback_days", v).apply() }

    companion object {
        const val DEFAULT_LOOKBACK_DAYS = 30
        val LOOKBACK_OPTIONS = listOf(14 to "2 weeks", 30 to "1 month", 90 to "3 months", 365 to "1 year")
        const val FILE = "media_settings"
        const val DEFAULT_CHAPTER_MINUTES = 8
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

    fun clear(service: MediaService) {
        prefs.edit().remove("snap_" + service.key).also { if (service == MediaService.SIMKL) it.remove("simkl_stamp") }.apply()
    }

    companion object {
        const val FILE = "media_sync"
    }
}
