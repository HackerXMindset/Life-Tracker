package com.lifetracker.app.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where a watched show or a read chapter is noted: the three accounts, and the backups Mihon and Kotatsu keep of their own
 * history (which also say how long each chapter was really read).
 */
enum class MediaService(val key: String, val label: String, val backup: Boolean = false) {
    ANILIST("anilist", "AniList"),
    MAL("mal", "MyAnimeList"),
    SIMKL("simkl", "Simkl"),
    COMICK("comick", "Comick"),
    MIHON("mihon", "Mihon backup", true),
    KOTATSU("kotatsu", "Kotatsu backup", true);

    companion object {
        fun of(key: String?): MediaService? = entries.firstOrNull { it.key == key }

        /** The ones with an account to connect. */
        val accounts: List<MediaService> get() = entries.filter { !it.backup }

        /** Who wins when the same chapter or episode is noted in more than one place: real reading time first, then Simkl. */
        val DEFAULT_PRIORITY: List<MediaService> get() = listOf(MIHON, KOTATSU, SIMKL, ANILIST, MAL, COMICK)
    }
}

/** What was read or watched. [category] is the built-in activity the Timeline entry goes under. */
enum class MediaKind(val category: String) {
    MANGA("Reading"),
    ANIME("Shows"),
    SHOW("Shows"),
    MOVIE("Shows"),
}

/**
 * One thing that was read or watched, as a service told us. [atMs] is when it was noted (the end of the chapter or episode).
 * [units] is how many chapters or episodes. [minutesPerUnit] is the length of one when the service knows it.
 * [ids] name the title in the services' own numbering ("mal:manga:12", "anilist:anime:5") so the same title in two places is
 * recognised; [numFrom] to [numTo] are the chapter or episode numbers, when known; [exactMs] is a real measured time (from a
 * Mihon or Kotatsu backup), which is used as it is.
 */
class MediaEvent(
    val id: String,
    val service: MediaService,
    val kind: MediaKind,
    val title: String,
    val atMs: Long,
    val units: Int = 1,
    val label: String = "",
    val minutesPerUnit: Int? = null,
    val ids: Set<String> = emptySet(),
    val numFrom: Double? = null,
    val numTo: Double? = null,
    val exactMs: Long? = null,
) {
    /** The title on the Timeline: "Solo Leveling · ch 47". */
    val entryTitle: String get() = if (label.isBlank()) title else "$title · $label"
}

/** One title on a list: how far you are, and when that last changed. */
class ListItem(
    val key: String,
    val title: String,
    val kind: MediaKind,
    val progress: Int,
    val updatedMs: Long,
    val minutesPerUnit: Int? = null,
    val ids: Set<String> = emptySet(),
)

/** Typed names, with the web address or the @ allowed, cleaned to just the name. */
object MediaNames {
    private val NAME = Regex("^[A-Za-z0-9_\\-.]{1,40}$")

    private fun clean(input: String, marker: String): String? {
        var text = input.trim().removePrefix("@")
        val at = text.lowercase().indexOf(marker)
        if (at >= 0) text = text.substring(at + marker.length)
        text = text.substringBefore('/').substringBefore('?').substringBefore('#').trim()
        return text.takeIf { NAME.matches(it) }
    }

    /** "name", "@name" or "https://anilist.co/user/name/" give "name". Null when it cannot be a name. */
    fun aniList(input: String): String? = clean(input, "anilist.co/user/")

    /** "name" or "https://myanimelist.net/profile/name" give "name". Null when it cannot be a name. */
    fun myAnimeList(input: String): String? = clean(input, "myanimelist.net/profile/")

    /** A Simkl client id is a long run of letters and numbers; spaces around it are dropped. */
    fun simklClientId(input: String): String? = input.trim().takeIf { Regex("^[A-Za-z0-9]{16,80}$").matches(it) }
}

/** Which service an app most likely goes with, only to suggest it. Nothing is set without you choosing. */
object MediaSuggest {
    fun forPackage(pkg: String): MediaService? = when {
        pkg == "app.mihon" || pkg.startsWith("app.mihon.") || pkg.startsWith("eu.kanade.tachiyomi") || pkg.startsWith("app.komikku") -> MediaService.ANILIST
        pkg.startsWith("org.koitharu.kotatsu") -> MediaService.MAL
        pkg.startsWith("com.lagradost.cloudstream3") -> MediaService.SIMKL
        else -> null
    }
}

