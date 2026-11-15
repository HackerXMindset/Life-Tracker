package com.lifetracker.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Asks AniList, MyAnimeList and Simkl what you read and watched (and reads the backups Mihon and Kotatsu keep of their own
 * history), and puts it on the Timeline as entries under Reading and Movies and shows. Real measured time from a backup is used
 * as it is; time in an app you tied to a service is used when there is some; otherwise the entry goes at the time the service
 * noted, as long as the chapters or episodes take. The same thing noted in two places is added once, from the place you rank
 * first. Time you wrote down yourself is never covered, and an entry you delete does not come back.
 */
object MediaSync {
    const val SOURCE = "media"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val OVERLAP_MS = 10L * 60 * 1000
    private const val MAX_BACKUP_BYTES = 150L * 1024 * 1024
    private val mutex = Mutex()

    class Outcome(
        val added: Int,
        val skipped: Int,
        val problems: List<String>,
        val bulk: Int = 0,
        val maybe: Int = 0,
        val duplicates: Int = 0,
        val replaced: Int = 0,
    ) {
        val line: String
            get() {
                val parts = ArrayList<String>()
                parts += if (added == 0) "Nothing new" else "Added $added"
                if (replaced > 0) parts += "$replaced replaced by a better record"
                if (duplicates > 0) parts += "$duplicates left out (already noted elsewhere)"
                if (maybe > 0) parts += "$maybe held for you to check"
                if (skipped > 0) parts += "$skipped left out (that time was already filled)"
                if (bulk > 0) parts += "$bulk left out (many marked at once, so no real time)"
                parts += problems
                return parts.joinToString(" · ")
            }
    }

    /** True when there is an account or a backup folder to read. */
    fun hasSources(context: Context): Boolean {
        val sources = DataSources(context)
        return MediaAccounts(context).anyConnected() || sources.folder(DataSources.Slot.Mihon) != null || sources.folder(DataSources.Slot.Kotatsu) != null
    }

    /** Null when the feature is off or there is nothing to read. */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Outcome? = mutex.withLock {
        withContext(Dispatchers.IO) { runLocked(context.applicationContext, now) }
    }

