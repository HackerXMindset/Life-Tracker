package com.lifetracker.app.data

import android.content.Context
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
 * Asks AniList, MyAnimeList and Simkl what you read and watched, and puts it on the Timeline as entries (under Reading and
 * Movies and shows). Time spent in an app you tied to a service is used when there is some; otherwise the entry goes at the
 * time the service noted, as long as the chapters or episodes take. Time you wrote down yourself is never covered, and an
 * entry you delete does not come back.
 */
object MediaSync {
    const val SOURCE = "media"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val OVERLAP_MS = 10L * 60 * 1000
    private val mutex = Mutex()

    class Outcome(val added: Int, val skipped: Int, val problems: List<String>) {
        val line: String
            get() {
                val parts = ArrayList<String>()
                parts += if (added == 0) "Nothing new" else "Added $added"
                if (skipped > 0) parts += "$skipped left out (that time was already filled)"
                parts += problems
                return parts.joinToString(" · ")
            }
    }

    /** Null when the feature is off or no account is connected. */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Outcome? = mutex.withLock {
        withContext(Dispatchers.IO) { runLocked(context.applicationContext, now) }
    }

    private suspend fun runLocked(context: Context, now: Long): Outcome? {
        if (!Features.isOn(context, Feature.MEDIA)) return null
        val accounts = MediaAccounts(context)
        if (!accounts.anyConnected()) return null
        val state = MediaSyncState(context)
        val settings = MediaSettings(context)
        val lookbackStart = now - settings.lookbackDays * DAY_MS
        val since = max(state.lastSyncMs - OVERLAP_MS, lookbackStart)

        val events = ArrayList<MediaEvent>()
        val problems = ArrayList<String>()
        val afterWrite = ArrayList<() -> Unit>()

        if (accounts.connected(MediaService.ANILIST)) {
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
            try {
                events += MediaClients.malHistory(accounts.malUser, lookbackStart)
            } catch (e: MediaException) {
                problems += "MyAnimeList: ${e.message}"
            }
        }
        if (accounts.connected(MediaService.SIMKL)) {
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

        val outcome = write(context, events, settings, now, problems)
        if (problems.size < accountsTried(accounts)) {
            afterWrite.forEach { it() }
            state.lastSyncMs = now
        }
        state.lastLine = outcome.line
        AppLog.i("Shows and manga", outcome.line)
        return outcome
    }

    private fun accountsTried(accounts: MediaAccounts): Int = MediaService.entries.count { accounts.connected(it) }

    private suspend fun write(context: Context, events: List<MediaEvent>, settings: MediaSettings, now: Long, problems: List<String>): Outcome {
        val db = AppDatabase.get(context)
        val known = db.importDao().sourceIds(SOURCE).toSet()
        val fresh = events.filter { it.id !in known }.distinctBy { it.id }
        if (fresh.isEmpty()) return Outcome(0, 0, problems)

        val zone = ZoneId.systemDefault()
        val earliest = fresh.minOf { it.atMs }
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
            events = fresh,
            sessionsFor = { service -> sessions.filter { map[it.pkg] == service }.map { MediaSession(it.pkg, it.startMs, it.endMs) } },
            userBusy = userBusy,
            autoBusy = autoBusy,
            minutesPerChapter = settings.minutesPerChapter,
            nowMs = now,
            zone = zone,
        )

        db.withTransaction {
            val marks = ArrayList<ImportedItemEntity>()
            for (p in result.placed) {
                val endDay = Instant.ofEpochMilli(p.endMs - 1).atZone(zone).toLocalDate()
                val dayStart = endDay.atStartOfDay(zone).toInstant().toEpochMilli()
                val start = max(p.startMs, dayStart)
                val startMinute = ((start - dayStart) / 60_000L).toInt()
                val endMinute = (((p.endMs - dayStart) / 60_000L).toInt()).coerceAtMost(1439)
                if (endMinute <= startMinute) continue
                val id = db.entryDao().insert(
                    EntryEntity(
                        date = endDay.toString(),
                        startMinute = startMinute,
                        endMinute = endMinute,
                        category = p.event.kind.category,
                        title = p.event.entryTitle,
                        note = "From " + p.event.service.label,
                    ),
                )
                marks += ImportedItemEntity(SOURCE, p.event.id, id)
            }
            db.importDao().insertAll(marks)
        }
        return Outcome(result.placed.size, result.skipped.size, problems)
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
