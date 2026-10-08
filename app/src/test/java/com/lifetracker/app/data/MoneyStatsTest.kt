package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyStatsTest {
    private fun entry(kind: Int, category: String, amount: Double, date: String = "2026-10-05") =
        MoneyEntryEntity(MoneyStats.newId(), date, kind, category, "x", amount, "", "")

    @Test
    fun totalsSeparateIncomeFromSpending() {
        val t = MoneyStats.totals(
            listOf(entry(KIND_INCOME, "i", 5000.0), entry(KIND_EXPENSE, "a", 120.5), entry(KIND_EXPENSE, "b", 79.5)),
        )
        assertEquals(5000.0, t.income, 0.001)
        assertEquals(200.0, t.spent, 0.001)
        assertEquals(4800.0, t.net, 0.001)
    }

    @Test
    fun categoriesAreBiggestFirst() {
        val rows = MoneyStats.byCategory(
            listOf(entry(KIND_EXPENSE, "food", 40.0), entry(KIND_EXPENSE, "rent", 8000.0), entry(KIND_EXPENSE, "food", 60.0), entry(KIND_INCOME, "pay", 1.0)),
            KIND_EXPENSE,
        )
        assertEquals(listOf("rent", "food"), rows.map { it.first })
        assertEquals(100.0, rows[1].second, 0.001)
    }

    @Test
    fun monthRangeCoversTheWholeMonth() {
        assertEquals("2026-10-01" to "2026-11-01", MoneyStats.range(YearMonth.of(2026, 10)))
        assertEquals("2026-12-01" to "2027-01-01", MoneyStats.range(YearMonth.of(2026, 12)))
    }

    @Test
    fun rupeesUseIndianGrouping() {
        assertEquals("₹0", MoneyStats.format(0.0, "₹"))
        assertEquals("₹999", MoneyStats.format(999.0, "₹"))
        assertEquals("₹1,000", MoneyStats.format(1000.0, "₹"))
        assertEquals("₹1,25,000", MoneyStats.format(125000.0, "₹"))
        assertEquals("₹12,34,567.50", MoneyStats.format(1234567.5, "₹"))
    }

    @Test
    fun otherCurrenciesUseThousands() {
        assertEquals("$125,000", MoneyStats.format(125000.0, "$"))
        assertEquals("-$40.25", MoneyStats.format(-40.25, "$"))
        assertEquals("€0.05", MoneyStats.format(0.05, "€"))
    }

    @Test
    fun ordinals() {
        assertEquals("1st", MoneyStats.ordinal(1))
        assertEquals("2nd", MoneyStats.ordinal(2))
        assertEquals("3rd", MoneyStats.ordinal(3))
        assertEquals("11th", MoneyStats.ordinal(11))
        assertEquals("22nd", MoneyStats.ordinal(22))
        assertEquals("31st", MoneyStats.ordinal(31))
    }

    // ---- monthly items

    private fun rent(day: Int = 5, start: String = "2026-08-01", end: String = "", last: String = "", archived: Boolean = false) =
        MoneyItemEntity("r", "Rent", KIND_EXPENSE, "x-bills", 8000.0, MoneyItemEntity.MONTHLY, day, start, end, last, archived, 0)

    @Test
    fun everyMonthSinceTheStartIsAdded() {
        val plan = Recurring.plan(rent(), LocalDate.of(2026, 10, 8))
        assertEquals(listOf("2026-08-05", "2026-09-05", "2026-10-05"), plan.entries.map { it.date })
        assertEquals("2026-10", plan.lastPosted)
        assertTrue(plan.entries.all { it.amount == 8000.0 && it.itemId == "r" })
    }

    @Test
    fun aChargeDayThatHasNotComeYetWaits() {
        val plan = Recurring.plan(rent(day = 20), LocalDate.of(2026, 10, 8))
        assertEquals(listOf("2026-08-20", "2026-09-20"), plan.entries.map { it.date })
        assertEquals("2026-09", plan.lastPosted)
    }

    @Test
    fun nothingIsAddedTwice() {
        val first = Recurring.plan(rent(), LocalDate.of(2026, 10, 8))
        val again = Recurring.plan(rent(last = first.lastPosted), LocalDate.of(2026, 10, 8))
        assertTrue(again.entries.isEmpty())
        // The next month, only that month is added.
        val next = Recurring.plan(rent(last = first.lastPosted), LocalDate.of(2026, 11, 6))
        assertEquals(listOf("2026-11-05"), next.entries.map { it.date })
        assertEquals("auto|r|2026-11", next.entries.single().id)
    }

    @Test
    fun aDeletedEntryDoesNotComeBack() {
        // lastPosted already covers October, so October is never planned again.
        val plan = Recurring.plan(rent(last = "2026-10"), LocalDate.of(2026, 10, 30))
        assertTrue(plan.entries.isEmpty())
    }

    @Test
    fun shortMonthsUseTheirLastDay() {
        val plan = Recurring.plan(rent(day = 31, start = "2026-01-01"), LocalDate.of(2026, 4, 30))
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), plan.entries.map { it.date })
    }

    @Test
    fun startInTheMiddleOfAMonthSkipsAChargeThatAlreadyPassed() {
        val plan = Recurring.plan(rent(day = 5, start = "2026-10-08"), LocalDate.of(2026, 11, 10))
        assertEquals(listOf("2026-11-05"), plan.entries.map { it.date })
    }

    @Test
    fun endDateStopsIt() {
        val plan = Recurring.plan(rent(end = "2026-09-30"), LocalDate.of(2026, 12, 1))
        assertEquals(listOf("2026-08-05", "2026-09-05"), plan.entries.map { it.date })
    }

    @Test
    fun stoppedAndBrokenItemsAddNothing() {
        assertTrue(Recurring.plan(rent(archived = true), LocalDate.of(2026, 10, 8)).entries.isEmpty())
        assertTrue(Recurring.plan(rent(day = 0), LocalDate.of(2026, 10, 8)).entries.isEmpty())
        assertTrue(Recurring.plan(rent(start = "garbage"), LocalDate.of(2026, 10, 8)).entries.isEmpty())
        val often = rent().copy(type = MoneyItemEntity.OFTEN)
        assertTrue(Recurring.plan(often, LocalDate.of(2026, 10, 8)).entries.isEmpty())
    }

    @Test
    fun aStartInTheFutureWaits() {
        val plan = Recurring.plan(rent(start = "2026-12-01"), LocalDate.of(2026, 10, 8))
        assertTrue(plan.entries.isEmpty())
        assertEquals("", plan.lastPosted)
    }

    @Test
    fun anOldStartDoesNotAddThousandsOfEntries() {
        val plan = Recurring.plan(rent(start = "1990-01-01"), LocalDate.of(2026, 10, 8))
        assertTrue(plan.entries.size <= 120)
        assertEquals("2026-10", plan.lastPosted)
    }
}
