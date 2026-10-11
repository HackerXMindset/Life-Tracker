package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StayConfidenceTest {
    private val min = 60_000L
    private val t0 = 1_760_000_000_000L

    private fun fix(atMs: Long, lat: Double = 28.6, acc: Float = 10f) = RawFixEntity(t0 + atMs, lat, 77.2, acc)

    @Test
    fun aLongWellSupportedVisitToANamedPlaceIsVerySure() {
        val readings = (0..15).map { fix(it * 2 * min) }
        val score = StayConfidence.score(t0, t0 + 30 * min, readings, named = true)
        assertTrue("score was $score", score >= 95)
        assertFalse(StayConfidence.isUnsure(score))
    }

    @Test
    fun aShortThinUnnamedStopWithPoorReadingsIsUnsure() {
        val readings = listOf(fix(0, 28.6, 60f), fix(90_000L, 28.6003, 60f), fix(180_000L, 28.6, 60f))
        val score = StayConfidence.score(t0, t0 + 3 * min, readings, named = false)
        assertTrue("score was $score", score in 30..49)
        assertTrue(StayConfidence.isUnsure(score))
    }

    @Test
    fun noReadingsMeansNotWorkedOutAndNeverUnsure() {
        assertEquals(-1, StayConfidence.score(t0, t0 + 10 * min, emptyList(), named = true))
        // Readings outside the visit do not count.
        assertEquals(-1, StayConfidence.score(t0 + 100 * min, t0 + 110 * min, listOf(fix(0), fix(min)), named = false))
        assertFalse(StayConfidence.isUnsure(-1))
    }

    @Test
    fun longSilenceCountsAgainstAVisit() {
        val dense = (0..30).map { fix(it * 2 * min) }
        val sparse = (0..2).map { fix(it * 2 * min) }
        val a = StayConfidence.score(t0, t0 + 60 * min, dense, named = true)
        val b = StayConfidence.score(t0, t0 + 60 * min, sparse, named = true)
        assertTrue("dense $a sparse $b", a > b)
    }

    @Test
    fun readingsSpreadOverAWideAreaScoreLowerThanTightOnes() {
        val tight = (0..9).map { fix(it * 2 * min) }
        val wide = (0..9).map { fix(it * 2 * min, 28.6 + (if (it % 2 == 0) 0.0 else 0.0005)) }
        assertTrue(StayConfidence.score(t0, t0 + 20 * min, tight, true) > StayConfidence.score(t0, t0 + 20 * min, wide, true))
    }

    @Test
    fun theUnsureLineIsFifty() {
        assertTrue(StayConfidence.isUnsure(0))
        assertTrue(StayConfidence.isUnsure(49))
        assertFalse(StayConfidence.isUnsure(50))
        assertFalse(StayConfidence.isUnsure(100))
    }
}
