package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyViewsTest {
    private val wed = LocalDate.of(2026, 11, 18) // a Wednesday

    private fun e(date: String, amount: Double, name: String = "Zomato", kind: Int = KIND_EXPENSE, cat: String = "x-food", id: String = "id$date$amount$name", source: Int = SOURCE_MINE) =
        MoneyEntryEntity(id, date, kind, cat, name, amount, "", "", source)

    @Test
    fun aWeekRunsMondayToSunday() {
        assertEquals(LocalDate.of(2026, 11, 16), MoneyViews.start(Period.Week, wed))
        assertEquals(LocalDate.of(2026, 11, 23), MoneyViews.endExclusive(Period.Week, wed))
        assertEquals(LocalDate.of(2026, 11, 16), MoneyViews.start(Period.Week, LocalDate.of(2026, 11, 22)))
        assertEquals(LocalDate.of(2026, 11, 1), MoneyViews.start(Period.Month, wed))
        assertEquals(LocalDate.of(2026, 12, 1), MoneyViews.endExclusive(Period.Month, wed))
        assertEquals(LocalDate.of(2026, 1, 1), MoneyViews.start(Period.Year, wed))
        assertEquals(LocalDate.of(2027, 1, 1), MoneyViews.endExclusive(Period.Year, wed))
        assertEquals(wed, MoneyViews.start(Period.Day, wed))
    }

    @Test
    fun shiftingMovesByThePeriod() {
        assertEquals(LocalDate.of(2026, 11, 11), MoneyViews.shift(Period.Week, wed, -1))
        assertEquals(LocalDate.of(2026, 10, 18), MoneyViews.shift(Period.Month, wed, -1))
        assertEquals(LocalDate.of(2025, 11, 18), MoneyViews.shift(Period.Year, wed, -1))
        assertEquals(LocalDate.of(2026, 11, 19), MoneyViews.shift(Period.Day, wed, 1))
    }

    @Test
    fun titlesAreReadable() {
        assertEquals("Today", MoneyViews.title(Period.Day, wed, wed))
        assertEquals("Yesterday", MoneyViews.title(Period.Day, wed.minusDays(1), wed))
        assertEquals("Mon 9 Nov", MoneyViews.title(Period.Day, LocalDate.of(2026, 11, 9), wed))
        assertEquals("This week · 16–22 Nov", MoneyViews.title(Period.Week, wed, wed))
        assertEquals("9–15 Nov", MoneyViews.title(Period.Week, wed.minusWeeks(1), wed))
        assertEquals("26 Oct – 1 Nov", MoneyViews.title(Period.Week, LocalDate.of(2026, 10, 28), wed))
        assertEquals("November 2026", MoneyViews.title(Period.Month, wed, wed))
        assertEquals("2026", MoneyViews.title(Period.Year, wed, wed))
    }

    @Test
    fun aWeekHasSevenBarsWithOwnMoneyOnly() {
        val entries = listOf(
            e("2026-11-16", 100.0), e("2026-11-16", 50.0), e("2026-11-18", 300.0),
            e("2026-11-18", 999.0, source = SOURCE_OTHER), e("2026-11-19", 5000.0, kind = KIND_INCOME, cat = "i-salary"),
        )
        val b = MoneyViews.buckets(Period.Week, wed, entries)
        assertEquals(7, b.size)
        assertEquals("M", b[0].label)
        assertEquals(150.0, b[0].spent, 0.001)
        assertEquals(0.0, b[1].spent, 0.001)
        assertEquals(300.0, b[2].spent, 0.001)
        assertEquals(5000.0, b[3].income, 0.001)
        assertEquals("S", b[6].label)
    }

    @Test
    fun aMonthHasABarForEveryDayAndAYearForEveryMonth() {
        assertEquals(30, MoneyViews.buckets(Period.Month, wed, emptyList()).size)
        assertEquals("30", MoneyViews.buckets(Period.Month, wed, emptyList()).last().label)
        val year = MoneyViews.buckets(Period.Year, wed, listOf(e("2026-03-05", 40.0), e("2026-03-20", 60.0), e("2026-12-01", 7.0)))
        assertEquals(12, year.size)
        assertEquals(100.0, year[2].spent, 0.001)
        assertEquals(7.0, year[11].spent, 0.001)
        assertTrue(MoneyViews.buckets(Period.Day, wed, emptyList()).isEmpty())
    }

    @Test
    fun placesJoinTheSameShopAndRankByTotal() {
        val list = MoneyViews.places(
            listOf(e("2026-11-16", 100.0, "Zomato"), e("2026-11-17", 200.0, "zomato "), e("2026-11-17", 250.0, "Swiggy"), e("2026-11-17", 9000.0, "Pay", kind = KIND_INCOME)),
            KIND_EXPENSE,
        )
        assertEquals(2, list.size)
        assertEquals("Zomato", list[0].name)
        assertEquals(300.0, list[0].total, 0.001)
        assertEquals(2, list[0].count)
        assertEquals("Swiggy", list[1].name)
    }

    @Test
    fun changeIsAFractionAndSaysNothingWithoutAPast() {
        assertNull(MoneyViews.change(100.0, 0.0))
        assertEquals(0.25, MoneyViews.change(125.0, 100.0)!!, 0.0001)
        assertEquals(-0.5, MoneyViews.change(50.0, 100.0)!!, 0.0001)
        assertEquals("▲ 25%", MoneyViews.changeText(0.25))
        assertEquals("▼ 50%", MoneyViews.changeText(-0.5))
        assertEquals("no change", MoneyViews.changeText(0.001))
    }

    @Test
    fun theAverageUsesOnlyDaysThatHavePassed() {
        assertEquals(3, MoneyViews.elapsedDays(Period.Week, wed, wed))
        assertEquals(7, MoneyViews.elapsedDays(Period.Week, wed.minusWeeks(1), wed))
        assertEquals(18, MoneyViews.elapsedDays(Period.Month, wed, wed))
        assertEquals(30, MoneyViews.elapsedDays(Period.Month, LocalDate.of(2026, 9, 10), wed))
        assertEquals(0, MoneyViews.elapsedDays(Period.Week, wed.plusWeeks(2), wed))
        assertEquals(100.0, MoneyViews.dailyAverage(300.0, 3), 0.001)
        assertEquals(0.0, MoneyViews.dailyAverage(300.0, 0), 0.001)
    }

    @Test
    fun heatLevelsRunFromNothingToFour() {
        assertEquals(0, MoneyViews.heatLevel(0.0, 100.0))
        assertEquals(1, MoneyViews.heatLevel(1.0, 100.0))
        assertEquals(2, MoneyViews.heatLevel(30.0, 100.0))
        assertEquals(4, MoneyViews.heatLevel(100.0, 100.0))
        assertEquals(0, MoneyViews.heatLevel(5.0, 0.0))
    }

    @Test
    fun aCalendarStartsOnTheRightWeekday() {
        val cells = MoneyViews.calendarCells(YearMonth.of(2026, 11)) // 1 Nov 2026 is a Sunday
        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(6 + 30, cells.size)
    }

    @Test
    fun entriesSayWhereTheyCameFrom() {
        assertEquals("SMS", MoneyViews.sourceTag(e("2026-11-16", 1.0, id = "sms-abc")))
        assertEquals("Gmail", MoneyViews.sourceTag(e("2026-11-16", 1.0, id = "gmail-abc")))
        assertEquals("Takeout", MoneyViews.sourceTag(e("2026-11-16", 1.0, id = "mbox-abc")))
        assertEquals("Monthly", MoneyViews.sourceTag(e("2026-11-16", 1.0, id = "auto|x|2026-11")))
        assertNull(MoneyViews.sourceTag(e("2026-11-16", 1.0, id = "6f1c-uuid")))
    }

    @Test
    fun busiestAndDailySpend() {
        val entries = listOf(e("2026-11-16", 100.0), e("2026-11-17", 400.0), e("2026-11-17", 100.0, source = SOURCE_UNSURE))
        val b = MoneyViews.buckets(Period.Week, wed, entries)
        assertEquals(LocalDate.of(2026, 11, 17), MoneyViews.busiest(b)!!.start)
        assertNull(MoneyViews.busiest(MoneyViews.buckets(Period.Week, wed, emptyList())))
        val daily = MoneyViews.dailySpent(entries)
        assertEquals(400.0, daily[LocalDate.of(2026, 11, 17)]!!, 0.001)
    }

    @Test
    fun brandColoursAreFoundInNames() {
        assertEquals(0xFFE23744, BrandColors.of("Zomato"))
        assertEquals(0xFFFC8019, BrandColors.of("SWIGGY Instamart"))
        assertEquals(0xFFFF9900, BrandColors.of("Amazon Pay India"))
        assertNull(BrandColors.of("Ramu Tea Stall"))
    }
}
