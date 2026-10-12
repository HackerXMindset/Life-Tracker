package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySegmentsTest {
    private val min = 60_000L
    private val dayStart = 0L
    private val dayEnd = 24 * 60 * min

    private fun seg(from: Long, to: Long, label: String, logged: Boolean, argb: Int = 1) =
        DaySegments.Seg(from * min, to * min, argb, label, logged)

    @Test
    fun whatYouLoggedBeatsWhatWasCollected() {
        val out = DaySegments.flatten(
            listOf(seg(0, 100, "Phone", false), seg(30, 60, "Study", true)),
            dayStart, dayEnd,
        )
        assertEquals(listOf("Phone", "Study", "Phone"), out.map { it.label })
        assertEquals(listOf(0L, 30L, 60L), out.map { it.startMs / min })
        assertEquals(listOf(30L, 60L, 100L), out.map { it.endMs / min })
    }

    @Test
    fun nothingOverlapsAndNothingIsCountedTwice() {
        val out = DaySegments.flatten(
            listOf(seg(0, 60, "A", true), seg(30, 90, "B", true)),
            dayStart, dayEnd,
        )
        assertEquals(90 * min, DaySegments.totalMs(out))
        for (i in 0 until out.size - 1) assertTrue(out[i].endMs <= out[i + 1].startMs)
    }

    @Test
    fun stretchesAreCutAtTheEdgesOfTheDay() {
        val out = DaySegments.flatten(listOf(seg(-60, 30, "Sleep", false), seg(1400, 1500, "Late", false)), dayStart, dayEnd)
        assertEquals(0L, out.first().startMs)
        assertEquals(30 * min, out.first().endMs)
        assertEquals(dayEnd, out.last().endMs)
        assertEquals(30 * min + 40 * min, DaySegments.totalMs(out))
    }

    @Test
    fun touchingStretchesOfTheSameKindAreJoined() {
        val out = DaySegments.flatten(listOf(seg(0, 30, "Phone", false), seg(30, 50, "Phone", false)), dayStart, dayEnd)
        assertEquals(1, out.size)
        assertEquals(50 * min, out[0].endMs)
    }

    @Test
    fun totalsPerLabelAreListedLongestFirst() {
        val out = DaySegments.flatten(listOf(seg(0, 30, "A", true), seg(40, 100, "B", true), seg(120, 130, "A", true)), dayStart, dayEnd)
        val totals = DaySegments.byLabel(out)
        assertEquals(listOf("B", "A"), totals.map { it.first })
        assertEquals(40L, totals[1].third / min)
    }

    @Test
    fun emptyInputGivesNothing() {
        assertTrue(DaySegments.flatten(emptyList(), dayStart, dayEnd).isEmpty())
    }
}
