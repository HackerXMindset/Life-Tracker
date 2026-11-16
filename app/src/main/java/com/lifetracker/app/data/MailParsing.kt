package com.lifetracker.app.data

import java.nio.charset.Charset
import java.util.Base64
import org.json.JSONObject

/** One email, reduced to what is needed to tell whether it was a purchase: who sent it, the subject, the day and the plain text. */
class MailDoc(val id: String, val from: String, val subject: String, val dateMs: Long, val text: String)

/** Reads Gmail's answers (JSON) and turns an email into plain text. No Android in it, so it is tested. */
object GmailParsers {
    class ListPage(val ids: List<String>, val next: String?)

    fun listPage(text: String): ListPage {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: throw MediaException("Gmail sent something that could not be read. Try again.")
        val array = json.optJSONArray("messages")
        val ids = ArrayList<String>()
        if (array != null) for (i in 0 until array.length()) array.optJSONObject(i)?.optString("id")?.takeIf { it.isNotEmpty() }?.let { ids += it }
        return ListPage(ids, json.optString("nextPageToken").takeIf { it.isNotEmpty() })
    }

    fun profileEmail(text: String): String? =
        runCatching { JSONObject(text).optString("emailAddress") }.getOrNull()?.lowercase()?.takeIf { it.contains('@') }

    /** An email as Gmail gives it with format=full. Null when it has no id or date. */
    fun message(text: String): MailDoc? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val id = json.optString("id")
        val date = json.optString("internalDate").toLongOrNull() ?: return null
        if (id.isEmpty()) return null
        val payload = json.optJSONObject("payload") ?: return null
        val headers = payload.optJSONArray("headers")
        fun header(name: String): String {
            if (headers == null) return ""
            for (i in 0 until headers.length()) {
                val h = headers.optJSONObject(i) ?: continue
                if (h.optString("name").equals(name, ignoreCase = true)) return h.optString("value")
            }
            return ""
        }
        val plain = StringBuilder()
        val html = StringBuilder()
        collect(payload, plain, html, 0)
        val body = if (plain.trim().length >= 80 || html.isEmpty()) plain.toString() else htmlToText(html.toString())
        return MailDoc(id, header("From"), header("Subject"), date, body.take(MAX_TEXT))
    }

    private const val MAX_TEXT = 30_000

    private fun collect(part: JSONObject, plain: StringBuilder, html: StringBuilder, depth: Int) {
        if (depth > 8) return
        val mime = part.optString("mimeType").lowercase()
        val data = part.optJSONObject("body")?.optString("data").orEmpty()
        if (data.isNotEmpty() && (mime == "text/plain" || mime == "text/html")) {
            val decoded = decode(data, charsetOf(part))
            if (mime == "text/plain") plain.append(decoded).append('\n') else html.append(decoded).append('\n')
        }
        val parts = part.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) parts.optJSONObject(i)?.let { collect(it, plain, html, depth + 1) }
    }

    private fun charsetOf(part: JSONObject): Charset {
        val headers = part.optJSONArray("headers") ?: return Charsets.UTF_8
        for (i in 0 until headers.length()) {
            val h = headers.optJSONObject(i) ?: continue
            if (h.optString("name").equals("Content-Type", ignoreCase = true)) {
                val name = Regex("charset=\"?([A-Za-z0-9_\\-]+)", RegexOption.IGNORE_CASE).find(h.optString("value"))?.groupValues?.get(1)
                if (name != null) return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
            }
        }
        return Charsets.UTF_8
    }

    private fun decode(data: String, charset: Charset): String =
        runCatching { String(Base64.getUrlDecoder().decode(data), charset) }.getOrDefault("")

    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    /** Page text with the tags gone and a line break wherever a block or table cell ended, so a label and its amount stay on near lines. */
    fun htmlToText(html: String): String {
        var s = html
        s = Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>").replace(s, " ")
        s = Regex("(?i)<br\\s*/?>|</(p|div|tr|td|th|li|h[1-6]|table|section)>").replace(s, "\n")
        s = Regex("(?s)<[^>]*>").replace(s, " ")
        s = ENTITY.replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: " "
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: " "
                else -> when (e.lowercase()) {
                    "nbsp", "zwnj", "zwj" -> " "
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    "rupee" -> "₹"
                    "euro" -> "€"
                    "pound" -> "£"
                    else -> " "
                }
            }
        }
        return s.lines().map { it.replace(' ', ' ').replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}