/**
 * Turns a list (where each title is up to now) into events, by comparing it with what was seen last time. A title that moved
 * forward gives one event for the chapters or episodes it moved. The first time a title is seen, only a recent change counts
 * (as one chapter), because before that there is no way to know how many were read.
 */
object MediaDiff {
    const val MAX_UNITS = 30

    class Result(val events: List<MediaEvent>, val snapshot: Map<String, Int>)

    fun label(kind: MediaKind, from: Int, to: Int): String {
        val word = if (kind == MediaKind.MANGA) "ch" else "ep"
        return if (from >= to) "$word $to" else "$word $from–$to"
    }

    fun events(service: MediaService, items: List<ListItem>, old: Map<String, Int>, sinceMs: Long): Result {
        val events = ArrayList<MediaEvent>()
        val snapshot = old.toMutableMap()
        for (item in items) {
            val before = old[item.key]
            val now = item.progress
            if (before == null) {
                if (now > 0 && item.updatedMs >= sinceMs) {
                    events += event(service, item, 1, label(item.kind, now, now), now, now)
                }
            } else if (now > before) {
                val units = min(now - before, MAX_UNITS)
                events += event(service, item, units, label(item.kind, now - units + 1, now), now - units + 1, now)
            }
            snapshot[item.key] = now
        }
        return Result(events, snapshot)
    }

    private fun event(service: MediaService, item: ListItem, units: Int, label: String, from: Int, to: Int) = MediaEvent(
        id = "${service.key}|${item.key}|${item.progress}|${item.updatedMs}",
        service = service,
        kind = item.kind,
        title = item.title,
        atMs = item.updatedMs,
        units = units,
        label = label,
        minutesPerUnit = item.minutesPerUnit,
        ids = item.ids,
        numFrom = from.toDouble(),
        numTo = to.toDouble(),
    )
}

/** One stretch of the phone being used in an app. */
class MediaSession(val pkg: String, val startMs: Long, val endMs: Long)

/** A stretch of time that is taken. */
class MediaSpan(val startMs: Long, val endMs: Long)

/**
 * Where one event goes on the Timeline. [fromSession] is true when it takes the time of an app you use for that service;
 * [exact] is true when the time is a real measured one from a backup.
 */
class MediaPlacement(val event: MediaEvent, val startMs: Long, val endMs: Long, val fromSession: Boolean, val exact: Boolean = false) {
    /** How much to trust the time: "real", "app" or "estimated". */
    val how: String get() = if (exact) "real" else if (fromSession) "app" else "estimated"
}

/** An event held back for you to decide on, with why. */
class MediaMaybe(val event: MediaEvent, val reason: String)

/**
 * Decides where on the day each event goes, with no Android in it so it can be tested.
 *
 *  - A real measured time (from a Mihon or Kotatsu backup) is used as it is, ending when it was last read.
 *  - If an app you tied to the event's service was open around that time, a chapter takes that time: it starts where the app
 *    was opened (or where the last event in the same stretch ended) and ends when the service noted it.
 *  - An episode or a movie is as long as it really is, but never longer than the time you actually spent in the app for it.
 *  - Otherwise it is added on its own, ending at the time the service noted and as long as the chapters or episodes take.
 *  - Time you wrote down yourself is never covered. Several events that land on the same moment are placed one before the other.
 *  - Doubtful ones (many at once with no app time, or many in almost no app time) are held back for you to decide.
 */
object MediaPlacer {
    /** A tracker notes a chapter when it is finished, and Simkl may be told a little later. */
    const val SLACK_MS = 15 * 60_000L

    /** A stretch of app use this long past its end is taken to be finished, so it can be filled to its end. */
    const val SETTLE_MS = 20 * 60_000L

    const val MAX_BLOCK_MS = 4 * 60 * 60_000L
    const val MIN_BLOCK_MS = 60_000L
    private const val MINUTE_MS = 60_000L

    /** An app stretch shorter than this is too little to say how long an episode took. */
    const val MIN_WATCH_SESSION_MS = 3 * 60_000L

    const val DEFAULT_ANIME_MIN = 24
    const val DEFAULT_SHOW_MIN = 42
    const val DEFAULT_MOVIE_MIN = 100

    /** Three or more with no app time, each within this of the one before, are one bulk update (a list import, a "mark all as read"). */
    const val BURST_GAP_MS = 3 * 60_000L
    const val BURST_SIZE = 3

    /** One event with this many chapters, or this many episodes, and no app time or measured time, is held for you. */
    const val BIG_CHAPTERS = 10
    const val BIG_EPISODES = 4

