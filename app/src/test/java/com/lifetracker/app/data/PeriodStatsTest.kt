package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodStatsTest {
    private val today = LocalDate.of(2026, 11, 12) // a Thursday

    private fun meal(date: String, kcal: Double, name: String = "dal", carbs: Double = 0.0, protein: Double = 0.0, fat: Double = 0.0) =
        MealEntity("$date$name$kcal", date, 600, "lunch", name, 100.0, kcal, carbs, fat, protein, "app", "")

    private fun goal(kcal: Double) = FoodGoalEntity("2026-01-01", kcal, 0.0, 0.0, 0.0)

    private fun build(span: StatSpan, meals: List<MealEntity>, goal: FoodGoalEntity? = null, weights: List<WeightEntity> = emptyList(),
                      checks: List<TrackerEventEntity> = emptyList(), habits: List<String> = emptyList()) =
        PeriodStats.build(span, today, meals, { goal }, weights, checks, habits)

    // ---- spans ----

    @Test
    fun aWeekRunsMondayToSunday() {
        val s = StatSpans.span(StatPeriod.WEEK, today, today, null)
        assertEquals(LocalDate.of(2026, 11, 9), s.from)
        assertEquals(LocalDate.of(2026, 11, 15), s.to)
        assertEquals(7, s.days)
    }

    @Test
    fun aMonthIsTheCalendarMonth() {
        val s = StatSpans.span(StatPeriod.MONTH, LocalDate.of(2026, 2, 10), today, null)
        assertEquals(LocalDate.of(2026, 2, 1), s.from)
        assertEquals(LocalDate.of(2026, 2, 28), s.to)
    }

    @Test
    fun ninetyDaysEndOnTheAnchorButNeverAfterToday() {
        val s = StatSpans.span(StatPeriod.DAYS90, today.plusDays(30), today, null)
        assertEquals(today, s.to)
        assertEquals(90, s.days)
        assertEquals(today.minusDays(89), s.from)
    }

    @Test
    fun allTimeStartsAtTheFirstLoggedDay() {
        val s = StatSpans.span(StatPeriod.ALL, today, today, LocalDate.of(2026, 3, 5))
        assertEquals(LocalDate.of(2026, 3, 5), s.from)
        assertEquals(today, s.to)
        assertEquals(today, StatSpans.span(StatPeriod.ALL, today, today, null).from)
    }

    @Test
    fun shiftingMovesByOnePeriod() {
        assertEquals(today.minusWeeks(1), StatSpans.shift(StatPeriod.WEEK, today, -1))
        assertEquals(LocalDate.of(2026, 10, 12), StatSpans.shift(StatPeriod.MONTH, today, -1))
        assertEquals(today.minusDays(90), StatSpans.shift(StatPeriod.DAYS90, today, -1))
        assertEquals(today, StatSpans.shift(StatPeriod.ALL, today, -1))
    }

    @Test
    fun youCannotGoLaterThanTodayOrEarlierThanYourFirstDay() {
        assertFalse(StatSpans.canGoLater(StatPeriod.WEEK, today, today))
        assertTrue(StatSpans.canGoLater(StatPeriod.WEEK, today.minusWeeks(1), today))
        assertFalse(StatSpans.canGoLater(StatPeriod.ALL, today.minusWeeks(1), today))
        val first = LocalDate.of(2026, 11, 3)
        assertTrue(StatSpans.canGoEarlier(StatPeriod.WEEK, today, today, first))
        assertFalse(StatSpans.canGoEarlier(StatPeriod.WEEK, LocalDate.of(2026, 11, 3), today, first))
        assertFalse(StatSpans.canGoEarlier(StatPeriod.WEEK, today, today, null))
    }

    @Test
    fun titlesNameTheCurrentPeriodPlainly() {
        assertEquals("This week", StatSpans.title(StatPeriod.WEEK, StatSpans.span(StatPeriod.WEEK, today, today, null), today))
        assertEquals("This month", StatSpans.title(StatPeriod.MONTH, StatSpans.span(StatPeriod.MONTH, today, today, null), today))
        assertEquals("Last 90 days", StatSpans.title(StatPeriod.DAYS90, StatSpans.span(StatPeriod.DAYS90, today, today, null), today))
        assertEquals("All time", StatSpans.title(StatPeriod.ALL, StatSpans.span(StatPeriod.ALL, today, today, null), today))
    }

    @Test
    fun aPeriodIsRememberedByItsKey() {
        assertEquals(StatPeriod.DAYS90, StatPeriod.fromKey("90days"))
        assertEquals(StatPeriod.WEEK, StatPeriod.fromKey("what"))
    }

    // ---- numbers ----

    @Test
    fun averagesLeaveOutDaysWithNothingLogged() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15))
        val r = build(span, listOf(meal("2026-11-09", 2000.0), meal("2026-11-10", 1000.0)))
        assertEquals(2, r.loggedDays)
        assertEquals(1500.0, r.avgKcal, 1e-9)
        assertEquals(4, r.elapsedDays) // Mon to Thu; the rest is the future
        assertEquals(2, r.emptyDays)
    }

    @Test
    fun anEmptyPeriodHasNoNumbersAtAll() {
        val r = build(StatSpan(today.minusDays(6), today), emptyList())
        assertEquals(0, r.loggedDays)
        assertEquals(0.0, r.avgKcal, 1e-9)
        assertNull(r.macros)
        assertNull(r.highest)
        assertTrue(r.points.all { it.kcal == null })
    }

    @Test
    fun daysWithinTenPercentOfTheGoalAreInBand() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12))
        val r = build(span, listOf(meal("2026-11-09", 2050.0), meal("2026-11-10", 1500.0), meal("2026-11-11", 2190.0)), goal(2000.0))
        assertEquals(3, r.goalDays)
        assertEquals(2, r.daysInBand) // 2050 is in; 1500 is under; 2190 is 9.5 % over, so in too
    }

    @Test
    fun theAverageIsComparedWithTheGoal() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12))
        val r = build(span, listOf(meal("2026-11-09", 1800.0)), goal(2000.0))
        assertEquals(-0.1, r.avgVsGoal!!, 1e-9)
        assertNull(build(span, listOf(meal("2026-11-09", 1800.0))).avgVsGoal)
    }

    @Test
    fun macroSharesAddUpToOne() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12))
        val r = build(span, listOf(meal("2026-11-09", 500.0, carbs = 50.0, protein = 25.0, fat = 20.0)))
        val m = r.macros!!
        assertEquals(1.0, m.carbs + m.protein + m.fat, 1e-9)
        assertEquals(200.0 / 480.0, m.carbs, 1e-9)
        assertEquals(180.0 / 480.0, m.fat, 1e-9)
    }

    @Test
    fun highestAndLowestNeedAtLeastTwoDays() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12))
        assertNull(build(span, listOf(meal("2026-11-09", 1800.0))).highest)
        val r = build(span, listOf(meal("2026-11-09", 1800.0), meal("2026-11-10", 2400.0), meal("2026-11-11", 900.0)))
        assertEquals(LocalDate.of(2026, 11, 10), r.highest!!.date)
        assertEquals(LocalDate.of(2026, 11, 11), r.lowest!!.date)
    }

    @Test
    fun weightChangeNeedsTwoWeighIns() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15))
        assertNull(build(span, emptyList(), weights = listOf(WeightEntity("2026-11-10", 70.0))).weightChange)
        val r = build(span, emptyList(), weights = listOf(WeightEntity("2026-11-10", 70.0), WeightEntity("2026-11-14", 69.4), WeightEntity("2026-10-01", 90.0)))
        assertEquals(-0.6, r.weightChange!!, 1e-9)
    }

    @Test
    fun habitsCountDaysAndChecksInThePeriodOnly() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15))
        val checks = listOf(
            TrackerEventEntity("a|2026-11-09|100|0", "a", "2026-11-09", 100),
            TrackerEventEntity("a|2026-11-09|200|0", "a", "2026-11-09", 200),
            TrackerEventEntity("a|2026-11-11|100|0", "a", "2026-11-11", 100),
            TrackerEventEntity("a|2026-10-01|100|0", "a", "2026-10-01", 100),
        )
        val r = build(span, emptyList(), checks = checks, habits = listOf("b", "a"))
        assertEquals(listOf("b", "a"), r.habits.map { it.habitId })
        assertEquals(0, r.habits[0].daysDone)
        assertNull(r.habits[0].last)
        assertEquals(2, r.habits[1].daysDone)
        assertEquals(3, r.habits[1].checks)
        assertEquals(LocalDate.of(2026, 11, 11), r.habits[1].last)
    }

    @Test
    fun theFoodsEatenMostComeFirstWithTheirLastDay() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15))
        val meals = listOf(
            meal("2026-11-09", 100.0, "Palak paneer"), meal("2026-11-10", 100.0, "palak paneer "), meal("2026-11-11", 100.0, "Rice"),
        )
        val top = build(span, meals).topFoods
        assertEquals("Palak paneer", top[0].name)
        assertEquals(2, top[0].times)
        assertEquals(LocalDate.of(2026, 11, 10), top[0].last)
        assertEquals("Rice", top[1].name)
    }

    // ---- the chart ----

    @Test
    fun aWeekIsSevenDailyPointsWithGapsForEmptyDaysAndTheFuture() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15))
        val r = build(span, listOf(meal("2026-11-09", 2000.0), meal("2026-11-11", 1000.0)))
        assertEquals(7, r.points.size)
        assertEquals(listOf(2000.0, null, 1000.0, null, null, null, null), r.points.map { it.kcal })
    }

    @Test
    fun ninetyDaysAreWeeklyAveragesOfTheDaysThatHaveFood() {
        val span = StatSpans.span(StatPeriod.DAYS90, today, today, null)
        val r = build(span, listOf(meal("2026-11-09", 2000.0), meal("2026-11-10", 1000.0)))
        assertTrue(r.points.size in 13..14)
        val last = r.points.last { it.kcal != null }
        assertEquals(1500.0, last.kcal!!, 1e-9)
        assertTrue(r.points.count { it.kcal == null } >= r.points.size - 1)
    }

    @Test
    fun aLongSpanIsMonthlyPointsAndTheLabelsAreSparse() {
        val span = StatSpan(LocalDate.of(2025, 11, 12), today)
        val r = build(span, listOf(meal("2026-11-09", 2000.0)))
        assertEquals(13, r.points.size)
        assertTrue(r.points.count { it.label.isNotEmpty() } <= 7)
    }
}
