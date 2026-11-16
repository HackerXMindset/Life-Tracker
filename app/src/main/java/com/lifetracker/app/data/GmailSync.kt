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
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads the purchases out of your connected Gmail accounts and puts them on the Money tab. New mail is read after the last
 * one seen; history is read backwards, a few hundred emails at a time, until the chosen start is reached. Each email becomes at
 * most one entry, the same purchase in two emails (confirmed, then delivered) is added once, and an entry you delete does not
 * come back. When the amount is not certain the email goes to a short list to look at instead.
 */
object GmailSync {
    const val SOURCE = "gmail"
    private const val MARGIN_MS = 60 * 60 * 1000L
    private val mutex = Mutex()

    class Outcome(val scanned: Int, val added: Int, val unsure: Int, val duplicates: Int, val problems: List<String>, val allDone: Boolean) {
        val line: String
            get() {
                val parts = ArrayList<String>()
                parts += if (scanned == 0) "Nothing new" else "Read $scanned emails, added $added"
                if (duplicates > 0) parts += "$duplicates repeats left out"
                if (unsure > 0) parts += "$unsure to check"
                parts += problems
                return parts.joinToString(" · ")
            }
    }

    /** The Gmail search that finds mail about money. Wide on purpose; the reader above throws away what is not a purchase. */
    fun query(ignored: Set<String>): String {
        val senders = "zomato OR swiggy OR blinkit OR zepto OR bigbasket OR amazon OR flipkart OR myntra OR ajio OR nykaa OR meesho OR uber OR ola OR rapido OR " +
            "irctc OR makemytrip OR goibibo OR redbus OR bookmyshow OR netflix OR spotify OR hotstar OR phonepe OR paytm OR dominos OR starbucks"
        val subjects = "order OR receipt OR invoice OR payment OR paid OR booking OR ticket OR subscription OR renewal OR refund OR debited OR bill OR \"thank you for\" OR trip OR ride"
        val skip = ignored.take(40).joinToString(" ") { "-from:$it" }
        return "(category:purchases OR subject:($subjects) OR (from:($senders) -category:promotions)) -in:spam -in:trash $skip".trim()
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    fun hasAccounts(context: Context): Boolean = GmailAccounts(context).list().isNotEmpty()

    /** [budget] is how many emails to read in this go. Null when the feature is off or no account is connected. */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis(), budget: Int = 250): Outcome? = mutex.withLock {
        withContext(Dispatchers.IO) { runLocked(context.applicationContext, now, budget) }
    }

    private class Counts(var scanned: Int = 0, var added: Int = 0, var unsure: Int = 0, var duplicates: Int = 0)

    private suspend fun runLocked(context: Context, now: Long, budget: Int): Outcome? {
        if (!Features.isOn(context, Feature.MONEY)) return null
        val accounts = GmailAccounts(context)
        val emails = accounts.list()
        if (emails.isEmpty()) return null
        val settings = GmailSettings(context)
        val log = GmailLog(context)
        val db = AppDatabase.get(context)
        val counts = Counts()
        val problems = ArrayList<String>()
        var allDone = true
        val query = query(settings.ignored())
        val lookbackStart = settings.lookbackStartMs(now)

        for (email in emails) {
            try {
                val token = GmailAuth.silentToken(context, email)
                if (!accounts.hasCursor(email)) accounts.save(email, accounts.cursor(email, now))
                var cursor = accounts.cursor(email, now)
                var remaining = budget

                // 1. Mail that arrived since last time.
                val after = (cursor.newestMs - MARGIN_MS) / 1000
                val forward = scan(context, token, email, "$query after:$after", remaining, now, log, db, counts)
                remaining -= forward.read
                if (forward.finished) cursor = GmailAccounts.Cursor(maxOf(cursor.newestMs, forward.newest ?: 0L), cursor.oldestMs, cursor.done)
                accounts.save(email, cursor)

                // 2. History, newest to oldest, until the chosen start.
                if (!cursor.done && remaining > 0) {
                    val before = cursor.oldestMs / 1000
                    val back = scan(context, token, email, "$query after:${lookbackStart / 1000} before:$before", remaining, now, log, db, counts)
                    val oldest = back.oldest?.let { minOf(it, cursor.oldestMs) } ?: cursor.oldestMs
                    // Every page of the window gone through means nothing older is left to read.
                    cursor = GmailAccounts.Cursor(cursor.newestMs, oldest, back.finished)
                    accounts.save(email, cursor)
                }
                if (!cursor.done) allDone = false
            } catch (e: MediaException) {
                problems += "$email: ${e.message}"
                allDone = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                problems += "$email: ${e.javaClass.simpleName}"
                allDone = false
            }
        }
        val outcome = Outcome(counts.scanned, counts.added, counts.unsure, counts.duplicates, problems, allDone)
        AppLog.i("Gmail purchases", outcome.line)
        GmailRunState(context).lastLine = outcome.line
        return outcome
    }

