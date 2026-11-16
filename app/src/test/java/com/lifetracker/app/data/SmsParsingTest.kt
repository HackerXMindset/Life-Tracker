package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParsingTest {
    private fun sms(body: String, sender: String = "AX-HDFCBK-S", at: Long = 1_700_000_000_000L) = SmsDoc("k", sender, body, at)

    private fun found(body: String, includeIncome: Boolean = false, sender: String = "AX-HDFCBK-S"): Purchase {
        val v = SmsPurchases.judge(sms(body, sender), includeIncome)
        assertTrue("expected Found but was $v for: $body", v is MailVerdict.Found)
        return (v as MailVerdict.Found).purchase
    }

    private fun skipped(body: String, includeIncome: Boolean = false, sender: String = "AX-HDFCBK-S") {
        val v = SmsPurchases.judge(sms(body, sender), includeIncome)
        assertTrue("expected Skip but was $v for: $body", v is MailVerdict.Skip)
    }

    @Test
    fun upiDebitToAVpaNamesTheShopFromTheVpa() {
        val p = found("Rs.471.00 debited from a/c **1234 on 14-11-26 to VPA swiggy@icici(UPI Ref No 432112345678). Not you? Call 18002586161")
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Swiggy", p.merchant)
        assertEquals("x-food", p.categoryId)
        assertEquals(KIND_EXPENSE, p.kind)
        assertEquals("432112345678", p.orderId)
    }

    @Test
    fun cardSpendTakesTheShopAfterAt() {
        val p = found("Spent Rs.471.00 On HDFC Bank Card 1234 At ZOMATO On 2026-11-14:10:22:11.Not You? To Block+Reissue Call 18002586161", sender = "AD-HDFCBK-S")
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Zomato", p.merchant)
    }

    @Test
    fun cardSpendWithABalanceAfterIt() {
        val p = found("INR 471.00 spent on ICICI Bank Card XX1234 on 14-Nov-26 at ZOMATO. Avl Limit: INR 50,000.00", sender = "VM-ICICIT-S")
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Zomato", p.merchant)
    }

    @Test
    fun anAmountWithNoSymbolAndATransferTo() {
        val p = found("Dear UPI user A/C X1234 debited by 471.0 on date 14Nov26 trf to ZOMATO Refno 432112345678. If not you, call 1800111109", sender = "SBIUPI")
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Zomato", p.merchant)
        assertEquals("432112345678", p.orderId)
    }

    @Test
    fun anUpiSlashLineTakesTheNameBetweenTheSlashAndTheBoilerplate() {
        val p = found("INR 471.00 debited A/c no. XX1234 14-11-26, 10:22:33 UPI/P2M/432112345678/ZOMATO SMS BLOCK 1234 to 919951860002 if not you", sender = "AX-AXISBK-S")
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Zomato", p.merchant)
    }

    @Test
    fun theNameBeforeCredited() {
        val p = found("ICICI Bank Acct XX123 debited for Rs 471.00 on 14-Nov-26; ZOMATO credited. UPI:432112345678. Call 18002662 for dispute. SMS Block 123 to 9215676766.", sender = "VM-ICICIB-S")
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Zomato", p.merchant)
        assertEquals("432112345678", p.orderId)
    }

    @Test
    fun aPaymentAppText() {
        val p = found("Paid ₹471 to ZOMATO. UPI Ref: 432112345678", sender = "PHONEPE")
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Zomato", p.merchant)
    }

    @Test
    fun anUnknownShopKeepsItsOwnName() {
        val p = found("Rs 120.00 debited from A/c XX1234 on 14-11-26 and credited to RAMU TEA STALL via UPI. Ref 432112345678")
        assertEquals(120.0, p.amount, 0.001)
        assertEquals("x-other", p.categoryId)
    }

    @Test
    fun cashFromAnAtm() {
        val p = found("Rs.5000 withdrawn from ATM XX1234 on 14-11-26 at XYZ ROAD. Avl Bal Rs.20000")
        assertEquals(5000.0, p.amount, 0.001)
        assertEquals("ATM cash", p.merchant)
    }

    @Test
    fun codesOffersBalancesAndFailuresAreLeftOut() {
        skipped("123456 is your OTP for transaction of Rs 471 at ZOMATO. Do not share.")
        skipped("Get a pre-approved loan of Rs 5,00,000! Click here. T&C apply")
        skipped("Your a/c XX1234 balance is Rs 5000.00")
        skipped("Your UPI transaction of Rs 471 to ZOMATO has failed. Amount not debited.")
        skipped("Rs 499 will be debited from your a/c on 20-Nov for Netflix mandate")
        skipped("Payment of Rs 15000 received towards your HDFC credit card XX1234. Thank you")
        skipped("Rs.471 debited to ZOMATO", sender = "VM-HDFCBK-P")
        skipped("Lunch at 1 pm? I paid last time")
    }

    @Test
    fun moneyReceivedIsLeftOutUnlessAskedFor() {
        val text = "Rs.25000 credited to a/c XX1234 on 01-11-26 by UPI from RAM KUMAR (UPI Ref 432112345678)"
        skipped(text)
        val p = found(text, includeIncome = true)
        assertEquals(KIND_INCOME, p.kind)
        assertEquals(25000.0, p.amount, 0.001)
        assertEquals("Ram Kumar", p.merchant)
        assertEquals("i-other", p.categoryId)
    }

    @Test
    fun aSalaryIsSalary() {
        val p = found("Rs.50000.00 credited to a/c XX1234 on 01-11-26. Info: Salary for Oct", includeIncome = true)
        assertEquals("i-salary", p.categoryId)
    }

    @Test
    fun aRefundAlwaysCountsAndKeepsTheShop() {
        val p = found("Refund of Rs 471.00 for your ZOMATO order credited to your a/c XX1234")
        assertEquals(KIND_INCOME, p.kind)
        assertEquals(471.0, p.amount, 0.001)
        assertEquals("Zomato", p.merchant)
        assertEquals("i-other", p.categoryId)
    }

    @Test
    fun theAmountIsNeverTheBalance() {
        assertEquals(500.0, SmsPurchases.amountOf("Rs.500 debited. Avl Bal Rs.1,20,000.50")!!, 0.001)
        assertEquals(50.0, SmsPurchases.amountOf("Avl Bal Rs 100. Rs 50 debited")!!, 0.001)
        assertNull(SmsPurchases.amountOf("No money in this text"))
    }

    @Test
    fun namesAreTidiedOrRefused() {
        assertNull(SmsPurchases.cleanName("9876543210"))
        assertNull(SmsPurchases.cleanName("XXXX1234"))
        assertNull(SmsPurchases.cleanName("UPI"))
        assertEquals("Swiggy Instamart", SmsPurchases.cleanName("SWIGGY INSTAMART"))
        assertEquals("Zomato", SmsPurchases.cleanName("Zomato Online Pvt Ltd"))
    }

    @Test
    fun longNumbersAreHiddenInTheNote() {
        val s = SmsPurchases.snippet("Rs.471 debited from a/c 1234567890 UPI Ref 432112345678")
        assertFalse(s.contains("1234567890"))
        assertFalse(s.contains("432112345678"))
    }

    @Test
    fun theSameAlertTwiceIsOne() {
        val a = Purchase("Zomato", 471.0, KIND_EXPENSE, "x-food", null, 1_000_000L, "")
        val soon = Purchase("Zomato", 471.0, KIND_EXPENSE, "x-food", null, 1_000_000L + 60_000L, "")
        val later = Purchase("Zomato", 471.0, KIND_EXPENSE, "x-food", null, 1_000_000L + 10 * 60_000L, "")
        assertTrue(SmsPurchases.sameText(a, soon))
        assertFalse(SmsPurchases.sameText(a, later))
        val r1 = Purchase("Zomato", 20.0, KIND_EXPENSE, "x-food", "111111111111", 1_000_000L, "")
        val r2 = Purchase("Zomato", 20.0, KIND_EXPENSE, "x-food", "222222222222", 1_000_000L + 30_000L, "")
        val r3 = Purchase("Zomato", 20.0, KIND_EXPENSE, "x-food", "111111111111", 1_000_000L + 60 * 60_000L, "")
        assertFalse(SmsPurchases.sameText(r1, r2))
        assertTrue(SmsPurchases.sameText(r1, r3))
    }

    @Test
    fun aTextAndAReceiptForTheSameSpendingMeet() {
        assertTrue(MoneyMerge.sameSpend("Zomato", 471.0, KIND_EXPENSE, "UPI payment", 471.0, KIND_EXPENSE))
        assertTrue(MoneyMerge.sameSpend("Zomato", 471.0, KIND_EXPENSE, "Zomato Online", 471.0, KIND_EXPENSE))
        assertFalse(MoneyMerge.sameSpend("Zomato", 471.0, KIND_EXPENSE, "Swiggy", 471.0, KIND_EXPENSE))
        assertFalse(MoneyMerge.sameSpend("Zomato", 471.0, KIND_EXPENSE, "Zomato", 470.0, KIND_EXPENSE))
        assertFalse(MoneyMerge.sameSpend("Zomato", 471.0, KIND_EXPENSE, "Zomato", 471.0, KIND_INCOME))
    }

    @Test
    fun shopsAreFoundInPlainText() {
        assertEquals("Swiggy", Merchants.byText("swiggy@icici")!!.name)
        assertEquals("Amazon", Merchants.byText("Amazon Pay")!!.name)
        assertEquals("Swiggy", Merchants.byText("SWIGGY INSTAMART")!!.name)
        assertNull(Merchants.byText("Ramu Tea Stall"))
    }
}
