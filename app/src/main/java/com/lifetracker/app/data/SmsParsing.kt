package com.lifetracker.app.data

import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex

/** One text message: who sent it ("AX-HDFCBK-S"), what it said and when. [id] is a fingerprint, so the same message is never read twice. */
class SmsDoc(val id: String, val sender: String, val body: String, val dateMs: Long)

/** Where spending noted in two places (a bank text and a shop's receipt) is recognised as the same. No Android in it, so it is tested. */
object MoneyMerge {
    /** Gmail and SMS reading both write to Money; this keeps them from checking and adding at the same moment. */
    val lock = Mutex()

    private val GENERIC = setOf("upipayment", "cardpayment", "bankpayment", "moneyreceived", "atmcash", "refund")

    private fun norm(s: String): String = buildString { for (c in s.lowercase()) if (c.isLetterOrDigit()) append(c) }

    /** The same shop, or one side too vague to say (a text that only says "UPI payment"). */
    fun compatible(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty() || x in GENERIC || y in GENERIC) return true
        return x.contains(y) || y.contains(x)
    }

    fun sameSpend(aName: String, aAmount: Double, aKind: Int, bName: String, bAmount: Double, bKind: Int): Boolean =
        aKind == bKind && abs(aAmount - bAmount) < 0.005 && compatible(aName, bName)
}

/**
 * Reads the texts banks, cards and UPI apps send ("Rs.471 debited ... to ZOMATO") and works out what was paid, to whom and for how much.
 * It leaves out one-time codes, offers, failed and upcoming payments, card bill payments and balance messages, and says nothing rather than guess.
 * No Android in it, so it is tested.
 */
object SmsPurchases {
    private val AMOUNT = Regex("(?<![a-z])(?:rs\\.?|inr|₹)\\s*:?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val BARE_AMOUNT = Regex(
        "(?:debited|credited|spent|paid|sent|withdrawn|received|purchase of|payment of)\\s*(?:by|for|of|with|:)?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE,
    )
    /** An amount that is a balance or a limit, not the payment: the words just before it say so. */
    private val BALANCE_BEFORE = Regex("(?:avl|avail\\w*|bal\\w*|limit|outstanding|due)[^0-9]{0,12}$", RegexOption.IGNORE_CASE)

    private val DONE = Regex("debited|credited|spent|withdrawn|\\bpaid\\b|received|purchase|\\bsent\\b|deducted|charged")
    private val OTP = Regex("\\botp\\b|one.?time password|verification code|\\bcvv\\b|\\bpin\\b is")
    private val FUTURE = Regex(
        "will be (?:debited|charged|deducted)|to be debited|is due|\\bdue (?:on|by|date)|reminder|requested|request from|collect request|" +
            "please pay|pls pay|pay now|mandate.{0,30}(?:created|registered|set ?up|request|approve|revoked)|standing instruction|scheduled|" +
            "statement|bill (?:generated|of)|minimum due",
    )
    private val FAIL = Regex("failed|declined|unsuccessful|not successful|could not|unable to|insufficient|rejected|blocked|cancel(?:l)?ed|not (?:be )?processed")
    private val REFUND = Regex("refund|reversal|reversed|chargeback")
    private val CARD_BILL = Regex("payment.{0,40}received.{0,40}(?:credit card|card|loan|emi)|thank you for (?:your )?payment")
    private val PROMO = Regex("\\boffer\\b|\\bwin\\b|apply now|pre-?approved|click|\\bloan\\b|t&c|reward points|get up to|upto|limited period|congratulations|download the app|\\bemi\\b")
    private val PROMO_SENDER = Regex("-P$", RegexOption.IGNORE_CASE)
    private val DEBIT = Regex("debited|spent|\\bpaid\\b|withdrawn|purchase|charged|deducted|\\bsent\\b|transferred|payment of|txn of|transaction of")
    private val CREDIT = Regex("credited|received|deposited|refund|reversed|reversal")
    private val SALARY = Regex("salary|\\bsal credit|\\bsal\\b")

    private const val NAME = "[A-Za-z0-9][A-Za-z0-9&.' *\\-]{1,38}?"
    private const val STOP = "(?=\\s+(?:on|via|using|with|ref|refno|utr|upi|avl|avbl|bal|if|not|from|vpa|limit)\\b|\\s+\\d{1,2}[-/]|\\s*[.;,(@]|\\s*$)"
    private val VPA_NAME = Regex("(?i)\\bvpa\\s+[\\w.\\-]+@\\w+\\s*\\(([^)]{2,40})\\)")
    private val VPA = Regex("(?i)\\b(?:vpa|upi id)\\s+([\\w.\\-]+)@\\w+")
    private val UPI_SLASH = Regex(
        "(?i)UPI/(?:P2M|P2A|P2P|CR|DR)?/?\\d{6,}/([A-Za-z][A-Za-z0-9 &.\\-]{1,38}?)(?=\\s+(?:sms|block|if|not|to|call|avl|bal)\\b|\\s*[,;]|\\s*$)",
    )
    private val AT = Regex("(?i)\\bat\\s+($NAME)$STOP")
    private val TO = Regex("(?i)\\b(?:trf to|transfer to|paid to|sent to|to)\\s+(?!your\\b|a/c|account|vpa\\b|card|bank|self\\b|my\\b|the\\b)($NAME)$STOP")
    private val SEMI = Regex("(?i);\\s*([A-Za-z0-9][A-Za-z0-9&.' \\-]{1,38}?)\\s+credited")
    private val FROM = Regex("(?i)\\b(?:from|by)\\s+(?!a/c|account|your\\b|card|bank|vpa\\b)($NAME)$STOP")

