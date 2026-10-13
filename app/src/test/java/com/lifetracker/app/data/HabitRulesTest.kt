package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitRulesTest {
    // 2026-10-14 is a Wednesday. Oct 12 is a Monday, Oct 11 a Sunday, Oct 1 a Thursday.
    private val today = LocalDate.of(2026, 10, 14)
    private val created = LocalDate.of(2026, 10, 1)

    private fun day(d: Int, month: Int = 10) = LocalDate.of(2026, month, d)

    private fun rules(
        done: List<LocalDate> = emptyList(),
        plan: HabitPlan = HabitPlan(),
        kind: Int = HabitStats.KIND_YES_NO,
        target: Double = 1.0,
        anyAmount: Boolean = false,
        createdOn: LocalDate = created,
        amounts: Map<LocalDate, Double> = done.associateWith { 1.0 },
    ) = HabitRules(kind, target, anyAmount, plan, amounts, createdOn, today)

    @Test
    fun aDailyStreakCountsBackFromToday() {
        val r = rules(listOf(day(12), day(13), day(14)))
        assertEquals(3, r.currentStreak())
    }

    @Test
    fun aStreakSurvivesTodayNotDoneYetButNotTwoMissedDays() {
        assertEquals(3, rules(listOf(day(11), day(12), day(13))).currentStreak())
        assertEquals(0, rules(listOf(day(10), day(11), day(12))).currentStreak())
    }

    @Test
    fun theBestStreakIsTheLongestRunEver() {
        val r = rules(listOf(day(1), day(2), day(3), day(4), day(5), day(8), day(9)))
        assertEquals(5, r.longestStreak())
    }

    @Test
    fun aWeeklyRestDayDoesNotBreakTheStreak() {
        // Sunday (Oct 11) is a rest day.
        val r = rules(listOf(day(9), day(10), day(12), day(13), day(14)), HabitPlan(restDays = listOf(7)))
        assertTrue(r.isPaused(day(11)))
        assertEquals(5, r.currentStreak())
        assertEquals(5, r.longestStreak())
    }

    @Test
    fun aHolidayDoesNotBreakTheStreakAndAnOpenOneRunsUpToToday() {
        val plan = HabitPlan(vacations = listOf(VacationSpan(day(11), day(11))))
        val r = rules(listOf(day(9), day(10), day(12), day(13), day(14)), plan)
        assertTrue(r.isNeutral(day(11)))
        assertEquals(5, r.currentStreak())

        val open = rules(listOf(day(10), day(11), day(12)), HabitPlan(vacations = listOf(VacationSpan(day(13), null))))
        assertTrue(open.isPaused(day(14)))
        assertEquals(3, open.currentStreak())
    }

    @Test
    fun aDayOnAHolidayThatYouDidDoStillCounts() {
        val plan = HabitPlan(vacations = listOf(VacationSpan(day(11), day(11))))
        val r = rules(listOf(day(10), day(11), day(12), day(13), day(14)), plan)
        assertFalse(r.isNeutral(day(11)))
        assertEquals(5, r.currentStreak())
    }

    @Test
    fun aMondayWednesdayFridayHabitSkipsTheDaysItIsNotDue() {
        val plan = HabitPlan(interval = HabitPlan.WEEKDAYS, weekdays = listOf(1, 3, 5))
        val r = rules(listOf(day(9), day(12), day(14)), plan)
        assertTrue(r.isScheduled(day(14)))
        assertFalse(r.isScheduled(day(13)))
        assertEquals(3, r.currentStreak())
        assertTrue(r.longestStreak() >= 3)
    }

    @Test
    fun aDayThatIsNotDueIsOffAndSoIsADayNothingIsOwed() {
        val plan = HabitPlan(interval = HabitPlan.WEEKDAYS, weekdays = listOf(1, 5))
        assertTrue(rules(plan = plan).isDoneForNow())
        assertFalse(rules(plan = HabitPlan()).isDoneForNow())
        assertTrue(rules(listOf(day(14))).isDoneForNow())
    }

    @Test
    fun everyTwoDaysIsDueOnEverySecondDayFromTheStart() {
        val plan = HabitPlan(interval = HabitPlan.EVERY_X_DAYS, every = 2, scheduleStart = day(10))
        val r = rules(plan = plan)
        assertFalse(r.isScheduled(day(9)))
        assertTrue(r.isScheduled(day(10)))
        assertFalse(r.isScheduled(day(11)))
        assertTrue(r.isScheduled(day(12)))
        assertTrue(r.isScheduled(day(14)))
    }

    @Test
    fun doingAnEveryThreeDaysHabitEarlyCoversTheDay() {
        val plan = HabitPlan(interval = HabitPlan.EVERY_X_DAYS, every = 3, scheduleStart = day(1))
        val r = rules(listOf(day(12)), plan)
        assertTrue(r.isScheduled(day(13)))
        assertTrue(r.isDoneAhead(day(13)))
        assertTrue(r.isSatisfied(day(13)))
        assertFalse(r.isDoneAhead(day(10)))
    }

    @Test
    fun fromLastDoneTheNextDueDayIsCountedFromTheDayYouDidIt() {
        val plan = HabitPlan(interval = HabitPlan.EVERY_X_DAYS, every = 3, scheduleStart = day(1), fromLastDone = true)
        val r = rules(listOf(day(5)), plan)
        assertTrue(r.isScheduled(day(4)))
        assertFalse(r.isScheduled(day(7)))
        assertTrue(r.isScheduled(day(8)))
    }

    @Test
    fun theSecondWednesdayOfEveryMonthIsFoundByWeekdaySlot() {
        val plan = HabitPlan(
            interval = HabitPlan.EVERY_X_DAYS, every = 1, unit = HabitPlan.UNIT_MONTHS,
            scheduleStart = day(14), monthWeekday = true,
        )
        val r = rules(plan = plan, createdOn = day(1))
        assertTrue(r.isScheduled(day(11, 11)))
        assertFalse(r.isScheduled(day(14, 11)))
        assertFalse(r.isScheduled(day(4, 11)))
    }

    @Test
    fun anEveryMonthHabitOnTheThirtyFirstFallsOnTheLastDayOfShortMonths() {
        val plan = HabitPlan(
            interval = HabitPlan.EVERY_X_DAYS, every = 1, unit = HabitPlan.UNIT_MONTHS,
            scheduleStart = LocalDate.of(2026, 8, 31),
        )
        val r = rules(plan = plan, createdOn = LocalDate.of(2026, 8, 1))
        assertTrue(r.isScheduled(LocalDate.of(2026, 9, 30)))
        assertFalse(r.isScheduled(LocalDate.of(2026, 9, 29)))
        assertTrue(r.isScheduled(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun aWeeklyHabitStreakCountsWeeksThatReachedTheTarget() {
        val plan = HabitPlan(interval = HabitPlan.WEEKLY, timesPerPeriod = 3)
        val r = rules(listOf(day(5), day(6), day(7), day(12), day(13), day(14)), plan)
        assertEquals(2, r.currentStreak())
        assertEquals(2, r.longestStreak())
    }

    @Test
    fun aWeekThatIsNotFinishedYetDoesNotBreakTheWeeklyStreak() {
        val plan = HabitPlan(interval = HabitPlan.WEEKLY, timesPerPeriod = 3)
        val r = rules(listOf(day(5), day(6), day(7), day(12)), plan)
        // This week has 1 of 3, but last week reached 3, so the streak is last week.
        assertEquals(1, r.currentStreak())
    }

    @Test
    fun daysAfterAWeekReachedItsTargetAreCoveredNotMissed() {
        val plan = HabitPlan(interval = HabitPlan.WEEKLY, timesPerPeriod = 3)
        val r = rules(listOf(day(5), day(6), day(7)), plan)
        assertTrue(r.isCovered(day(8)))
        assertFalse(r.isCovered(day(6)))
        assertFalse(r.isCovered(day(13)))
    }

    @Test
    fun aMonthlyHabitStreakCountsMonthsThatReachedTheTarget() {
        val plan = HabitPlan(interval = HabitPlan.MONTHLY, timesPerPeriod = 2)
        val r = rules(listOf(day(3, 9), day(10, 9), day(2), day(9)), plan, createdOn = day(1, 9))
        assertEquals(2, r.currentStreak())
        assertEquals(2, r.longestStreak())
    }

    @Test
    fun aRelapseHabitCountsDaysWithoutARelapse() {
        val r = rules(kind = HabitStats.KIND_RELAPSE, amounts = mapOf(day(10) to 1.0))
        assertTrue(r.isCompleted(day(9)))
        assertFalse(r.isCompleted(day(10)))
        assertEquals(4, r.currentStreak())
        assertEquals(9, r.longestStreak())
        assertEquals(13, r.cleanDays())
        assertEquals(Streaks(4, 9, 1), r.streaks())
    }

    @Test
    fun aRelapseTodayEndsTheCleanStreak() {
        val r = rules(kind = HabitStats.KIND_RELAPSE, amounts = mapOf(day(14) to 1.0))
        assertEquals(0, r.currentStreak())
    }

    @Test
    fun aCountedHabitNeedsItsGoalUnlessAnyAmountIsEnough() {
        val goal = rules(kind = HabitStats.KIND_COUNTED, target = 8.0)
        assertFalse(goal.reaches(7.0))
        assertTrue(goal.reaches(8.0))
        val any = rules(kind = HabitStats.KIND_COUNTED, target = 8.0, anyAmount = true)
        assertTrue(any.reaches(0.5))
        assertFalse(any.reaches(0.0))
    }

    @Test
    fun aPartialDayDoesNotCountAsDone() {
        val r = rules(
            kind = HabitStats.KIND_COUNTED, target = 8.0,
            amounts = mapOf(day(12) to 8.0, day(13) to 3.0, day(14) to 8.0),
        )
        assertFalse(r.isCompleted(day(13)))
        assertEquals(1, r.currentStreak())
        assertEquals(2, r.totalCompletions())
    }

    @Test
    fun theTotalIsTheNumberOfDaysThatReachedTheGoal() {
        val r = rules(listOf(day(2), day(5), day(9)))
        assertEquals(3, r.totalCompletions())
        assertEquals(Streaks(0, 1, 3), r.streaks())
    }

    @Test
    fun aHabitStartsCountingFromItsEarliestRecordIfThatIsOlderThanItsCreationDate() {
        val r = rules(listOf(day(25, 9), day(26, 9)), createdOn = day(1))
        assertEquals(day(25, 9), r.startedAt)
    }

    @Test
    fun theScheduleIsReadFromTheStreakRecord() {
        val plan = HabitPlan.fromRaw(
            """{"id":"h","interval":3,"scheduleWeekdays":[1,3,5],"scheduleStart":"04-10-2026","fromLastDone":true,
            "targetFrequency":4,"scheduleEvery":3,"scheduleUnit":1,"monthWeekday":true,"restDays":[7],
            "vacations":[{"start":"2026-10-01T00:00:00.000","end":"2026-10-03T00:00:00.000"},{"start":"2026-10-20T00:00:00.000"}]}""",
        )
        assertEquals(HabitPlan.WEEKDAYS, plan.interval)
        assertEquals(listOf(1, 3, 5), plan.weekdays)
        assertEquals(LocalDate.of(2026, 10, 4), plan.scheduleStart)
        assertTrue(plan.fromLastDone)
        assertTrue(plan.monthWeekday)
        assertEquals(4, plan.timesPerPeriod)
        assertEquals(21, plan.spanDays)
        assertEquals(listOf(7), plan.restDays)
        assertEquals(VacationSpan(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3)), plan.vacations[0])
        assertEquals(VacationSpan(LocalDate.of(2026, 10, 20), null), plan.vacations[1])
    }

    @Test
    fun aRecordThatCannotBeReadIsADailyHabit() {
        assertEquals(HabitPlan(), HabitPlan.fromRaw("not json"))
        assertEquals(HabitPlan(), HabitPlan.fromRaw("{}"))
    }

    @Test
    fun aMonthIsThirtyOneDaysWhenWorkingOutTheSpan() {
        assertEquals(62, HabitPlan(every = 2, unit = HabitPlan.UNIT_MONTHS).spanDays)
        assertEquals(2, HabitPlan(every = 2, unit = HabitPlan.UNIT_DAYS).spanDays)
    }
}
