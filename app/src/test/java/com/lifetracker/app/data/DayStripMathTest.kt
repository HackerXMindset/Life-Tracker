package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayStripMathTest {
    private val day = 24 * 60 * 60_000L

    @Test
    fun fractionRunsFromStartToEndAndStaysInside() {
        assertEquals(0f, DayStripMath.fraction(1000L, 1000L, day), 0.0001f)
        assertEquals(0.5f, DayStripMath.fraction(1000L + day / 2, 1000L, day), 0.0001f)
        assertEquals(1f, DayStripMath.fraction(1000L + day * 3, 1000L, day), 0.0001f)
        assertEquals(0f, DayStripMath.fraction(0L, 1000L, day), 0.0001f)
    }

    @Test
    fun tapInsideAStretchPicksIt() {
        val s = listOf(0L to 100L, 100L to 300L, 500L to 600L)
        assertEquals(0, DayStripMath.hit(s, 50L, 10L))
        assertEquals(1, DayStripMath.hit(s, 100L, 10L))
        assertEquals(2, DayStripMath.hit(s, 599L, 10L))
    }

    @Test
    fun tapNearAThinStretchStillPicksIt() {
        val s = listOf(0L to 100L, 500L to 510L)
        assertEquals(1, DayStripMath.hit(s, 520L, 15L))
        assertEquals(1, DayStripMath.hit(s, 490L, 15L))
    }

    @Test
    fun tapInEmptySpaceSelectsNothing() {
        val s = listOf(0L to 100L, 500L to 600L)
        assertNull(DayStripMath.hit(s, 300L, 15L))
        assertNull(DayStripMath.hit(emptyList(), 300L, 15L))
    }
}
