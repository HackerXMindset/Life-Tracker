package com.lifetracker.app.data

import java.time.Instant
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/** Reads what AniList, MyAnimeList and Simkl send back, with no Android in it so it can be tested. */
object MediaParsers {
    /** A watched time of 1970 is how Simkl says "I do not remember when". */
    private const val PLACEHOLDER_BEFORE_MS = 86_400_000L

    fun parseInstant(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
    }

    // ---- AniList ----

    /** The id of the user from the answer to the user lookup, or an error that says what to do. */
    fun aniListUserId(body: String): Int {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: throw MediaException("AniList sent an answer that could not be read. Try again.")
        aniListError(root)
        val user = root.optJSONObject("data")?.optJSONObject("User")
        val id = user?.optInt("id", 0) ?: 0
        if (id <= 0) throw MediaException("AniList has no user with that name. Check the spelling.")
        return id
    }

    private fun aniListError(root: JSONObject) {
        val errors = root.optJSONArray("errors") ?: return
        if (errors.length() == 0) return
        val first = errors.optJSONObject(0)
        val status = first?.optInt("status", 0) ?: 0
        val message = first?.optString("message").orEmpty()
        throw MediaException(
            when {
                status == 404 -> "AniList has no user with that name. Check the spelling."
                status == 429 -> "AniList says too many requests. Try again in a minute."
                "private" in message.lowercase() -> "This AniList list is private. Make it public in your AniList settings, then try again."
                else -> "AniList refused the request" + (if (message.isNotBlank()) ": $message" else "") + "."
            },
        )
    }

