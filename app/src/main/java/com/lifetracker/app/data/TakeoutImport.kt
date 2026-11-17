package com.lifetracker.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the Takeout import remembers between visits to its page. Left out of backups. */
class TakeoutPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("takeout_state", Context.MODE_PRIVATE)

    var lastLine: String
        get() = prefs.getString("last_line", "").orEmpty()
        set(v) { prefs.edit().putString("last_line", v).apply() }

    /** The time of the last import that added anything, so it can be undone; 0 when there is nothing to undo. */
    var lastRun: Long
        get() = prefs.getLong("last_run", 0L)
        set(v) { prefs.edit().putLong("last_run", v).apply() }
}

private class CountingInput(input: InputStream) : FilterInputStream(input) {
    var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
}

/**
 * Reads the Gmail mailbox files from Google Takeout (a .zip, or the .mbox inside it) and adds the purchases in them to the Money tab,
 * the same way live Gmail reading does. It goes through the file as a stream, so a mailbox of many gigabytes is fine. The same email is
 * never added twice, in this file, in another, or alongside live Gmail. The whole import can be undone.
 */
object TakeoutImport {
    const val SOURCE = "mbox"
    const val LOG_FILE = "takeout_log"
    private const val BATCH = 200

    class Status(val running: Boolean, val line: String, val fraction: Float?)

    private val state = MutableStateFlow(Status(false, "", null))
    val status: StateFlow<Status> get() = state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** The note of an entry from here ends with "#order number" when the email had one. */
    fun orderTag(note: String): String? = Regex("#([A-Za-z0-9-]{6,30})$").find(note)?.groupValues?.get(1)

    /** Is this already-added entry the same spending as [p]? The same shop and amount that day, and the same order number where both have one. */
    fun sameOrder(p: Purchase, entry: MoneyEntryEntity): Boolean {
        if (!MoneyMerge.sameSpend(p.merchant, p.amount, p.kind, entry.name, entry.amount, entry.kind)) return false
        val tag = orderTag(entry.note)
        return p.orderId == null || tag == null || tag == p.orderId
    }

    private class Counts(var messages: Int = 0, var matched: Int = 0, var added: Int = 0, var duplicates: Int = 0, var unsure: Int = 0)

    private class Run(
        val appContext: Context,
        val runMs: Long,
        val ignored: Set<String>,
        val db: AppDatabase,
        val log: GmailLog,
        val known: MutableSet<String>,
        val seen: MutableList<Purchase>,
        val counts: Counts,
        val totalBytes: Long,
    ) {
        var doneBefore = 0L
        var current: CountingInput? = null
    }

    fun start(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || state.value.running) return
        val app = context.applicationContext
        if (!Features.isOn(app, Feature.MONEY)) {
            state.value = Status(false, "Switch on the Money feature first (Settings → Features).", null)
            return
        }
        state.value = Status(true, "Starting…", 0f)
        job = scope.launch {
            val line = try {
                runImport(app, uris)
            } catch (e: CancellationException) {
                "Stopped. Start it again and nothing is added twice."
            } catch (e: Exception) {
                "Stopped: " + (e.message ?: e.javaClass.simpleName) + ". Start it again and nothing is added twice."
            }
            TakeoutPrefs(app).lastLine = line
            state.value = Status(false, line, null)
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun sizeOf(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            }
        }.getOrNull() ?: -1L

