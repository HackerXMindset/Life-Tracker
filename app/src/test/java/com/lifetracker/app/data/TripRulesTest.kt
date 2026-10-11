package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRulesTest {
    private val t0 = 1_760_000_000_000L
    private val sec = 1000L

    /** 0.001 degrees of latitude is about 111 m, so a step of 0.001 every 30 seconds is about 13 km/h. */
    private fun line(count: Int, stepDeg: Double, everySec: Int, acc: Float = 10f) =
        (0 until count).map { TripPointEntity("t", t0 + it * everySec * sec, 28.6 + it * stepDeg, 77.2, acc) }

    @Test
    fun aStraightWalkIsMeasured() {
        val pts = line(11, 0.001, 30)
        val s = TripRules.stats(pts, t0, t0 + 300 * sec)
        assertEquals(1112.0, s.distanceM.toDouble(), 5.0)
        assertEquals(13, s.medianKmh)
        assertEquals(13, s.topKmh)
        assertEquals(1112.0, s.farthestM.toDouble(), 5.0)
        assertEquals(300 * sec, s.durationMs)
        assertTrue(TripRules.isRealTrip(s))
    }

    @Test
    fun jitterAtOneSpotIsNotATrip() {
        // Readings wander about 5 m while you sit still.
        val pts = (0 until 10).map {
            TripPointEntity("t", t0 + it * 30 * sec, 28.6 + (if (it % 2 == 0) 0.00003 else -0.00003), 77.2, 12f)
        }
        val s = TripRules.stats(pts, t0, t0 + 270 * sec)
        assertEquals(0, s.distanceM)
        assertEquals(0, s.farthestM)
        assertFalse(TripRules.isRealTrip(s))
    }

    @Test
    fun oneWildReadingIsSkipped() {
        val pts = line(7, 0.001, 30).toMutableList()
        // The fourth reading jumps 55 km away and the next one is back on the road.
        pts[3] = pts[3].copy(lat = 29.1)
        val s = TripRules.stats(pts, t0, t0 + 180 * sec)
        assertEquals(667.0, s.distanceM.toDouble(), 8.0)
        assertEquals(667.0, s.farthestM.toDouble(), 8.0)
        assertTrue(s.topKmh < 30)
    }

    @Test
    fun aShortTripOrOneThatNeverGetsAwayIsNotReal() {
        assertFalse(TripRules.isRealTrip(TripStats(900, 30_000L, 20, 30, 300)))
        assertFalse(TripRules.isRealTrip(TripStats(900, 300_000L, 20, 30, 150)))
        assertTrue(TripRules.isRealTrip(TripStats(900, 300_000L, 20, 30, 250)))
    }

    @Test
    fun tooFewReadingsGiveNothing() {
        val s = TripRules.stats(line(1, 0.001, 30), t0, t0 + 60 * sec)
        assertEquals(0, s.distanceM)
        assertFalse(TripRules.isRealTrip(s))
    }

    @Test
    fun readingsOutsideTheTripAreIgnored() {
        val pts = line(11, 0.001, 30)
        val s = TripRules.stats(pts, t0 + 60 * sec, t0 + 180 * sec)
        // Readings at 60, 90, 120, 150 and 180 seconds: four steps.
        assertEquals(445.0, s.distanceM.toDouble(), 5.0)
    }

    @Test
    fun textHelpers() {
        assertEquals("850 m", TripRules.distanceText(850))
        assertEquals("4.2 km", TripRules.distanceText(4200))
        assertEquals(12, TripRules.avgKmh(1000, 300_000L))
        assertEquals(0, TripRules.avgKmh(1000, 0L))
    }

    @Test
    fun activityTextRoundTrips() {
        val map = mapOf("WALKING" to 60_000L, "IN_VEHICLE" to 540_000L, "STILL" to 5L)
        val text = TripModes.activityText(map)
        assertEquals("IN_VEHICLE=540000,WALKING=60000", text)
        assertEquals(mapOf("WALKING" to 60_000L, "IN_VEHICLE" to 540_000L), TripModes.parseActivity(text))
        assertEquals(emptyMap<String, Long>(), TripModes.parseActivity(""))
        assertEquals(emptyMap<String, Long>(), TripModes.parseActivity("junk,a=b,=5"))
    }

    private fun stats(median: Int, top: Int) = TripStats(2000, 600_000L, median, top, 1500)

    @Test
    fun slowTripsAreWalksAndFasterOnesAreCycles() {
        assertEquals("walk", TripModes.guess(stats(5, 8), emptyMap(), "", "", emptyList()))
        assertEquals("run", TripModes.guess(stats(8, 13), emptyMap(), "", "", emptyList()))
        assertEquals("cycle", TripModes.guess(stats(15, 25), emptyMap(), "", "", emptyList()))
        assertEquals("vehicle", TripModes.guess(stats(40, 60), emptyMap(), "", "", emptyList()))
    }

    @Test
    fun androidsActivityBeatsTheSpeeds() {
        val walking = mapOf("WALKING" to 500_000L, "IN_VEHICLE" to 20_000L)
        assertEquals("walk", TripModes.guess(stats(40, 60), walking, "", "", emptyList()))
        val cycling = mapOf("ON_BICYCLE" to 400_000L)
        assertEquals("cycle", TripModes.guess(stats(5, 8), cycling, "", "", emptyList()))
    }

    @Test
    fun whatYouToldItAboutTheSameTwoPlacesWins() {
        val learned = listOf(
            TripModes.Learned("home", "lib", "auto", 22, 40),
            TripModes.Learned("lib", "home", "auto", 25, 45),
            TripModes.Learned("home", "lib", "bus", 18, 35),
        )
        assertEquals("auto", TripModes.guess(stats(60, 90), emptyMap(), "home", "lib", learned))
        assertEquals("auto", TripModes.guess(stats(60, 90), emptyMap(), "lib", "home", learned))
        // Other places are not affected.
        assertEquals("vehicle", TripModes.guess(stats(60, 90), emptyMap(), "home", "gym", learned))
    }

    @Test
    fun noClearWinnerForAPairFallsBackToTheSpeeds() {
        val learned = listOf(
            TripModes.Learned("home", "lib", "auto", 22, 40),
            TripModes.Learned("home", "lib", "bus", 18, 35),
        )
        assertEquals("walk", TripModes.guess(stats(5, 8), emptyMap(), "home", "lib", learned))
    }

    @Test
    fun vehiclesAreToldApartBySpeedFromTripsYouLabelled() {
        val learned = listOf(
            TripModes.Learned("a", "b", "auto", 22, 40),
            TripModes.Learned("c", "d", "auto", 20, 38),
            TripModes.Learned("e", "f", "train", 70, 110),
            TripModes.Learned("g", "h", "train", 65, 105),
        )
        assertEquals("auto", TripModes.guess(stats(24, 42), emptyMap(), "", "", learned))
        assertEquals("train", TripModes.guess(stats(68, 108), emptyMap(), "", "", learned))
        // Nothing similar: it does not pretend to know.
        assertEquals("vehicle", TripModes.guess(stats(40, 70), emptyMap(), "", "", learned))
        // In a vehicle according to Android, same thing.
        assertEquals("auto", TripModes.guess(stats(24, 42), mapOf("IN_VEHICLE" to 500_000L), "", "", learned))
    }

    @Test
    fun everyModeHasALabel() {
        assertEquals("Auto-rickshaw", TripModes.label("auto"))
        assertEquals("Vehicle (not sure which)", TripModes.label("nonsense"))
    }
}