    private val REF = listOf(
        Regex("(?i)(?:upi\\s*ref(?:erence)?(?:\\s*no\\.?)?|ref(?:\\s*no\\.?)?|refno|utr|rrn|txn\\s*id|transaction\\s*id)\\s*[:\\-#]?\\s*(\\d{8,16})"),
        Regex("(?i)UPI/(?:P2M|P2A|P2P|CR|DR)?/?(\\d{9,16})"),
        Regex("\\b(\\d{12})\\b"),
    )

    private val REJECT = setOf("upi", "neft", "imps", "rtgs", "atm", "pos", "ecom", "card", "bank", "vpa", "ac", "acct", "account", "self", "you", "your", "info", "txn", "sms", "ref", "avl", "bal")
    private val SUFFIX = Regex("(?i)\\s+(?:pvt|private|ltd|limited|india|online|technologies|services|payments?)\\.?$")

    /** Tidies a name found in the text. Null when it is not a name (a number, a masked account, a word like UPI). */
    fun cleanName(raw: String): String? {
        var s = raw.trim().trim('.', ',', ';', '-', '*', ' ').replace(Regex("\\s+"), " ")
        repeat(4) { s = SUFFIX.replace(s, "").trim() }
        if (s.length < 2) return null
        if (s.count { it.isDigit() } >= 6) return null
        if (!s.any { it.isLetter() }) return null
        if (Regex("(?i)x{2,}\\d").containsMatchIn(s) || Regex("(?i)^x+$").matches(s)) return null
        if (s.lowercase() in REJECT) return null
        val tidy = if (s == s.uppercase() || s == s.lowercase()) s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } else s
        return tidy.take(40)
    }

    private fun nameIn(body: String, order: List<Regex>): String? {
        for (r in order) {
            for (m in r.findAll(body)) cleanName(m.groupValues[1])?.let { return it }
        }
        return null
    }

    fun reference(body: String): String? {
        for (r in REF) r.find(body)?.groupValues?.get(1)?.let { return it }
        return null
    }

    /** The amount paid: the first one that is not a balance or a limit. */
    fun amountOf(body: String): Double? {
        for (m in AMOUNT.findAll(body)) {
            val before = body.substring((m.range.first - 22).coerceAtLeast(0), m.range.first)
            if (BALANCE_BEFORE.containsMatchIn(before)) continue
            val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (v > 0.0 && v <= 10_000_000.0) return v
        }
        val bare = BARE_AMOUNT.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        return bare?.takeIf { it > 0.0 && it <= 10_000_000.0 }
    }

    /** The text with long numbers hidden, to keep in the note beside the entry. */
    fun snippet(body: String): String = body.replace(Regex("\\d{5,}"), "…").replace(Regex("\\s+"), " ").trim().take(80)

    fun judge(doc: SmsDoc, includeIncome: Boolean): MailVerdict {
        val body = doc.body
        val lower = body.lowercase()
        val hasDone = DONE.containsMatchIn(lower)
        if (OTP.containsMatchIn(lower) && !hasDone) return MailVerdict.Skip
        if (FUTURE.containsMatchIn(lower)) return MailVerdict.Skip
        val refundCredit = REFUND.containsMatchIn(lower) && lower.contains("credited")
        if (!refundCredit && FAIL.containsMatchIn(lower)) return MailVerdict.Skip
        if (CARD_BILL.containsMatchIn(lower)) return MailVerdict.Skip
        if (PROMO_SENDER.containsMatchIn(doc.sender.trim())) return MailVerdict.Skip
        if (!hasDone && PROMO.containsMatchIn(lower)) return MailVerdict.Skip
        if (!hasDone) return MailVerdict.Skip

        val d = DEBIT.find(lower)?.range?.first
        val c = CREDIT.find(lower)?.range?.first
        val income = when {
            refundCredit -> true
            d != null && (c == null || d < c) -> false
            c != null -> true
            else -> return MailVerdict.Skip
        }
        if (income && !refundCredit && !includeIncome) return MailVerdict.Skip

        val amount = amountOf(body) ?: return MailVerdict.Skip
        val kind = if (income) KIND_INCOME else KIND_EXPENSE
        val raw = if (income) nameIn(body, listOf(VPA_NAME, FROM, VPA, UPI_SLASH)) else nameIn(body, listOf(VPA_NAME, UPI_SLASH, AT, TO, SEMI, VPA))
        val known = raw?.let { Merchants.byText(it) } ?: if (raw == null && refundCredit) Merchants.byText(body) else null
        val atm = !income && Regex("\\batm\\b|cash withdrawal|\\bwithdrawn\\b").containsMatchIn(lower)
        val merchant = when {
            known != null -> known.name
            atm -> "ATM cash"
            raw != null -> raw
            income && refundCredit -> "Refund"
            income -> "Money received"
            "upi" in lower -> "UPI payment"
            "card" in lower -> "Card payment"
            else -> "Bank payment"
        }
        val salary = income && !refundCredit && SALARY.containsMatchIn(lower)
        val category = when {
            salary -> "i-salary"
            income -> "i-other"
            else -> known?.categoryId ?: "x-other"
        }
        return MailVerdict.Found(Purchase(merchant, amount, kind, category, reference(body), doc.dateMs, snippet(body)))
    }

    const val SAME_WINDOW_MS = 3 * 60_000L

    /** A bank often sends the same alert twice. With a reference number it is the number that decides; without, the same amount within minutes. */
    fun sameText(a: Purchase, b: Purchase): Boolean {
        if (a.kind != b.kind || abs(a.amount - b.amount) >= 0.005) return false
        if (a.orderId != null && b.orderId != null) return a.orderId == b.orderId
        return abs(a.atMs - b.atMs) <= SAME_WINDOW_MS
    }
}
