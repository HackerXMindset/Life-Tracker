package com.lifetracker.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Gmail accounts that are connected, and how far back each has been read. Only addresses and dates are kept (Google keeps the
 * sign-in itself), and this file is left out of backups.
 */
class GmailAccounts(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun list(): List<String> = prefs.getString("accounts", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun add(email: String) {
        val all = list().toMutableList()
        if (email !in all) all += email
        prefs.edit().putString("accounts", all.joinToString("\n")).apply()
    }

    fun remove(email: String) {
        prefs.edit().putString("accounts", list().filter { it != email }.joinToString("\n"))
            .remove("newest_$email").remove("oldest_$email").remove("done_$email").apply()
    }

    /** The newest email read (new mail is looked for after it), the oldest read so far (history is read back from it), and whether history is finished. */
    class Cursor(val newestMs: Long, val oldestMs: Long, val done: Boolean)

    fun cursor(email: String, nowMs: Long): Cursor = Cursor(
        prefs.getLong("newest_$email", nowMs),
        prefs.getLong("oldest_$email", nowMs),
        prefs.getBoolean("done_$email", false),
    )

    fun hasCursor(email: String): Boolean = prefs.contains("oldest_$email")

    fun save(email: String, cursor: Cursor) {
        prefs.edit().putLong("newest_$email", cursor.newestMs).putLong("oldest_$email", cursor.oldestMs).putBoolean("done_$email", cursor.done).apply()
    }

    /** Reads the history again from now backwards (after changing how far back to read). */
    fun restartHistory(email: String, nowMs: Long) {
        prefs.edit().putLong("oldest_$email", nowMs).putBoolean("done_$email", false).apply()
    }

    companion object {
        const val FILE = "gmail_accounts"
    }
}

/** How far back to read, and senders to leave alone. Kept in backups. */
class GmailSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Months of history to read; 0 means everything. */
    var months: Int
        get() = prefs.getInt("months", 12)
        set(v) { prefs.edit().putInt("months", v).apply() }

    fun lookbackStartMs(nowMs: Long): Long = if (months <= 0) 0L else nowMs - months * 30L * 24 * 60 * 60 * 1000

    fun ignored(): Set<String> = prefs.getString("ignored", "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    fun ignore(domain: String) {
        if (domain.isBlank()) return
        prefs.edit().putString("ignored", (ignored() + domain).joinToString("\n")).apply()
    }

    fun unignore(domain: String) {
        prefs.edit().putString("ignored", (ignored() - domain).joinToString("\n")).apply()
    }

    companion object {
        const val FILE = "gmail_settings"
        val MONTH_CHOICES = listOf(3 to "3 months", 12 to "1 year", 36 to "3 years", 60 to "5 years", 0 to "Everything")
    }
}

/** One money entry this feature added, remembered so it can be shown, taken back, and recognised if the same purchase turns up again. */
class GmailRecent(
    val entryId: String,
    val account: String,
    val msgId: String,
    val date: String,
    val merchant: String,
    val amount: Double,
    val kind: Int,
    val subject: String,
    val orderId: String?,
    val atMs: Long,
    val domain: String,
    val runMs: Long,
)

/** One email that looked like money but where the amount was not certain. */
class GmailUnsure(
    val account: String,
    val msgId: String,
    val from: String,
    val subject: String,
    val atMs: Long,
    val reason: String,
    val merchant: String,
    val amount: Double?,
    val kind: Int,
    val categoryId: String,
    val orderId: String?,
)

/** What the Gmail reading remembers about its work. Left out of backups. */
class GmailLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun recent(): List<GmailRecent> {
        val array = runCatching { JSONArray(prefs.getString("recent", "[]").orEmpty()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            GmailRecent(
                o.optString("entry"), o.optString("acc"), o.optString("msg"), o.optString("date"), o.optString("m"), o.optDouble("amt"),
                o.optInt("k"), o.optString("sub"), o.optString("oid").takeIf { it.isNotEmpty() }, o.optLong("at"), o.optString("dom"), o.optLong("run"),
            )
        }
    }

    fun addRecent(items: List<GmailRecent>, nowMs: Long) {
        val oldest = nowMs - KEEP_DAYS * 24L * 60 * 60 * 1000
        val all = (recent() + items).filter { it.runMs >= oldest }.takeLast(KEEP)
        val array = JSONArray()
        all.forEach {
            array.put(
                JSONObject().put("entry", it.entryId).put("acc", it.account).put("msg", it.msgId).put("date", it.date).put("m", it.merchant)
                    .put("amt", it.amount).put("k", it.kind).put("sub", it.subject).put("oid", it.orderId ?: "").put("at", it.atMs)
                    .put("dom", it.domain).put("run", it.runMs),
            )
        }
        prefs.edit().putString("recent", array.toString()).apply()
    }

    fun removeRecent(entryIds: Set<String>) {
        val keep = recent().filter { it.entryId !in entryIds }
        prefs.edit().putString("recent", "[]").apply()
        if (keep.isNotEmpty()) addRecent(keep, System.currentTimeMillis())
    }

    fun unsure(): List<GmailUnsure> {
        val array = runCatching { JSONArray(prefs.getString("unsure", "[]").orEmpty()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            GmailUnsure(
                o.optString("acc"), o.optString("msg"), o.optString("from"), o.optString("sub"), o.optLong("at"), o.optString("why"),
                o.optString("m"), if (o.has("amt")) o.optDouble("amt") else null, o.optInt("k"), o.optString("cat"), o.optString("oid").takeIf { it.isNotEmpty() },
            )
        }
    }

    fun setUnsure(list: List<GmailUnsure>) {
        val array = JSONArray()
        list.takeLast(UNSURE_KEEP).forEach {
            val o = JSONObject().put("acc", it.account).put("msg", it.msgId).put("from", it.from).put("sub", it.subject).put("at", it.atMs)
                .put("why", it.reason).put("m", it.merchant).put("k", it.kind).put("cat", it.categoryId).put("oid", it.orderId ?: "")
            it.amount?.let { a -> o.put("amt", a) }
            array.put(o)
        }
        prefs.edit().putString("unsure", array.toString()).apply()
    }

    companion object {
        const val FILE = "gmail_log"
        const val KEEP = 600
        const val KEEP_DAYS = 400L
        const val UNSURE_KEEP = 300
    }
}
