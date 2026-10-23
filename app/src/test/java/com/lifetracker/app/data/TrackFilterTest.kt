package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFilterTest {
    private val t0 = 1_760_000_000_000L

    /** [sec] seconds after the start; 0.001 degrees of latitude is about 111 m. */
    private fun fix(sec: Int, lat: Double = 28.6, lng: Double = 77.2, acc: Float = 10f, speed: Float = -1f) =
        RawFixEntity(t0 + sec * 1000L, lat, lng, acc, speed)

    @Test
    fun theFirstReadingIsKept() {
        val v = LiveFilter.check(FilterState(), fix(0), 150, 0)
        assertNull(v.reject)
        assertEquals(fix(0), v.state.last)
    }

    @Test
    fun aReadingNotNewerThanTheLastIsRefused() {
        val state = FilterState(fix(10))
        assertEquals(Reject.OUT_OF_ORDER, LiveFilter.check(state, fix(10), 150, 0).reject)
        assertEquals(Reject.OUT_OF_ORDER, LiveFilter.check(state, fix(5), 150, 0).reject)
    }

    @Test
    fun theAccuracyLimitIsYoursAndZeroMeansNoLimit() {
        val state = FilterState(fix(0))
        val poor = fix(10, acc = 80f)
        assertEquals(Reject.TOO_INACCURATE, LiveFilter.check(state, poor, 50, 0).reject)
        assertNull(LiveFilter.check(state, poor, 100, 0).reject)
        assertNull(LiveFilter.check(state, poor, 0, 0).reject)
        // A refused reading changes nothing the filter remembers.
        assertEquals(state, LiveFilter.check(state, poor, 50, 0).state)
    }

    @Test
    fun theMovementLimitKeepsOnlyReadingsFarEnoughFromTheLastKeptOne() {
        val state = FilterState(fix(0))
        val near = fix(10, lat = 28.60004) // about 4 m
        val far = fix(20, lat = 28.6003) // about 33 m
        val v = LiveFilter.check(state, near, 150, 10)
        assertEquals(Reject.TOO_CLOSE, v.reject)
        assertEquals(fix(0), v.state.last)
        assertNull(LiveFilter.check(v.state, far, 150, 10).reject)
        assertNull(LiveFilter.check(state, near, 150, 0).reject)
    }

    @Test
    fun aJumpFasterThanACarIsRefusedUntilThreeInARowThenBelieved() {
        var state = FilterState(fix(0))
        // 11 km in half a minute or less is far beyond any vehicle.
        for (i in 1..3) {
            val v = LiveFilter.check(state, fix(10 * i, lat = 28.7 + i * 0.0001), 150, 0)
            assertEquals(Reject.JUMP, v.reject)
            state = v.state
        }
        assertEquals(3, state.jumpsInRow)
        val fourth = LiveFilter.check(state, fix(40, lat = 28.7004), 150, 0)
        assertNull(fourth.reject)
        assertEquals(0, fourth.state.jumpsInRow)
    }

    @Test
    fun aJumpThePhoneDidNotFeelIsRefusedButARealRideIsKept() {
        val state = FilterState(fix(0))
        // 1 km in 30 s (33 m/s) while the phone says it moves at 1 m/s.
        val glitch = fix(30, lat = 28.609, speed = 1f)
        assertEquals(Reject.JUMP, LiveFilter.check(state, glitch, 150, 0).reject)
        // The same distance with the phone saying 30 m/s is a real ride.
        val ride = fix(30, lat = 28.609, speed = 30f)
        assertNull(LiveFilter.check(state, ride, 150, 0).reject)
        // A walk is not a jump.
        assertNull(LiveFilter.check(state, fix(30, lat = 28.60036, speed = 1.3f), 150, 0).reject)
    }

    @Test
    fun rollingSpeedUsesThePhonesSpeedWhenItGaveOneAndPositionsWhenNot() {
        assertNull(RollingSpeed.avgMs(listOf(fix(0))))
        val byPosition = listOf(fix(0), fix(10, lat = 28.6009), fix(20, lat = 28.6018))
        assertEquals(10.0, RollingSpeed.avgMs(byPosition)!!, 0.2)
        assertEquals(36.0, RollingSpeed.avgKmh(byPosition)!!, 0.7)
        val byPhone = listOf(fix(0, speed = 2f), fix(10, speed = 2f), fix(20, speed = 2f))
        assertEquals(2.0, RollingSpeed.avgMs(byPhone)!!, 0.001)
    }

    @Test
    fun rollingSpeedLooksOnlyAtTheLastFiveReadings() {
        val fast = (0..2).map { fix(it * 10, lat = 28.6 + it * 0.0009) }
        val slow = (3..7).map { fix(it * 10, lat = 28.6018) }
        val all = fast + slow
        assertEquals(0.0, RollingSpeed.avgMs(all)!!, 0.001)
    }

    @Test
    fun stillMeansEverySpeedUnderTheLimitAcrossAFullMinute() {
        val still = (0..7).map { fix(it * 10, speed = 0f) }
        assertTrue(StillDetector.isStill(still))
        // Not yet a minute of readings.
        assertFalse(StillDetector.isStill(still.take(5)))
        // One quick step inside the minute.
        val moved = still.take(6) + fix(60, lat = 28.601) + fix(70, lat = 28.601)
        assertFalse(StillDetector.isStill(moved))
        // A quick step older than the minute does not count.
        val old = listOf(fix(0, speed = 5f), fix(10, speed = 5f)) + (2..9).map { fix(it * 10, speed = 0f) }
        assertTrue(StillDetector.isStill(old))
        assertFalse(StillDetector.isStill(emptyList()))
    }

    @Test
    fun accuracyModesHaveLabels() {
        assertEquals(3, AccuracyMode.all.size)
        assertNotNull(AccuracyMode.label(AccuracyMode.HIGH))
        assertEquals("Normal", AccuracyMode.label(""))
    }
}
