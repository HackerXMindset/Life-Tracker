package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceRulesTest {
    private val min = 60_000L

    // Home is at 28.6000, 77.2000. 0.001 degrees of latitude is about 111 m.
    private val home = PlaceEntity("home", "Home", "home", 28.6000, 77.2000, 150, false, 0L)
    private val library = PlaceEntity("lib", "Library", "study", 28.6100, 77.2100, 100, false, 0L)
    private val places = listOf(home, library)

    @Test
    fun distanceIsAboutRight() {
        assertEquals(0.0, PlaceRules.distanceM(28.6, 77.2, 28.6, 77.2), 0.001)
        // 0.001 degrees of latitude is 111.2 m everywhere.
        assertEquals(111.2, PlaceRules.distanceM(28.600, 77.2, 28.601, 77.2), 1.0)
        // Delhi to Mumbai is about 1150 km in a straight line.
        val d = PlaceRules.distanceM(28.6139, 77.2090, 19.0760, 72.8777)
        assertTrue(d > 1_100_000 && d < 1_200_000)
    }

    @Test
    fun placeAtFindsThePlaceYouAreInside() {
        assertEquals("home", PlaceRules.placeAt(places, 28.6005, 77.2000)?.id)
        assertEquals("lib", PlaceRules.placeAt(places, 28.6100, 77.2102)?.id)
        assertNull(PlaceRules.placeAt(places, 28.6050, 77.2050))
    }

    @Test
    fun archivedPlacesAreNotWatched() {
        assertNull(PlaceRules.placeAt(listOf(home.copy(archived = true)), 28.6, 77.2))
    }

    @Test
    fun overlappingPlacesPickTheDeeperOne() {
        val big = PlaceEntity("big", "Campus", "study", 28.6000, 77.2000, 300, false, 0L)
        val small = PlaceEntity("small", "Hostel", "home", 28.6004, 77.2000, 100, false, 0L)
        assertEquals("small", PlaceRules.placeAt(listOf(big, small), 28.6004, 77.2000)?.id)
    }

    @Test
    fun unnamedStopsAreGroupedBySpotMostFrequentFirst() {
        fun stop(id: String, start: Long, lat: Double, lng: Double) =
            PlaceVisitEntity(id, "", start, start + 30 * min, lat, lng, "stop", false, false)
        val list = listOf(
            stop("a", 0L, 28.70, 77.30),
            stop("b", 1_000_000L, 28.80, 77.30),
            stop("c", 2_000_000L, 28.7003, 77.3002),
            stop("d", 3_000_000L, 28.7001, 77.2999),
        )
        val clusters = PlaceRules.clusters(list)
        assertEquals(2, clusters.size)
        assertEquals(3, clusters[0].visits.size)
        assertEquals(listOf("a", "c", "d"), clusters[0].ids)
        assertEquals(90 * min, clusters[0].totalMs)
        assertEquals(28.7, clusters[0].lat, 0.001)
        assertEquals(1, clusters[1].visits.size)
    }

    @Test
    fun totalsSkipDriveBysAndSplitAtMidnight() {
        val day = 24 * 60 * min
        val drive = PlaceVisitEntity("a", "home", 0L, 4 * min, 28.6, 77.2, "geofence", false, false)
        // Home from 22:00 to 07:00 the next day.
        val night = PlaceVisitEntity("b", "home", -2 * 60 * min, 7 * 60 * min, 28.6, 77.2, "geofence", false, false)
        val lib = PlaceVisitEntity("c", "lib", 9 * 60 * min, 12 * 60 * min, 28.61, 77.21, "stop", false, false)
        val hidden = PlaceVisitEntity("d", "lib", 13 * 60 * min, 14 * 60 * min, 28.61, 77.21, "stop", false, true)
        val unnamed = PlaceVisitEntity("e", "", 15 * 60 * min, 16 * 60 * min, 28.7, 77.3, "stop", false, false)
        val totals = PlaceRules.totals(listOf(drive, night, lib, hidden, unnamed), 0L, day, 2 * day)
        assertEquals(2, totals.size)
        // Home counts from midnight (0) to 07:00 only, which is the longest.
        assertEquals("home", totals[0].placeId)
        assertEquals(7 * 60 * min, totals[0].ms)
        assertEquals(1, totals[0].visits)
        assertEquals("lib", totals[1].placeId)
        assertEquals(3 * 60 * min, totals[1].ms)
    }

    @Test
    fun anOngoingVisitCountsUpToNow() {
        val open = PlaceVisitEntity("a", "home", 0L, 0L, 28.6, 77.2, "geofence", true, false)
        val totals = PlaceRules.totals(listOf(open), 0L, 10 * 60 * min, 120 * min)
        assertEquals(120 * min, totals[0].ms)
    }

    @Test
    fun coordinatesAreReadFromWhatMapsCopies() {
        assertEquals(28.6139 to 77.2090, PlaceRules.parseCoordinates("28.6139, 77.2090"))
        assertEquals(28.6139 to 77.2090, PlaceRules.parseCoordinates("  28.6139 77.2090 "))
        assertEquals(-33.8688 to 151.2093, PlaceRules.parseCoordinates("-33.8688, 151.2093"))
        assertNull(PlaceRules.parseCoordinates("28.6139"))
        assertNull(PlaceRules.parseCoordinates("hello"))
        assertNull(PlaceRules.parseCoordinates("128.6, 77.2"))
        assertNull(PlaceRules.parseCoordinates("28.6, 277.2"))
        assertNull(PlaceRules.parseCoordinates("1, 2, 3"))
    }

    @Test
    fun coordinatesTextUsesDots() {
        assertEquals("28.61390, 77.20900", PlaceRules.coordinatesText(28.6139, 77.209))
    }
}