    private class ScanResult(val read: Int, val listed: Int, val newest: Long?, val oldest: Long?, val finished: Boolean)

    /** Lists the emails for [q], reads up to [limit] of them and writes what they hold. [finished] is true when every page was gone through. */
    private suspend fun scan(
        context: Context,
        token: String,
        email: String,
        q: String,
        limit: Int,
        now: Long,
        log: GmailLog,
        db: AppDatabase,
        counts: Counts,
    ): ScanResult {
        if (limit <= 0) return ScanResult(0, 0, null, null, false)
        val known = db.importDao().sourceIds(SOURCE).toSet() + log.unsure().map { "${it.account}|${it.msgId}" }
        var read = 0
        var listed = 0
        var newest: Long? = null
        var oldest: Long? = null
        var page: String? = null
        var finished = false
        while (true) {
            val result = GmailClient.list(token, q, page)
            listed += result.ids.size
            val docs = ArrayList<MailDoc>()
            var lastKnown: String? = null
            var cut = false
            for (id in result.ids) {
                if (read >= limit) { cut = true; break }
                if ("$email|$id" in known) { lastKnown = id; continue }
                val doc = GmailClient.message(token, id) ?: continue
                read++
                docs += doc
            }
            for (d in docs) {
                newest = maxOf(newest ?: d.dateMs, d.dateMs)
                oldest = minOf(oldest ?: d.dateMs, d.dateMs)
            }
            if (docs.isEmpty() && lastKnown != null) {
                // A page of emails already read: find out how old they are so the history can move on past them.
                GmailClient.dateOf(token, lastKnown)?.let { oldest = minOf(oldest ?: it, it); newest = maxOf(newest ?: it, it) }
            }
            counts.scanned += docs.size
            if (docs.isNotEmpty()) write(context, email, docs, now, log, db, counts)
            if (cut) break
            page = result.next
            if (page == null) { finished = true; break }
            if (read >= limit) break
        }
        return ScanResult(read, listed, newest, oldest, finished)
    }

    internal fun categoryFor(wanted: String, kind: Int, existing: Set<String>): String {
        if (wanted in existing) return wanted
        val fallback = if (kind == KIND_INCOME) "i-other" else "x-other"
        return if (fallback in existing) fallback else existing.firstOrNull().orEmpty()
    }

    private suspend fun write(context: Context, email: String, docs: List<MailDoc>, now: Long, log: GmailLog, db: AppDatabase, counts: Counts) =
        MoneyMerge.lock.withLock { writeLocked(context, email, docs, now, log, db, counts) }