/** What a sender is: a known shop or service, its kind of spending, and the name to show. */
object Merchants {
    class Known(val name: String, val categoryId: String)

    private const val FOOD = "x-food"
    private const val SHOP = "x-shopping"
    private const val TRAVEL = "x-travel"
    private const val BILLS = "x-bills"
    private const val OTHER = "x-other"

    private val KNOWN: Map<String, Known> = mapOf(
        "zomato" to Known("Zomato", FOOD), "swiggy" to Known("Swiggy", FOOD), "blinkit" to Known("Blinkit", FOOD),
        "grofers" to Known("Blinkit", FOOD), "zepto" to Known("Zepto", FOOD), "bigbasket" to Known("BigBasket", FOOD),
        "dunzo" to Known("Dunzo", FOOD), "dominos" to Known("Domino's", FOOD), "mcdonalds" to Known("McDonald's", FOOD),
        "kfc" to Known("KFC", FOOD), "starbucks" to Known("Starbucks", FOOD), "eatsure" to Known("EatSure", FOOD),
        "amazon" to Known("Amazon", SHOP), "flipkart" to Known("Flipkart", SHOP), "myntra" to Known("Myntra", SHOP),
        "ajio" to Known("Ajio", SHOP), "nykaa" to Known("Nykaa", SHOP), "meesho" to Known("Meesho", SHOP),
        "lenskart" to Known("Lenskart", SHOP), "croma" to Known("Croma", SHOP), "tatacliq" to Known("Tata CLiQ", SHOP),
        "uber" to Known("Uber", TRAVEL), "olacabs" to Known("Ola", TRAVEL), "rapido" to Known("Rapido", TRAVEL),
        "irctc" to Known("IRCTC", TRAVEL), "makemytrip" to Known("MakeMyTrip", TRAVEL), "goibibo" to Known("Goibibo", TRAVEL),
        "ixigo" to Known("ixigo", TRAVEL), "redbus" to Known("redBus", TRAVEL), "indigo" to Known("IndiGo", TRAVEL),
        "netflix" to Known("Netflix", BILLS), "spotify" to Known("Spotify", BILLS), "hotstar" to Known("Hotstar", BILLS),
        "airtel" to Known("Airtel", BILLS), "jio" to Known("Jio", BILLS), "myvi" to Known("Vi", BILLS),
        "bsnl" to Known("BSNL", BILLS), "tatasky" to Known("Tata Play", BILLS),
        "bookmyshow" to Known("BookMyShow", OTHER), "phonepe" to Known("PhonePe", OTHER), "paytm" to Known("Paytm", OTHER),
        "pharmeasy" to Known("PharmEasy", OTHER), "1mg" to Known("Tata 1mg", OTHER), "apple" to Known("Apple", OTHER),
    )

    /** The part after the @ in the From line, lower case. */
    fun domainOf(from: String): String {
        val address = Regex("<([^>]+)>").find(from)?.groupValues?.get(1) ?: from
        return address.substringAfter('@', "").trim().lowercase()
    }

    fun known(from: String): Known? {
        val labels = domainOf(from).split('.')
        return labels.firstNotNullOfOrNull { KNOWN[it] }
    }

    private val NOISE = setOf("noreply", "no-reply", "donotreply", "do-not-reply", "orders", "order", "team", "support", "info", "billing", "alerts", "notifications", "mail", "updates", "receipts", "invoice")

    /** The name to show: the known one, else the sender's name, else the site name. */
    fun nameOf(from: String): String {
        known(from)?.let { return it.name }
        val display = from.substringBefore('<').trim().trim('"')
        val cleaned = display.split(' ', '-', '_').filter { it.isNotBlank() && it.lowercase() !in NOISE }.joinToString(" ")
        if (cleaned.length >= 2 && !cleaned.contains('@')) return cleaned.take(40)
        val labels = domainOf(from).split('.').filter { it.isNotBlank() }
        val base = labels.getOrNull((labels.size - 2).coerceAtLeast(0)).orEmpty()
        return base.replaceFirstChar { it.uppercase() }.ifEmpty { "Unknown" }
    }
}

