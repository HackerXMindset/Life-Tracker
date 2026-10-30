package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLayoutTest {
    private val min = 60_000L
    private val dayStart = 0L
    private val dayEnd = 24 * 60 * min
    private val names = mapOf(
        "home" to "Home", "park" to "Park", "mosque" to "Green Park Mosque",
        "sta" to "Praya Station", "stb" to "Midway Station", "far" to "Far City",
    )

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

    @Test
    fun stretchesThatOverlapSitInSeparateLanes() {
        val m = 60_000L
        val lanes = DayLayout.lanes(listOf(0L to 30 * m, 10 * m to 40 * m, 20 * m to 25 * m))
        assertEquals(listOf(0, 1, 2), lanes)
    }

    @Test
    fun stretchesOneAfterAnotherShareALane() {
        val m = 60_000L
        val lanes = DayLayout.lanes(listOf(0L to 10 * m, 10 * m to 20 * m, 25 * m to 30 * m))
        assertEquals(listOf(0, 0, 0), lanes)
    }

    @Test
    fun aFreedLaneIsUsedAgainAndTheOrderOfTheInputIsKept() {
        val m = 60_000L
        // Given out of order: the second starts first.
        val lanes = DayLayout.lanes(listOf(30 * m to 50 * m, 0L to 20 * m, 10 * m to 40 * m))
        assertEquals(listOf(0, 0, 1), lanes)
    }

    @Test
    fun aGapIsCutIntoOfflinePhoneOffAndNoData() {
        val m = 60_000L
        val offs = listOf(DayLayout.Off(20 * m, 60 * m, false), DayLayout.Off(70 * m, 100 * m, true))
        val pieces = DayLayout.cutGap(0, 120 * m, offs, 10 * m, "x")
        assertEquals(
            listOf(
                Triple(DayLayout.GapKind.NO_DATA, 0L, 20 * m),
                Triple(DayLayout.GapKind.OFFLINE, 20 * m, 60 * m),
                Triple(DayLayout.GapKind.NO_DATA, 60 * m, 70 * m),
                Triple(DayLayout.GapKind.PHONE_OFF, 70 * m, 100 * m),
                Triple(DayLayout.GapKind.NO_DATA, 100 * m, 120 * m),
            ),
            pieces.map { Triple(it.kind, it.startMs, it.endMs) },
        )
    }

    @Test
    fun phoneOffWinsWhereTheTwoOverlapAndShortPiecesAreDropped() {
        val m = 60_000L
        val offs = listOf(DayLayout.Off(0, 50 * m, false), DayLayout.Off(10 * m, 30 * m, true))
        val pieces = DayLayout.cutGap(0, 55 * m, offs, 10 * m, "x")
        assertEquals(
            listOf(
                Triple(DayLayout.GapKind.OFFLINE, 0L, 10 * m),
                Triple(DayLayout.GapKind.PHONE_OFF, 10 * m, 30 * m),
                Triple(DayLayout.GapKind.OFFLINE, 30 * m, 50 * m),
            ),
            pieces.map { Triple(it.kind, it.startMs, it.endMs) },
        )
    }

    @Test
    fun withoutOffPeriodsAGapStaysOneNoDataGap() {
        val m = 60_000L
        val pieces = DayLayout.cutGap(5 * m, 50 * m, emptyList(), 10 * m, "note")
        assertEquals(1, pieces.size)
        assertEquals(DayLayout.GapKind.NO_DATA, pieces[0].kind)
        assertEquals("note", pieces[0].note)
    }

    @Test
    fun withShowGapsOffNoGapIsDrawnAnywhereButEverythingElseStays() {
        val items = listOf(item("a", 0, 30), item("b", 45, 60), item("c", 200, 220))
        val visits = listOf(visit("v1", "home", 0, 60))
        val on = DayLayout.build(items, visits, emptyList(), names, dayStart, dayEnd, dayEnd + 1)
        val off = DayLayout.build(items, visits, emptyList(), names, dayStart, dayEnd, dayEnd + 1, showGaps = false)
        // On: a gap inside Home, and one between Home and the loose item.
        assertEquals(2, on.filterIsInstance<DayLayout.Gap>().size + (on.first() as DayLayout.Place).children.filterIsInstance<DayLayout.Gap>().size)
        // Off: none, and the same things are still there.
        assertEquals(0, off.filterIsInstance<DayLayout.Gap>().size)
        val home = off.first() as DayLayout.Place
        assertEquals(0, home.children.filterIsInstance<DayLayout.Gap>().size)
        assertEquals(listOf("a", "b"), home.children.filterIsInstance<DayLayout.Thing>().map { it.key })
        assertEquals(1, off.filterIsInstance<DayLayout.Thing>().size)
    }

    @Test
    fun aStaleOpenVisitDoesNotHideTheMosqueOrTheLaterHomeVisit() {
        val rows = build(
            visits = listOf(
                visit("home-old", "home", 10, 10, ongoing = true),
                visit("mosque", "mosque", 20, 60),
                visit("home-new", "home", 70, 70, ongoing = true),
            ),
            now = 100 * min,
        )
        val stays = rows.filterIsInstance<DayLayout.Place>().sortedBy { it.startMs }

        assertEquals(listOf("home-old", "mosque", "home-new"), stays.map { it.visit.id })
        assertEquals(listOf(10 * min, 20 * min, 70 * min), stays.map { it.startMs })
        assertEquals(listOf(20 * min, 60 * min, 100 * min), stays.map { it.endMs })
        assertFalse(stays[0].ongoing)
        assertEquals("Green Park Mosque", stays[1].name)
        assertTrue(stays[2].ongoing)
    }

    @Test
    fun twoBackToBackStaysAtTheSameNamedPlaceAreOneRow() {
        val rows = build(visits = listOf(visit("a", "park", 100, 104), visit("b", "park", 104, 112))).filterIsInstance<DayLayout.Place>()
        assertEquals(1, rows.size)
        assertEquals(100 * min, rows[0].startMs)
        assertEquals(112 * min, rows[0].endMs)
    }

    @Test
    fun aTripBetweenTwoStaysKeepsThemApart() {
        val rows = build(
            visits = listOf(visit("a", "park", 100, 104), visit("b", "park", 106, 112)),
            trips = listOf(trip("t", 104, 106, "park", "park")),
        ).filterIsInstance<DayLayout.Place>()
        assertEquals(2, rows.size)
    }

    @Test
    fun aVisibleGapOrAnotherPlaceKeepsStaysApart() {
        assertEquals(2, build(visits = listOf(visit("a", "park", 100, 104), visit("b", "park", 130, 140))).filterIsInstance<DayLayout.Place>().size)
        assertEquals(2, build(visits = listOf(visit("a", "park", 100, 104), visit("b", "home", 104, 112))).filterIsInstance<DayLayout.Place>().size)
    }

    private fun ride(id: String, from: Long, to: Long, km: Int, fromPlace: String, toPlace: String, mode: String = "train") =
        TripEntity(id, from * min, to * min, fromPlace, toPlace, km * 1000, 50, 90, "", mode, "auto")

    /** The train day: asleep at home until 5:30, then three legs of one train ride with two stations between them. */
    private fun trainDay(extra: List<DayLayout.Item> = emptyList(), stationMinutes: Long = 8, withEntry: Boolean = false): List<DayLayout.Row> {
        val sleep = DayLayout.Item("sleep1", DayLayout.Kind.SLEEP, 136 * min, 546 * min, true, null)
        val logged = if (withEntry) listOf(DayLayout.Item("note1", DayLayout.Kind.NOTE, 412 * min, 412 * min, false, null)) else emptyList()
        return build(
            items = listOf(sleep) + extra + logged,
            visits = listOf(
                visit("v1", "home", 0, 330),
                visit("v2", "sta", 410, 410 + stationMinutes),
                visit("v3", "stb", 520, 528),
                visit("v4", "far", 600, 700),
            ),
            trips = listOf(
                ride("t1", 330, 410, 60, "home", "sta"),
                ride("t2", 410 + stationMinutes, 520, 90, "sta", "stb"),
                ride("t3", 528, 600, 40, "stb", "far"),
            ),
        )
    }

    @Test
    fun stationsInsideOneTrainJourneyBecomeSmallStopsNotRows() {
        val rows = trainDay()
        val shape = rows.map { if (it is DayLayout.Place) it.name else if (it is DayLayout.Travel) "journey" else "?" }
        assertEquals(listOf("Home", "journey", "Far City"), shape)
        val journey = rows[1] as DayLayout.Travel
        assertEquals(3, journey.legs.size)
        assertEquals(listOf("Praya Station", "Midway Station"), journey.stops.map { it.name })
        assertEquals(330 * min, journey.startMs)
        assertEquals(600 * min, journey.endMs)
        assertEquals("Home", journey.fromName)
        assertEquals("Far City", journey.toName)
        assertEquals(190_000, journey.distanceM)
        assertTrue(journey.routeKnown)
    }

    @Test
    fun theSleepRowStopsWhereTheTrainJourneyBegins() {
        val rows = trainDay()
        val home = rows[0] as DayLayout.Place
        val sleep = home.children.filterIsInstance<DayLayout.Thing>().single { it.item.kind == DayLayout.Kind.SLEEP }
        assertEquals(136 * min, sleep.item.startMs)
        assertEquals(330 * min, sleep.item.endMs)
        assertTrue(rows.filterIsInstance<DayLayout.Thing>().none { it.item.kind == DayLayout.Kind.SLEEP })
    }

    @Test
    fun aStopWithSomethingYouLoggedStaysItsOwnRow() {
        val rows = trainDay(withEntry = true)
        assertTrue(rows.count { it is DayLayout.Travel } == 2)
        assertTrue(rows.any { it is DayLayout.Place && it.name == "Praya Station" })
    }

    @Test
    fun aLongStopIsAPlaceYouWentTo() {
        val rows = trainDay(stationMinutes = 40)
        val journeys = rows.filterIsInstance<DayLayout.Travel>()
        assertEquals(2, journeys.size)
        assertEquals(listOf(0, 1), journeys.map { it.stops.size })
        assertTrue(rows.any { it is DayLayout.Place && it.name == "Praya Station" })
    }

    @Test
    fun walkingLegsWithAStopBetweenAreNotOneJourney() {
        val rows = build(
            visits = listOf(visit("v1", "home", 0, 60), visit("v2", "park", 80, 90), visit("v3", "far", 110, 150)),
            trips = listOf(trip("t1", 60, 80, "home", "park"), trip("t2", 90, 110, "park", "far")),
        )
        assertEquals(2, rows.count { it is DayLayout.Travel })
        assertEquals(3, rows.count { it is DayLayout.Place })
    }

    @Test
    fun twoFastTripsWithNoStayBetweenThemJoin() {
        val rows = build(
            visits = listOf(visit("v1", "home", 0, 60), visit("v4", "far", 200, 260)),
            trips = listOf(ride("t1", 60, 120, 50, "home", ""), ride("t2", 125, 200, 70, "", "far")),
        )
        val journey = rows.filterIsInstance<DayLayout.Travel>().single()
        assertEquals(2, journey.legs.size)
        assertEquals(120_000, journey.distanceM)
    }

    @Test
    fun aJourneyYouAnsweredByHandHasNoRecordOfTheRoute() {
        val manual = TripEntity("manual100-v1-v2", 60 * min, 120 * min, "home", "far", 343_000, 343, 0, "", "train", "you")
        val rows = build(visits = listOf(visit("v1", "home", 0, 60), visit("v2", "far", 120, 200)), trips = listOf(manual))
        val journey = rows.filterIsInstance<DayLayout.Travel>().single()
        assertFalse(journey.routeKnown)
        assertFalse(DayLayout.hasRoute(manual))
        assertTrue(DayLayout.hasRoute(ride("t1", 0, 10, 5, "", "")))
    }

    @Test
    fun clippingSleepAroundJourneys() {
        val j = listOf(330 * min to 600 * min)
        assertEquals(136 * min to 330 * min, DayLayout.clipSleep(136 * min, 546 * min, j))
        assertEquals(600 * min to 700 * min, DayLayout.clipSleep(500 * min, 700 * min, j))
        assertNull(DayLayout.clipSleep(340 * min, 560 * min, j))
        assertEquals(0L to 100 * min, DayLayout.clipSleep(0, 100 * min, j))
        assertNull(DayLayout.clipSleep(325 * min, 340 * min, j))
    }

    @Test
    fun aSecondBarWithTheSameNameRightAfterTheFirstDoesNotWriteItAgain() {
        val a = DayLayout.BarName("Praya", 0, 100 * min)
        assertTrue(DayLayout.repeatsName(a, DayLayout.BarName("Praya", 101 * min, 200 * min)))
        assertFalse(DayLayout.repeatsName(a, DayLayout.BarName("Praya", 300 * min, 400 * min)))
        assertFalse(DayLayout.repeatsName(a, DayLayout.BarName("Home", 101 * min, 200 * min)))
        assertFalse(DayLayout.repeatsName(null, a))
    }
}
