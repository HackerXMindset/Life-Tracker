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
        // 300x: one real second is five minutes of the day.
        assertEquals(5 * min, ReplayRules.advance(0, 1000, 300.0, false, emptyList(), 24 * 60 * min))
        assertEquals(10 * min, ReplayRules.advance(0, 1000, 600.0, false, emptyList(), 24 * 60 * min))
        // Real time is 1x.
        assertEquals(1000L, ReplayRules.advance(0, 1000, 1.0, false, emptyList(), 24 * 60 * min))
        assertEquals(60 * min, ReplayRules.advance(0, 100, 36_000.0, false, emptyList(), 60 * min))
        // Smart speed's factor multiplies it.
        assertEquals(500L, ReplayRules.advance(0, 1000, 1.0, false, emptyList(), 24 * 60 * min, 0.5))
    }

    @Test
    fun skippingMakesQuietStretchesGoAtLeastFourHoursASecond() {
        val quiet = listOf(Quiet(0, 100 * min))
        assertEquals(60.0, ReplayRules.rate(10 * min, 60.0, false, quiet), 1e-9)
        assertEquals(240 * min, ReplayRules.advance(10 * min, 1000, 60.0, true, quiet, 24 * 60 * min) - 10 * min)
        // A speed already faster than the skip speed is kept, and outside a quiet stretch nothing changes.
        assertEquals(36_000.0, ReplayRules.rate(10 * min, 36_000.0, true, quiet), 1e-9)
        assertEquals(60.0, ReplayRules.rate(200 * min, 60.0, true, quiet), 1e-9)
    }

    @Test
    fun theClockStopsAtTheEndOfTheDay() {
        assertEquals(100 * min, ReplayRules.advance(99 * min, 60_000, 3600.0, false, emptyList(), 100 * min))
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

    @Test
    fun groundSpeedIsHowFastYouReallyMoved() {
        // 0.0054 degrees of latitude is about 600 m; readings every 30 s, so 20 m/s.
        val train = (0..10).map { s(it * 0L, 12.0) }.let { _ -> (0..10).map { Sample(it * 30_000L, 12.0 + it * 0.0054, 77.0) } }
        assertEquals(20.0, ReplayRules.groundSpeedMs(train, 150_000L), 0.5)
        val still = listOf(s(0, 12.0), s(10, 12.0), s(20, 12.0))
        assertEquals(0.0, ReplayRules.groundSpeedMs(still, 10 * min), 1e-9)
        assertEquals(0.0, ReplayRules.groundSpeedMs(emptyList(), 0), 1e-9)
    }

    @Test
    fun theMapZoomsOutAsYouGoFaster() {
        val walk = ReplayRules.autoZoom(1.4)
        val bike = ReplayRules.autoZoom(5.0)
        val car = ReplayRules.autoZoom(15.0)
        val train = ReplayRules.autoZoom(30.0)
        val plane = ReplayRules.autoZoom(250.0)
        assertTrue(walk > bike && bike > car && car > train && train > plane)
        assertEquals(17.0, ReplayRules.autoZoom(0.0), 1e-9)
        assertTrue(walk in 15.5..17.0)
        assertTrue(car in 12.5..14.5)
        assertTrue(plane >= 5.0)
        assertEquals(17.0, ReplayRules.autoZoom(-3.0), 1e-9)
    }

    @Test
    fun theShownZoomGlidesTowardsTheWantedOne() {
        val a = ReplayRules.easeZoom(17.0, 13.0)
        assertTrue(a < 17.0 && a > 13.0)
        var z = 17.0
        repeat(100) { z = ReplayRules.easeZoom(z, 13.0) }
        assertEquals(13.0, z, 0.01)
    }

    @Test
    fun speedsGoUpToADayASecondAndAreNamedInPlainWords() {
        assertEquals("1 day/s", ReplayRules.speedLabel(86_400.0))
        assertEquals("8 h/s", ReplayRules.speedLabel(28_800.0))
        assertEquals("5 min/s", ReplayRules.speedLabel(300.0))
        assertEquals("10x", ReplayRules.speedLabel(10.0))
        assertEquals("1x", ReplayRules.speedLabel(1.0))
        assertEquals(86_400.0, ReplayRules.SPEEDS.last(), 0.0)
        assertEquals(1.0, ReplayRules.SPEEDS.first(), 0.0)
        // Real time and slow steps are on offer: a minute a second is no longer the slowest.
        assertTrue(ReplayRules.SPEEDS.count { it < 60.0 } >= 4)
    }

    private fun walk(): List<Sample> {
        // Walking about in a small area: small steps every 10 s that turn back and forth.
        val out = ArrayList<Sample>()
        for (i in 0..60) out += Sample(i * 10_000L, 12.0 + (if (i % 2 == 0) 0.0 else 0.0002) + i * 0.00001, 77.0 + (if (i % 4 < 2) 0.0 else 0.0002))
        return out
    }

    private fun train(): List<Sample> = (0..60).map { Sample(it * 10_000L, 12.0 + it * 0.0018, 77.0) }

    @Test
    fun walkingAboutIsBusyAndAStillStretchIsQuiet() {
        val busy = ReplayRules.busyness(walk(), emptyList(), 300_000L)
        val still = ReplayRules.busyness(listOf(s(0, 12.0), s(10, 12.0), s(20, 12.0)), emptyList(), 10 * min)
        assertTrue("walking about should be busy, was $busy", busy > 0.6)
        assertEquals(0.0, still, 1e-9)
    }

    @Test
    fun aFastStraightRideIsLessBusyThanWalkingAbout() {
        val ride = ReplayRules.busyness(train(), emptyList(), 300_000L)
        val walkAbout = ReplayRules.busyness(walk(), emptyList(), 300_000L)
        assertTrue(ride < walkAbout)
    }

    @Test
    fun somethingThatHappenedNearbyMakesAMomentBusy() {
        val still = listOf(s(0, 12.0), s(10, 12.0), s(20, 12.0))
        val event = listOf(Event(11 * min, "Arrived", "", Kind.ARRIVE))
        assertEquals(0.5, ReplayRules.busyness(still, event, 10 * min), 1e-9)
        assertEquals(0.0, ReplayRules.busyness(still, event, 18 * min), 1e-9)
    }

    @Test
    fun smartFactorGoesFromTheQuietBoostToTheBusySlowDown() {
        val smart = ReplayRules.Smart(0.1, 3.0)
        val still = listOf(s(0, 12.0), s(10, 12.0), s(20, 12.0))
        assertEquals(3.0, ReplayRules.smartFactor(smart, still, emptyList(), 10 * min), 1e-9)
        val busyFactor = ReplayRules.smartFactor(smart, walk(), emptyList(), 300_000L)
        assertTrue(busyFactor < 1.0)
        assertTrue(busyFactor >= 0.1 - 1e-9)
        // No slow-down and no boost leaves the speed as it is.
        assertEquals(1.0, ReplayRules.smartFactor(ReplayRules.Smart(1.0, 1.0), walk(), emptyList(), 300_000L), 1e-9)
    }

    @Test
    fun theFactorGlidesAndNeverOvershoots() {
        val a = ReplayRules.easeFactor(1.0, 0.1, 100)
        assertTrue(a < 1.0 && a > 0.1)
        var f = 1.0
        repeat(200) { f = ReplayRules.easeFactor(f, 0.1, 100) }
        assertEquals(0.1, f, 0.001)
        assertEquals(1.0, ReplayRules.easeFactor(1.0, 1.0, 100), 1e-9)
    }

    @Test
    fun distanceSoFarGrowsAlongTheRouteAndSkipsSilences() {
        val samples = listOf(s(0, 12.0), s(10, 12.0054), s(20, 12.0108), s(200, 13.0))
        val cum = ReplayRules.cumulative(samples)
        assertEquals(0.0, cum[0], 1e-9)
        assertEquals(1200.0, cum[2], 15.0)
        // The jump across the long silence is not counted.
        assertEquals(cum[2], cum[3], 1e-9)
        assertEquals(600.0, ReplayRules.distanceAt(samples, cum, 10 * min), 8.0)
        assertEquals(300.0, ReplayRules.distanceAt(samples, cum, 5 * min), 8.0)
        assertEquals(0.0, ReplayRules.distanceAt(samples, cum, -1), 1e-9)
    }

    @Test
    fun theBrightTrailOnlyHoldsTheLastStretch() {
        val samples = (0..10).map { s(it * 10L, 12.0 + it * 0.001) }
        val all = ReplayRules.trail(samples, 100 * min)
        val recent = ReplayRules.trail(samples, 100 * min, 70 * min)
        assertEquals(11, all.single().size)
        assertEquals(4, recent.single().size)
        assertEquals(12.007, recent.single().first().first, 1e-9)
    }
}
