package com.lifetracker.app.data

import java.time.Instant
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONObject

/** Reads what AniList, Jikan (MyAnimeList) and Simkl send back, with no Android in it so it can be tested. */
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
            out += ListItem(
                key = mediaId.toString(),
                title = title,
                kind = kind,
                progress = o.optInt("progress", 0),
                updatedMs = o.optLong("updatedAt", 0L) * 1000L,
                minutesPerUnit = minutes,
            )
        }
        return out
    }

    // ---- MyAnimeList, through Jikan ----

    /** Jikan's answer to a user lookup: true when the user exists. */
    fun jikanUserExists(body: String): Boolean =
        runCatching { JSONObject(body).optJSONObject("data")?.optString("username").orEmpty().isNotBlank() }.getOrDefault(false)

    /** What the user read or watched, as events at the times MyAnimeList noted. */
    fun jikanHistory(body: String, sinceMs: Long): List<MediaEvent> {
        val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return emptyList()
        val out = ArrayList<MediaEvent>()
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            val entry = o.optJSONObject("entry") ?: continue
            val malId = entry.optInt("mal_id", 0)
            val at = parseInstant(o.optString("date")) ?: continue
            if (malId <= 0 || at < sinceMs) continue
            val manga = entry.optString("type") == "manga"
            val kind = if (manga) MediaKind.MANGA else MediaKind.ANIME
            val number = o.optInt("increment", 0)
            out += MediaEvent(
                id = "mal|${entry.optString("type")}|$malId|$number|$at",
                service = MediaService.MAL,
                kind = kind,
                title = entry.optString("name").ifBlank { "Untitled" },
                atMs = at,
                units = 1,
                label = if (number > 0) MediaDiff.label(kind, number, number) else "",
            )
        }
        return out
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

    private fun simklBucket(items: JSONArray?, kind: MediaKind, nested: String, sinceMs: Long, out: MutableList<MediaEvent>) {
        if (items == null) return
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val info = item.optJSONObject(nested) ?: continue
            val simklId = info.optJSONObject("ids")?.optLong("simkl", 0L) ?: 0L
            if (simklId <= 0L) continue
            val title = info.optString("title").ifBlank { "Untitled" }
            val runtime = info.optInt("runtime", 0).takeIf { it > 0 }
            if (kind == MediaKind.MOVIE) {
                val at = parseInstant(item.optString("last_watched_at")) ?: continue
                if (item.optString("status") == "completed" && at > PLACEHOLDER_BEFORE_MS && at >= sinceMs) {
                    out += MediaEvent("simkl|movie|$simklId|$at", MediaService.SIMKL, kind, title, at, 1, "", runtime)
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
                            ep.optInt("runtime", 0).takeIf { it > 0 } ?: runtime,
                        )
                    }
                }
            }
            if (!any) {
                // No episode times (the list only says what was last watched): note that one, at the time it was marked.
                val at = parseInstant(item.optString("last_watched_at")) ?: continue
                if (at > PLACEHOLDER_BEFORE_MS && at >= sinceMs) {
                    out += MediaEvent("simkl|$simklId|last|$at", MediaService.SIMKL, kind, title, at, 1, item.optString("last_watched"), runtime)
                }
            }
        }
    }
}

/** Something went wrong talking to a service; [message] says what to do. */
class MediaException(message: String, val offline: Boolean = false) : Exception(message)
