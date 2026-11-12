package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerMigrationTest {
    @Test
    fun tapsBecomeTheTimesOfANewDay() {
        val day = TrackerMigration.merge(null, "h1", "2026-11-01", listOf(900, 480))
        assertEquals(2.0, day.count, 1e-9)
        assertEquals(listOf(480, 900), HabitTicks.parse(day.marks))
        assertEquals(900, day.minuteOfDay)
    }

    @Test
    fun tapsJoinAHabitThatAlreadyHasTheDay() {
        val old = CompletionEntity("h1", "2026-11-01", 1.0, 600, "600")
        val day = TrackerMigration.merge(old, "h1", "2026-11-01", listOf(480))
        assertEquals(2.0, day.count, 1e-9)
        assertEquals(listOf(480, 600), HabitTicks.parse(day.marks))
    }

    @Test
    fun doingItTwiceChangesNothing() {
        val first = TrackerMigration.merge(null, "h1", "2026-11-01", listOf(480, 900))
        val second = TrackerMigration.merge(first, "h1", "2026-11-01", listOf(480, 900))
        assertEquals(first, second)
    }

    @Test
    fun aCheckWithTheSameMinuteAlreadyThereIsNotCountedAgain() {
        val old = CompletionEntity("h1", "2026-11-01", 1.0, 480, "480")
        val day = TrackerMigration.merge(old, "h1", "2026-11-01", listOf(480, 480))
        assertEquals(2.0, day.count, 1e-9)
        assertEquals(listOf(480, 480), HabitTicks.parse(day.marks))
    }

    @Test
    fun eachNewTapIsWorthTheHabitsStep() {
        val day = TrackerMigration.merge(null, "w", "2026-11-01", listOf(480, 900), amount = 250.0)
        assertEquals(500.0, day.count, 1e-9)
    }

    @Test
    fun aHabitWithTheSameNameIsFoundWhateverTheCase() {
        assertEquals(TrackerMigration.fold("  Head  Ache "), TrackerMigration.fold("head ache"))
    }
}