    /** [bulk] are the events left out because they look like many things marked at once; [maybe] are held for you to accept. */
    class Result(
        val placed: List<MediaPlacement>,
        val skipped: List<MediaEvent>,
        val bulk: List<MediaEvent> = emptyList(),
        val maybe: List<MediaMaybe> = emptyList(),
    )

    fun lengthMs(event: MediaEvent, minutesPerChapter: Int, minutesPerEpisode: Int = DEFAULT_ANIME_MIN): Long {
        event.exactMs?.takeIf { it > 0 }?.let { return min(it, MAX_BLOCK_MS) }
        val each = event.minutesPerUnit?.takeIf { it > 0 } ?: when (event.kind) {
            MediaKind.MANGA -> minutesPerChapter
            MediaKind.ANIME -> minutesPerEpisode
            MediaKind.SHOW -> DEFAULT_SHOW_MIN
            MediaKind.MOVIE -> DEFAULT_MOVIE_MIN
        }
        return min(each.toLong() * max(1, event.units) * MINUTE_MS, MAX_BLOCK_MS)
    }

    /** The session of one of the service's apps that was open at [atMs] (or within the slack of it), the closest one. */
    fun sessionFor(atMs: Long, sessions: List<MediaSession>): MediaSession? =
        sessions.filter { atMs >= it.startMs - SLACK_MS && atMs <= it.endMs + SLACK_MS }
            .minByOrNull { if (atMs in it.startMs..it.endMs) 0L else min(abs(atMs - it.startMs), abs(atMs - it.endMs)) }

    fun place(
        events: List<MediaEvent>,
        sessionsFor: (MediaService) -> List<MediaSession>,
        userBusy: List<MediaSpan>,
        autoBusy: List<MediaSpan>,
        minutesPerChapter: Int,
        nowMs: Long,
        zone: ZoneId,
        minutesPerEpisode: Int = DEFAULT_ANIME_MIN,
        force: Set<String> = emptySet(),
    ): Result {
        val ordered = events.sortedWith(compareBy({ it.atMs }, { it.id }))
        val auto = autoBusy.toMutableList()
        val placed = ArrayList<MediaPlacement>()
        val skipped = ArrayList<MediaEvent>()
        val bulk = ArrayList<MediaEvent>()
        val maybe = ArrayList<MediaMaybe>()
        val sessionById = ordered.associate { it.id to if (it.exactMs != null) null else sessionFor(it.atMs, sessionsFor(it.service)) }
        val bulkIds = bulkIds(ordered) { sessionById[it.id] != null || it.exactMs != null }
        for (event in ordered) {
            val forced = event.id in force
            if (event.id in bulkIds && !forced) {
                bulk += event
                continue
            }
            val sessions = sessionsFor(event.service)
            val session = sessionById[event.id]
            val big = if (event.kind == MediaKind.MANGA) BIG_CHAPTERS else BIG_EPISODES
            if (!forced && session == null && event.exactMs == null && event.units >= big) {
                maybe += MediaMaybe(event, "${event.units} ${if (event.kind == MediaKind.MANGA) "chapters" else "episodes"} noted at once, with no time in the app")
                continue
            }
            var exact = false
            var used = session
            val span = when {
                event.exactMs != null -> {
                    exact = true
                    alone(event, userBusy, auto, lengthMs(event, minutesPerChapter, minutesPerEpisode), zone, event.atMs, null)
                }
                session != null && event.kind == MediaKind.MANGA -> inSession(event, session, ordered, sessions, userBusy, auto, nowMs)
                session != null && session.endMs - session.startMs >= MIN_WATCH_SESSION_MS -> {
                    // As long as it really is, but not longer than the time actually spent in the app for it.
                    val length = lengthMs(event, minutesPerChapter, minutesPerEpisode)
                    alone(event, userBusy, auto, length, zone, event.atMs.coerceIn(session.startMs, session.endMs), session.startMs)
                        ?: run { used = null; alone(event, userBusy, auto, length, zone, event.atMs, null) }
                }
                else -> {
                    used = null
                    alone(event, userBusy, auto, lengthMs(event, minutesPerChapter, minutesPerEpisode), zone, event.atMs, null)
                }
            }
            if (span == null || span.endMs - span.startMs < MIN_BLOCK_MS) {
                skipped += event
                continue
            }
            if (!forced && used != null && event.units >= 3 && span.endMs - span.startMs < event.units * MINUTE_MS) {
                maybe += MediaMaybe(event, "${event.units} noted, but only ${(span.endMs - span.startMs) / MINUTE_MS} min in the app")
                continue
            }
            val rounded = roundToMinutes(span)
            auto += MediaSpan(rounded.first, rounded.second)
            placed += MediaPlacement(event, rounded.first, rounded.second, used != null, exact)
        }
        return Result(placed, skipped, bulk, maybe)
    }

