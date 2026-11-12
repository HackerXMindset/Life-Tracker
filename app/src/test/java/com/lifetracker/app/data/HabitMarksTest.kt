package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitMarksTest {
    private fun day(id: String, date: String, count: Double, marks: String, last: Int? = null) =
        CompletionEntity(id, date, count, last ?: HabitTicks.parse(marks).lastOrNull(), marks)

    @Test
    fun oneEventForEachTimeOfAWatchedHabit() {
        val events = HabitMarks.events(listOf(day("h1", "2026-11-01", 2.0, "480,900")), setOf("h1"))
        assertEquals(listOf(480, 900), events.map { it.minuteOfDay })
        assertTrue(events.all { it.trackerId == "h1" && it.date == "2026-11-01" })
    }

    @Test
    fun habitsThatAreNotPickedAreLeftOut() {
        val events = HabitMarks.events(listOf(day("h1", "2026-11-01", 1.0, "480"), day("h2", "2026-11-01", 1.0, "500")), setOf("h2"))
        assertEquals(listOf("h2"), events.map { it.trackerId })
    }

    @Test
    fun aDayWithAnAmountButNoTimeGivesOneEventWithoutATime() {
        val events = HabitMarks.events(listOf(CompletionEntity("h1", "2026-11-02", 3.0, null)), setOf("h1"))
        assertEquals(1, events.size)
        assertEquals(HabitMarks.NO_TIME, events[0].minuteOfDay)
    }

    @Test
    fun anOldSingleTimeStillCounts() {
        val events = HabitMarks.events(listOf(CompletionEntity("h1", "2026-11-02", 1.0, 615)), setOf("h1"))
        assertEquals(listOf(615), events.map { it.minuteOfDay })
    }

    @Test
    fun aClearedDayGivesNothing() {
        assertTrue(HabitMarks.events(listOf(CompletionEntity("h1", "2026-11-02", 0.0, null)), setOf("h1")).isEmpty())
    }

    @Test
    fun twoChecksAtTheSameMinuteGetDifferentIds() {
        val events = HabitMarks.events(listOf(day("h1", "2026-11-01", 2.0, "480,480")), setOf("h1"))
        assertEquals(2, events.map { it.id }.distinct().size)
    }

    @Test
    fun eventsComeInTimeOrderAcrossDays() {
        val events = HabitMarks.events(
            listOf(day("h1", "2026-11-02", 1.0, "100"), day("h1", "2026-11-01", 2.0, "900,300")),
            setOf("h1"),
        )
        assertEquals(listOf("2026-11-01" to 300, "2026-11-01" to 900, "2026-11-02" to 100), events.map { it.date to it.minuteOfDay })
    }

    @Test
    fun anIdCanBeReadBack() {
        val id = HabitMarks.eventId("abc-123", "2026-11-01", 480, 1)
        assertEquals(HabitMarks.Ref("abc-123", "2026-11-01", 480), HabitMarks.parseId(id))
        assertNull(HabitMarks.parseId("e:123456"))
        assertNull(HabitMarks.parseId("a|b|c|d"))
    }

    @Test
    fun takingOneCheckAwayKeepsTheOthers() {
        val left = HabitMarks.without(day("h1", "2026-11-01", 3.0, "300,600,900"), 600, 1.0)!!
        assertEquals(2.0, left.count, 1e-9)
        assertEquals(listOf(300, 900), HabitTicks.parse(left.marks))
        assertEquals(900, left.minuteOfDay)
    }

    @Test
    fun takingTheOnlyCheckAwayClearsTheDay() {
        assertNull(HabitMarks.without(day("h1", "2026-11-01", 1.0, "300"), 300, 1.0))
    }

    @Test
    fun aCheckWithoutATimeTakesAwayTheLastOne() {
        val left = HabitMarks.without(CompletionEntity("h1", "2026-11-01", 2.0, null), HabitMarks.NO_TIME, 1.0)!!
        assertEquals(1.0, left.count, 1e-9)
    }

    @Test
    fun aCountedStepIsTakenAwayWhole() {
        val left = HabitMarks.without(day("h1", "2026-11-01", 500.0, "300,600"), 600, 250.0)!!
        assertEquals(250.0, left.count, 1e-9)
    }
}
