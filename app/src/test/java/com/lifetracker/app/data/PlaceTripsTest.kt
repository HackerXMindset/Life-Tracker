package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTripsTest {
    private val t0 = 1_760_000_000_000L

    // The library is a 50 m circle at 28.6100, 77.2100. 0.001 degrees of latitude is about 111 m.
    private val library = PlaceEntity("lib", "Library", "study", 28.6100, 77.2100, 50, false, 0L)
    private val places = listOf(library)

    private fun fix(sec: Int, lat: Double, acc: Float = 10f) = Fix(lat, 77.2100, acc, t0 + sec * 1000L)

    private class Run(val places: List<PlaceEntity>) {
        var state = WatchState()
        val rows = LinkedHashMap<String, PlaceVisitEntity>()
        val points = ArrayList<TripPointEntity>()
        val finishes = ArrayList<TripFinish>()
        var last: PlaceEngine.Step? = null

        fun feed(f: Fix, recordTrips: Boolean = true): PlaceEngine.Step {
            val step = PlaceEngine.onFix(state, f, places, rows.values.toList(), f.timeMs, recordTrips)
            step.saves.forEach { rows[it.id] = it }
            points.addAll(step.points)
            step.finish?.let { finishes.add(it) }
            state = step.state
            last = step
            return step
        }
    }

    /** Eight minutes at the library, one reading a minute. */
    private fun atLibrary(run: Run) {
        for (s in 0..420 step 60) run.feed(fix(s, 28.6100))
    }

    /** Drives north 4.4 km at 27 km/h with a reading every 30 seconds, starting at 7:30. */
    private fun drive(run: Run) {
        for (i in 1..20) run.feed(fix(450 + 30 * i, 28.6100 + 0.002 * i))
    }

    /** Sits at the end for five minutes, one reading every 30 seconds, from 17:30. */
    private fun arrive(run: Run) {
        for (s in 1080..1350 step 30) run.feed(fix(s, 28.6500))
    }

    @Test
    fun leavingAPlaceStartsATripAndArrivingEndsIt() {
        val run = Run(places)
        atLibrary(run)
        assertNull(run.state.trip)
        assertTrue(run.points.isEmpty())

        run.state = PlaceEngine.onMoving(run.state, t0 + 450_000L, t0 + 455_000L)
        run.feed(fix(480, 28.6120))
        val trip = run.state.trip
        assertNotNull(trip)
        assertEquals("t" + (t0 + 450_000L), trip!!.id)
        assertEquals(t0 + 450_000L, trip.startMs)
        assertEquals("lib", trip.fromPlaceId)
        // The route begins at the place and has the first reading after it.
        assertEquals(2, run.points.size)
        assertEquals(t0 + 450_000L, run.points[0].ms)
        assertEquals(28.6100, run.points[0].lat, 0.0001)

        for (i in 2..20) run.feed(fix(450 + 30 * i, 28.6100 + 0.002 * i))
        assertTrue(run.finishes.isEmpty())
        arrive(run)

        assertEquals(1, run.finishes.size)
        val f = run.finishes[0]
        assertEquals(trip.id, f.id)
        assertEquals(t0 + 450_000L, f.startMs)
        // Arrived when the stay at the far end began, not five minutes later.
        assertEquals(t0 + 1_050_000L, f.endMs)
        assertEquals("lib", f.fromPlaceId)
        assertEquals("", f.toPlaceId)
        assertNull(run.state.trip)
        // 1 start + 20 on the road + 10 at the end.
        assertEquals(31, run.points.size)

        val stats = TripRules.stats(run.points, f.startMs, f.endMs)
        assertTrue(TripRules.isRealTrip(stats))
        assertEquals(4450.0, stats.distanceM.toDouble(), 120.0)
        assertEquals(26, stats.medianKmh)

        // The library visit ended when you got up, and the far end is a new stop waiting for a name.
        assertEquals(2, run.rows.size)
        val lib = run.rows.values.first { it.placeId == "lib" }
        assertFalse(lib.ongoing)
        assertEquals(t0 + 450_000L, lib.endMs)
        val stop = run.rows.values.first { it.placeId == "" }
        assertTrue(stop.ongoing)
        assertEquals(t0 + 1_050_000L, stop.startMs)
    }

    @Test
    fun androidsActivityIsCreditedToTheTrip() {
        val run = Run(places)
        atLibrary(run)
        run.state = PlaceEngine.onMoving(run.state, t0 + 450_000L, t0 + 455_000L)
        run.state = PlaceEngine.onActivity(run.state, "IN_VEHICLE", t0 + 455_000L, t0 + 456_000L)
        drive(run)
        arrive(run)
        val f = run.finishes.single()
        assertEquals(mapOf("IN_VEHICLE" to 595_000L), f.acts)
    }

    @Test
    fun walkingThenDrivingKeepsBothShares() {
        val run = Run(places)
        atLibrary(run)
        run.state = PlaceEngine.onActivity(run.state, "WALKING", t0 + 450_000L, t0 + 451_000L)
        for (i in 1..4) run.feed(fix(450 + 30 * i, 28.6100 + 0.002 * i))
        run.state = PlaceEngine.onActivity(run.state, "IN_VEHICLE", t0 + 570_000L, t0 + 571_000L)
        for (i in 5..20) run.feed(fix(450 + 30 * i, 28.6100 + 0.002 * i))
        arrive(run)
        val acts = run.finishes.single().acts
        assertEquals(120_000L, acts["WALKING"])
        assertEquals(480_000L, acts["IN_VEHICLE"])
    }

    @Test
    fun noTripsWhenSwitchedOff() {
        val run = Run(places)
        atLibrary(run)
        run.state = PlaceEngine.onMoving(run.state, t0 + 450_000L, t0 + 455_000L)
        for (i in 1..20) run.feed(fix(450 + 30 * i, 28.6100 + 0.002 * i), recordTrips = false)
        assertNull(run.state.trip)
        assertTrue(run.points.isEmpty())
        assertTrue(run.finishes.isEmpty())
    }

    @Test
    fun oneReadingAloneIsNotAPlaceToStartATripFrom() {
        val run = Run(places)
        run.feed(fix(0, 28.7000))
        run.feed(fix(30, 28.7050))
        assertNull(run.state.trip)
        assertTrue(run.points.isEmpty())
    }

    @Test
    fun gpsDriftThatComesBackIsNotARealTrip() {
        val run = Run(places)
        for (s in 0..360 step 60) run.feed(fix(s, 28.6100))
        // One reading 122 m off, then back where you were.
        run.feed(fix(420, 28.6111))
        assertNotNull(run.state.trip)
        for (s in 480..780 step 60) run.feed(fix(s, 28.6100))
        val f = run.finishes.single()
        assertEquals(t0 + 480_000L, f.endMs)
        assertEquals("lib", f.toPlaceId)
        assertFalse(TripRules.isRealTrip(TripRules.stats(run.points, f.startMs, f.endMs)))
        // It is still one visit to the library.
        assertEquals(1, run.rows.size)
        assertTrue(run.rows.values.single().ongoing)
        assertEquals(t0, run.rows.values.single().startMs)
    }

    @Test
    fun aLongSilenceEndsTheTripAtTheLastReading() {
        val run = Run(places)
        atLibrary(run)
        run.state = PlaceEngine.onMoving(run.state, t0 + 450_000L, t0 + 455_000L)
        run.feed(fix(480, 28.6120))
        run.feed(fix(510, 28.6140))
        assertNotNull(run.state.trip)
        // The phone was off for 40 minutes.
        run.feed(fix(510 + 40 * 60, 28.7000))
        val f = run.finishes.single()
        assertEquals(t0 + 510_000L, f.endMs)
        assertEquals("", f.toPlaceId)
        assertNull(run.state.trip)
    }

    @Test
    fun theTripPaceIsUsedWhileMovingOrOnATrip() {
        val still = WatchState()
        assertEquals(60, PlaceEngine.intervalSec(still, 60, 30))
        assertEquals(30, PlaceEngine.intervalSec(still.copy(moving = true), 60, 30))
        assertEquals(30, PlaceEngine.intervalSec(still.copy(trip = Trip("t1", 1L, "", 1L)), 60, 30))
        // A faster stay setting is never slowed down.
        assertEquals(15, PlaceEngine.intervalSec(still.copy(moving = true), 15, 30))
        assertEquals(15, PlaceEngine.intervalSec(still, 15, 30))
    }

    @Test
    fun readingsKeepComingOnATripEvenAtANamedPlace() {
        val stay = Stay(t0, 28.61, 77.21, 3, t0 + 120_000L, placeId = "lib")
        val state = WatchState(stay = stay, trip = Trip("t1", t0 - 600_000L, "", t0))
        assertTrue(PlaceEngine.keepWatching(state, t0 + 3_600_000L))
        // With no trip, two readings at a named place are enough.
        assertFalse(PlaceEngine.keepWatching(state.copy(trip = null), t0 + 3_600_000L))
    }

    @Test
    fun theStateWithATripSurvivesBeingSaved() {
        val state = WatchState(
            stay = Stay(t0, 28.61, 77.21, 4, t0 + 1000L, "v1", "lib", true),
            moving = true,
            triggerMs = 5L,
            moveStartMs = 6L,
            stillSinceMs = 0L,
            trip = Trip("t77", 77L, "lib", 99L, mapOf("WALKING" to 1000L, "IN_VEHICLE" to 5000L)),
            activity = "IN_VEHICLE",
            activitySinceMs = 88L,
        )
        assertEquals(state, WatchState.fromJson(state.toJson()))
    }

    @Test
    fun oldSavedStateStillLoads() {
        val old = """{"moving":false,"trigger":1,"moveStart":2,"stillSince":3}"""
        val s = WatchState.fromJson(old)
        assertNull(s.trip)
        assertEquals("", s.activity)
        assertEquals(3L, s.stillSinceMs)
        assertEquals(WatchState(), WatchState.fromJson("not json"))
    }
}
