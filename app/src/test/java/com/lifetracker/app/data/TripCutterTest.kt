package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCutterTest {
    private val t0 = 1_760_000_000_000L
    private val min = 60_000L

    private fun fix(minute: Int, lat: Double, lng: Double = 77.2, acc: Float = 8f) = RawFixEntity(t0 + minute * min, lat, lng, acc)

    private fun stay(fromMinute: Int, toMinute: Int, lat: Double) = BuiltStay(t0 + fromMinute * min, t0 + toMinute * min, lat, 77.2, 5, 0L)

    @Test
    fun aSilenceOfFifteenMinutesStartsANewTrip() {
        val first = (0..4).map { fix(it, 28.6 + it * 0.001) }
        val second = (25..29).map { fix(it, 28.6 + it * 0.001) }
        val segments = TripCutter.cut(first + second)
        assertEquals(2, segments.size)
        assertEquals(t0 + 4 * min, segments[0].endMs)
        assertEquals(t0 + 25 * min, segments[1].startMs)
    }

    @Test
    fun aSilenceJustUnderFifteenMinutesKeepsOneTrip() {
        val a = (0..3).map { fix(it, 28.6 + it * 0.001) }
        val b = (17..19).map { fix(it, 28.6 + it * 0.001) }
        assertEquals(1, TripCutter.cut(a + b).size)
    }

    @Test
    fun theReadingsBetweenTwoStaysAreTheTrip() {
        val stayA = (0..10).map { fix(it, 28.6) }
        val way = listOf(11, 12, 13, 14, 15).map { fix(it, 28.6 + (it - 10) * 0.002) }
        val stayB = (16..26).map { fix(it, 28.612) }
        val stays = listOf(stay(0, 10, 28.6), stay(16, 26, 28.612))
        val trips = TripCutter.keep(TripCutter.cut(stayA + way + stayB, stays))
        assertEquals(1, trips.size)
        val trip = trips.single()
        // It starts with the stay's last reading and ends with the next stay's first.
        assertEquals(t0 + 10 * min, trip.startMs)
        assertEquals(t0 + 16 * min, trip.endMs)
        assertEquals(7, trip.points.size)
        assertTrue(trip.farthestM() > 1000)
    }

    @Test
    fun aStayOfJustTwoReadingsStillSeparatesTheTripsOnEachSide() {
        val before = (0..4).map { fix(it, 28.6 + it * 0.001) }
        val stayFixes = listOf(fix(5, 28.604), fix(8, 28.604))
        val after = (9..13).map { fix(it, 28.604 + (it - 8) * 0.001) }
        val segments = TripCutter.cut(before + stayFixes + after, listOf(stay(5, 8, 28.604)))
        assertEquals(2, segments.size)
        assertEquals(t0 + 5 * min, segments[0].endMs)
        assertEquals(t0 + 8 * min, segments[1].startMs)
    }

    @Test
    fun aWanderThatNeverGetsFarEnoughIsNotATrip() {
        val wobble = (0..5).map { fix(it, 28.6 + (it % 2) * 0.0003) } // about 33 m
        val segments = TripCutter.cut(wobble)
        assertEquals(1, segments.size)
        assertTrue(TripCutter.keep(segments).isEmpty())
        // Unless it was split off by hand.
        assertEquals(1, TripCutter.keep(listOf(Segment(wobble, manual = true))).size)
    }

    @Test
    fun aSingleReadingIsNeverATrip() {
        assertTrue(TripCutter.keep(listOf(Segment(listOf(fix(0, 28.6)), manual = true))).isEmpty())
    }

    @Test
    fun theDistanceTheTripMustReachIsYoursToChange() {
        val walk = (0..5).map { fix(it, 28.6 + it * 0.0004) } // about 220 m
        val segments = TripCutter.cut(walk)
        assertEquals(1, TripCutter.keep(segments, 100).size)
        assertEquals(0, TripCutter.keep(segments, 500).size)
    }

    @Test
    fun duplicateAndUnsortedReadingsAreHandled() {
        val a = fix(0, 28.6)
        val b = fix(1, 28.601)
        val segments = TripCutter.cut(listOf(b, a, a, b))
        assertEquals(1, segments.size)
        assertEquals(listOf(a, b), segments[0].points)
        assertFalse(TripCutter.cut(emptyList()).isNotEmpty())
    }
}
