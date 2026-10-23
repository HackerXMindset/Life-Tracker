package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTrailTest {
    private val min = 60_000L
    private val now = 10_000 * min
    private val names = mapOf("home" to "Home", "park" to "Park", "mosque" to "Green Park Mosque")

    // 0.009 degrees of latitude is about 1,000 m.
    private fun visit(id: String, place: String, from: Long, to: Long, lat: Double = 0.0, ongoing: Boolean = false, ignored: Boolean = false) =
        PlaceVisitEntity(id, place, from * min, to * min, lat, 0.0, "auto", ongoing, ignored)

    private fun trip(id: String, from: Long, to: Long, mode: String = "walk", source: String = "auto") =
        TripEntity(id, from * min, to * min, "home", "park", 1000, 5, 6, "", mode, source)

    private fun build(visits: List<PlaceVisitEntity>, trips: List<TripEntity> = emptyList()) =
        PlaceTrail.build(visits, trips, names, now)

    @Test
    fun aRecordedTripSitsBetweenTheTwoStays() {
        val rows = build(
            listOf(visit("a", "home", 0, 60), visit("b", "park", 75, 120, lat = 0.009)),
            listOf(trip("t", 60, 75)),
        )
        assertEquals(3, rows.size)
        assertTrue(rows[0] is PlaceTrail.Stay)
        val journey = rows[1] as PlaceTrail.Journey
        assertEquals("t", journey.trip?.id)
        assertEquals("Home", journey.fromName)
        assertEquals("Park", journey.toName)
        assertEquals(1000, journey.distanceM)
        assertTrue(rows[2] is PlaceTrail.Stay)
    }

    @Test
    fun movingBetweenTwoPlacesWithNoTripIsAnUnknownJourneyWithTheStraightLineDistance() {
        val rows = build(listOf(visit("a", "home", 0, 60), visit("b", "park", 80, 120, lat = 0.009)))
        val journey = rows[1] as PlaceTrail.Journey
        assertNull(journey.trip)
        assertTrue(journey.unknownMode)
        assertEquals(60 * min, journey.startMs)
        assertEquals(80 * min, journey.endMs)
        assertTrue(journey.distanceM in 990..1010)
    }

    @Test
    fun twoStaysAtTheSamePlaceHaveNoJourneyBetween() {
        val rows = build(listOf(visit("a", "home", 0, 60), visit("b", "home", 70, 120)))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is PlaceTrail.Stay })
    }

    @Test
    fun twoUnnamedStopsOnTheSameSpotAreNotAJourneyButFarApartTheyAre() {
        val same = build(listOf(visit("a", "", 0, 60), visit("b", "", 70, 120, lat = 0.0002)))
        assertEquals(2, same.size)
        val far = build(listOf(visit("a", "", 0, 60), visit("b", "", 70, 120, lat = 0.005)))
        assertEquals(3, far.size)
        val journey = far[1] as PlaceTrail.Journey
        assertEquals("", journey.fromName)
        assertEquals("", journey.toName)
        assertEquals("Unnamed spot", (far[0] as PlaceTrail.Stay).name)
    }

    @Test
    fun hiddenAndTooShortVisitsAreLeftOut() {
        val rows = build(
            listOf(
                visit("a", "home", 0, 60),
                visit("b", "park", 61, 62, lat = 0.009),
                visit("c", "park", 70, 120, lat = 0.009, ignored = true),
            ),
        )
        assertEquals(1, rows.size)
    }

    @Test
    fun aVisitThatIsStillGoingRunsUpToNow() {
        val rows = build(listOf(visit("a", "home", now / min - 30, now / min - 30, ongoing = true)))
        val stay = rows.single() as PlaceTrail.Stay
        assertTrue(stay.ongoing)
        assertEquals(now, stay.endMs)
    }

    @Test
    fun tripsBeforeTheFirstAndAfterTheLastStayStillShow() {
        val rows = build(listOf(visit("a", "home", 100, 160)), listOf(trip("early", 60, 90), trip("late", 170, 200)))
        assertEquals(listOf("trip", "stay", "trip"), rows.map { if (it is PlaceTrail.Stay) "stay" else "trip" })
    }

    @Test
    fun aVehicleGuessCountsAsUnknownUntilYouAnswer() {
        assertTrue(PlaceTrail.isUnknownMode(trip("t", 0, 10, mode = "vehicle")))
        assertFalse(PlaceTrail.isUnknownMode(trip("t", 0, 10, mode = "vehicle", source = "you")))
        assertFalse(PlaceTrail.isUnknownMode(trip("t", 0, 10, mode = "bus")))
        assertTrue(PlaceTrail.isUnknownMode(null))
    }

    @Test
    fun yourAnswerForAnUnknownJourneyBecomesATripRecordWithTheStraightLineDistance() {
        val rows = build(listOf(visit("a", "home", 0, 60), visit("b", "park", 80, 120, lat = 0.009)))
        val journey = rows[1] as PlaceTrail.Journey
        val made = PlaceTrail.tripFor(journey, "bus")!!
        assertEquals("bus", made.mode)
        assertEquals("you", made.modeSource)
        assertEquals("home", made.fromPlaceId)
        assertEquals("park", made.toPlaceId)
        assertEquals(60 * min, made.startMs)
        assertEquals(80 * min, made.endMs)
        assertEquals(journey.distanceM, made.distanceM)
    }

    @Test
    fun aStaleOpenVisitDoesNotHideLaterStopsInThePlaceTrail() {
        val rows = build(
            listOf(
                visit("home-old", "home", 10, 10, ongoing = true),
                visit("mosque", "mosque", 20, 60, lat = 0.009),
                visit("home-new", "home", 70, 70, ongoing = true),
            ),
        )
        val stays = rows.filterIsInstance<PlaceTrail.Stay>().sortedBy { it.startMs }

        assertEquals(listOf("home-old", "mosque", "home-new"), stays.map { it.visit.id })
        assertEquals(listOf(10 * min, 20 * min, 70 * min), stays.map { it.startMs })
        assertEquals(listOf(20 * min, 60 * min, now), stays.map { it.endMs })
        assertFalse(stays[0].ongoing)
        assertEquals("Green Park Mosque", stays[1].name)
        assertTrue(stays[2].ongoing)
    }
}