    /** Ids of events that look like one bulk update: a run of three or more with no app time, each close after the one before. */
    fun bulkIds(ordered: List<MediaEvent>, hasSession: (MediaEvent) -> Boolean): Set<String> {
        val out = HashSet<String>()
        for ((_, group) in ordered.filter { !hasSession(it) }.groupBy { it.service }) {
            var run = ArrayList<MediaEvent>()
            for (event in group.sortedBy { it.atMs }) {
                if (run.isNotEmpty() && event.atMs - run.last().atMs > BURST_GAP_MS) {
                    if (run.size >= BURST_SIZE) run.forEach { out += it.id }
                    run = ArrayList()
                }
                run += event
            }
            if (run.size >= BURST_SIZE) run.forEach { out += it.id }
        }
        return out
    }

    private fun inSession(
        event: MediaEvent,
        session: MediaSession,
        all: List<MediaEvent>,
        sessions: List<MediaSession>,
        userBusy: List<MediaSpan>,
        auto: List<MediaSpan>,
        nowMs: Long,
    ): MediaSpan? {
        var end = event.atMs.coerceIn(session.startMs, session.endMs)
        val lastOfSession = all.none { other ->
            other !== event && other.service == event.service && other.atMs > event.atMs && sessionFor(other.atMs, sessions) === session
        }
        if (lastOfSession && nowMs >= session.endMs + SETTLE_MS) end = session.endMs
        val blockers = userBusy + auto
        if (blockers.any { it.startMs < end && it.endMs > end }) return null
        val start = blockers.filter { it.endMs <= end }.maxOfOrNull { it.endMs }?.let { max(session.startMs, it) } ?: session.startMs
        return if (end > start) MediaSpan(start, end) else null
    }

    /** A block of [length] ending at [anchorMs] (moved earlier past what is already there), never before [floorMs]. */
    private fun alone(
        event: MediaEvent,
        userBusy: List<MediaSpan>,
        auto: List<MediaSpan>,
        length: Long,
        zone: ZoneId,
        anchorMs: Long,
        floorMs: Long?,
    ): MediaSpan? {
        var end = anchorMs
        // Events that land on the same moment stack one before the other instead of on top of each other.
        var moved = true
        while (moved) {
            moved = false
            for (a in auto) {
                if (a.startMs < end && a.endMs >= end) { end = a.startMs; moved = true }
            }
        }
        if (userBusy.any { it.startMs < end && it.endMs > end }) return null
        var start = end - length
        val dayStart = Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        start = max(start, dayStart)
        if (floorMs != null) start = max(start, floorMs)
        val blockers = userBusy + auto
        blockers.filter { it.endMs <= end && it.endMs > start }.maxOfOrNull { it.endMs }?.let { start = it }
        return if (end > start) MediaSpan(start, end) else null
    }

    private fun roundToMinutes(span: MediaSpan): Pair<Long, Long> {
        val s = Math.round(span.startMs.toDouble() / MINUTE_MS) * MINUTE_MS
        var e = Math.round(span.endMs.toDouble() / MINUTE_MS) * MINUTE_MS
        if (e <= s) e = s + MINUTE_MS
        return s to e
    }
}

/** How long a chapter really takes you, learned from measured times. */
object MediaSpeed {
    const val MIN_SAMPLES = 8
    const val MAX_SAMPLES = 40

    /** Seconds for one chapter from a placement, only when the time is real or from the app and plausible. Null otherwise. */
    fun sample(p: MediaPlacement): Int? {
        if (p.event.kind != MediaKind.MANGA || !(p.exact || p.fromSession)) return null
        val seconds = (p.endMs - p.startMs) / 1000 / max(1, p.event.units)
        return seconds.toInt().takeIf { it in 20..1800 }
    }

    /** The middle of the samples in whole minutes (2 to 30), or null while there are too few to say. */
    fun learnedMinutes(samples: List<Int>): Int? {
        if (samples.size < MIN_SAMPLES) return null
        val sorted = samples.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble() else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        return Math.round(median / 60.0).toInt().coerceIn(2, 30)
    }
}
