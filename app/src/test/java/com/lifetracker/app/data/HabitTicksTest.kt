package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTicksTest {
    private val today = LocalDate.of(2026, 10, 14)

    private fun day(count: Double, minute: Int?, marks: String = "") = CompletionEntity("h", "2026-10-14", count, minute, marks)

    private fun habit(kind: Int, raw: String = "{}", target: Double = 1.0) =
        HabitEntity("h", "x", "target", "", 0, kind, 0, "", target, 1.0, false, -1, 0, "2026-10-01", false, 0, raw)

    private fun rules(
        kind: Int = HabitStats.KIND_COUNTED,
        target: Double = 0.0,
        tracking: Boolean = false,
        amounts: Map<LocalDate, Double> = emptyMap(),
        times: Map<LocalDate, List<Int>> = emptyMap(),
    ) = HabitRules(
        kind, target, false, HabitPlan(), amounts, LocalDate.of(2026, 10, 1), today,
        tracking = tracking, times = times,
    )

    @Test
    fun marksTextReadsAndWritesAndIgnoresJunk() {
        assertEquals(listOf(480, 615, 1300), HabitTicks.parse("480,615,1300"))
        assertEquals(listOf(5, 7), HabitTicks.parse(" 5 , x, 99999, -4, 7"))
        assertEquals(emptyList<Int>(), HabitTicks.parse(""))
        assertEquals("480,615", HabitTicks.join(listOf(480, 615)))
    }

    @Test
    fun anOldDayWithOneTimeStillShowsItAndADayWithNoneShowsNone() {
        assertEquals(listOf(780), HabitTicks.times(day(1.0, 780)))
        assertEquals(emptyList<Int>(), HabitTicks.times(day(1.0, null)))
        assertEquals(listOf(1, 2), HabitTicks.times(day(2.0, 780, "1,2")))
        assertEquals(emptyList<Int>(), HabitTicks.times(null))
    }

    @Test
    fun eachCheckKeepsItsTimeAndAddsToTheAmount() {
        val first = HabitTicks.add(null, "h", "2026-10-14", 1.0, 480)
        val second = HabitTicks.add(first, "h", "2026-10-14", 2.0, 615)
        assertEquals(3.0, second.count, 0.0)
        assertEquals("480,615", second.marks)
        assertEquals(615, second.minuteOfDay)
    }

    @Test
    fun aCheckOnAnotherDayHasNoTimeButTheOldTimeBecomesTheFirst() {
        val past = HabitTicks.add(null, "h", "2026-10-10", 1.0, null)
        assertEquals("", past.marks)
        assertNull(past.minuteOfDay)
        val legacy = HabitTicks.add(day(1.0, 780), "h", "2026-10-14", 1.0, 900)
        assertEquals("780,900", legacy.marks)
    }

    @Test
    fun amountsAreNotSpoiledByDecimals() {
        var d: CompletionEntity? = null
        repeat(3) { d = HabitTicks.add(d, "h", "2026-10-14", 0.1, 10) }
        assertEquals(0.3, d!!.count, 0.0)
    }

    @Test
    fun takingBackTheLastCheckDropsItsTimeAndClearsAnEmptyDay() {
        val two = day(2.0, 615, "480,615")
        val one = HabitTicks.undo(two, 1.0)!!
        assertEquals(1.0, one.count, 0.0)
        assertEquals("480", one.marks)
        assertEquals(480, one.minuteOfDay)
        assertNull(HabitTicks.undo(one, 1.0))
    }

    @Test
    fun changingATimeKeepsTheListInOrderAndTheLastTimeCurrent() {
        val two = day(2.0, 615, "480,615")
        val moved = HabitTicks.retime(two, 480, 700)
        assertEquals("615,700", moved.marks)
        assertEquals(700, moved.minuteOfDay)
        assertEquals(2.0, moved.count, 0.0)
        // A day with no time gets one.
        val none = HabitTicks.retime(day(1.0, null), null, 540)
        assertEquals("540", none.marks)
        assertEquals(540, none.minuteOfDay)
        // An old single time is changed in place.
        assertEquals("600", HabitTicks.retime(day(1.0, 780), 780, 600).marks)
    }

    @Test
    fun replacingTheTotalKeepsTheTimesAndZeroClears() {
        val two = day(2.0, 615, "480,615")
        val six = HabitTicks.withTotal(two, "h", "2026-10-14", 6.0, 1000)!!
        assertEquals(6.0, six.count, 0.0)
        assertEquals("480,615", six.marks)
        val fresh = HabitTicks.withTotal(null, "h", "2026-10-14", 3.0, 1000)!!
        assertEquals("1000", fresh.marks)
        val past = HabitTicks.withTotal(null, "h", "2026-10-10", 3.0, null)!!
        assertEquals("", past.marks)
        assertNull(HabitTicks.withTotal(two, "h", "2026-10-14", 0.0, 1000))
    }

    @Test
    fun theLastCheckReadsInWords() {
        assertEquals("Never", HabitTicks.lastCheckText(null, today))
        assertEquals("Today · 14:05", HabitTicks.lastCheckText(today to (14 * 60 + 5), today))
        assertEquals("Yesterday", HabitTicks.lastCheckText(today.minusDays(1) to null, today))
        assertEquals("Yesterday · 09:30", HabitTicks.lastCheckText(today.minusDays(1) to 570, today))
        assertTrue(HabitTicks.lastCheckText(today.minusDays(5) to 0, today).endsWith("· 00:00"))
    }

    @Test
    fun trackingIsReadFromTheHabitAndNeverForAnAvoidHabit() {
        assertTrue(HabitRules.isTracking(habit(HabitStats.KIND_COUNTED, """{"tracking":true}""")))
        assertTrue(HabitRules.isTracking(habit(HabitStats.KIND_YES_NO, """{"tracking":true}""")))
        assertFalse(HabitRules.isTracking(habit(HabitStats.KIND_RELAPSE, """{"tracking":true}""")))
        assertFalse(HabitRules.isTracking(habit(HabitStats.KIND_YES_NO, """{"tracking":false}""")))
        assertFalse(HabitRules.isTracking(habit(HabitStats.KIND_YES_NO, "not json")))
    }

    @Test
    fun aTrackingHabitHasNoStrengthNoMissedDaysAndIsNeverOwed() {
        val r = rules(tracking = true, amounts = mapOf(today.minusDays(1) to 3.0))
        assertEquals(0.0, r.strength(), 0.0)
        assertEquals(DayKind.DONE, r.cell(today.minusDays(1)).kind)
        // A day with nothing is blank, never "missed".
        assertEquals(DayKind.OFF, r.cell(today.minusDays(2)).kind)
        assertFalse(r.canMiss(today.minusDays(2)))
        assertTrue(r.isDoneForNow())
        assertEquals(TodayProgress(0, 0), HabitCalendar.todayProgress(listOf(r), today))
    }

    @Test
    fun trackingTotalsAndTheLastCheck() {
        // 2026-10-14 is a Wednesday; the week starts Monday the 12th, the month on the 1st.
        val amounts = mapOf(
            today to 2.0,
            LocalDate.of(2026, 10, 12) to 3.0,
            LocalDate.of(2026, 10, 11) to 4.0,
            LocalDate.of(2026, 10, 2) to 5.0,
            LocalDate.of(2026, 9, 30) to 9.0,
        )
        val r = rules(tracking = true, amounts = amounts, times = mapOf(today to listOf(480, 905)))
        assertEquals(5.0, r.weekTotal(), 0.0)
        assertEquals(14.0, r.monthTotal(), 0.0)
        assertEquals(today to 905, r.lastCheck())
        assertNull(rules(tracking = true).lastCheck())
    }

    @Test
    fun aCountedHabitWithNoGoalCountsAnyAmountAsADay() {
        val r = rules(kind = HabitStats.KIND_COUNTED, target = 0.0, amounts = mapOf(today to 0.5))
        assertTrue(r.isCompleted(today))
        assertEquals(DayKind.DONE, r.cell(today).kind)
        assertFalse(r.isCompleted(today.minusDays(1)))
    }

    @Test
    fun severalRelapsesInADayStillCountAsOneRelapseDay() {
        val r = rules(kind = HabitStats.KIND_RELAPSE, amounts = mapOf(today to 3.0))
        assertEquals(DayKind.RELAPSE, r.cell(today).kind)
        assertEquals(3.0, r.amountOn(today), 0.0)
        assertEquals(1, r.streaks().totalDays)
    }

    @Test
    fun theHabitFormKeepsTheTrackingSwitchButNeverOnAnAvoidHabit() {
        val now = java.time.LocalDateTime.of(2026, 10, 14, 9, 0)
        val tracked = HabitEditor.build(HabitForm(name = "Coke zero", kind = HabitStats.KIND_COUNTED, target = 0.0, tracking = true), null, "h", 0, today, now)
        assertTrue(HabitRules.isTracking(tracked))
        assertTrue(HabitEditor.formFrom(tracked).tracking)
        val avoid = HabitEditor.build(HabitForm(name = "Smoke", kind = HabitStats.KIND_RELAPSE, tracking = true), null, "h2", 1, today, now)
        assertFalse(HabitRules.isTracking(avoid))
    }
}
