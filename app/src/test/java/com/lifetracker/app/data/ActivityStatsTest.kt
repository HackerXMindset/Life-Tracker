package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityStatsTest {
    private fun entry(category: String, start: Int, end: Int?) =
        EntryEntity(0, "2026-10-08", start, end, category, "x", "")

    private val study = DefaultActivities.list.first { it.id == "Study" }
    private val screen = ActivityTypeEntity("Screen", "Screen", 0xFF8A5FC2L, 120, ActivityTypeEntity.GOAL_AT_MOST, false, 6)
    private val reading = ActivityTypeEntity("c-r", "Reading", 0xFFD6457FL, 0, 0, false, 8)

    @Test
    fun momentsLastZeroMinutes() {
        assertEquals(0, ActivityStats.minutes(entry("Event", 600, null)))
        assertEquals(90, ActivityStats.minutes(entry("Study", 540, 630)))
        assertEquals(0, ActivityStats.minutes(entry("Study", 600, 500)))
    }

    @Test
    fun minutesAddUpPerActivity() {
        val entries = listOf(entry("Study", 0, 60), entry("Study", 100, 160), entry("Sleep", 200, 260))
        val by = ActivityStats.minutesByActivity(entries)
        assertEquals(120, by["Study"])
        assertEquals(60, by["Sleep"])
    }

    @Test
    fun minimumGoalIsReachedAtTheGoal() {
        val below = ActivityStats.GoalProgress(study, 479)
        val at = ActivityStats.GoalProgress(study, 480)
        assertFalse(below.ok)
        assertTrue(at.ok)
        assertEquals(1f, at.fraction, 0f)
    }

    @Test
    fun limitIsOkUntilItIsPassed() {
        assertTrue(ActivityStats.GoalProgress(screen, 120).ok)
        assertFalse(ActivityStats.GoalProgress(screen, 121).ok)
        assertEquals(1f, ActivityStats.GoalProgress(screen, 500).fraction, 0f)
    }

    @Test
    fun onlyActiveActivitiesWithAGoalAreListed() {
        val hiddenStudy = study.copy(archived = true)
        val entries = listOf(entry("Study", 0, 60), entry("Screen", 0, 30))
        val goals = ActivityStats.goalProgress(listOf(hiddenStudy, screen, reading), entries)
        assertEquals(listOf("Screen"), goals.map { it.type.id })
        assertEquals(30, goals.first().minutes)
        // With nothing logged an activity with a goal still appears, at zero.
        assertEquals(0, ActivityStats.goalProgress(listOf(study), emptyList()).first().minutes)
    }

    @Test
    fun missingActivityFallsBackToGrey() {
        val found = ActivityStats.find(listOf(study), "gone")
        assertEquals("gone", found.name)
        assertEquals(study, ActivityStats.find(listOf(study), "Study"))
    }

    @Test
    fun builtInActivitiesKeepTheirOldKeys() {
        val ids = DefaultActivities.list.map { it.id }
        assertEquals(listOf("Sleep", "Food", "Study", "Health", "Routine", "Exercise", "Screen", "Event"), ids)
        assertEquals(480, study.goalMinutes)
        assertTrue(ActivityStats.newId(1_700_000_000_000L).startsWith("c-"))
    }
}
