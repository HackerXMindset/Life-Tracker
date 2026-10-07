package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitStatsTest {
    private val today = LocalDate.of(2026, 10, 7)
    private fun d(offset: Int): LocalDate = today.plusDays(offset.toLong())

    private fun habit(kind: Int, target: Double = 1.0, anyAmount: Boolean = false) = HabitEntity(
        "h", "H", "target", "", 0, kind, 0, "", target, 1.0, anyAmount, -1, 0, "2026-09-01", false, 0, "{}",
    )

    @Test
    fun noDaysMeansNoStreak() {
        assertEquals(Streaks(0, 0, 0), HabitStats.streaks(emptySet(), today))
    }

    @Test
    fun streakCountsBackFromToday() {
        val s = HabitStats.streaks(setOf(d(0), d(-1), d(-2), d(-4)), today)
        assertEquals(3, s.current)
        assertEquals(3, s.best)
        assertEquals(4, s.totalDays)
    }

    @Test
    fun streakSurvivesUntilTodayEnds() {
        // Today is not done yet, but yesterday and the day before were.
        val s = HabitStats.streaks(setOf(d(-1), d(-2)), today)
        assertEquals(2, s.current)
    }

    @Test
    fun missedYesterdayBreaksTheStreak() {
        val s = HabitStats.streaks(setOf(d(-2), d(-3), d(-4)), today)
        assertEquals(0, s.current)
        assertEquals(3, s.best)
    }

    @Test
    fun bestStreakIsTheLongestRun() {
        val s = HabitStats.streaks(setOf(d(-10), d(-9), d(-8), d(-7), d(-3), d(-2), d(0)), today)
        assertEquals(1, s.current)
        assertEquals(4, s.best)
    }

    @Test
    fun cleanStreakWithNoRelapses() {
        val s = HabitStats.cleanStreaks(emptySet(), d(-9), today)
        assertEquals(Streaks(10, 10, 0), s)
    }

    @Test
    fun cleanStreakCountsDaysSinceLastRelapse() {
        val s = HabitStats.cleanStreaks(setOf(d(-20), d(-5)), d(-30), today)
        assertEquals(5, s.current)
        assertEquals(14, s.best) // between the two relapses: 14 clean days
        assertEquals(2, s.totalDays)
    }

    @Test
    fun relapseTodayResetsToZero() {
        val s = HabitStats.cleanStreaks(setOf(d(0)), d(-3), today)
        assertEquals(0, s.current)
        assertEquals(3, s.best)
    }

    @Test
    fun goalRules() {
        assertTrue(HabitStats.reachedGoal(habit(0), 1.0))
        assertFalse(HabitStats.reachedGoal(habit(0), 0.0))
        assertFalse(HabitStats.reachedGoal(habit(2, target = 8.0), 7.0))
        assertTrue(HabitStats.reachedGoal(habit(2, target = 8.0), 8.0))
        assertTrue(HabitStats.reachedGoal(habit(2, target = 3000.0, anyAmount = true), 1.0))
    }
}
