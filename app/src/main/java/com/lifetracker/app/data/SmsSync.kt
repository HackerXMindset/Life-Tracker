package com.lifetracker.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the SMS reading remembers: how far back to go, whether money received counts, and the span of time already read. Left out of backups. */
class SmsSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Months of history to read; 0 means everything. */
    var months: Int
        get() = prefs.getInt("months", 12)
        set(v) { prefs.edit().putInt("months", v).apply() }

    /** Money received (not refunds, which always count) is added only when this is on. */
    var includeIncome: Boolean
        get() = prefs.getBoolean("income", false)
        set(v) { prefs.edit().putBoolean("income", v).apply() }

    /** The newest time already read, 0 before the first reading. */
    var newestMs: Long
        get() = prefs.getLong("newest", 0L)
        set(v) { prefs.edit().putLong("newest", v).apply() }

    /** The start of what has been read. */
    var oldestMs: Long
        get() = prefs.getLong("oldest", 0L)
        set(v) { prefs.edit().putLong("oldest", v).apply() }

    var lastLine: String
        get() = prefs.getString("last_line", "").orEmpty()
        set(v) { prefs.edit().putString("last_line", v).apply() }

    fun lookbackStartMs(nowMs: Long): Long = if (months <= 0) 0L else nowMs - months * 30L * 24 * 60 * 60 * 1000

    companion object {
        const val FILE = "sms_state"
    }
}

/** Reads the bank, card and UPI texts on the phone and adds what was paid to the Money tab. Needs the SMS permission. */
object SmsSync {
    const val SOURCE = "sms"
    const val LOG_FILE = "sms_log"
    private const val MARGIN_MS = 60 * 60 * 1000L
    private const val BATCH = 300
    private val mutex = Mutex()

    class Outcome(val scanned: Int, val added: Int, val merged: Int, val duplicates: Int, val problem: String?) {
        val line: String
            get() {
                val parts = ArrayList<String>()
                parts += if (scanned == 0) "Nothing new" else "Read $scanned texts, added $added"
                if (merged > 0) parts += "$merged already there from Gmail"
                if (duplicates > 0) parts += "$duplicates repeats left out"
                problem?.let { parts += it }
                return parts.joinToString(" · ")
            }
    }

    fun hasAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    /** Null when the feature is off or the permission was not given. */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Outcome? = mutex.withLock {
        withContext(Dispatchers.IO) { runLocked(context.applicationContext, now) }
    }

    private class Counts(var scanned: Int = 0, var added: Int = 0, var merged: Int = 0, var duplicates: Int = 0)

    private suspend fun runLocked(context: Context, now: Long): Outcome? {
        if (!Features.isOn(context, Feature.MONEY)) return null
        if (!hasAccess(context)) return null
        val settings = SmsSettings(context)
        val start = settings.lookbackStartMs(now)
        val never = settings.newestMs == 0L
        val ranges = ArrayList<Pair<Long, Long>>()
        if (never) {
            ranges += start to now
        } else {
            ranges += (settings.newestMs - MARGIN_MS) to now
            if (settings.oldestMs > start) ranges += start to (settings.oldestMs + MARGIN_MS)
        }
        val db = AppDatabase.get(context)
        val log = GmailLog(context, LOG_FILE)
        val counts = Counts()
        var problem: String? = null
        try {
            val known = db.importDao().sourceIds(SOURCE).toMutableSet()
            for ((from, to) in ranges) scan(context, from, to, now, settings.includeIncome, known, log, db, counts)
            settings.newestMs = now
            settings.oldestMs = if (never) start else minOf(settings.oldestMs, start)
        } catch (e: SecurityException) {
            problem = "Android refused access to your texts"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = "Could not read texts (${e.javaClass.simpleName})"
        }
        val outcome = Outcome(counts.scanned, counts.added, counts.merged, counts.duplicates, problem)
        AppLog.i("SMS purchases", outcome.line)
        settings.lastLine = outcome.line
        return outcome
    }

