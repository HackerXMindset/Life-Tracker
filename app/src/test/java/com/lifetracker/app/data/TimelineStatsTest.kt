package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineStatsTest {
    @Test
    fun neverChosenGivesTheOriginalThree() {
        assertEquals(listOf(TimelineStats.Cell.STEPS, TimelineStats.Cell.PHONE, TimelineStats.Cell.STUDIED), TimelineStats.parse(null))
    }

    @Test
    fun anEmptyChoiceHidesTheBox() {
        assertTrue(TimelineStats.parse("").isEmpty())
    }

    @Test
    fun cellsKeepTheFixedOrderAndUnknownKeysAreIgnored() {
        assertEquals(
            listOf(TimelineStats.Cell.STEPS, TimelineStats.Cell.SLEEP),
            TimelineStats.parse("sleep, nonsense,steps"),
        )
    }

    @Test
    fun whatIsSavedComesBackTheSame() {
        val cells = listOf(TimelineStats.Cell.FOOD, TimelineStats.Cell.PHONE)
        assertEquals(listOf(TimelineStats.Cell.PHONE, TimelineStats.Cell.FOOD), TimelineStats.parse(TimelineStats.encode(cells)))
        assertEquals("", TimelineStats.encode(emptyList()))
    }
}