/** What was made of one email. */
sealed class MailVerdict {
    class Found(val purchase: Purchase) : MailVerdict()

    /** Looks like money but cannot be sure of the amount; [candidate] is the best guess, if any. */
    class Unsure(val reason: String, val candidate: Purchase?) : MailVerdict()
    object Skip : MailVerdict()
}

class Purchase(
    val merchant: String,
    val amount: Double,
    val kind: Int,
    val categoryId: String,
    val orderId: String?,
    val atMs: Long,
    val subject: String,
)

/**
 * Decides whether an email is a purchase, a payment or a refund, and for how much. It looks for the total the way a person would
 * (the amount next to "Grand total", "Amount paid" and the like, never the subtotal, the delivery fee or a discount), skips
 * offers, cancelled orders and anything that is not about money, and says "unsure" rather than guess. No Android in it, so it is tested.
 */
object MailPurchases {
    private val CURRENCY_AMOUNT = Regex(
        "(₹|rs\\.?|inr|\\$|usd|€|eur|£|gbp)?\\s*(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)(?:\\s*/-)?",
        RegexOption.IGNORE_CASE,
    )
    private val STRONG_LABEL = Regex(
        "grand total|total paid|amount paid|paid amount|you paid|amount charged|net payable|bill total|order total|total payable|" +
            "amount debited|total charged|total amount paid|total bill|amount payable|total due|total amount|amount due|payment amount",
    )
    private val MEDIUM_LABEL = Regex("\\btotal\\b|\\bamount\\b|\\bpaid\\b|\\bdebited\\b|\\bcharged\\b|\\bpayable\\b|\\brefund(?:ed)?\\b|\\bcredited\\b")
    private val NOT_TOTAL = Regex(
        "sub ?total|item total|items total|delivery|shipping|handling|\\btax|\\bgst\\b|discount|saving|saved|coupon|cashback|\\bmrp\\b|\\bfee\\b|\\btip\\b|" +
            "points|you save|\\boff\\b|packing|convenience|platform|surge|promo|\\bbalance\\b|available|limit|reward",
    )
    private val STRONG_MAIL = Regex(
        "order (?:confirmed|confirmation|placed|summary|receipt|invoice)|your order|\\breceipt\\b|\\binvoice\\b|payment (?:successful|received|confirmation|of)|" +
            "thank you for (?:your )?(?:order|payment|purchase|ordering)|\\bpaid\\b|\\bdebited\\b|e-?ticket|booking (?:confirmed|confirmation)|" +
            "(?:trip|ride) (?:receipt|summary)|\\bsubscription\\b|\\brenewed\\b|bill (?:paid|payment)|\\btransaction\\b|\\brefund",
    )
    private val CANCELLED = Regex("cancel|failed|unsuccessful|declined|could not be processed|not (?:be )?processed|payment pending|unable to process")
    private val REFUND = Regex("refund|money back|reversed")
    private val PROMO = Regex("\\boffers?\\b|\\bsale\\b|\\bdeals?\\b|coupon|% ?off|\\d+% |discount|hurry|last chance|flat \\d+|win |cashback up|save up|limited time|price drop|recommended for you")
    private val RECEIPT_SUBJECT = Regex("receipt|invoice|order confirmed|order placed|payment successful|your order|thank you for|booking confirmed|trip|ride|renewed|debited|paid")
    private val AFTER_WORD = Regex("^(?:paid|debited|charged|credited|refunded)\\b")
    private val ORDER_ID = Regex("(?i)(?:order|booking|trip|ride|invoice|transaction|txn|reference|ref)\\s*(?:id|no\\.?|number|#)?\\s*[:#]?\\s*([A-Z0-9][A-Z0-9-]{5,29})")

    class Hit(val amount: Double, val priority: Int, val currency: String, val line: Int, val pos: Int)