    private fun newestBackup(context: Context, folder: Uri, accept: (String) -> Boolean): DocumentFile? {
        val tree = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.exists() }
            ?: throw MediaException("That folder cannot be reached any more. Choose it again.")
        return tree.listFiles().filter { f -> f.isFile && f.name?.let(accept) == true }.maxByOrNull { it.lastModified() }
    }

    private fun readBackup(context: Context, file: DocumentFile): ByteArray {
        if (file.length() > MAX_BACKUP_BYTES) throw MediaException("${file.name} is too big to read here.")
        return context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: throw MediaException("Could not open ${file.name}.")
    }

    private suspend fun runLocked(context: Context, now: Long): Outcome? {
        if (!Features.isOn(context, Feature.MEDIA)) return null
        if (!hasSources(context)) return null
        val accounts = MediaAccounts(context)
        val sources = DataSources(context)
        val state = MediaSyncState(context)
        val settings = MediaSettings(context)
        val lookbackStart = now - settings.lookbackDays * DAY_MS
        val since = max(state.lastSyncMs - OVERLAP_MS, lookbackStart)

        val events = ArrayList<MediaEvent>()
        val problems = ArrayList<String>()
        val afterWrite = ArrayList<() -> Unit>()
        var tried = 0

        if (accounts.connected(MediaService.ANILIST)) {
            tried++
            try {
                val items = MediaClients.aniListItems(accounts.aniListId)
                val result = MediaDiff.events(MediaService.ANILIST, items, state.snapshot(MediaService.ANILIST), since)
                events += result.events
                afterWrite += { state.saveSnapshot(MediaService.ANILIST, result.snapshot) }
            } catch (e: MediaException) {
                problems += "AniList: ${e.message}"
            }
        }
        if (accounts.connected(MediaService.MAL)) {
            tried++
            try {
                // Manga: the feed shows where each title is now, so changes come from comparing with last time. Anime: the feed lists each update.
                val manga = MediaDiff.events(MediaService.MAL, MediaClients.malMangaItems(accounts.malUser), state.snapshot(MediaService.MAL), since)
                events += manga.events
                afterWrite += { state.saveSnapshot(MediaService.MAL, manga.snapshot) }
                events += MediaClients.malAnimeEvents(accounts.malUser, lookbackStart)
            } catch (e: MediaException) {
                problems += "MyAnimeList: ${e.message}"
            }
        }
        if (accounts.connected(MediaService.SIMKL)) {
            tried++
            try {
                val stamp = MediaClients.simklStamp(accounts.simklClientId, accounts.simklToken)
                if (stamp == null || stamp != state.simklStamp) {
                    val dateFrom = state.simklStamp.ifBlank { Instant.ofEpochMilli(lookbackStart).toString() }
                    events += MediaClients.simklEvents(accounts.simklClientId, accounts.simklToken, dateFrom, lookbackStart)
                    if (stamp != null) afterWrite += { state.simklStamp = stamp }
                }
            } catch (e: MediaException) {
                problems += "Simkl: ${e.message}"
            }
        }

        if (accounts.connected(MediaService.COMICK)) {
            tried++
            try {
                val items = MediaClients.comickLibrary(ComickAuth.accessToken(context, now))
                val result = MediaDiff.events(MediaService.COMICK, items, state.snapshot(MediaService.COMICK), since)
                events += result.events
                afterWrite += { state.saveSnapshot(MediaService.COMICK, result.snapshot) }
            } catch (e: MediaException) {
                problems += "Comick: ${e.message}"
            }
        }

        // The backups are read when a newer file is there. Their own times only ever grow, so what is new is found by comparing.
        val mihonFolder = sources.folder(DataSources.Slot.Mihon)
        if (mihonFolder != null) {
            tried++
            try {
                val file = newestBackup(context, mihonFolder) { it.endsWith(".tachibk") || it.endsWith(".proto.gz") }
                if (file == null) {
                    problems += "Mihon backup: no backup file in that folder yet"
                } else {
                    val key = "${file.name}|${file.lastModified()}|${file.length()}"
                    if (key != state.backupSeen(MediaService.MIHON)) {
                        val result = MediaBackups.mihonEvents(MediaBackups.mihonRows(readBackup(context, file)), state.snapshot(MediaService.MIHON), lookbackStart)
                        events += result.events
                        afterWrite += { state.saveSnapshot(MediaService.MIHON, result.snapshot); state.setBackupSeen(MediaService.MIHON, key) }
                    }
                }
            } catch (e: MediaException) {
                problems += "Mihon backup: ${e.message}"
            } catch (e: Exception) {
                problems += "Mihon backup: could not read it (${e.javaClass.simpleName})"
            } catch (e: OutOfMemoryError) {
                problems += "Mihon backup: too big to read on this phone"
            }
        }
        val kotatsuFolder = sources.folder(DataSources.Slot.Kotatsu)
        if (kotatsuFolder != null) {
            tried++
            try {
                val file = newestBackup(context, kotatsuFolder) { it.endsWith(".zip") || it.contains(".bk") }
                if (file == null) {
                    problems += "Kotatsu backup: no backup file in that folder yet"
                } else {
                    val key = "${file.name}|${file.lastModified()}|${file.length()}"
                    if (key != state.backupSeen(MediaService.KOTATSU)) {
                        events += MediaBackups.kotatsuEvents(MediaBackups.kotatsuData(readBackup(context, file)), lookbackStart)
                        afterWrite += { state.setBackupSeen(MediaService.KOTATSU, key) }
                    }
                }
            } catch (e: MediaException) {
                problems += "Kotatsu backup: ${e.message}"
            } catch (e: Exception) {
                problems += "Kotatsu backup: could not read it (${e.javaClass.simpleName})"
            } catch (e: OutOfMemoryError) {
                problems += "Kotatsu backup: too big to read on this phone"
            }
        }

        val outcome = write(context, events, settings, now, problems)
        if (problems.size < tried) {
            afterWrite.forEach { it() }
            state.lastSyncMs = now
        }
        state.lastLine = outcome.line
        AppLog.i("Shows and manga", outcome.line)
        return outcome
    }

    private suspend fun write(context: Context, events: List<MediaEvent>, settings: MediaSettings, now: Long, problems: List<String>): Outcome {
        val db = AppDatabase.get(context)
        val log = MediaLog(context)
        val known = db.importDao().sourceIds(SOURCE).toSet() + log.pending().map { it.event.id }
        val fresh = events.filter { it.id !in known }.distinctBy { it.id }
        if (fresh.isEmpty()) return Outcome(0, 0, problems)

        // Things already added that a new one may be the same as. One you changed or deleted is left as you made it.
        val candidates = log.recent().filter { l -> fresh.any { MediaMatch.same(it, l.event) } }
        val logged = ArrayList<MediaMatch.Logged>()
        for (l in candidates) {
            val row = db.entryDao().byId(l.entryId)
            val untouched = row != null && row.date == l.date && row.startMinute == l.startMinute && row.endMinute == l.endMinute && row.title == l.title
            logged += MediaMatch.Logged(l.event, untouched)
        }
        val plan = MediaMatch.plan(fresh, logged, settings.priority)
        val replaced = candidates.filter { l -> plan.replace.any { it === l.event } }

        val result = db.withTransaction {
            replaced.forEach { db.entryDao().deleteById(it.entryId) }
            if (replaced.isNotEmpty()) log.removeRecent(replaced.map { it.entryId }.toSet())
            db.importDao().insertAll(plan.drop.map { ImportedItemEntity(SOURCE, it.first.id, 0L) })
            val placed = placeAndInsert(db, settings, log, plan.place, emptySet(), now)
            if (placed.maybe.isNotEmpty()) log.setPending(log.pending() + placed.maybe)
            placed
        }
        return Outcome(result.placed.size, result.skipped.size, problems, result.bulk.size, result.maybe.size, plan.drop.size, replaced.size)
    }

    /** Places the events around what is already on the Timeline, adds them as entries, and remembers them. */
    private suspend fun placeAndInsert(
        db: AppDatabase,
        settings: MediaSettings,
        log: MediaLog,
        events: List<MediaEvent>,
        force: Set<String>,
        now: Long,
    ): MediaPlacer.Result {
        if (events.isEmpty()) return MediaPlacer.Result(emptyList(), emptyList())
        val zone = ZoneId.systemDefault()
        val earliest = events.minOf { it.atMs }
        val from = LocalDate.ofInstant(Instant.ofEpochMilli(earliest - DAY_MS), zone).toString()
        val to = LocalDate.ofInstant(Instant.ofEpochMilli(now + DAY_MS), zone).toString()
        val autoIds = db.importDao().entryIds(SOURCE).toSet()
        val spans = db.entryDao().between(from, to).mapNotNull { entry ->
            val end = entry.endMinute ?: return@mapNotNull null
            if (end <= entry.startMinute) return@mapNotNull null
            val day = LocalDate.parse(entry.date).atStartOfDay(zone)
            val span = MediaSpan(day.plusMinutes(entry.startMinute.toLong()).toInstant().toEpochMilli(), day.plusMinutes(end.toLong()).toInstant().toEpochMilli())
            (entry.id in autoIds) to span
        }
        val userBusy = spans.filter { !it.first }.map { it.second }
        val autoBusy = spans.filter { it.first }.map { it.second }

        val map = settings.appMap()
        val sessions = if (map.isEmpty()) emptyList() else
            db.usageDao().sessionsOnce(earliest - 6 * 60 * 60 * 1000L, now).filter { it.pkg in map }
        val result = MediaPlacer.place(
            events = events,
            sessionsFor = { service -> sessions.filter { map[it.pkg] == service }.map { MediaSession(it.pkg, it.startMs, it.endMs) } },
            userBusy = userBusy,
            autoBusy = autoBusy,
            minutesPerChapter = settings.chapterMinutesFor(log.speedSamples()),
            nowMs = now,
            zone = zone,
            minutesPerEpisode = settings.episodeMinutes,
            force = force,
        )

        val marks = ArrayList<ImportedItemEntity>()
        val items = ArrayList<RecentItem>()
        for (p in result.placed) {
            val endDay = Instant.ofEpochMilli(p.endMs - 1).atZone(zone).toLocalDate()
            val dayStart = endDay.atStartOfDay(zone).toInstant().toEpochMilli()
            val start = max(p.startMs, dayStart)
            val startMinute = ((start - dayStart) / 60_000L).toInt()
            val endMinute = (((p.endMs - dayStart) / 60_000L).toInt()).coerceAtMost(1439)
            if (endMinute <= startMinute) continue
            val how = when (p.how) {
                "real" -> "measured reading time"
                "app" -> "time in the app"
                else -> "time estimated"
            }
            val title = p.event.entryTitle
            val id = db.entryDao().insert(
                EntryEntity(
                    date = endDay.toString(),
                    startMinute = startMinute,
                    endMinute = endMinute,
                    category = p.event.kind.category,
                    title = title,
                    note = "From ${p.event.service.label} · $how",
                ),
            )
            marks += ImportedItemEntity(SOURCE, p.event.id, id)
            items += RecentItem(id, endDay.toString(), startMinute, endMinute, title, p.how, now, p.event)
        }
        db.importDao().insertAll(marks)
        log.addRecent(items, now)
        log.addSpeedSamples(result.placed.mapNotNull { MediaSpeed.sample(it) })
        return result
    }

    /** Puts a held-back one on the Timeline after all. */
    suspend fun accept(context: Context, eventId: String, now: Long = System.currentTimeMillis()): Outcome? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val log = MediaLog(app)
            val pending = log.pending()
            val item = pending.firstOrNull { it.event.id == eventId } ?: return@withContext null
            val db = AppDatabase.get(app)
            val result = db.withTransaction {
                val placed = placeAndInsert(db, MediaSettings(app), log, listOf(item.event), setOf(eventId), now)
                if (placed.placed.isEmpty()) db.importDao().insertAll(listOf(ImportedItemEntity(SOURCE, eventId, 0L)))
                placed
            }
            log.setPending(pending.filter { it.event.id != eventId })
            Outcome(result.placed.size, result.skipped.size, emptyList())
        }
    }

    /** Throws a held-back one away for good. */
    suspend fun dismiss(context: Context, eventId: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val log = MediaLog(app)
            AppDatabase.get(app).importDao().insertAll(listOf(ImportedItemEntity(SOURCE, eventId, 0L)))
            log.setPending(log.pending().filter { it.event.id != eventId })
        }
    }

    /** Takes entries this feature added off the Timeline. They stay marked, so they do not come back. */
    suspend fun remove(context: Context, entryIds: Set<Long>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val db = AppDatabase.get(app)
            db.withTransaction { entryIds.forEach { db.entryDao().deleteById(it) } }
            MediaLog(app).removeRecent(entryIds)
        }
    }
}

/** Checks the three services every 15 minutes (Android's shortest) when an account is connected, so new chapters appear soon after. */
class MediaSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppLog.track("Shows and manga", "Check", { it?.line ?: "off or no account" }) { MediaSync.run(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "media-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MediaSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
