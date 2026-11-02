package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalStatsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 11, 4) // a Wednesday
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(4, 18)

    private val places = listOf(
        PlaceEntity("home", "Home", "home", 0.0, 0.0, 50, false, 0L),
        PlaceEntity("gym", "Gym", "gym", 0.0, 0.0, 50, false, 0L),
        PlaceEntity("lib", "Library", "study", 0.0, 0.0, 50, false, 0L),
    )

    private fun visit(id: String, place: String, start: Long, end: Long, ongoing: Boolean = false, ignored: Boolean = false) =
        PlaceVisitEntity(id, place, start, end, 0.0, 0.0, "auto", ongoing, ignored)

    private fun trip(id: String, start: Long, end: Long, meters: Int, mode: String = "car") =
        TripEntity(id, start, end, "home", "gym", meters, 30, 50, "", mode, "auto")

    private fun goal(
        measure: String,
        period: String = GoalPeriods.WEEK,
        direction: String = GoalDirections.AT_LEAST,
        target: Double = 10.0,
        placeId: String = "",
        category: String = "",
        mode: String = "",
        days: Int = 7,
    ) = GoalEntity("g", "Goal", measure, placeId, category, mode, period, days, direction, target, true, 0L, 0)

    private fun value(g: GoalEntity, visits: List<PlaceVisitEntity> = emptyList(), trips: List<TripEntity> = emptyList()) =
        GoalStats.value(g, visits, trips, places, now, today, zone)

    @Test
    fun periodsRunFromTheRightStarts() {
        fun r(period: String, days: Int = 7) = GoalStats.range(goal(GoalMeasures.VISITS, period = period, days = days), today, zone)
        assertEquals(at(4, 0), r(GoalPeriods.DAY).fromMs)
        assertEquals(at(5, 0), r(GoalPeriods.DAY).toMs)
        assertEquals(at(2, 0), r(GoalPeriods.WEEK).fromMs) // Monday 2 Nov
        assertEquals(at(9, 0), r(GoalPeriods.WEEK).toMs)
        assertEquals(at(1, 0), r(GoalPeriods.MONTH).fromMs)
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0).atZone(zone).toInstant().toEpochMilli(), r(GoalPeriods.MONTH).toMs)
        assertEquals(LocalDateTime.of(2026, 10, 29, 0, 0).atZone(zone).toInstant().toEpochMilli(), r(GoalPeriods.DAYS, 7).fromMs)
        assertEquals(at(5, 0), r(GoalPeriods.DAYS, 7).toMs)
    }

    @Test
    fun aWeekStartingOnASundayStillStartsOnMonday() {
        val sunday = LocalDate.of(2026, 11, 8)
        val r = GoalStats.range(goal(GoalMeasures.VISITS), sunday, zone)
        assertEquals(at(2, 0), r.fromMs)
    }

    @Test
    fun hoursAtOnePlaceAKindAndAnyPlace() {
        val visits = listOf(
            visit("a", "gym", at(2, 7), at(2, 8, 30)), // 1.5 h
            visit("b", "gym", at(3, 7), at(3, 8)), // 1 h
            visit("c", "lib", at(3, 10), at(3, 12)), // 2 h
            visit("d", "gym", at(1, 7), at(1, 9)), // before the week
        )
        assertEquals(2.5, value(goal(GoalMeasures.HOURS_AT, placeId = "gym"), visits), 1e-9)
        assertEquals(2.0, value(goal(GoalMeasures.HOURS_AT, category = "study"), visits), 1e-9)
        assertEquals(4.5, value(goal(GoalMeasures.HOURS_AT), visits), 1e-9)
    }

    @Test
    fun aStayAcrossMidnightIsSplitBetweenDays() {
        val visits = listOf(visit("a", "home", at(3, 22), at(4, 6)))
        assertEquals(6.0, value(goal(GoalMeasures.HOURS_AT, GoalPeriods.DAY, placeId = "home"), visits), 1e-9)
        assertEquals(8.0, value(goal(GoalMeasures.HOURS_AT, GoalPeriods.WEEK, placeId = "home"), visits), 1e-9)
    }

    @Test
    fun anOngoingStayCountsUpToNow() {
        val visits = listOf(visit("a", "gym", at(4, 16), at(4, 16), ongoing = true))
        assertEquals(2.0, value(goal(GoalMeasures.HOURS_AT, GoalPeriods.DAY, placeId = "gym"), visits), 1e-9)
    }

    @Test
    fun hiddenAndShortStaysDoNotCount() {
        val visits = listOf(
            visit("a", "gym", at(4, 9), at(4, 10), ignored = true),
            visit("b", "gym", at(4, 11), at(4, 11, 1)), // a drive-by
            visit("c", "", at(4, 12), at(4, 13)), // not named
        )
        assertEquals(0.0, value(goal(GoalMeasures.HOURS_AT, GoalPeriods.DAY), visits), 1e-9)
        assertEquals(0.0, value(goal(GoalMeasures.VISITS, GoalPeriods.DAY), visits), 1e-9)
    }

    @Test
    fun visitsCountOnTheDayTheyStart() {
        val visits = listOf(
            visit("a", "gym", at(2, 7), at(2, 8)),
            visit("b", "gym", at(3, 7), at(3, 8)),
            visit("c", "lib", at(3, 9), at(3, 10)),
            visit("d", "gym", at(1, 7), at(1, 8)),
        )
        assertEquals(2.0, value(goal(GoalMeasures.VISITS, placeId = "gym"), visits), 1e-9)
        assertEquals(3.0, value(goal(GoalMeasures.VISITS), visits), 1e-9)
    }

    @Test
    fun timeOutIsEverythingThatIsNotHomePlusTrips() {
        val visits = listOf(
            visit("a", "home", at(4, 0), at(4, 8)),
            visit("b", "lib", at(4, 9), at(4, 12)), // 3 h
            visit("c", "home", at(4, 13), at(4, 18)),
        )
        val trips = listOf(trip("t1", at(4, 8), at(4, 9), 5000), trip("t2", at(4, 12), at(4, 13), 5000))
        assertEquals(5.0, value(goal(GoalMeasures.HOURS_OUT, GoalPeriods.DAY, category = "home"), visits, trips), 1e-9)
        // The default home kind is "home".
        assertEquals(5.0, value(goal(GoalMeasures.HOURS_OUT, GoalPeriods.DAY), visits, trips), 1e-9)
        // Calling the library home counts the two stays at the real home (13 h) and the trips (2 h) as out.
        assertEquals(15.0, value(goal(GoalMeasures.HOURS_OUT, GoalPeriods.DAY, category = "study"), visits, trips), 1e-9)
    }

    @Test
    fun distanceAndTravelTimeFollowTheWayOfTravelling() {
        val trips = listOf(
            trip("t1", at(2, 8), at(2, 9), 12_000, "car"),
            trip("t2", at(3, 8), at(3, 8, 30), 3_000, "walk"),
            trip("t3", at(1, 8), at(1, 9), 40_000, "car"), // before the week
        )
        assertEquals(15.0, value(goal(GoalMeasures.DISTANCE), trips = trips), 1e-9)
        assertEquals(12.0, value(goal(GoalMeasures.DISTANCE, mode = "car"), trips = trips), 1e-9)
        assertEquals(1.5, value(goal(GoalMeasures.HOURS_TRAVEL), trips = trips), 1e-9)
        assertEquals(0.5, value(goal(GoalMeasures.HOURS_TRAVEL, mode = "walk"), trips = trips), 1e-9)
    }

    @Test
    fun aTripThatBeganBeforeThePeriodCountsOnlyItsTimeInside() {
        val trips = listOf(trip("t", at(1, 23), at(2, 1), 50_000)) // 1 h of it on Monday
        assertEquals(1.0, value(goal(GoalMeasures.HOURS_TRAVEL), trips = trips), 1e-9)
    }

    @Test
    fun onePlaceCanFeedSeveralGoals() {
        val visits = listOf(visit("a", "gym", at(4, 7), at(4, 9)))
        val day = goal(GoalMeasures.HOURS_AT, GoalPeriods.DAY, placeId = "gym")
        val kind = goal(GoalMeasures.HOURS_AT, GoalPeriods.WEEK, category = "gym")
        val out = goal(GoalMeasures.HOURS_OUT, GoalPeriods.WEEK)
        assertEquals(2.0, value(day, visits), 1e-9)
        assertEquals(2.0, value(kind, visits), 1e-9)
        assertEquals(2.0, value(out, visits), 1e-9)
    }

    @Test
    fun aTargetIsDoneWhenReachedAndALimitIsOverWhenPassed() {
        val target = goal(GoalMeasures.HOURS_AT, target = 10.0)
        val half = GoalStats.progress(target, 5.0)
        assertEquals(0.5, half.fraction, 1e-9)
        assertFalse(half.good)
        val done = GoalStats.progress(target, 12.0)
        assertEquals(1.0, done.fraction, 1e-9)
        assertTrue(done.good)
        assertFalse(done.over)

        val limit = goal(GoalMeasures.HOURS_TRAVEL, direction = GoalDirections.AT_MOST, target = 2.0)
        assertTrue(GoalStats.progress(limit, 2.0).good)
        assertFalse(GoalStats.progress(limit, 2.0).over)
        val over = GoalStats.progress(limit, 2.5)
        assertTrue(over.over)
        assertFalse(over.good)
        assertEquals(1.0, over.fraction, 1e-9)
    }

    @Test
    fun aLimitOfZeroIsKeptOnlyWhileNothingIsCounted() {
        val none = goal(GoalMeasures.VISITS, direction = GoalDirections.AT_MOST, target = 0.0)
        assertTrue(GoalStats.progress(none, 0.0).good)
        assertTrue(GoalStats.progress(none, 1.0).over)
    }

    @Test
    fun theWordsAreShortAndSayWhatIsLeft() {
        val target = goal(GoalMeasures.HOURS_OUT, target = 10.0)
        assertEquals("6.5 of 10 h this week", GoalStats.lineText(target, GoalStats.progress(target, 6.5)))
        assertEquals("65%", GoalStats.stateText(target, GoalStats.progress(target, 6.5)))
        assertEquals("Done", GoalStats.stateText(target, GoalStats.progress(target, 10.0)))
        val limit = goal(GoalMeasures.DISTANCE, GoalPeriods.DAY, GoalDirections.AT_MOST, 20.0)
        assertEquals("12 km, at most 20 km today", GoalStats.lineText(limit, GoalStats.progress(limit, 12.0)))
        assertEquals("Kept", GoalStats.stateText(limit, GoalStats.progress(limit, 12.0)))
        assertEquals("Over", GoalStats.stateText(limit, GoalStats.progress(limit, 25.0)))
        val days = goal(GoalMeasures.VISITS, GoalPeriods.DAYS, days = 30, target = 12.0)
        assertEquals("3 of 12 visits in the last 30 days", GoalStats.lineText(days, GoalStats.progress(days, 3.0)))
    }

    @Test
    fun anEmptyNameIsMadeFromTheSettings() {
        assertEquals("Hours at Gym", GoalStats.defaultName(goal(GoalMeasures.HOURS_AT), "Gym"))
        assertEquals("Visits to Study", GoalStats.defaultName(goal(GoalMeasures.VISITS), "Study"))
        assertEquals("Hours out of the house", GoalStats.defaultName(goal(GoalMeasures.HOURS_OUT), ""))
    }

    @Test
    fun onlyGoalsSwitchedOnAreActiveAndTheEarliestStartIsFound() {
        val a = goal(GoalMeasures.VISITS, GoalPeriods.DAY)
        val b = goal(GoalMeasures.VISITS, GoalPeriods.MONTH).copy(id = "h", enabled = false)
        assertEquals(listOf(a), GoalStats.active(listOf(a, b)))
        assertEquals(at(1, 0), GoalStats.earliest(listOf(a, b), today, zone))
    }
}