    private suspend fun scan(
        context: Context,
        from: Long,
        to: Long,
        now: Long,
        includeIncome: Boolean,
        known: MutableSet<String>,
        log: GmailLog,
        db: AppDatabase,
        counts: Counts,
    ) {
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf("address", "body", "date"),
            "date >= ? AND date <= ?",
            arrayOf(from.toString(), to.toString()),
            "date ASC",
        ) ?: return
        cursor.use { c ->
            val batch = ArrayList<SmsDoc>()
            while (c.moveToNext()) {
                val body = c.getString(1) ?: continue
                val address = c.getString(0).orEmpty()
                val date = c.getLong(2)
                counts.scanned++
                val key = hash("$address|$date|$body")
                if (key in known) continue
                batch += SmsDoc(key, address, body, date)
                if (batch.size >= BATCH) {
                    write(context, batch, now, includeIncome, known, log, db, counts)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) write(context, batch, now, includeIncome, known, log, db, counts)
        }
    }

    private suspend fun write(
        context: Context,
        docs: List<SmsDoc>,
        now: Long,
        includeIncome: Boolean,
        known: MutableSet<String>,
        log: GmailLog,
        db: AppDatabase,
        counts: Counts,
    ) = MoneyMerge.lock.withLock {
        val zone = ZoneId.systemDefault()
        val categories = db.moneyDao().allCategories().map { it.id }.toSet()
        val seen = log.recent().takeLast(100).map { Purchase(it.merchant, it.amount, it.kind, "", it.orderId, it.atMs, it.subject) }.toMutableList()
        val entries = ArrayList<MoneyEntryEntity>()
        val marks = ArrayList<ImportedItemEntity>()
        val items = ArrayList<GmailRecent>()
        for (doc in docs.sortedBy { it.dateMs }) {
            val verdict = SmsPurchases.judge(doc, includeIncome) as? MailVerdict.Found ?: continue
            val p = verdict.purchase
            marks += ImportedItemEntity(SOURCE, doc.id, 0L)
            known += doc.id
            if (seen.any { SmsPurchases.sameText(p, it) }) {
                counts.duplicates++
                continue
            }
            val date = LocalDate.ofInstant(Instant.ofEpochMilli(p.atMs), zone).toString()
            // A receipt from Gmail for the same spending says more (the shop), so the text is not added beside it.
            val twin = db.moneyDao().entriesOn(date, "gmail-%").any { MoneyMerge.sameSpend(p.merchant, p.amount, p.kind, it.name, it.amount, it.kind) }
            if (twin) {
                counts.merged++
                continue
            }
            val id = "sms-" + hash(doc.id)
            entries += MoneyEntryEntity(
                id = id, date = date, kind = p.kind, categoryId = GmailSync.categoryFor(p.categoryId, p.kind, categories), name = p.merchant,
                amount = p.amount, note = "SMS · " + p.subject, itemId = "",
            )
            seen += p
            items += GmailRecent(id, "sms", doc.id, date, p.merchant, p.amount, p.kind, p.subject, p.orderId, p.atMs, doc.sender, now)
        }
        db.withTransaction {
            if (entries.isNotEmpty()) db.moneyDao().upsertEntries(entries)
            if (marks.isNotEmpty()) db.importDao().insertAll(marks)
        }
        if (items.isNotEmpty()) log.addRecent(items, now)
        counts.added += entries.size
    }

    /** Takes entries this feature added off the Money tab. They stay marked, so they do not come back. */
    suspend fun remove(context: Context, entryIds: Set<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val db = AppDatabase.get(app)
            db.withTransaction { entryIds.forEach { db.moneyDao().deleteEntryById(it) } }
            GmailLog(app, LOG_FILE).removeRecent(entryIds)
        }
    }
}

/** Reads new texts every 15 minutes or so. */
class SmsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppLog.track("SMS purchases", "Check", { it?.line ?: "off or no permission" }) { SmsSync.run(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "sms-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
