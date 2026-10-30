package com.lifetracker.app.data

import com.lifetracker.app.data.ReplayRules.Event
import com.lifetracker.app.data.ReplayRules.Kind
import com.lifetracker.app.data.ReplayRules.Quiet
import com.lifetracker.app.data.ReplayRules.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayRulesTest {
    private val min = 60_000L
    private fun s(m: Long, lat: Double, lng: Double = 77.0) = Sample(m * min, lat, lng)

    @Test
    fun positionIsWorkedOutBetweenTheTwoNearestReadings() {
        val samples = listOf(s(0, 10.0), s(10, 10.1), s(20, 10.3))
        assertEquals(10.05, ReplayRules.positionAt(samples, 5 * min)!!.first, 1e-9)
        assertEquals(10.2, ReplayRules.positionAt(samples, 15 * min)!!.first, 1e-9)
        assertEquals(10.1, ReplayRules.positionAt(samples, 10 * min)!!.first, 1e-9)
        assertEquals(10.3, ReplayRules.positionAt(samples, 20 * min)!!.first, 1e-9)
    }

    @Test
    fun noPositionBeforeTheFirstOrAfterTheLastReading() {
        val samples = listOf(s(10, 10.0), s(20, 10.1))
        assertNull(ReplayRules.positionAt(samples, 5 * min))
        assertNull(ReplayRules.positionAt(samples, 25 * min))
        assertNull(ReplayRules.positionAt(emptyList(), 0))
    }

    @Test
    fun theDotStaysPutAcrossALongSilence() {
        val samples = listOf(s(0, 10.0), s(120, 11.0))
        assertEquals(10.0, ReplayRules.positionAt(samples, 60 * min)!!.first, 1e-9)
        assertEquals(11.0, ReplayRules.positionAt(samples, 120 * min)!!.first, 1e-9)
    }

    @Test
    fun theTrailEndsAtTheDotAndDoesNotJoinAcrossASilence() {
        val samples = listOf(s(0, 10.0), s(5, 10.1), s(120, 12.0), s(125, 12.1))
        val one = ReplayRules.trail(samples, 2 * min + 30_000L)
        assertEquals(1, one.size)
        assertEquals(2, one[0].size)
        val two = ReplayRules.trail(samples, 122 * min)
        assertEquals(2, two.size)
        assertEquals(12.0, two[1].first().first, 1e-9)
        assertTrue(ReplayRules.trail(samples, -1).isEmpty())
    }

    @Test
    fun aLongStayAndASilenceAreQuietStretches() {
        // Still for 90 minutes, a walk, then a 3-hour silence.
        val samples = listOf(s(0, 10.0), s(45, 10.0001), s(90, 10.0), s(100, 10.01), s(105, 10.02), s(285, 10.02))
        val quiet = ReplayRules.quietSpans(samples, 0, 300 * min)
        assertEquals(listOf(0L to 90 * min, 105 * min to 285 * min), quiet.map { it.startMs to it.endMs })
    }

    @Test
    fun aDayWithNoReadingsIsOneQuietStretch() {
        val quiet = ReplayRules.quietSpans(emptyList(), 0, 24 * 60 * min)
        assertEquals(1, quiet.size)
    }

    @Test
    fun theSpeedOnlyChangesHowFastTheClockRuns() {
        // 5 min/s: one real second is five minutes of the day.
        assertEquals(5 * min, ReplayRules.advance(0, 1000, 5, false, emptyList(), 24 * 60 * min))
        assertEquals(10 * min, ReplayRules.advance(0, 1000, 10, false, emptyList(), 24 * 60 * min))
        assertEquals(60 * min, ReplayRules.advance(0, 100, 600, false, emptyList(), 60 * min))
    }

    @Test
    fun skippingMakesQuietStretchesGoAtLeastFourHoursASecond() {
        val quiet = listOf(Quiet(0, 100 * min))
        assertEquals(60.0, ReplayRules.rate(10 * min, 1, false, quiet), 1e-9)
        assertEquals(240 * min, ReplayRules.advance(10 * min, 1000, 1, true, quiet, 24 * 60 * min) - 10 * min)
        // A speed already faster than the skip speed is kept, and outside a quiet stretch nothing changes.
        assertEquals(36_000.0, ReplayRules.rate(10 * min, 600, true, quiet), 1e-9)
        assertEquals(60.0, ReplayRules.rate(200 * min, 1, true, quiet), 1e-9)
    }

    @Test
    fun theClockStopsAtTheEndOfTheDay() {
        assertEquals(100 * min, ReplayRules.advance(99 * min, 60_000, 60, false, emptyList(), 100 * min))
    }

    @Test
    fun eventsCrossedInOneStepAreReportedOldestFirst() {
        val events = listOf(Event(30 * min, "b", "", Kind.LOGGED), Event(10 * min, "a", "", Kind.ARRIVE), Event(50 * min, "c", "", Kind.LEAVE))
        assertEquals(listOf("a", "b"), ReplayRules.crossed(events, 0, 30 * min).map { it.title })
        assertEquals(listOf("c"), ReplayRules.crossed(events, 30 * min, 60 * min).map { it.title })
        assertEquals(30 * min, ReplayRules.stepTo(events, 10 * min, true))
        assertEquals(10 * min, ReplayRules.stepTo(events, 30 * min, false))
        assertNull(ReplayRules.stepTo(events, 50 * min, true))
    }
}
