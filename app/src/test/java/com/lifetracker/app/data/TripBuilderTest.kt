package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripBuilderTest {
    private val t0 = 1_760_000_000_000L
    private val min = 60_000L
    private val stride = 0.70

    private fun at(minute: Int) = t0 + minute * min

    private fun fix(minute: Int, lat: Double) = RawFixEntity(at(minute), lat, 77.2, 8f, steps = 5_000L + minute * 160L)

    /** A stay at home, a walk of twelve minutes north, a stay at work. */
    private val stayHome = BuiltStay(at(-10), at(0), 28.600, 77.2, 11, 0L)
    private val stayWork = BuiltStay(at(12), at(30), 28.612, 77.2, 19, 0L)
    private val fixes =
        (-10..0).map { fix(it, 28.600) } +
            (1..11).map { fix(it, 28.600 + it * 0.001) } +
            (12..30).map { fix(it, 28.612) }
    private val stays = listOf(stayHome, stayWork)
    private val visits = listOf(
        PlaceVisitEntity("v1", "home", at(-10), at(0), 28.600, 77.2, "gps", false, false),
        PlaceVisitEntity("v2", "work", at(12), at(30), 28.612, 77.2, "gps", false, false),
    )

    private fun plan(
        existing: List<TripEntity> = emptyList(),
        edits: List<TripEditEntity> = emptyList(),
        settled: Long = at(120),
        fixesIn: List<RawFixEntity> = fixes,
        staysIn: List<BuiltStay> = stays,
    ) = TripBuilder.plan(fixesIn, staysIn, existing, visits, edits, emptyList(), stride, settled)

    @Test
    fun aTripBetweenTwoStaysIsBuiltWithItsPlacesAndReadings() {
        val built = plan().single()
        val trip = built.trip
        assertEquals("b${at(0)}", trip.id)
        assertEquals(at(0), trip.startMs)
        assertEquals(at(12), trip.endMs)
        assertEquals("home", trip.fromPlaceId)
        assertEquals("work", trip.toPlaceId)
        assertEquals("auto", trip.modeSource)
        assertTrue("distance ${trip.distanceM}", trip.distanceM in 1000..1400)
        assertEquals(13, built.points.size)
        assertTrue(built.points.all { it.tripId == trip.id })
        assertEquals(5_000L, built.points.first().steps)
    }

    @Test
    fun theWayYouTravelledIsGuessedFromTheSpeeds() {
        // 111 m a minute is about 6.7 km/h.
        assertEquals("walk", plan().single().trip.mode)
    }

    @Test
    fun aTripThatIsAlreadySavedIsNeverBuiltOverEvenIfYouHidIt() {
        val old = TripEntity("old", at(1), at(11), "home", "work", 1000, 6, 7, "", "walk", "you")
        assertTrue(plan(existing = listOf(old)).isEmpty())
    }

    @Test
    fun aTripThatOnlyTouchesAnotherDoesNotCountAsCovered() {
        val before = TripEntity("old", at(-40), at(0), "", "", 100, 1, 1, "", "walk", "auto")
        assertEquals(1, plan(existing = listOf(before)).size)
    }

    @Test
    fun aTripThatMayStillBeGoingWaitsForTheNextTime() {
        assertTrue(plan(settled = at(20)).isEmpty())
        assertEquals(1, plan(settled = at(27)).size)
    }

    @Test
    fun movementThatNeverGotFarIsNotATrip() {
        val wobble = (-10..0).map { fix(it, 28.600) } + (1..11).map { fix(it, 28.600 + (it % 2) * 0.00005) } + (12..30).map { fix(it, 28.600) }
        val staysHere = listOf(BuiltStay(at(-10), at(0), 28.600, 77.2, 11, 0L), BuiltStay(at(12), at(30), 28.600, 77.2, 19, 0L))
        assertTrue(plan(fixesIn = wobble, staysIn = staysHere).isEmpty())
    }

    @Test
    fun aSplitYouMadeAppliesToATripThatIsBuiltAfterwards() {
        val split = TripEditRules.splitEdit("s", at(6), 1L)
        val built = plan(edits = listOf(split))
        assertEquals(2, built.size)
        assertEquals(at(0), built[0].trip.startMs)
        assertEquals(at(6) - 0L, built[1].trip.startMs)
    }

    @Test
    fun aSplitYouUndidIsNotApplied() {
        val split = TripEditRules.splitEdit("s", at(6), 1L).copy(undoneMs = 9L)
        assertEquals(1, plan(edits = listOf(split)).size)
    }

    @Test
    fun readingsOnlyInAGapMakeNoTrip() {
        val sparse = listOf(fix(0, 28.600), fix(40, 28.610), fix(80, 28.620))
        assertTrue(plan(fixesIn = sparse, staysIn = emptyList()).isEmpty())
    }

    @Test
    fun theNearestNamedPlaceInTimeIsUsedWithinTenMinutes() {
        assertEquals("home", TripBuilder.placeNear(visits, at(5)))
        assertEquals("work", TripBuilder.placeNear(visits, at(9)))
        assertEquals("", TripBuilder.placeNear(visits, at(60)))
        assertEquals("", TripBuilder.placeNear(emptyList(), at(0)))
    }

    @Test
    fun anUnnamedOrIgnoredStayIsNotAPlace() {
        val unnamed = PlaceVisitEntity("u", "", at(0), at(10), 28.0, 77.0, "gps", false, false)
        val ignored = PlaceVisitEntity("i", "gym", at(0), at(10), 28.0, 77.0, "gps", false, true)
        assertEquals("", TripBuilder.placeNear(listOf(unnamed, ignored), at(5)))
    }
}