    /** The titles on the list, newest change first. */
    fun aniListItems(body: String): List<ListItem> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: throw MediaException("AniList sent an answer that could not be read. Try again.")
        aniListError(root)
        val list = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("mediaList") ?: return emptyList()
        val out = ArrayList<ListItem>()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val media = o.optJSONObject("media") ?: continue
            val mediaId = media.optInt("id", 0)
            if (mediaId <= 0) continue
            val kind = if (media.optString("type") == "MANGA") MediaKind.MANGA else MediaKind.ANIME
            val title = media.optJSONObject("title")?.optString("userPreferred").orEmpty().ifBlank { "Untitled" }
            val minutes = media.optInt("duration", 0).takeIf { it > 0 && kind == MediaKind.ANIME }
            val type = if (kind == MediaKind.MANGA) "manga" else "anime"
            val ids = HashSet<String>()
            ids += "anilist:$type:$mediaId"
            media.optInt("idMal", 0).takeIf { it > 0 }?.let { ids += "mal:$type:$it" }
            out += ListItem(
                key = mediaId.toString(),
                title = title,
                kind = kind,
                progress = o.optInt("progress", 0),
                updatedMs = o.optLong("updatedAt", 0L) * 1000L,
                minutesPerUnit = minutes,
                ids = ids,
            )
        }
        return out
    }

    // ---- MyAnimeList, through its own public feeds ----

    /** One line of a MyAnimeList feed: the title, its page, "Reading - 17 of 72 chapters" and when it was noted. */
    class FeedItem(val title: String, val link: String, val description: String, val pubMs: Long?)

    class Feed(val channelTitle: String, val items: List<FeedItem>)

    private val TYPE_SUFFIX = Regex(
        " - (TV Special|TV|ONA|OVA|Movie|Special|Music|CM|PV|Manga|Manhwa|Manhua|Light Novel|Novel|One-shot|Doujinshi|OEL|Unknown)$",
    )
    private val PROGRESS = Regex("^(.+?) - (\\d+) of (\\d+|\\?) (?:episodes|chapters)")
    private val LINK_ID = Regex("/(anime|manga)/(\\d+)")

    private fun xmlText(raw: String): String = raw
        .replace("<![CDATA[", "").replace("]]>", "")
        .replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        .replace("&amp;", "&")
        .trim()

    private fun tag(block: String, name: String): String =
        Regex("(?s)<$name>(.*?)</$name>").find(block)?.groupValues?.get(1)?.let { xmlText(it) }.orEmpty()

    fun malFeed(body: String): Feed {
        val channel = tag(body.substringBefore("<item>"), "title")
        val items = Regex("(?s)<item>(.*?)</item>").findAll(body).map { m ->
            val block = m.groupValues[1]
            val pub = runCatching {
                java.time.ZonedDateTime.parse(tag(block, "pubDate"), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }.getOrNull()
            FeedItem(tag(block, "title"), tag(block, "link"), tag(block, "description"), pub)
        }.toList()
        return Feed(channel, items)
    }

    /** A feed with a title belongs to a real user whose list can be read; an empty one means no such user, or a private list. */
    fun malFeedHasUser(body: String): Boolean = malFeed(body).channelTitle.isNotBlank()

    private fun cleanTitle(title: String): String = title.replace(TYPE_SUFFIX, "").ifBlank { "Untitled" }

    private class Parsed(val kind: String, val id: Long, val status: String, val progress: Int)

    private fun parse(item: FeedItem): Parsed? {
        val link = LINK_ID.find(item.link) ?: return null
        val progress = PROGRESS.find(item.description) ?: return null
        return Parsed(link.groupValues[1], link.groupValues[2].toLong(), progress.groupValues[1], progress.groupValues[2].toInt())
    }

    /** Episodes watched, from the "recently watched" feed: one event for each time the count was moved up. */
    fun malAnimeEvents(items: List<FeedItem>, sinceMs: Long): List<MediaEvent> = items.mapNotNull { item ->
        val at = item.pubMs ?: return@mapNotNull null
        val parsed = parse(item) ?: return@mapNotNull null
        if (parsed.kind != "anime" || parsed.progress <= 0 || at < sinceMs) return@mapNotNull null
        MediaEvent(
            id = "mal|anime|${parsed.id}|${parsed.progress}|$at",
            service = MediaService.MAL,
            kind = MediaKind.ANIME,
            title = cleanTitle(item.title),
            atMs = at,
            units = 1,
            label = MediaDiff.label(MediaKind.ANIME, parsed.progress, parsed.progress),
            ids = setOf("mal:anime:${parsed.id}"),
            numFrom = parsed.progress.toDouble(),
            numTo = parsed.progress.toDouble(),
        )
    }

    /** The manga list, newest change first. The feed shows where each title is now, so changes are found by comparing with last time. */
    fun malMangaItems(items: List<FeedItem>): List<ListItem> = items.mapNotNull { item ->
        val at = item.pubMs ?: return@mapNotNull null
        val parsed = parse(item) ?: return@mapNotNull null
        if (parsed.kind != "manga") return@mapNotNull null
        ListItem("manga:${parsed.id}", cleanTitle(item.title), MediaKind.MANGA, parsed.progress, at, ids = setOf("mal:manga:${parsed.id}"))
    }

    // ---- Simkl ----

    /** The "all" stamp of the activities answer: it changes whenever anything on the account changes. */
    fun simklActivitiesStamp(body: String): String? =
        runCatching { JSONObject(body).optString("all") }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The device sign-in answer: the code to type, where to type it, and what to poll with. */
    class DeviceCode(val userCode: String, val verificationUrl: String, val deviceCode: String, val intervalSeconds: Int)

    fun simklDeviceCode(body: String): DeviceCode {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: throw MediaException("Simkl sent an answer that could not be read. Try again.")
        val user = o.optString("user_code")
        val device = o.optString("device_code")
        if (user.isBlank() || device.isBlank()) {
            throw MediaException("Simkl did not give a sign-in code. Check the client ID, then try again.")
        }
        val url = o.optString("verification_uri").ifBlank { o.optString("verification_url") }.ifBlank { "https://simkl.com/pin" }
        return DeviceCode(user, url, device, o.optInt("interval", 5).coerceIn(2, 30))
    }

    /** What polling for the token gave: the token, "pending", "slow", or an error message. */
    sealed interface TokenPoll {
        class Token(val accessToken: String) : TokenPoll
        object Pending : TokenPoll
        object SlowDown : TokenPoll
        class Failed(val message: String) : TokenPoll
    }

    fun simklToken(body: String): TokenPoll {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return TokenPoll.Failed("Simkl sent an answer that could not be read.")
        val token = o.optString("access_token")
        if (token.isNotBlank()) return TokenPoll.Token(token)
        return when (o.optString("error")) {
            "authorization_pending" -> TokenPoll.Pending
            "slow_down" -> TokenPoll.SlowDown
            "expired_token" -> TokenPoll.Failed("The code ran out. Start again.")
            "access_denied" -> TokenPoll.Failed("Simkl was told no. Start again and allow it.")
            "" -> TokenPoll.Pending
            else -> TokenPoll.Failed("Simkl refused the sign-in (${o.optString("error")}).")
        }
    }

    /** Watched items from the all-items answer, as events: one per episode with its time, one per finished movie. */
    fun simklEvents(body: String, sinceMs: Long): List<MediaEvent> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<MediaEvent>()
        simklBucket(root.optJSONArray("shows"), MediaKind.SHOW, "show", sinceMs, out)
        simklBucket(root.optJSONArray("anime"), MediaKind.ANIME, "show", sinceMs, out)
        simklBucket(root.optJSONArray("movies"), MediaKind.MOVIE, "movie", sinceMs, out)
        return out
    }

    /** The title's numbers in Simkl and, for anime, in MyAnimeList and AniList, so the same title elsewhere is recognised. */
    private fun simklIds(ids: JSONObject?, simklId: Long, kind: MediaKind): Set<String> {
        val out = HashSet<String>()
        out += "simkl:$simklId"
        if (ids != null && kind == MediaKind.ANIME) {
            ids.optString("mal").toLongOrNull()?.takeIf { it > 0 }?.let { out += "mal:anime:$it" }
            ids.optString("anilist").toLongOrNull()?.takeIf { it > 0 }?.let { out += "anilist:anime:$it" }
        }
        return out
    }

    private fun simklBucket(items: JSONArray?, kind: MediaKind, nested: String, sinceMs: Long, out: MutableList<MediaEvent>) {
        if (items == null) return
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val info = item.optJSONObject(nested) ?: continue
            val simklId = info.optJSONObject("ids")?.optLong("simkl", 0L) ?: 0L
            if (simklId <= 0L) continue
            val title = info.optString("title").ifBlank { "Untitled" }
            val runtime = info.optInt("runtime", 0).takeIf { it > 0 }
            val ids = simklIds(info.optJSONObject("ids"), simklId, kind)
            if (kind == MediaKind.MOVIE) {
                val at = parseInstant(item.optString("last_watched_at")) ?: continue
                if (item.optString("status") == "completed" && at > PLACEHOLDER_BEFORE_MS && at >= sinceMs) {
                    out += MediaEvent("simkl|movie|$simklId|$at", MediaService.SIMKL, kind, title, at, 1, "", runtime, ids)
                }
                continue
            }
            var any = false
            val seasons = item.optJSONArray("seasons")
            if (seasons != null) {
                for (s in 0 until seasons.length()) {
                    val season = seasons.optJSONObject(s) ?: continue
                    val seasonNo = season.optInt("number", 1)
                    val episodes = season.optJSONArray("episodes") ?: continue
                    for (e in 0 until episodes.length()) {
                        val ep = episodes.optJSONObject(e) ?: continue
                        val at = parseInstant(ep.optString("watched_at")) ?: continue
                        val number = ep.optInt("number", 0)
                        if (at <= PLACEHOLDER_BEFORE_MS) continue
                        any = true
                        if (at < sinceMs) continue
                        val label = if (kind == MediaKind.ANIME) "ep $number" else "S${seasonNo}E$number"
                        out += MediaEvent(
                            "simkl|$simklId|$seasonNo|$number|$at", MediaService.SIMKL, kind, title, at, 1, label,
                            ep.optInt("runtime", 0).takeIf { it > 0 } ?: runtime, ids,
                            numFrom = if (kind == MediaKind.ANIME) number.toDouble() else null,
                            numTo = if (kind == MediaKind.ANIME) number.toDouble() else null,
                        )
                    }
                }
            }
            if (!any) {
                // No episode times (the list only says what was last watched): note that one, at the time it was marked.
                val at = parseInstant(item.optString("last_watched_at")) ?: continue
                if (at > PLACEHOLDER_BEFORE_MS && at >= sinceMs) {
                    out += MediaEvent("simkl|$simklId|last|$at", MediaService.SIMKL, kind, title, at, 1, item.optString("last_watched"), runtime, ids)
                }
            }
        }
    }
}

/** Something went wrong talking to a service; [message] says what to do. */
class MediaException(message: String, val offline: Boolean = false) : Exception(message)
