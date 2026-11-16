package com.lifetracker.app.data

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailParsingTest {
    private fun doc(from: String, subject: String, text: String, at: Long = 1_700_000_000_000L) = MailDoc("m1", from, subject, at, text)

    private fun found(v: MailVerdict): Purchase {
        assertTrue("expected Found but was $v", v is MailVerdict.Found)
        return (v as MailVerdict.Found).purchase
    }

    @Test
    fun foodOrderTakesTheGrandTotalNotTheParts() {
        val text = "Order confirmed\nItem total ₹420.00\nDelivery fee ₹30.00\nTaxes ₹21.00\nGrand Total ₹471.00\nOrder ID: 1234567890"
        val p = found(MailPurchases.judge(doc("Zomato <noreply@zomato.com>", "Your order is confirmed", text)))
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Zomato", p.merchant)
        assertEquals("x-food", p.categoryId)
        assertEquals(KIND_EXPENSE, p.kind)
        assertEquals("1234567890", p.orderId)
    }

    @Test
    fun subtotalIsNeverTheTotal() {
        val text = "Subtotal ₹500\nDelivery ₹40\nTotal ₹540"
        assertEquals(540.0, MailPurchases.bestTotal(text)!!.amount, 0.001)
        val p = found(MailPurchases.judge(doc("Amazon <auto-confirm@amazon.in>", "Your Amazon.in order", text)))
        assertEquals(540.0, p.amount, 0.001)
        assertEquals("x-shopping", p.categoryId)
    }

    @Test
    fun aLabelAboveItsAmountInATableStillCounts() {
        val best = MailPurchases.bestTotal("Payment details\nAmount paid\n₹ 299.00")
        assertNotNull(best)
        assertEquals(299.0, best!!.amount, 0.001)
    }

    @Test
    fun aBareNumberWithoutASymbolOrLabelIsIgnored() {
        assertNull(MailPurchases.bestTotal("Your order 1234567 will arrive in 2 days. Quantity 3"))
    }

    @Test
    fun offersAreSkipped() {
        val v = MailPurchases.judge(doc("Amazon <deals@amazon.in>", "Big Sale: 50% off today", "Prices from ₹999. Total savings ₹2,000"))
        assertTrue(v is MailVerdict.Skip)
    }

    @Test
    fun cancelledOrdersAreSkipped() {
        val v = MailPurchases.judge(doc("Swiggy <noreply@swiggy.in>", "Your order has been cancelled", "Grand Total ₹250.00"))
        assertTrue(v is MailVerdict.Skip)
    }

    @Test
    fun aRefundIsMoneyIn() {
        val p = found(MailPurchases.judge(doc("Flipkart <noreply@flipkart.com>", "Refund processed for your order", "Refund amount ₹250.00")))
        assertEquals(250.0, p.amount, 0.001)
        assertEquals(KIND_INCOME, p.kind)
        assertEquals("i-other", p.categoryId)
    }

    @Test
    fun anotherCurrencyIsLeftForYouToCheck() {
        val v = MailPurchases.judge(doc("Spotify <no-reply@spotify.com>", "Your receipt", "Order total $12.99"))
        assertTrue(v is MailVerdict.Unsure)
        assertTrue((v as MailVerdict.Unsure).reason.contains("USD"))
    }

    @Test
    fun aReceiptWithNoTotalIsUnsure() {
        val v = MailPurchases.judge(doc("Shop <orders@someshop.com>", "Order confirmed", "Thank you for your order. We will deliver soon."))
        assertTrue(v is MailVerdict.Unsure)
        assertNull((v as MailVerdict.Unsure).candidate)
    }

    @Test
    fun anOrdinaryMailAboutNothingIsSkipped() {
        val v = MailPurchases.judge(doc("Sam <sam@gmail.com>", "Hello", "Let's meet for lunch tomorrow. I will bring ₹500."))
        assertTrue(v is MailVerdict.Skip)
    }

    @Test
    fun theSamePurchaseInTwoMailsIsOne() {
        val a = Purchase("Zomato", 471.0, KIND_EXPENSE, "x-food", "ORD123456", 1_000_000L, "confirmed")
        val b = Purchase("Zomato", 471.0, KIND_EXPENSE, "x-food", "ORD123456", 1_000_000L + 3 * 3_600_000L, "delivered")
        val other = Purchase("Zomato", 471.0, KIND_EXPENSE, "x-food", "ORD999999", 1_000_000L, "other order")
        assertTrue(MailPurchases.samePurchase(a, b))
        assertTrue(!MailPurchases.samePurchase(a, other))
    }

    @Test
    fun withoutAnOrderNumberAmountAndTimeDecide() {
        val a = Purchase("Uber", 220.0, KIND_EXPENSE, "x-travel", null, 1_000_000L, "trip")
        val near = Purchase("Uber", 220.0, KIND_EXPENSE, "x-travel", null, 1_000_000L + 3_600_000L, "receipt")
        val far = Purchase("Uber", 220.0, KIND_EXPENSE, "x-travel", null, 1_000_000L + 24 * 3_600_000L, "receipt")
        val otherShop = Purchase("Ola", 220.0, KIND_EXPENSE, "x-travel", null, 1_000_000L, "trip")
        assertTrue(MailPurchases.samePurchase(a, near))
        assertTrue(!MailPurchases.samePurchase(a, far))
        assertTrue(!MailPurchases.samePurchase(a, otherShop))
    }

    @Test
    fun sendersAreRecognisedAndNamed() {
        assertEquals("zomato.com", Merchants.domainOf("Zomato <noreply@zomato.com>"))
        assertEquals("Swiggy", Merchants.known("Swiggy <no-reply@bounce.swiggy.in>")!!.name)
        assertNull(Merchants.known("Joe <orders@joescafe.com>"))
        assertEquals("Joe's Cafe", Merchants.nameOf("Joe's Cafe <orders@joescafe.com>"))
        assertEquals("Acme", Merchants.nameOf("billing@acme.com"))
    }

    @Test
    fun htmlBecomesLinesWithLabelsNextToAmounts() {
        val html = "<html><head><style>.a{color:red}</style></head><body><table><tr><td>Grand Total</td><td>&#8377;471.00</td></tr></table><p>Tom &amp; Jerry&nbsp;Cafe</p></body></html>"
        val text = GmailParsers.htmlToText(html)
        assertEquals("Grand Total\n₹471.00\nTom & Jerry Cafe", text)
        assertTrue(!text.contains("color"))
    }

    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    @Test
    fun aGmailMessageIsReadFromItsParts() {
        val json = JSONObject()
            .put("id", "abc123")
            .put("internalDate", "1700000000000")
            .put(
                "payload",
                JSONObject()
                    .put("mimeType", "multipart/alternative")
                    .put(
                        "headers",
                        JSONArray()
                            .put(JSONObject().put("name", "From").put("value", "Zomato <noreply@zomato.com>"))
                            .put(JSONObject().put("name", "subject").put("value", "Your order is confirmed")),
                    )
                    .put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("mimeType", "text/html")
                                .put("body", JSONObject().put("data", b64("<table><tr><td>Grand Total</td><td>₹471.00</td></tr></table>"))),
                        ),
                    ),
            )
        val d = GmailParsers.message(json.toString())!!
        assertEquals("abc123", d.id)
        assertEquals("Your order is confirmed", d.subject)
        assertEquals(1_700_000_000_000L, d.dateMs)
        assertEquals(471.0, found(MailPurchases.judge(d)).amount, 0.001)
    }

    @Test
    fun aMessageWithoutADateIsNotUsed() {
        assertNull(GmailParsers.message("{\"id\":\"x\",\"payload\":{}}"))
        assertNull(GmailParsers.message("not json"))
    }

    @Test
    fun listPagesAndProfilesAreRead() {
        val page = GmailParsers.listPage("{\"messages\":[{\"id\":\"a\"},{\"id\":\"b\"}],\"nextPageToken\":\"tok\"}")
        assertEquals(listOf("a", "b"), page.ids)
        assertEquals("tok", page.next)
        val last = GmailParsers.listPage("{\"resultSizeEstimate\":0}")
        assertTrue(last.ids.isEmpty())
        assertNull(last.next)
        assertEquals("me@gmail.com", GmailParsers.profileEmail("{\"emailAddress\":\"Me@Gmail.com\"}"))
        assertNull(GmailParsers.profileEmail("{}"))
    }
}
