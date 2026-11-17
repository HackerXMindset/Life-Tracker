package com.lifetracker.app.data

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MboxTest {
    private fun readAll(text: String, max: Int = 2_000_000): List<String> {
        val reader = MboxReader(ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1)), max)
        val out = ArrayList<String>()
        while (true) out += String(reader.next() ?: break, Charsets.ISO_8859_1)
        return out
    }

    @Test
    fun anMboxIsSplitAtFromLinesAfterABlankLine() {
        val file = "From 111@xxx Thu Nov 14 10:22:33 +0000 2024\nSubject: one\n\nHello\nFrom the team\n\nFrom 222@xxx Thu Nov 14 11:00:00 +0000 2024\nSubject: two\n\nBye\n"
        val messages = readAll(file)
        assertEquals(2, messages.size)
        assertTrue(messages[0].contains("Subject: one"))
        assertTrue(messages[0].contains("From the team"))
        assertFalse(messages[0].contains("Subject: two"))
        assertTrue(messages[1].contains("Subject: two"))
    }

    @Test
    fun aFromLineInsideTextWithoutABlankLineBeforeItIsNotASplit() {
        val messages = readAll("From 111@xxx Thu Nov 14 10:22:33 +0000 2024\nSubject: one\n\nLine\nFrom me to you\nmore\n")
        assertEquals(1, messages.size)
        assertTrue(messages[0].contains("From me to you"))
    }

    @Test
    fun aHugeEmailKeepsOnlyItsStart() {
        val big = "x".repeat(300) + "\n"
        val file = "From 1@x Thu Nov 14 10:22:33 +0000 2024\nSubject: big\n\n" + big.repeat(50) + "\nFrom 2@x Thu Nov 14 10:22:33 +0000 2024\nSubject: next\n\nok\n"
        val messages = readAll(file, max = 2000)
        assertEquals(2, messages.size)
        assertTrue(messages[0].length < 2500)
        assertTrue(messages[1].contains("Subject: next"))
    }

    @Test
    fun anEmptyFileHasNoEmails() {
        assertTrue(readAll("").isEmpty())
    }

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    private val html = "<table><tr><td>Grand Total</td><td>₹471.00</td></tr></table>"

    private fun qpHtml(): String = "<table><tr><td>Grand Total</td><td>=E2=82=B9471.00</td></tr></table>=\n<p>Order ID: 1234567890</p>"

    @Test
    fun aMultipartEmailWithQuotedPrintableHtmlIsRead() {
        val raw = "From: =?UTF-8?B?${b64("Zomato")}?= <noreply@zomato.com>\r\n" +
            "Subject: =?UTF-8?B?${b64("Your order is confirmed")}?=\r\n" +
            "Date: Thu, 14 Nov 2024 10:22:33 +0530\r\n" +
            "Message-ID: <abc@mail.zomato.com>\r\n" +
            "X-Gmail-Labels: Inbox,Category Updates\r\n" +
            "Content-Type: multipart/alternative; boundary=\"BOUND\"\r\n\r\n" +
            "--BOUND\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n" +
            qpHtml().replace("\n", "\r\n") + "\r\n--BOUND--\r\n"
        val d = MimeParser.parse(raw.toByteArray(Charsets.ISO_8859_1))
        assertNotNull(d)
        assertEquals("Zomato <noreply@zomato.com>", d!!.from)
        assertEquals("Your order is confirmed", d.subject)
        assertEquals("abc@mail.zomato.com", d.id)
        assertEquals(Instant.parse("2024-11-14T04:52:33Z").toEpochMilli(), d.dateMs)
        assertTrue(d.text.contains("₹471.00"))
        val v = MailPurchases.judge(d)
        assertTrue(v is MailVerdict.Found)
        assertEquals(471.0, (v as MailVerdict.Found).purchase.amount, 0.001)
        assertEquals("1234567890", v.purchase.orderId)
    }

    @Test
    fun aBase64PlainEmailAndALatin1Subject() {
        val body = "Thank you for your order.\nGrand Total ₹299.00\n" + "pad ".repeat(30)
        val raw = "From: Shop <orders@someshop.com>\nSubject: =?ISO-8859-1?Q?Caf=E9_order?=\nDate: 14 Nov 2024 10:22:33 +0000\n" +
            "Content-Type: text/plain; charset=utf-8\nContent-Transfer-Encoding: base64\n\n" + b64(body) + "\n"
        val d = MimeParser.parse(raw.toByteArray(Charsets.ISO_8859_1))!!
        assertEquals("Café order", d.subject)
        assertTrue(d.text.contains("Grand Total ₹299.00"))
        assertEquals(Instant.parse("2024-11-14T10:22:33Z").toEpochMilli(), d.dateMs)
    }

    @Test
    fun attachmentsAreNotRead() {
        val raw = "From: a@b.com\nSubject: s\nDate: Thu, 14 Nov 2024 10:22:33 +0000\nContent-Type: multipart/mixed; boundary=B\n\n" +
            "--B\nContent-Type: text/plain\n\nBody text\n--B\nContent-Type: text/plain; name=a.txt\nContent-Disposition: attachment; filename=a.txt\n\nSECRET\n--B--\n"
        val d = MimeParser.parse(raw.toByteArray())!!
        assertTrue(d.text.contains("Body text"))
        assertFalse(d.text.contains("SECRET"))
    }

    @Test
    fun anUnreadableDateOrAnUnwantedEmailGivesNothing() {
        val raw = "From: a@b.com\nSubject: s\nDate: not a date\n\nhi\n"
        assertNull(MimeParser.parse(raw.toByteArray()))
        val ok = "From: a@b.com\nSubject: s\nDate: Thu, 14 Nov 2024 10:22:33 +0000\n\nhi\n"
        assertNull(MimeParser.parse(ok.toByteArray()) { _, _, _ -> false })
    }

    @Test
    fun aWrongWeekdayIsStillADate() {
        assertEquals(Instant.parse("2024-11-14T10:22:33Z").toEpochMilli(), MimeParser.parseDate("Mon, 14 Nov 2024 10:22:33 +0000 (UTC)"))
    }

    @Test
    fun quotedPrintableDecodes() {
        assertEquals("₹471 = ok", String(MimeParser.qp("=E2=82=B9471 =3D ok".toByteArray()), Charsets.UTF_8))
        assertEquals("joined", String(MimeParser.qp("join=\ned".toByteArray())))
    }

    @Test
    fun theFilterKeepsMoneyMailAndDropsTheRest() {
        val none = emptySet<String>()
        assertTrue(TakeoutFilter.wanted("Inbox", "Zomato <a@zomato.com>", "Hello", none))
        assertTrue(TakeoutFilter.wanted("Inbox", "Someone <a@b.com>", "Your order is confirmed", none))
        assertTrue(TakeoutFilter.wanted("Category Purchases", "Someone <a@b.com>", "Hello", none))
        assertFalse(TakeoutFilter.wanted("Spam", "Someone <a@b.com>", "Your order", none))
        assertFalse(TakeoutFilter.wanted("Trash,Inbox", "Zomato <a@zomato.com>", "Your order", none))
        assertFalse(TakeoutFilter.wanted("Inbox,Category Promotions", "Zomato <a@zomato.com>", "Hello", none))
        assertFalse(TakeoutFilter.wanted("Inbox", "Someone <a@b.com>", "Lunch tomorrow?", none))
        assertFalse(TakeoutFilter.wanted("Inbox", "Zomato <a@zomato.com>", "Your order", setOf("zomato.com")))
    }

    @Test
    fun sameOrderUsesTheOrderNumberWhenBothHaveOne() {
        val p = Purchase("Swiggy", 49.0, KIND_EXPENSE, "x-food", "ORD111111", 0L, "")
        fun entry(note: String) = MoneyEntryEntity("mbox-1", "2024-11-14", KIND_EXPENSE, "x-food", "Swiggy", 49.0, note, "")
        assertTrue(TakeoutImport.sameOrder(p, entry("Takeout · s · #ORD111111")))
        assertFalse(TakeoutImport.sameOrder(p, entry("Takeout · s · #ORD222222")))
        assertTrue(TakeoutImport.sameOrder(p, entry("Gmail · s")))
        assertFalse(TakeoutImport.sameOrder(p, MoneyEntryEntity("x", "d", KIND_EXPENSE, "c", "Swiggy", 50.0, "", "")))
    }
}
