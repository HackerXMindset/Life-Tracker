package com.lifetracker.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitCalendarTest {
    // 2026-10-14 is a Wednesday. October 2026 starts on a Thursday and ends on a Saturday.
    private val today = LocalDate.of(2026, 10, 14)
    private val created = LocalDate.of(2026, 10, 1)

    private fun d(day: Int, month: Int = 10) = LocalDate.of(2026, month, day)

    private fun rules(
        done: List<LocalDate> = emptyList(),
        plan: HabitPlan = HabitPlan(),
        kind: Int = HabitStats.KIND_YES_NO,
        target: Double = 1.0,
        amounts: Map<LocalDate, Double> = done.associateWith { 1.0 },
        createdOn: LocalDate = created,
    ) = HabitRules(kind, target, false, plan, amounts, createdOn, today)

    @Test
    fun theWeekRunsFromMondayToSunday() {
        val week = HabitCalendar.weekDays(today)
        assertEquals(d(12), week.first())
        assertEquals(d(18), week.last())
        assertEquals(7, week.size)
    }

    @Test
    fun theWeekCanStartOnSunday() {
        val week = HabitCalendar.weekDays(today, DayOfWeek.SUNDAY)
        assertEquals(d(11), week.first())
        assertEquals(d(17), week.last())
    }

    @Test
    fun theMonthIsWholeWeeksFromTheWeekOfThe1stToTheWeekOfTheLastDay() {
        val weeks = HabitCalendar.monthWeeks(today)
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 9, 28), weeks.first().first())
        assertEquals(LocalDate.of(2026, 11, 1), weeks.last().last())
        assertTrue(weeks.all { it.size == 7 })
    }

    @Test
    fun aMonthThatFitsFourWeeksHasFourRows() {
        val feb = HabitCalendar.monthWeeks(LocalDate.of(2027, 2, 10))
        assertEquals(4, feb.size)
    }

    @Test
    fun theYearGridIs53WeeksEndingThisWeek() {
        val cols = HabitCalendar.yearColumns(today)
        assertEquals(53, cols.size)
        assertEquals(d(12), cols.last().first())
        assertEquals(d(12).minusWeeks(52), cols.first().first())
    }

    @Test
    fun theYearGridNamesTheColumnsWhereAMonthStarts() {
        val cols = HabitCalendar.yearColumns(today)
        val labels = HabitCalendar.yearMonthLabels(cols)
        assertEquals(cols.first()[0].monthValue, labels[0])
        assertTrue(labels.size in 12..13)
    }

    @Test
    fun theRingCountsHabitsDueAndNotPausedAndFinishedOnes() {
        val done = rules(listOf(d(14)))
        val notDone = rules()
        val offToday = rules(plan = HabitPlan(interval = HabitPlan.WEEKDAYS, weekdays = listOf(1, 5)))
        val paused = rules(plan = HabitPlan(restDays = listOf(3)))
        val p = HabitCalendar.todayProgress(listOf(done, notDone, offToday, paused), today)
        assertEquals(TodayProgress(1, 2), p)
        assertEquals(0.5f, p.ratio, 0.001f)
        assertFalse(p.allDone)
        assertTrue(HabitCalendar.todayProgress(listOf(done), today).allDone)
        assertEquals(0f, HabitCalendar.todayProgress(emptyList(), today).ratio, 0.001f)
    }

    @Test
    fun aWeeklyHabitAlreadyMetThisWeekCountsAsFinishedToday() {
        val weekly = rules(listOf(d(12), d(13)), HabitPlan(interval = HabitPlan.WEEKLY, timesPerPeriod = 2))
        assertEquals(TodayProgress(1, 1), HabitCalendar.todayProgress(listOf(weekly), today))
    }

    @Test
    fun streakTextMarksWeeksAndMonths() {
        assertEquals("3", HabitCalendar.streakText(3, HabitPlan.DAILY))
        assertEquals("3w", HabitCalendar.streakText(3, HabitPlan.WEEKLY))
        assertEquals("3mo", HabitCalendar.streakText(3, HabitPlan.MONTHLY))
    }

    @Test
    fun categoryChipsKeepYourOrderThenTheRestByName() {
        val chips = HabitCalendar.categoryChips(listOf("Study", "Body", "", "Mind", "Body"), listOf("Mind", "Money", "Study"))
        assertEquals(listOf("Mind", "Study", "Body"), chips)
    }

    @Test
    fun aDoneDayShowsFullAndAMissedDueDayShowsMissed() {
        val r = rules(listOf(d(13)))
        assertEquals(HabitCell(DayKind.DONE, 1f), r.cell(d(13)))
        assertEquals(DayKind.MISSED, r.cell(d(12)).kind)
        assertEquals(DayKind.OFF, r.cell(d(20)).kind)
        assertEquals(DayKind.BEFORE, r.cell(LocalDate.of(2026, 9, 1)).kind)
    }

    @Test
    fun aPartialCountedDayShowsHowFullItIs() {
        val r = rules(kind = HabitStats.KIND_COUNTED, target = 8.0, amounts = mapOf(d(13) to 4.0, d(12) to 1.0, d(11) to 8.0))
        assertEquals(HabitCell(DayKind.PARTIAL, 0.5f), r.cell(d(13)))
        assertEquals(HabitCell(DayKind.PARTIAL, 0.25f), r.cell(d(12)))
        assertEquals(HabitCell(DayKind.DONE, 1f), r.cell(d(11)))
    }

    @Test
    fun aDayNotDueIsOffAndAPausedDayIsPausedButADoneDayOnAHolidayShowsDone() {
        val weekdays = rules(plan = HabitPlan(interval = HabitPlan.WEEKDAYS, weekdays = listOf(1, 3, 5)))
        assertEquals(DayKind.OFF, weekdays.cell(d(13)).kind)
        assertEquals(DayKind.MISSED, weekdays.cell(d(12)).kind)
        val holiday = rules(listOf(d(12)), HabitPlan(vacations = listOf(VacationSpan(d(11), d(12)))))
        assertEquals(DayKind.PAUSED, holiday.cell(d(11)).kind)
        assertEquals(DayKind.DONE, holiday.cell(d(12)).kind)
    }

    @Test
    fun aRelapseHabitShowsRelapseCleanAndFutureDays() {
        val r = rules(kind = HabitStats.KIND_RELAPSE, amounts = mapOf(d(10) to 1.0))
        assertEquals(DayKind.RELAPSE, r.cell(d(10)).kind)
        assertEquals(DayKind.CLEAN, r.cell(d(11)).kind)
        assertEquals(DayKind.FUTURE, r.cell(d(20)).kind)
        assertEquals(DayKind.BEFORE, r.cell(LocalDate.of(2026, 9, 20)).kind)
    }

    @Test
    fun aWeekThatReachedItsTargetShowsTheRestOfItsDaysAsCovered() {
        val r = rules(listOf(d(5), d(6)), HabitPlan(interval = HabitPlan.WEEKLY, timesPerPeriod = 2))
        assertEquals(DayKind.COVERED, r.cell(d(8)).kind)
    }

    @Test
    fun strengthIsFullForAHabitDoneEveryDayAndFallsWhenDaysAreMissed() {
        val every = rules((1..14).map { d(it) })
        assertEquals(1.0, every.strength(), 0.001)
        assertEquals(100, every.consistency())
        val none = rules()
        assertEquals(0.0, none.strength(), 0.001)
        val half = rules((1..14).filter { it % 2 == 0 }.map { d(it) })
        assertTrue(half.strength() in 0.3..0.7)
    }

    @Test
    fun recentMissesHurtMoreThanOldOnes() {
        val oldMiss = rules((2..14).map { d(it) })
        val recentMiss = rules((1..13).map { d(it) })
        assertTrue(oldMiss.strength() > recentMiss.strength())
    }

    @Test
    fun aCleanRelapseHabitHasFullStrength() {
        val r = rules(kind = HabitStats.KIND_RELAPSE)
        assertEquals(1.0, r.strength(), 0.001)
    }

    @Test
    fun monthSharesHoldTheFractionOfEachMonthDone() {
        val r = rules(listOf(d(1), d(2), d(3), d(4), d(5), d(6), d(7)))
        val shares = HabitCalendar.monthShares(r, today)
        assertEquals(12, shares.size)
        assertNull(shares[0])
        assertEquals(0.5f, shares[9]!!, 0.001f)
    }
}