    private suspend fun writeLocked(context: Context, email: String, docs: List<MailDoc>, now: Long, log: GmailLog, db: AppDatabase, counts: Counts) {
        val replaced = HashSet<String>()
        val zone = ZoneId.systemDefault()
        val categories = db.moneyDao().allCategories().map { it.id }.toSet()
        val recent = log.recent().toMutableList()
        val entries = ArrayList<MoneyEntryEntity>()
        val marks = ArrayList<ImportedItemEntity>()
        val items = ArrayList<GmailRecent>()
        val unsure = ArrayList<GmailUnsure>()
        // Oldest first, so of an order's several emails the first one is the one kept.
        for (doc in docs.sortedBy { it.dateMs }) {
            val key = "$email|${doc.id}"
            when (val verdict = MailPurchases.judge(doc)) {
                is MailVerdict.Skip -> {}
                is MailVerdict.Unsure -> {
                    val c = verdict.candidate
                    unsure += GmailUnsure(
                        email, doc.id, doc.from, doc.subject, doc.dateMs, verdict.reason, Merchants.nameOf(doc.from), c?.amount,
                        c?.kind ?: KIND_EXPENSE, c?.categoryId ?: "x-other", c?.orderId,
                    )
                }
                is MailVerdict.Found -> {
                    val p = verdict.purchase
                    val repeat = recent.any { r -> MailPurchases.samePurchase(p, Purchase(r.merchant, r.amount, r.kind, "", r.orderId, r.atMs, r.subject)) }
                    if (repeat) {
                        marks += ImportedItemEntity(SOURCE, key, 0L)
                        counts.duplicates++
                    } else {
                        val id = "gmail-" + hash(key)
                        val date = LocalDate.ofInstant(Instant.ofEpochMilli(p.atMs), zone).toString()
                        // The same spending may already be there from a bank SMS; the receipt says more (the shop), so it takes its place.
                        db.moneyDao().entriesOn(date, "sms-%").filter { MoneyMerge.sameSpend(p.merchant, p.amount, p.kind, it.name, it.amount, it.kind) }
                            .forEach { replaced += it.id }
                        entries += MoneyEntryEntity(
                            id = id, date = date, kind = p.kind, categoryId = categoryFor(p.categoryId, p.kind, categories), name = p.merchant,
                            amount = p.amount, note = "Gmail · " + p.subject.take(80), itemId = "",
                        )
                        marks += ImportedItemEntity(SOURCE, key, 0L)
                        val item = GmailRecent(id, email, doc.id, date, p.merchant, p.amount, p.kind, p.subject, p.orderId, p.atMs, Merchants.domainOf(doc.from), now)
                        items += item
                        recent += item
                    }
                }
            }
        }
        db.withTransaction {
            replaced.forEach { db.moneyDao().deleteEntryById(it) }
            if (entries.isNotEmpty()) db.moneyDao().upsertEntries(entries)
            if (marks.isNotEmpty()) db.importDao().insertAll(marks)
        }
        if (replaced.isNotEmpty()) GmailLog(context, SmsSync.LOG_FILE).removeRecent(replaced)
        if (items.isNotEmpty()) log.addRecent(items, now)
        if (unsure.isNotEmpty()) log.setUnsure(log.unsure() + unsure)
        counts.added += entries.size
        counts.unsure += unsure.size
    }

    /** Adds an email from the "to check" list as it was guessed. */
    suspend fun accept(context: Context, account: String, msgId: String, now: Long = System.currentTimeMillis()): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val log = GmailLog(app)
            val item = log.unsure().firstOrNull { it.account == account && it.msgId == msgId } ?: return@withContext false
            val amount = item.amount ?: return@withContext false
            val db = AppDatabase.get(app)
            val zone = ZoneId.systemDefault()
            val categories = db.moneyDao().allCategories().map { it.id }.toSet()
            val key = "$account|$msgId"
            val id = "gmail-" + hash(key)
            val date = LocalDate.ofInstant(Instant.ofEpochMilli(item.atMs), zone).toString()
            db.withTransaction {
                db.moneyDao().upsertEntry(
                    MoneyEntryEntity(id, date, item.kind, categoryFor(item.categoryId, item.kind, categories), item.merchant, amount, "Gmail · " + item.subject.take(80), ""),
                )
                db.importDao().insertAll(listOf(ImportedItemEntity(SOURCE, key, 0L)))
            }
            log.addRecent(listOf(GmailRecent(id, account, msgId, date, item.merchant, amount, item.kind, item.subject, item.orderId, item.atMs, Merchants.domainOf(item.from), now)), now)
            log.setUnsure(log.unsure().filter { !(it.account == account && it.msgId == msgId) })
            true
        }
    }

    /** Throws an email on the "to check" list away for good. */
    suspend fun dismiss(context: Context, account: String, msgId: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            AppDatabase.get(app).importDao().insertAll(listOf(ImportedItemEntity(SOURCE, "$account|$msgId", 0L)))
            val log = GmailLog(app)
            log.setUnsure(log.unsure().filter { !(it.account == account && it.msgId == msgId) })
        }
    }

    /** Takes entries this feature added off the Money tab. They stay marked, so they do not come back. */
    suspend fun remove(context: Context, entryIds: Set<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val db = AppDatabase.get(app)
            db.withTransaction { entryIds.forEach { db.moneyDao().deleteEntryById(it) } }
            GmailLog(app).removeRecent(entryIds)
        }
    }
}

/** The last line of the Gmail reading, for the settings page. */
class GmailRunState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(GmailAccounts.FILE, Context.MODE_PRIVATE)

    var lastLine: String
        get() = prefs.getString("last_line", "").orEmpty()
        set(v) { prefs.edit().putString("last_line", v).apply() }
}

/** Reads new mail every 15 minutes or so, and a few hundred old emails at a time until the history is done. */
class GmailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppLog.track("Gmail purchases", "Check", { it?.line ?: "off or no account" }) { GmailSync.run(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "gmail-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GmailSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
