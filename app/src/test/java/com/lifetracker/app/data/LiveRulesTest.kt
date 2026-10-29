package com.lifetracker.app.data

import com.lifetracker.app.data.LiveRules.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRulesTest {
    private val t0 = 1_760_000_000_000L

    private fun fix(sec: Long, lat: Double, lng: Double = 77.0, acc: Float = 5f, speed: Float = -1f) =
        RawFixEntity(t0 + sec * 1000L, lat, lng, acc, speed)

    private fun read(fixes: List<RawFixEntity>, nowSec: Long, tracking: Boolean = true, paused: Boolean = false) =
        LiveRules.read(fixes, t0 + nowSec * 1000L, tracking, paused, 10)

    /** 0.0001 degrees of latitude is about 11 m. */
    private fun walking(from: Int, count: Int, startLat: Double = 28.0) = (0 until count).map { fix((from + it * 8).toLong(), startLat + it * 0.0001) }

    @Test fun offAndPausedWinOverEverything() {
        val f = walking(0, 20)
        assertEquals(State.OFF, read(f, 160, tracking = false).state)
        assertEquals(State.PAUSED, read(f, 160, paused = true).state)
    }

    @Test fun noReadingsOrOldOnesMeanWaiting() {
        assertEquals(State.WAITING, read(emptyList(), 10).state)
        assertEquals(State.WAITING, read(walking(0, 5), 5 * 60 + 600).state)
    }

    @Test fun staleLimitGrowsWithTheInterval() {
        assertEquals(3 * 60_000L, LiveRules.staleAfterMs(10))
        assertEquals(15 * 60_000L, LiveRules.staleAfterMs(300))
    }

    @Test fun stayingPutIsStillAndRemembersSince() {
        val f = (0 until 40).map { fix(it * 10L, 28.0 + (it % 3) * 0.00001) }
        val live = read(f, 395)
        assertEquals(State.STILL, live.state)
        assertEquals(t0, live.sinceMs)
        assertEquals(0, live.distanceM)
    }

    @Test fun walkingIsMovingWithDistanceAndSince() {
        val f = walking(0, 40)
        val live = read(f, 40 * 8)
        assertEquals(State.MOVING, live.state)
        assertEquals(t0, live.sinceMs)
        assertTrue("distance " + live.distanceM, live.distanceM in 400..480)
        assertTrue("speed " + live.kmh, live.kmh in 4..6)
    }

    @Test fun movingAfterALongStopStartsWhereTheStopEnded() {
        val still = (0 until 61).map { fix(it * 10L, 28.0) }
        val go = walking(610, 18, startLat = 28.0001)
        val live = read(still + go, 610 + 18 * 8)
        assertEquals(State.MOVING, live.state)
        assertTrue(live.sinceMs >= t0 + 600_000L && live.sinceMs <= t0 + 660_000L)
        assertTrue("distance " + live.distanceM, live.distanceM in 100..260)
    }

    @Test fun theWatchsOwnSpeedCountsAtTheStartOfADrive() {
        val f = listOf(fix(0, 28.0), fix(5, 28.00013, speed = 3.5f))
        assertEquals(State.MOVING, read(f, 6).state)
    }

    @Test fun readingsThatStoppedAndBeganAgainStartANewRun() {
        val before = walking(0, 10)
        val after = walking(1000, 10, startLat = 28.01)
        val live = read(before + after, 1000 + 9 * 8)
        assertEquals(State.MOVING, live.state)
        assertEquals(t0 + 1_000_000L, live.sinceMs)
    }

    @Test fun textsReadPlainly() {
        val m = LiveRules.Live(State.MOVING, sinceMs = t0)
        assertEquals("On the move for 6 min", LiveRules.sinceText(m, t0 + 6 * 60_000L))
        assertEquals("Still for 1 h 5 min", LiveRules.sinceText(LiveRules.Live(State.STILL, sinceMs = t0), t0 + 65 * 60_000L))
        assertEquals("Recording is paused", LiveRules.sinceText(LiveRules.Live(State.PAUSED), t0))
    }
}