    private suspend fun runImport(app: Context, uris: List<Uri>): String {
        val runMs = System.currentTimeMillis()
        val db = AppDatabase.get(app)
        val sizes = uris.map { sizeOf(app, it) }
        val total = if (sizes.all { it > 0 }) sizes.sum() else -1L
        val run = Run(
            app, runMs, GmailSettings(app).ignored(), db, GmailLog(app, LOG_FILE),
            db.importDao().sourceIds(SOURCE).toHashSet(), ArrayList(), Counts(), total,
        )
        var files = 0
        for ((i, uri) in uris.withIndex()) {
            val stream = app.contentResolver.openInputStream(uri) ?: continue
            stream.use { raw ->
                val counting = CountingInput(raw)
                run.current = counting
                val buffered = BufferedInputStream(counting, 1 shl 20)
                buffered.mark(4)
                val b0 = buffered.read()
                val b1 = buffered.read()
                buffered.reset()
                if (b0 == 'P'.code && b1 == 'K'.code) {
                    ZipInputStream(buffered).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (!entry.isDirectory && entry.name.lowercase().endsWith(".mbox")) { readMbox(zip, run); files++ }
                        }
                    }
                } else {
                    readMbox(buffered, run)
                    files++
                }
            }
            run.doneBefore += maxOf(0L, sizes[i])
            run.current = null
        }
        val c = run.counts
        if (c.added > 0) TakeoutPrefs(app).lastRun = runMs
        if (files == 0) return "No mailbox (.mbox) file was found in what you chose."
        return buildString {
            append("Read ${c.messages} emails, added ${c.added}")
            if (c.duplicates > 0) append(" · ${c.duplicates} were already there")
            if (c.unsure > 0) append(" · ${c.unsure} receipts with no clear total left out")
        }
    }

    private fun report(run: Run) {
        val c = run.counts
        val done = run.doneBefore + (run.current?.count ?: 0L)
        val fraction = if (run.totalBytes > 0) (done.toFloat() / run.totalBytes).coerceIn(0f, 1f) else null
        state.value = Status(true, "Read ${c.messages} emails, added ${c.added}", fraction)
    }

    private suspend fun readMbox(input: InputStream, run: Run) {
        val reader = MboxReader(input)
        val batch = ArrayList<MailDoc>()
        while (true) {
            currentCoroutineContext().ensureActive()
            val raw = reader.next() ?: break
            run.counts.messages++
            val doc = MimeParser.parse(raw) { from, subject, labels -> TakeoutFilter.wanted(labels, from, subject, run.ignored) }
            if (doc != null) batch += doc
            if (batch.size >= BATCH) {
                write(run, batch)
                batch.clear()
            }
            if (run.counts.messages % 100 == 0) report(run)
        }
        if (batch.isNotEmpty()) write(run, batch)
        report(run)
    }

    private suspend fun write(run: Run, docs: List<MailDoc>) = MoneyMerge.lock.withLock {
        val db = run.db
        val zone = ZoneId.systemDefault()
        val categories = db.moneyDao().allCategories().map { it.id }.toSet()
        val entries = ArrayList<MoneyEntryEntity>()
        val marks = ArrayList<ImportedItemEntity>()
        val items = ArrayList<GmailRecent>()
        val replaced = HashSet<String>()
        for (doc in docs.sortedBy { it.dateMs }) {
            val key = sha16(doc.id)
            if (key in run.known) continue
            when (val verdict = MailPurchases.judge(doc)) {
                is MailVerdict.Unsure -> run.counts.unsure++
                is MailVerdict.Skip -> {}
                is MailVerdict.Found -> {
                    val p = verdict.purchase
                    run.counts.matched++
                    marks += ImportedItemEntity(SOURCE, key, run.runMs)
                    run.known += key
                    if (run.seen.any { MailPurchases.samePurchase(p, it) }) {
                        run.counts.duplicates++
                        continue
                    }
                    run.seen += p
                    val date = LocalDate.ofInstant(Instant.ofEpochMilli(p.atMs), zone).toString()
                    val twin = db.moneyDao().entriesOn(date, "gmail-%").any { sameOrder(p, it) } ||
                        db.moneyDao().entriesOn(date, "mbox-%").any { sameOrder(p, it) }
                    if (twin) {
                        run.counts.duplicates++
                        continue
                    }
                    // A bank text for the same spending says less than this receipt, so the receipt takes its place.
                    db.moneyDao().entriesOn(date, "sms-%").filter { MoneyMerge.sameSpend(p.merchant, p.amount, p.kind, it.name, it.amount, it.kind) }
                        .forEach { replaced += it.id }
                    val id = "mbox-$key"
                    val tag = p.orderId?.let { " · #$it" }.orEmpty()
                    entries += MoneyEntryEntity(
                        id = id, date = date, kind = p.kind, categoryId = GmailSync.categoryFor(p.categoryId, p.kind, categories), name = p.merchant,
                        amount = p.amount, note = "Takeout · " + p.subject.take(70) + tag, itemId = "",
                    )
                    items += GmailRecent(id, "takeout", key, date, p.merchant, p.amount, p.kind, p.subject, p.orderId, p.atMs, Merchants.domainOf(doc.from), run.runMs)
                }
            }
        }
        db.withTransaction {
            replaced.forEach { db.moneyDao().deleteEntryById(it) }
            if (entries.isNotEmpty()) db.moneyDao().upsertEntries(entries)
            if (marks.isNotEmpty()) db.importDao().insertAll(marks)
        }
        if (replaced.isNotEmpty()) GmailLog(run.appContext, SmsSync.LOG_FILE).removeRecent(replaced)
        if (items.isNotEmpty()) run.log.addRecent(items, run.runMs)
        run.counts.added += entries.size
    }

    /** Takes everything the last import added off the Money tab, and forgets that it read those emails so a later import can add them again. */
    suspend fun undoLast(context: Context): Int = withContext(Dispatchers.IO) {
        MoneyMerge.lock.withLock {
            val app = context.applicationContext
            val prefs = TakeoutPrefs(app)
            val runMs = prefs.lastRun
            if (runMs == 0L) return@withLock 0
            val db = AppDatabase.get(app)
            val ids = db.importDao().sourceIdsForRun(SOURCE, runMs).map { "mbox-$it" }.toSet()
            db.withTransaction {
                ids.forEach { db.moneyDao().deleteEntryById(it) }
                db.importDao().deleteForRun(SOURCE, runMs)
            }
            GmailLog(app, LOG_FILE).removeRecent(ids)
            prefs.lastRun = 0L
            prefs.lastLine = "Undone. What that import added is off the Money tab."
            state.value = Status(false, prefs.lastLine, null)
            ids.size
        }
    }
}
