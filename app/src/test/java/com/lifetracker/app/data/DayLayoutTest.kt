package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLayoutTest {
    private val min = 60_000L
    private val dayStart = 0L
    private val dayEnd = 24 * 60 * min
    private val names = mapOf("home" to "Home", "park" to "Park")

    private fun visit(id: String, place: String, from: Long, to: Long, ongoing: Boolean = false) =
        PlaceVisitEntity(id, place, from * min, to * min, 0.0, 0.0, "auto", ongoing, false)

    private fun trip(id: String, from: Long, to: Long, fromPlace: String = "home", toPlace: String = "park") =
        TripEntity(id, from * min, to * min, fromPlace, toPlace, 1000, 5, 6, "", "walk", "auto")

    private fun item(key: String, from: Long, to: Long, kind: DayLayout.Kind = DayLayout.Kind.PHONE) =
        DayLayout.Item(key, kind, from * min, to * min, true, null)

    private fun build(
        items: List<DayLayout.Item> = emptyList(),
        visits: List<PlaceVisitEntity> = emptyList(),
        trips: List<TripEntity> = emptyList(),
        now: Long = dayEnd + 1,
    ) = DayLayout.build(items, visits, trips, names, dayStart, dayEnd, now)

    @Test
    fun everythingAtAPlaceGoesInsideOneBlock() {
        val rows = build(
            items = listOf(item("a", 0, 30), item("b", 35, 60), item("c", 62, 90)),
            visits = listOf(visit("v1", "home", 0, 100)),
        )
        val home = rows.single() as DayLayout.Place
        assertEquals("Home", home.name)
        assertEquals(listOf("a", "b", "c"), home.children.filterIsInstance<DayLayout.Thing>().map { it.key })
    }

    @Test
    fun aQuietStretchInsideAPlaceBecomesAGap() {
        val rows = build(
            items = listOf(item("a", 0, 30), item("b", 45, 60)),
            visits = listOf(visit("v1", "home", 0, 60)),
        )
        val home = rows.single() as DayLayout.Place
        val gaps = home.children.filterIsInstance<DayLayout.Gap>()
        assertEquals(1, gaps.size)
        assertEquals(30 * min, gaps[0].startMs)
        assertEquals(45 * min, gaps[0].endMs)
    }

    @Test
    fun aShortQuietStretchIsNotAGap() {
        val rows = build(items = listOf(item("a", 0, 30), item("b", 38, 60)), visits = listOf(visit("v1", "home", 0, 60)))
        assertTrue((rows.single() as DayLayout.Place).children.none { it is DayLayout.Gap })
    }

    @Test
    fun theWayBetweenTwoPlacesIsATripRowWhenThePhoneRecordedIt() {
        val rows = build(
            visits = listOf(visit("v1", "home", 0, 60), visit("v2", "park", 80, 120)),
            trips = listOf(trip("t1", 60, 80)),
        )
        assertEquals(listOf("Home", "trip", "Park"), rows.map { if (it is DayLayout.Place) it.name else if (it is DayLayout.Travel) "trip" else "?" })
        assertEquals("walk", (rows[1] as DayLayout.Travel).trip!!.mode)
    }

    @Test
    fun noTripRecordedBetweenDifferentPlacesIsAnUnknownJourney() {
        val rows = build(visits = listOf(visit("v1", "home", 0, 60), visit("v2", "park", 95, 120)))
        val travel = rows[1] as DayLayout.Travel
        assertNull(travel.trip)
        assertEquals("Home", travel.fromName)
        assertEquals("Park", travel.toName)
        assertEquals(60 * min, travel.startMs)
        assertEquals(95 * min, travel.endMs)
    }

    @Test
    fun missingTimeAtTheSamePlaceIsAGapNotAJourney() {
        val rows = build(visits = listOf(visit("v1", "home", 0, 60), visit("v2", "home", 95, 120)))
        assertTrue(rows[1] is DayLayout.Gap)
    }

    @Test
    fun aSmallBreakBetweenPlacesShowsNothing() {
        val rows = build(visits = listOf(visit("v1", "home", 0, 60), visit("v2", "park", 65, 120)))
        assertEquals(2, rows.size)
    }

    @Test
    fun nothingIsMadeUpBeforeTheFirstOrAfterTheLastThing() {
        val rows = build(visits = listOf(visit("v1", "home", 300, 360)))
        assertEquals(1, rows.size)
    }

    @Test
    fun thingsOutsideAnyPlaceStandOnTheirOwnInTimeOrder() {
        val rows = build(
            items = listOf(item("x", 65, 70)),
            visits = listOf(visit("v1", "home", 0, 60), visit("v2", "park", 80, 120)),
            trips = listOf(trip("t1", 60, 80)),
        )
        assertEquals(listOf("place", "trip", "x", "place"), rows.map {
            when (it) { is DayLayout.Place -> "place"; is DayLayout.Travel -> "trip"; else -> it.key }
        })
    }

    @Test
    fun onTodayAStayStillGoingRunsUpToNowAndNothingAfterNowIsMissing() {
        val rows = build(
            items = listOf(item("a", 0, 20)),
            visits = listOf(visit("v1", "home", 0, 0, ongoing = true)),
            now = 50 * min,
        )
        val home = rows.single() as DayLayout.Place
        assertEquals(50 * min, home.endMs)
        val gap = home.children.filterIsInstance<DayLayout.Gap>().single()
        assertEquals(20 * min, gap.startMs)
        assertEquals(50 * min, gap.endMs)
    }

    @Test
    fun aStayStartedYesterdayIsCutAtMidnight() {
        val rows = build(visits = listOf(PlaceVisitEntity("v", "home", -600 * min, 120 * min, 0.0, 0.0, "auto", false, false)))
        assertEquals(0L, (rows.single() as DayLayout.Place).startMs)
    }

    @Test
    fun aSpotWithoutANameIsStillABlock() {
        val rows = build(visits = listOf(visit("v1", "", 0, 30)))
        val p = rows.single() as DayLayout.Place
        assertEquals("Unnamed spot", p.name)
        assertEquals(false, p.named)
    }

    @Test
    fun tooShortAndHiddenStaysAreLeftOut() {
        val hidden = visit("v2", "park", 10, 60).copy(ignored = true)
        val rows = build(visits = listOf(visit("v1", "home", 0, 2), hidden))
        assertTrue(rows.isEmpty())
    }

    /** An entry made with Fill in only knows whole minutes, so it can start seconds before the stay does. */
    @Test
    fun anEntryStartingSecondsBeforeTheStayStillBelongsToIt() {
        val visits = listOf(visit("v1", "home", 0, 30), visit("v2", "park", 37, 100).copy(startMs = 37 * min + 40_000L))
        val filled = item("filled", 37, 100, DayLayout.Kind.ENTRY)
        val rows = build(items = listOf(filled), visits = visits)
        val park = rows.filterIsInstance<DayLayout.Place>().single { it.placeId == "park" }
        assertEquals(listOf("filled"), park.children.filterIsInstance<DayLayout.Thing>().map { it.key })
        assertTrue(rows.none { it is DayLayout.Thing })
    }

    @Test
    fun fillingTheWholeGapLeavesNoGapInsideThePlace() {
        val visits = listOf(visit("v2", "park", 0, 100).copy(startMs = 40_000L))
        val rows = build(items = listOf(item("filled", 0, 100, DayLayout.Kind.ENTRY)), visits = visits)
        assertTrue((rows.single() as DayLayout.Place).children.none { it is DayLayout.Gap })
    }

    @Test
    fun anItemGoesToTheStayItOverlapsMost() {
        val visits = listOf(visit("v1", "home", 0, 60), visit("v2", "park", 60, 120))
        val rows = build(items = listOf(item("x", 50, 110)), visits = visits)
        val park = rows.filterIsInstance<DayLayout.Place>().single { it.placeId == "park" }
        val home = rows.filterIsInstance<DayLayout.Place>().single { it.placeId == "home" }
        // It starts at Home, but 50 of its 60 minutes lie at the park, so it belongs to the park.
        assertEquals(1, park.children.filterIsInstance<DayLayout.Thing>().size)
        assertEquals(0, home.children.filterIsInstance<DayLayout.Thing>().size)
    }

    @Test
    fun anItemFarOutsideEveryStayStaysOnItsOwn() {
        val rows = build(items = listOf(item("far", 300, 330)), visits = listOf(visit("v1", "home", 0, 60)))
        assertTrue(rows.any { it is DayLayout.Thing })
    }
}
