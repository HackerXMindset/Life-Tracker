package com.lifetracker.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingMotionTest {
    @Test
    fun countingStartsAtZeroEndsAtTheTargetAndEasesOut() {
        assertEquals(0.0, ReadingMotion.count(500.0, 0f), 1e-9)
        assertEquals(500.0, ReadingMotion.count(500.0, 1f), 1e-9)
        // Eased out: more than half of the way at the half-time point.
        assertTrue(ReadingMotion.count(500.0, 0.5f) > 250.0)
        // Out-of-range times are held at the ends.
        assertEquals(500.0, ReadingMotion.count(500.0, 3f), 1e-9)
        assertEquals(0.0, ReadingMotion.count(500.0, -1f), 1e-9)
    }

    @Test
    fun thePenDrawsThenRubsOut() {
        assertEquals(0f to 0f, ReadingMotion.trace(0f))
        assertEquals(0f to 0.5f, ReadingMotion.trace(0.25f))
        assertEquals(0f to 1f, ReadingMotion.trace(0.5f))
        assertEquals(0.5f to 1f, ReadingMotion.trace(0.75f))
        assertEquals(1f to 1f, ReadingMotion.trace(1f))
    }

    @Test
    fun theMarkerBandStartsOffOneSideAndEndsOffTheOther() {
        assertEquals(-30f, ReadingMotion.sweepX(0f, 200f, 30f), 1e-4f)
        assertEquals(200f, ReadingMotion.sweepX(1f, 200f, 30f), 1e-4f)
    }

    @Test
    fun thePerimeterGoesClockwiseFromTheTopLeft() {
        assertEquals(0f to 0f, ReadingMotion.perimeter(0f, 100f, 50f))
        // A quarter of 300 is 75, which is on the top edge.
        assertEquals(75f to 0f, ReadingMotion.perimeter(0.25f, 100f, 50f))
        assertEquals(100f to 25f, ReadingMotion.perimeter(125f / 300f, 100f, 50f))
        assertEquals(100f to 50f, ReadingMotion.perimeter(0.5f, 100f, 50f))
        assertEquals(0f to 0f, ReadingMotion.perimeter(1f, 100f, 50f))
    }

    @Test
    fun theScribbleZigzagsDownTheBoxInsideItsEdges() {
        val pts = ReadingMotion.scribble(200f, 100f, 7)
        assertEquals(7, pts.size)
        assertTrue(pts.all { it.first in 0f..200f && it.second in 0f..100f })
        assertTrue(pts[0].first < pts[1].first && pts[1].first > pts[2].first)
        assertTrue(pts.zipWithNext().all { (a, b) -> b.second > a.second })
    }
}
