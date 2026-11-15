package com.lifetracker.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the backups that Mihon and Kotatsu make of their own history. They are the one place that says how long you really spent
 * on each chapter, so what they say is used as it is. Nothing is written to them and no Android is in this file, so it is tested.
 */
object MediaBackups {
    /** Two reading sessions of one title with less than this between them are one stretch. */
    const val MERGE_GAP_MS = 10 * 60_000L

    // ---- A tiny reader for the protocol buffers format Mihon's backup is written in ----

    class Field(val number: Int, val wire: Int, val value: Long, val data: ByteArray?) {
        fun text(): String = data?.toString(Charsets.UTF_8).orEmpty()
        fun float(): Float = Float.fromBits(value.toInt())
    }

    fun fields(b: ByteArray): List<Field> {
        val out = ArrayList<Field>()
        var i = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (i >= b.size || shift > 63) throw MediaException("That backup file is damaged or not from Mihon.")
                val c = b[i++].toInt() and 0xff
                result = result or ((c and 0x7f).toLong() shl shift)
                shift += 7
                if (c and 0x80 == 0) return result
            }
        }
        while (i < b.size) {
            val key = varint()
            val number = (key shr 3).toInt()
            when (val wire = (key and 7).toInt()) {
                0 -> out += Field(number, wire, varint(), null)
                1 -> {
                    if (i + 8 > b.size) throw MediaException("That backup file is damaged or not from Mihon.")
                    var v = 0L
                    for (k in 7 downTo 0) v = (v shl 8) or (b[i + k].toLong() and 0xff)
                    i += 8
                    out += Field(number, wire, v, null)
                }
                2 -> {
                    val n = varint().toInt()
                    if (n < 0 || i + n > b.size) throw MediaException("That backup file is damaged or not from Mihon.")
                    out += Field(number, wire, 0L, b.copyOfRange(i, i + n))
                    i += n
                }
                5 -> {
                    if (i + 4 > b.size) throw MediaException("That backup file is damaged or not from Mihon.")
                    var v = 0L
                    for (k in 3 downTo 0) v = (v shl 8) or (b[i + k].toLong() and 0xff)
                    i += 4
                    out += Field(number, wire, v, null)
                }
                else -> throw MediaException("That backup file is damaged or not from Mihon.")
            }
        }
        return out
    }

    fun gunzipIfNeeded(raw: ByteArray): ByteArray {
        if (raw.size < 2 || raw[0] != 0x1f.toByte() || raw[1] != 0x8b.toByte()) return raw
        return GZIPInputStream(ByteArrayInputStream(raw)).use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            out.toByteArray()
        }
    }

    // ---- Mihon (and the apps made from it) ----

    /** One chapter in a manga's history: when it was last read (the end), and the total time spent on it so far. */
    class MihonRow(val title: String, val ids: Set<String>, val chapterKey: String, val number: Double, val lastReadMs: Long, val durationMs: Long)

    /** Mihon's tracker numbers: 1 is MyAnimeList, 2 is AniList. */
    fun mihonRows(raw: ByteArray): List<MihonRow> {
        val top = fields(gunzipIfNeeded(raw))
        val out = ArrayList<MihonRow>()
        for (f in top) {
            if (f.number != 1 || f.wire != 2 || f.data == null) continue
            val manga = fields(f.data)
            val title = manga.firstOrNull { it.number == 3 }?.text().orEmpty()
            if (title.isBlank()) continue
            val sourceUrl = manga.firstOrNull { it.number == 2 }?.text().orEmpty()
            val ids = HashSet<String>()
            val numbers = HashMap<String, Double>()
            for (g in manga) {
                if (g.wire != 2 || g.data == null) continue
                when (g.number) {
                    16 -> {
                        val c = fields(g.data)
                        val url = c.firstOrNull { it.number == 1 }?.text().orEmpty()
                        val number = c.firstOrNull { it.number == 9 && it.wire == 5 }?.float()?.toDouble() ?: -1.0
                        if (url.isNotEmpty()) numbers[url] = number
                    }
                    18 -> {
                        val t = fields(g.data)
                        val sync = t.firstOrNull { it.number == 1 }?.value?.toInt() ?: 0
                        val remote = t.firstOrNull { it.number == 100 }?.value?.takeIf { it > 0 }
                            ?: t.firstOrNull { it.number == 3 }?.value?.takeIf { it > 0 } ?: 0L
                        if (remote > 0) when (sync) {
                            1 -> ids += "mal:manga:$remote"
                            2 -> ids += "anilist:manga:$remote"
                        }
                    }
                }
            }
            for (g in manga) {
                if (g.number != 104 || g.wire != 2 || g.data == null) continue
                val h = fields(g.data)
                val url = h.firstOrNull { it.number == 1 }?.text().orEmpty()
                val last = h.firstOrNull { it.number == 2 }?.value ?: 0L
                val duration = h.firstOrNull { it.number == 3 }?.value ?: 0L
                if (url.isEmpty() || last <= 0L) continue
                val number = numbers[url] ?: -1.0
                out += MihonRow(title, ids, "$sourceUrl|$url", number, last, max(0L, duration))
            }
        }
        return out
    }

    class BackupEvents(val events: List<MediaEvent>, val snapshot: Map<String, Int>)

    /**
     * Events from Mihon's history. The time on a chapter only ever grows, so what was read since last time is the difference from
     * what was seen then ([old], in seconds); a chapter read for the first time takes all of it. [sinceMs] is the oldest that counts,
     * and only chapters that recent are remembered in the snapshot.
     */
    fun mihonEvents(rows: List<MihonRow>, old: Map<String, Int>, sinceMs: Long): BackupEvents {
        val events = ArrayList<MediaEvent>()
        val snapshot = HashMap<String, Int>()
        for (r in rows) {
            if (r.lastReadMs < sinceMs) continue
            val seconds = (r.durationMs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val key = r.chapterKey
            snapshot[key] = seconds
            val before = old[key]
            // A chapter whose time did not grow was not read again (or was already reported).
            if (before != null && seconds <= before) continue
            val delta = if (before == null) seconds else seconds - before
            val number = r.number.takeIf { it >= 0.0 }
            val label = number?.let { "ch " + trim(it) }.orEmpty()
            events += MediaEvent(
                id = "mihon|$key|${r.lastReadMs}",
                service = MediaService.MIHON,
                kind = MediaKind.MANGA,
                title = r.title,
                atMs = r.lastReadMs,
                units = 1,
                label = label,
                ids = r.ids,
                numFrom = number,
                numTo = number,
                exactMs = delta.toLong().takeIf { it >= 20 }?.times(1000L),
            )
        }
        return BackupEvents(events, snapshot)
    }

    private fun trim(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

    // ---- Kotatsu ----

    class KotatsuSession(val mangaId: Long, val startMs: Long, val durationMs: Long, val pages: Int)

    class KotatsuData(val titles: Map<Long, String>, val ids: Map<Long, Set<String>>, val sessions: List<KotatsuSession>)

    /** Reads the zip Kotatsu writes: its entries "history", "statistics" and "scrobbling" are lists in JSON. */
    fun kotatsuData(raw: ByteArray): KotatsuData {
        val entries = HashMap<String, String>()
        try {
            ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    entries[entry.name.substringAfterLast('/').substringBeforeLast('.')] = out.toString("UTF-8")
                }
            }
        } catch (e: java.io.IOException) {
            throw MediaException("That backup file is damaged or not from Kotatsu.")
        }
        if (entries.isEmpty()) throw MediaException("That backup file is damaged or not from Kotatsu.")
        val titles = HashMap<Long, String>()
        for (o in objects(entries["history"])) {
            val manga = o.optJSONObject("manga") ?: continue
            val id = o.optLong("manga_id", manga.optLong("id", 0L))
            val title = manga.optString("title")
            if (id != 0L && title.isNotBlank()) titles[id] = title
        }
        val ids = HashMap<Long, MutableSet<String>>()
        for (o in objects(entries["scrobbling"])) {
            val manga = o.optLong("manga_id", 0L)
            val target = o.optLong("target_id", 0L)
            if (manga == 0L || target <= 0L) continue
            // Kotatsu's scrobbler numbers: 2 is AniList, 3 is MyAnimeList.
            when (o.optInt("scrobbler", 0)) {
                2 -> ids.getOrPut(manga) { HashSet() } += "anilist:manga:$target"
                3 -> ids.getOrPut(manga) { HashSet() } += "mal:manga:$target"
            }
        }
        val sessions = objects(entries["statistics"]).mapNotNull { o ->
            val started = o.optLong("started_at", 0L)
            val duration = o.optLong("duration", 0L)
            if (started <= 0L || duration <= 0L) null
            else KotatsuSession(o.optLong("manga_id", 0L), started, duration, o.optInt("pages", 0))
        }
        return KotatsuData(titles, ids, sessions)
    }

    private fun objects(text: String?): List<JSONObject> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    /** One event for each stretch of reading a title: sessions less than [MERGE_GAP_MS] apart are joined. Sessions before [sinceMs] are left out. */
    fun kotatsuEvents(data: KotatsuData, sinceMs: Long): List<MediaEvent> {
        val events = ArrayList<MediaEvent>()
        for ((mangaId, group) in data.sessions.filter { it.startMs >= sinceMs }.groupBy { it.mangaId }) {
            val title = data.titles[mangaId] ?: continue
            val sorted = group.sortedBy { it.startMs }
            var start = sorted.first().startMs
            var end = start + sorted.first().durationMs
            var reading = sorted.first().durationMs
            fun flush() {
                events += MediaEvent(
                    id = "kotatsu|$mangaId|$start",
                    service = MediaService.KOTATSU,
                    kind = MediaKind.MANGA,
                    title = title,
                    atMs = end,
                    units = 1,
                    label = "",
                    ids = data.ids[mangaId].orEmpty(),
                    exactMs = reading,
                )
            }
            for (s in sorted.drop(1)) {
                if (s.startMs - end <= MERGE_GAP_MS) {
                    reading += s.durationMs
                    end = max(end, s.startMs + s.durationMs)
                } else {
                    flush()
                    start = s.startMs
                    end = s.startMs + s.durationMs
                    reading = s.durationMs
                }
            }
            flush()
        }
        return events
    }
}