    private fun currencyOf(symbol: String): String = when (symbol.lowercase()) {
        "₹", "rs", "rs.", "inr" -> "INR"
        "$", "usd" -> "USD"
        "€", "eur" -> "EUR"
        "£", "gbp" -> "GBP"
        else -> ""
    }

    private fun priority(context: String): Int {
        val ctx = context.lowercase()
        if (STRONG_LABEL.containsMatchIn(ctx)) return 100
        if (NOT_TOTAL.containsMatchIn(ctx)) return 0
        if (MEDIUM_LABEL.containsMatchIn(ctx)) return 80
        return 0
    }

    /** Every amount in the text with how much its label says "this is the total". Exposed for tests. */
    fun hits(text: String): List<Hit> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<Hit>()
        var pos = 0
        for ((i, line) in lines.withIndex()) {
            for (m in CURRENCY_AMOUNT.findAll(line)) {
                val symbol = m.groupValues[1]
                val number = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
                if (number <= 0.0 || number > 10_000_000.0) continue
                val before = line.substring(0, m.range.first)
                val after = line.substring(m.range.last + 1).take(10).trimStart().lowercase()
                // A label alone on the line above (a table row) counts as this amount's label.
                val label = if (before.any { it.isLetter() }) before.takeLast(48) else (lines.getOrNull(i - 1).orEmpty().takeLast(48) + " " + before)
                var p = priority(label)
                if (p == 0 && AFTER_WORD.containsMatchIn(after)) p = 80
                if (symbol.isEmpty() && p < 100) continue
                out += Hit(number, p, currencyOf(symbol).ifEmpty { "INR" }, i, pos++)
            }
        }
        return out
    }

    /** The best total: the strongest label, and of equal ones the last (totals come at the bottom). */
    fun bestTotal(text: String): Hit? =
        hits(text).filter { it.priority > 0 }.maxWithOrNull(compareBy<Hit>({ it.priority }, { it.pos }))

    fun orderId(text: String): String? =
        ORDER_ID.findAll(text).map { it.groupValues[1] }.firstOrNull { id -> id.any { it.isDigit() } }

    fun judge(doc: MailDoc): MailVerdict {
        val subject = doc.subject.lowercase()
        val body = doc.text.lowercase()
        val known = Merchants.known(doc.from)
        val refund = REFUND.containsMatchIn(subject)
        if (!refund && (CANCELLED.containsMatchIn(subject))) return MailVerdict.Skip
        val strong = STRONG_MAIL.containsMatchIn(subject) || STRONG_MAIL.containsMatchIn(body.take(4000))
        if (PROMO.containsMatchIn(subject) && !RECEIPT_SUBJECT.containsMatchIn(subject)) return MailVerdict.Skip
        if (!strong && known == null) return MailVerdict.Skip

        val merchant = Merchants.nameOf(doc.from)
        val category = known?.categoryId ?: "x-other"
        val kind = if (refund) KIND_INCOME else KIND_EXPENSE
        val best = bestTotal(doc.text)
        val id = orderId(doc.subject + "\n" + doc.text)
        if (best == null) {
            return if (strong) MailVerdict.Unsure("no total found", null) else MailVerdict.Skip
        }
        val purchase = Purchase(merchant, best.amount, kind, if (kind == KIND_INCOME) "i-other" else category, id, doc.dateMs, doc.subject)
        if (best.currency != "INR") return MailVerdict.Unsure("amount is in ${best.currency}", purchase)
        if (best.priority >= 100 || (strong && best.priority >= 80)) return MailVerdict.Found(purchase)
        return MailVerdict.Unsure("not sure which amount is the total", purchase)
    }

    /** Are these two the same purchase (an order's "confirmed" and "delivered" emails, a receipt and its invoice)? */
    fun samePurchase(a: Purchase, b: Purchase): Boolean {
        if (a.merchant != b.merchant || a.kind != b.kind) return false
        if (a.orderId != null && b.orderId != null) return a.orderId == b.orderId
        return Math.abs(a.amount - b.amount) < 0.005 && Math.abs(a.atMs - b.atMs) <= SAME_WINDOW_MS
    }

    const val SAME_WINDOW_MS = 6 * 60 * 60 * 1000L
}
