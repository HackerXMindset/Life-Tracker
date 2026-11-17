package com.lifetracker.app.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

fun sha16(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

/**
 * Reads a mailbox file (what Google Takeout gives for Gmail) one email at a time, without holding the file in memory.
 * An email starts at a line beginning "From " that follows a blank line. Only the first [maxMessage] bytes of each email are kept
 * (the text comes first; attachments can be huge). No Android in it, so it is tested.
 */
class MboxReader(private val input: InputStream, private val maxMessage: Int = 2_000_000) {
    private val buf = ByteArray(1 shl 16)
    private var len = 0
    private var pos = 0
    private var eof = false
    private val line = ByteArrayOutputStream()
    private var prevBlank = true
    private var pendingStart = false

    /** The next email's bytes (headers and body, without the "From " line), or null at the end. */
    fun next(): ByteArray? {
        val current = ByteArrayOutputStream()
        var have = pendingStart
        pendingStart = false
        while (true) {
            val l = readLine() ?: break
            val sep = prevBlank && startsWithFrom(l)
            prevBlank = isBlank(l)
            if (sep) {
                if (have) {
                    pendingStart = true
                    return current.toByteArray()
                }
                have = true
                continue
            }
            have = true
            if (current.size() < maxMessage) current.write(l)
        }
        return if (have && current.size() > 0) current.toByteArray() else null
    }

    private fun startsWithFrom(l: ByteArray): Boolean =
        l.size >= 6 && l[0] == 'F'.code.toByte() && l[1] == 'r'.code.toByte() && l[2] == 'o'.code.toByte() && l[3] == 'm'.code.toByte() && l[4] == ' '.code.toByte()

    private fun isBlank(l: ByteArray): Boolean =
        (l.size == 1 && l[0] == '\n'.code.toByte()) || (l.size == 2 && l[0] == '\r'.code.toByte() && l[1] == '\n'.code.toByte())

    private fun readLine(): ByteArray? {
        line.reset()
        var any = false
        while (true) {
            if (pos >= len) {
                if (eof) break
                len = input.read(buf)
                pos = 0
                if (len <= 0) {
                    eof = true
                    len = 0
                    break
                }
            }
            var i = pos
            while (i < len && buf[i] != '\n'.code.toByte()) i++
            val end = if (i < len) i + 1 else len
            if (line.size() < maxMessage) line.write(buf, pos, end - pos)
            any = true
            pos = end
            if (i < len) return line.toByteArray()
        }
        return if (any) line.toByteArray() else null
    }
}

/** Takes one email's raw text apart: headers, encoded words, quoted-printable and base64 parts, and the text of the body. No Android in it, so it is tested. */
object MimeParser {
    private const val MAX_TEXT = 30_000
    private val LATIN1 = Charsets.ISO_8859_1

    class Headers(private val list: List<Pair<String, String>>) {
        operator fun get(name: String): String = list.firstOrNull { it.first == name.lowercase() }?.second.orEmpty()
    }

    private fun bodyStart(raw: ByteArray): Int {
        for (i in 0 until raw.size - 1) {
            if (raw[i] == '\n'.code.toByte()) {
                if (raw[i + 1] == '\n'.code.toByte()) return i + 2
                if (i + 2 < raw.size && raw[i + 1] == '\r'.code.toByte() && raw[i + 2] == '\n'.code.toByte()) return i + 3
            }
        }
        return raw.size
    }

    private fun headersOf(raw: ByteArray, end: Int): Headers {
        val text = String(raw, 0, end.coerceAtMost(raw.size), LATIN1).replace(Regex("\r?\n[ \t]+"), " ")
        val list = ArrayList<Pair<String, String>>()
        for (l in text.lines()) {
            val c = l.indexOf(':')
            if (c <= 0) continue
            list += l.substring(0, c).trim().lowercase() to l.substring(c + 1).trim()
        }
        return Headers(list)
    }

    private fun charsetOf(name: String?): Charset =
        if (name.isNullOrBlank()) Charsets.UTF_8 else runCatching { Charset.forName(name.trim('"', ' ')) }.getOrDefault(Charsets.UTF_8)

    private fun param(header: String, name: String): String? =
        Regex("(?i)\\b$name\\s*=\\s*(\"([^\"]*)\"|[^;\\s]+)").find(header)?.let { it.groupValues[2].ifEmpty { it.groupValues[1].trim('"') } }

    /** "=?UTF-8?B?...?=" and "=?ISO-8859-1?Q?...?=" in a header turned into text. */
    fun decodeWords(s: String): String {
        val joined = s.replace(Regex("\\?=\\s+=\\?"), "?==?")
        return Regex("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=").replace(joined) { m ->
            val cs = charsetOf(m.groupValues[1].substringBefore('*'))
            val data = m.groupValues[3]
            val bytes = if (m.groupValues[2].equals("b", true)) {
                runCatching { Base64.getMimeDecoder().decode(data) }.getOrDefault(ByteArray(0))
            } else {
                qp(data.replace('_', ' ').toByteArray(LATIN1))
            }
            String(bytes, cs)
        }
    }

    /** Quoted-printable: "=E2=82=B9" is three bytes, and "=" at the end of a line only joins it to the next. */
    fun qp(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size)
        var i = 0
        fun hex(b: Byte): Int = Character.digit(b.toInt().toChar(), 16)
        while (i < input.size) {
            val b = input[i]
            if (b == '='.code.toByte()) {
                if (i + 1 < input.size && input[i + 1] == '\n'.code.toByte()) { i += 2; continue }
                if (i + 2 < input.size && input[i + 1] == '\r'.code.toByte() && input[i + 2] == '\n'.code.toByte()) { i += 3; continue }
                if (i + 2 < input.size) {
                    val h = hex(input[i + 1])
                    val l = hex(input[i + 2])
                    if (h >= 0 && l >= 0) { out.write(h * 16 + l); i += 3; continue }
                }
            }
            out.write(b.toInt())
            i++
        }
        return out.toByteArray()
    }

    /** An email's Date header as milliseconds, or null when it cannot be read. */
    fun parseDate(value: String): Long? {
        val s = value.replace(Regex("\\([^)]*\\)"), "").trim()
        val formats = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm Z", Locale.ENGLISH),
        )
        // A weekday that does not match the date makes the strict reader refuse it, so it is tried without the weekday too.
        val candidates = listOf(s, s.replace(Regex("^[A-Za-z]{3},\\s*"), ""))
        for (c in candidates) for (f in formats) {
            val t = runCatching { ZonedDateTime.parse(c, f).toInstant().toEpochMilli() }.getOrNull()
            if (t != null) return t
        }
        return null
    }

    private fun decodeBody(headers: Headers, body: ByteArray): String {
        val cte = headers["content-transfer-encoding"].lowercase().trim()
        val charset = charsetOf(param(headers["content-type"], "charset"))
        val bytes = when (cte) {
            "base64" -> runCatching { Base64.getMimeDecoder().decode(body) }.getOrDefault(ByteArray(0))
            "quoted-printable" -> qp(body)
            else -> body
        }
        return String(bytes, charset)
    }

    private fun collect(headers: Headers, body: ByteArray, plain: StringBuilder, html: StringBuilder, depth: Int) {
        if (depth > 6) return
        val type = headers["content-type"]
        val mime = type.substringBefore(';').trim().lowercase().ifEmpty { "text/plain" }
        val disposition = headers["content-disposition"].lowercase()
        if (disposition.startsWith("attachment")) return
        when {
            mime.startsWith("multipart/") -> {
                val boundary = param(type, "boundary") ?: return
                val text = String(body, LATIN1)
                val marker = "--$boundary"
                var from = -1
                var i = 0
                while (i < text.length) {
                    val at = text.indexOf(marker, i)
                    if (at < 0) break
                    if (at != 0 && text[at - 1] != '\n') { i = at + marker.length; continue }
                    if (from >= 0) {
                        val part = text.substring(from, at).toByteArray(LATIN1)
                        val start = bodyStart(part)
                        collect(headersOf(part, start), part.copyOfRange(start.coerceAtMost(part.size), part.size), plain, html, depth + 1)
                    }
                    val after = at + marker.length
                    if (text.startsWith("--", after)) break
                    val nl = text.indexOf('\n', after)
                    if (nl < 0) break
                    from = nl + 1
                    i = from
                }
            }
            mime == "text/plain" -> plain.append(decodeBody(headers, body)).append('\n')
            mime == "text/html" -> html.append(decodeBody(headers, body)).append('\n')
        }
    }

    /**
     * The email as a [MailDoc], or null when it has no readable date or [wanted] (given the sender, subject and Gmail labels)
     * says it is not worth reading. [wanted] is asked before the body is decoded, which is where the time goes.
     */
    fun parse(raw: ByteArray, wanted: (String, String, String) -> Boolean = { _, _, _ -> true }): MailDoc? {
        val start = bodyStart(raw)
        val headers = headersOf(raw, start)
        val from = decodeWords(headers["from"])
        val subject = decodeWords(headers["subject"])
        if (!wanted(from, subject, headers["x-gmail-labels"])) return null
        val date = parseDate(headers["date"]) ?: return null
        val plain = StringBuilder()
        val html = StringBuilder()
        collect(headers, raw.copyOfRange(start.coerceAtMost(raw.size), raw.size), plain, html, 0)
        val body = if (plain.trim().length >= 80 || html.isEmpty()) plain.toString() else GmailParsers.htmlToText(html.toString())
        val messageId = headers["message-id"].trim().trim('<', '>').ifEmpty { sha16("$from|$subject|$date") }
        return MailDoc(messageId, from, subject, date, body.take(MAX_TEXT))
    }
}

/** Which emails in a mailbox file are worth reading: the same idea as the Gmail search, using the labels Takeout writes in each email. */
object TakeoutFilter {
    private val SUBJECT = Regex(
        "order|receipt|invoice|payment|paid|booking|ticket|subscription|renewal|refund|debited|bill|thank you for|trip|ride|purchase|confirmed|transaction",
        RegexOption.IGNORE_CASE,
    )

    fun wanted(labels: String, from: String, subject: String, ignored: Set<String>): Boolean {
        val set = labels.split(',').map { it.trim().lowercase() }.toSet()
        if ("spam" in set || "trash" in set) return false
        val domain = Merchants.domainOf(from)
        if (domain.isNotEmpty() && ignored.any { domain == it || domain.endsWith(".$it") }) return false
        if ("category purchases" in set) return true
        if (SUBJECT.containsMatchIn(subject)) return true
        return Merchants.known(from) != null && "category promotions" !in set
    }
}
