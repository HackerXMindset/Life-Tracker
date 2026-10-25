package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathSpeedsTest {
    @Test
    fun speedsFallIntoTheirBands() {
        assertEquals("under 6 km/h", PathSpeeds.band(0.0).label)
        assertEquals("under 6 km/h", PathSpeeds.band(5.9).label)
        assertEquals("6 to 20 km/h", PathSpeeds.band(6.0).label)
        assertEquals("20 to 50 km/h", PathSpeeds.band(35.0).label)
        assertEquals("over 50 km/h", PathSpeeds.band(120.0).label)
    }

    @Test
    fun eachStretchHasItsOwnSpeed() {
        // About 111 m north every 10 s is 40 km/h; the second stretch takes 40 s, so about 10 km/h.
        val lat = listOf(0.0, 0.001, 0.002)
        val lng = listOf(0.0, 0.0, 0.0)
        val ms = listOf(0L, 10_000L, 50_000L)
        val s = PathSpeeds.segments(lat, lng, ms)
        assertEquals(2, s.size)
        assertEquals(40.0, s[0].kmh, 0.5)
        assertEquals(10.0, s[1].kmh, 0.5)
    }

    @Test
    fun noTimesMeansNoSpeedButStillAPath() {
        val s = PathSpeeds.segments(listOf(0.0, 0.001), listOf(0.0, 0.0), emptyList())
        assertEquals(1, s.size)
        assertEquals(0.0, s[0].kmh, 0.0)
        assertTrue(PathSpeeds.segments(listOf(0.0), listOf(0.0), listOf(0L)).isEmpty())
    }

    @Test
    fun aCircleIsClosedAndTheRightSize() {
        val ring = PathSpeeds.circle(10.0, 20.0, 500.0, 32)
        assertEquals(33, ring.size)
        assertEquals(ring.first(), ring.last())
        val far = ring.maxOf { PlaceRules.distanceM(10.0, 20.0, it.first, it.second) }
        assertEquals(500.0, far, 5.0)
    }
}
