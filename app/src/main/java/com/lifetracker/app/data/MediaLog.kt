package com.lifetracker.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Events written as text, so what was added and what is held back can be remembered between checks. */
object MediaCodec {
    fun toJson(e: MediaEvent): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("s", e.service.key)
        put("k", e.kind.name)
        put("t", e.title)
        put("at", e.atMs)
        put("u", e.units)
        put("l", e.label)
        put("m", e.minutesPerUnit ?: -1)
        put("ids", JSONArray(e.ids.toList()))
        e.numFrom?.let { put("nf", it) }
        e.numTo?.let { put("nt", it) }
        e.exactMs?.let { put("ex", it) }
    }

    fun fromJson(o: JSONObject): MediaEvent? {
        val service = MediaService.of(o.optString("s")) ?: return null
        val kind = MediaKind.entries.firstOrNull { it.name == o.optString("k") } ?: return null
        val id = o.optString("id")
        if (id.isBlank()) return null
        val ids = HashSet<String>()
        o.optJSONArray("ids")?.let { for (i in 0 until it.length()) ids += it.optString(i) }
        return MediaEvent(
            id = id,
            service = service,
            kind = kind,
            title = o.optString("t"),
            atMs = o.optLong("at"),
            units = o.optInt("u", 1),
            label = o.optString("l"),
            minutesPerUnit = o.optInt("m", -1).takeIf { it > 0 },
            ids = ids,
            numFrom = if (o.has("nf")) o.optDouble("nf") else null,
            numTo = if (o.has("nt")) o.optDouble("nt") else null,
            exactMs = if (o.has("ex")) o.optLong("ex") else null,
        )
    }
}

/** One entry this feature added: where it went, how far to trust its time, and what it was made from. */
class RecentItem(
    val entryId: Long,
    val date: String,
    val startMinute: Int,
    val endMinute: Int,
    val title: String,
    val how: String,
    val runMs: Long,
    val event: MediaEvent,
)

/**
 * What the feature remembers about its own work: the entries it added (to show, to undo, and to spot the same thing noted in
 * another place), the doubtful ones held for you, and the measured chapter times it learns your speed from. Left out of backups.
 */
class MediaLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun recent(): List<RecentItem> {
        val array = runCatching { JSONArray(prefs.getString("recent", "[]").orEmpty()) }.getOrNull() ?: return emptyList()
        val out = ArrayList<RecentItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val event = MediaCodec.fromJson(o.optJSONObject("e") ?: continue) ?: continue
            out += RecentItem(
                o.optLong("entry"), o.optString("date"), o.optInt("s"), o.optInt("e2"), o.optString("title"),
                o.optString("how"), o.optLong("run"), event,
            )
        }
        return out
    }

    private fun saveRecent(items: List<RecentItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject().put("entry", it.entryId).put("date", it.date).put("s", it.startMinute).put("e2", it.endMinute)
                    .put("title", it.title).put("how", it.how).put("run", it.runMs).put("e", MediaCodec.toJson(it.event)),
            )
        }
        prefs.edit().putString("recent", array.toString()).apply()
    }

    /** Adds to the list, keeping the newest [KEEP] and nothing older than [KEEP_DAYS] days. */
    fun addRecent(items: List<RecentItem>, nowMs: Long) {
        val oldest = nowMs - KEEP_DAYS * 24L * 60 * 60 * 1000
        val all = (recent() + items).filter { it.event.atMs >= oldest }
        saveRecent(all.takeLast(KEEP))
    }

    fun removeRecent(entryIds: Set<Long>) {
        saveRecent(recent().filter { it.entryId !in entryIds })
    }

    fun pending(): List<MediaMaybe> {
        val array = runCatching { JSONArray(prefs.getString("pending", "[]").orEmpty()) }.getOrNull() ?: return emptyList()
        val out = ArrayList<MediaMaybe>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val event = MediaCodec.fromJson(o.optJSONObject("e") ?: continue) ?: continue
            out += MediaMaybe(event, o.optString("why"))
        }
        return out
    }

    fun setPending(list: List<MediaMaybe>) {
        val array = JSONArray()
        list.takeLast(PENDING_KEEP).forEach { array.put(JSONObject().put("why", it.reason).put("e", MediaCodec.toJson(it.event))) }
        prefs.edit().putString("pending", array.toString()).apply()
    }

    fun speedSamples(): List<Int> {
        val array = runCatching { JSONArray(prefs.getString("speed", "[]").orEmpty()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).map { array.optInt(it) }.filter { it > 0 }
    }

    fun addSpeedSamples(seconds: List<Int>) {
        if (seconds.isEmpty()) return
        val all = (speedSamples() + seconds).takeLast(MediaSpeed.MAX_SAMPLES)
        prefs.edit().putString("speed", JSONArray(all).toString()).apply()
    }

    companion object {
        const val FILE = "media_log"
        const val KEEP = 300
        const val KEEP_DAYS = 60L
        const val PENDING_KEEP = 100
    }
}
