package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitScheduleExtrasTest {
    private val today = LocalDate.of(2026, 10, 14) // a Wednesday, the second one of the month
    private val now = LocalDateTime.of(2026, 10, 14, 9, 0)

    private fun build(plan: HabitPlan, difficulty: Int = 0, existing: HabitEntity? = null) =
        HabitEditor.build(HabitForm(name = "x", plan = plan, difficulty = difficulty), existing, "h", 0, today, now)

    private fun everyX(unit: Int = HabitPlan.UNIT_MONTHS, every: Int = 1, start: LocalDate? = today, last: Boolean = false, weekday: Boolean = false) =
        HabitPlan(interval = HabitPlan.EVERY_X_DAYS, every = every, unit = unit, scheduleStart = start, fromLastDone = last, monthWeekday = weekday)

    @Test
    fun ordinalsReadRight() {
        assertEquals("1st", HabitEditor.ordinal(1))
        assertEquals("2nd", HabitEditor.ordinal(2))
        assertEquals("3rd", HabitEditor.ordinal(3))
        assertEquals("4th", HabitEditor.ordinal(4))
        assertEquals("11th", HabitEditor.ordinal(11))
        assertEquals("12th", HabitEditor.ordinal(12))
        assertEquals("13th", HabitEditor.ordinal(13))
        assertEquals("21st", HabitEditor.ordinal(21))
        assertEquals("22nd", HabitEditor.ordinal(22))
        assertEquals("31st", HabitEditor.ordinal(31))
    }

    @Test
    fun theWeekdaySlotIsTheSameAsTheRulesUse() {
        assertEquals("second Wednesday", HabitEditor.weekdaySlot(today))
        assertEquals("first Thursday", HabitEditor.weekdaySlot(LocalDate.of(2026, 10, 1)))
        assertEquals("last Friday", HabitEditor.weekdaySlot(LocalDate.of(2026, 10, 30)))
        // The 5th week counts as last.
        assertEquals("last Saturday", HabitEditor.weekdaySlot(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun eachScheduleIsDescribedInWords() {
        assertEquals("On the 14th of every month", HabitEditor.describe(everyX()))
        assertEquals("On the 14th of every 2 months", HabitEditor.describe(everyX(every = 2)))
        assertEquals("On the second Wednesday of every month", HabitEditor.describe(everyX(weekday = true)))
        assertEquals("Every 3 days, counted from the last time done", HabitEditor.describe(everyX(unit = HabitPlan.UNIT_DAYS, every = 3, last = true)))
        assertEquals("Every week, counted from the last time done", HabitEditor.describe(everyX(unit = HabitPlan.UNIT_WEEKS, last = true)))
        // From the last time done wins over the weekday switch, as in Streak.
        assertEquals("Every month, counted from the last time done", HabitEditor.describe(everyX(last = true, weekday = true)))
        assertEquals("Every 2 weeks", HabitEditor.describe(everyX(unit = HabitPlan.UNIT_WEEKS, every = 2)))
    }

    @Test
    fun theSwitchesAreWrittenAndReadBack() {
        val habit = build(everyX(weekday = true))
        val plan = HabitPlan.fromRaw(habit.raw)
        assertTrue(plan.monthWeekday)
        assertFalse(plan.fromLastDone)
        assertEquals(today, plan.scheduleStart)
        val back = HabitEditor.formFrom(habit).plan
        assertTrue(back.monthWeekday)
        val last = HabitPlan.fromRaw(build(everyX(unit = HabitPlan.UNIT_DAYS, every = 3, last = true)).raw)
        assertTrue(last.fromLastDone)
    }

    @Test
    fun theSwitchesAreClearedWhenTheyNoLongerApply() {
        // Same weekday needs months, and from-last-done wins over it.
        assertFalse(HabitPlan.fromRaw(build(everyX(unit = HabitPlan.UNIT_WEEKS, weekday = true)).raw).monthWeekday)
        assertFalse(HabitPlan.fromRaw(build(everyX(last = true, weekday = true)).raw).monthWeekday)
        // And neither means anything once the habit is not "every X".
        val daily = HabitPlan.fromRaw(build(HabitPlan(interval = HabitPlan.DAILY, fromLastDone = true, monthWeekday = true)).raw)
        assertFalse(daily.fromLastDone)
        assertFalse(daily.monthWeekday)
    }

    @Test
    fun aSameWeekdayHabitIsDueOnThatWeekdayOnlyThroughTheRecord() {
        val habit = build(everyX(weekday = true, start = LocalDate.of(2026, 9, 9))) // the second Wednesday of September
        val rules = HabitRules.of(habit.copy(startedOn = "2026-09-01"), emptyMap(), today)
        assertTrue(rules.isScheduled(LocalDate.of(2026, 10, 14)))
        assertFalse(rules.isScheduled(LocalDate.of(2026, 10, 7)))
        assertFalse(rules.isScheduled(LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun aFixedDayEachMonthFallsBackToTheLastDayOfAShortMonth() {
        val habit = build(everyX(start = LocalDate.of(2026, 1, 31)))
        val rules = HabitRules.of(habit.copy(startedOn = "2026-01-01"), emptyMap(), today)
        assertTrue(rules.isScheduled(LocalDate.of(2026, 2, 28)))
        assertTrue(rules.isScheduled(LocalDate.of(2026, 10, 31)))
        assertFalse(rules.isScheduled(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun difficultyIsWrittenReadAndKeptInRange() {
        assertEquals(3, HabitEditor.difficultyOf(build(HabitPlan(), difficulty = 3).raw))
        assertEquals(3, HabitEditor.formFrom(build(HabitPlan(), difficulty = 3)).difficulty)
        assertEquals(3, HabitEditor.difficultyOf(build(HabitPlan(), difficulty = 9).raw)) // 9 is cut to 3
        assertEquals(3, HabitEditor.difficultyOf("""{"difficulty":9}"""))
        assertEquals(0, HabitEditor.difficultyOf("""{"difficulty":-2}"""))
        assertEquals(0, HabitEditor.difficultyOf("not json"))
    }

    @Test
    fun theTodayRingCountsHabitsEqualUnlessDifficultyIsSwitchedOn() {
        fun rules(done: Boolean) = HabitRules(
            HabitStats.KIND_YES_NO, 1.0, false, HabitPlan(),
            if (done) mapOf(today to 1.0) else emptyMap(), LocalDate.of(2026, 10, 1), today,
        )
        // An easy habit is done, a hard one is not.
        val list = listOf(rules(true), rules(false))
        val plain = HabitCalendar.todayProgress(list, today)
        assertEquals(1, plain.done)
        assertEquals(2, plain.total)
        assertEquals(0.5f, plain.ratio, 0.0001f)
        val weighed = HabitCalendar.todayProgress(list, today, listOf(1, 3))
        // The text still counts habits; only the ring is weighed: 1 of 4.
        assertEquals(1, weighed.done)
        assertEquals(2, weighed.total)
        assertEquals(0.25f, weighed.ratio, 0.0001f)
        // Not set counts as medium (2): 2 of 5 when the other is hard.
        assertEquals(0.4f, HabitCalendar.todayProgress(list, today, listOf(0, 3)).ratio, 0.0001f)
        assertFalse(weighed.allDone)
    }

    @Test
    fun streaksOwnWeights() {
        assertEquals(1, HabitCalendar.weightOf(1))
        assertEquals(2, HabitCalendar.weightOf(2))
        assertEquals(3, HabitCalendar.weightOf(3))
        assertEquals(2, HabitCalendar.weightOf(0))
    }
}
