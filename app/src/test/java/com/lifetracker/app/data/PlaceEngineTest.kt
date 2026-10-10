package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceEngineTest {
    private val min = 60_000L
    private val t0 = 1_760_000_000_000L

    // The library is a 50 m circle at 28.6100, 77.2100. 0.0001 degrees of latitude is about 11 m.
    private val library = PlaceEntity("lib", "Library", "study", 28.6100, 77.2100, 50, false, 0L)
    private val places = listOf(library)

    private fun fix(minute: Int, lat: Double, lng: Double, acc: Float = 10f) = Fix(lat, lng, acc, t0 + minute * min)

    /** Feeds readings one after the other, saving visits like the app does, and returns the last step. */
    private class Run(val places: List<PlaceEntity>, var state: WatchState = WatchState()) {
        val rows = LinkedHashMap<String, PlaceVisitEntity>()
        var last: PlaceEngine.Step? = null
        fun feed(f: Fix, now: Long = f.timeMs): PlaceEngine.Step {
            val step = PlaceEngine.onFix(state, f, places, rows.values.toList(), now)
            step.saves.forEach { rows[it.id] = it }
            state = step.state
            last = step
            return step
        }
    }

    @Test
    fun fiveMinutesAtAnUnknownSpotBecomesAStopAndKeepsWatching() {
        val run = Run(places)
        for (m in 0..3) run.feed(fix(m, 28.7000, 77.3000))
        assertTrue(run.rows.isEmpty())
        val step = run.feed(fix(5, 28.7001, 77.3000))
        assertEquals(1, run.rows.size)
        val v = run.rows.values.first()
        assertEquals("", v.placeId)
        assertTrue(v.ongoing)
        assertEquals(t0, v.startMs)
        assertEquals(t0 + 5 * min, v.endMs)
        // Unnamed spot: the readings go on.
        assertTrue(step.keepWatching)
    }

    @Test
    fun theStartIsWhenThePhoneWentStill() {
        val run = Run(places, PlaceEngine.onStill(WatchState(moving = true), t0 - 3 * min, t0))
        run.feed(fix(0, 28.7, 77.3))
        run.feed(fix(2, 28.7, 77.3))
        run.feed(fix(3, 28.7, 77.3))
        assertEquals(t0 - 3 * min, run.rows.values.first().startMs)
    }

    @Test
    fun anOldStillTimeIsNotUsed() {
        val run = Run(places, PlaceEngine.onStill(WatchState(), t0 - 3 * 60 * min, t0))
        for (m in 0..5) run.feed(fix(m, 28.7, 77.3))
        assertEquals(t0, run.rows.values.first().startMs)
    }

    @Test
    fun leavingEndsTheStopWhenYouStartedMoving() {
        val run = Run(places)
        for (m in 0..10) run.feed(fix(m, 28.7, 77.3))
        // You got up at minute 10.5 and the next reading, at minute 11, is 300 m away.
        run.state = PlaceEngine.onMoving(run.state, t0 + 10 * min + 30_000L, t0 + 10 * min + 40_000L)
        run.feed(fix(11, 28.7027, 77.3))
        val v = run.rows.values.first()
        assertFalse(v.ongoing)
        assertEquals(t0 + 10 * min + 30_000L, v.endMs)
    }

    @Test
    fun withoutMovementInfoTheLastReadingIsTheEnd() {
        val run = Run(places)
        for (m in 0..10) run.feed(fix(m, 28.7, 77.3))
        run.feed(fix(11, 28.7027, 77.3))
        val v = run.rows.values.first()
        assertFalse(v.ongoing)
        assertEquals(t0 + 10 * min, v.endMs)
    }

    @Test
    fun aShortStopIsNeverSaved() {
        val run = Run(places)
        for (m in 0..3) run.feed(fix(m, 28.7, 77.3))
        run.feed(fix(4, 28.7027, 77.3))
        assertTrue(run.rows.isEmpty())
    }

    @Test
    fun aStayInsideANamedPlaceIsAVisitToItAndRests() {
        val run = Run(places)
        run.feed(fix(0, 28.6100, 77.2100))
        val step = run.feed(fix(1, 28.6101, 77.2100))
        // Two readings at a named place are enough: Android does the rest of the watching.
        assertFalse(step.keepWatching)
        for (m in listOf(6, 7)) run.feed(fix(m, 28.6100, 77.2101))
        val v = run.rows.values.first()
        assertEquals("lib", v.placeId)
        assertEquals(28.6100, v.lat, 0.00001)
    }

    @Test
    fun justOutsideTheCircleIsNotTheLibrary() {
        // 0.0008 degrees is about 89 m: outside a 50 m circle.
        val run = Run(places)
        for (m in 0..6) run.feed(fix(m, 28.6108, 77.2100))
        assertEquals("", run.rows.values.first().placeId)
    }

    @Test
    fun aNamedStayIsClosedWhenYouAreSeenAwayLater() {
        val run = Run(places)
        run.feed(fix(0, 28.6100, 77.2100))
        run.feed(fix(1, 28.6100, 77.2100))
        run.feed(fix(7, 28.6100, 77.2100))
        // Hours later, after the phone started moving at 180, the first reading is far away.
        run.state = PlaceEngine.onMoving(run.state, t0 + 180 * min, t0 + 181 * min)
        run.feed(fix(183, 28.62, 77.22), now = t0 + 183 * min)
        val v = run.rows.values.first()
        assertEquals("lib", v.placeId)
        assertFalse(v.ongoing)
        assertEquals(t0 + 180 * min, v.endMs)
    }

    @Test
    fun comingBackWithinTwentyMinutesJoinsTheSameVisit() {
        val run = Run(places)
        for (m in 0..6) run.feed(fix(m, 28.6100, 77.2100))
        run.feed(fix(7, 28.62, 77.22))
        val first = run.rows.values.first()
        assertFalse(first.ongoing)
        // Back at minute 15, for 6 minutes.
        for (m in 15..21) run.feed(fix(m, 28.6100, 77.2100))
        assertEquals(1, run.rows.size)
        val v = run.rows.values.first()
        assertEquals(first.id, v.id)
        assertEquals(t0, v.startMs)
        assertTrue(v.ongoing)
        assertEquals(t0 + 21 * min, v.endMs)
    }

    @Test
    fun aStopYouHidIsStillHiddenWhenYouComeBack() {
        val run = Run(places)
        for (m in 0..6) run.feed(fix(m, 28.7, 77.3))
        run.feed(fix(7, 28.72, 77.3))
        val id = run.rows.values.first().id
        run.rows[id] = run.rows.getValue(id).copy(ignored = true)
        for (m in 12..18) run.feed(fix(m, 28.7, 77.3))
        assertEquals(1, run.rows.size)
        assertTrue(run.rows.getValue(id).ignored)
    }

    @Test
    fun poorReadingsAreIgnored() {
        val run = Run(places)
        run.feed(fix(0, 28.7, 77.3))
        val step = run.feed(fix(1, 28.9, 77.9, acc = 500f))
        assertEquals(1, step.state.stay!!.n)
        assertEquals(28.7, step.state.stay!!.lat, 0.0001)
    }

    @Test
    fun whileMovingTheReadingsStopAfterTenMinutesAndTheRoadStayIsDropped() {
        var state = PlaceEngine.onMoving(WatchState(), t0, t0)
        assertTrue(PlaceEngine.keepWatching(state, t0 + 5 * min))
        assertFalse(PlaceEngine.keepWatching(state, t0 + 11 * min))
        val run = Run(places, state)
        val step = run.feed(fix(11, 28.8, 77.4), now = t0 + 11 * min)
        assertFalse(step.keepWatching)
        assertNull(step.state.stay)
        assertTrue(run.rows.isEmpty())
    }

    @Test
    fun aRealStayIsKeptWhenTheMovingWindowEnds() {
        val run = Run(places)
        for (m in 0..10) run.feed(fix(m, 28.7, 77.3))
        run.state = PlaceEngine.onMoving(run.state, t0 + 10 * min, t0 + 10 * min)
        // You only walked around the building; the window passes with the stay still going.
        val step = run.feed(fix(25, 28.7002, 77.3), now = t0 + 25 * min)
        assertFalse(step.keepWatching)
        assertNotNull(step.state.stay)
        assertTrue(run.rows.values.first().ongoing)
    }

    @Test
    fun departureRules() {
        assertEquals(500L, PlaceEngine.departure(100L, 900L, 500L))
        assertEquals(100L, PlaceEngine.departure(100L, 100L + 4 * 60_000L, 0L))
        assertEquals(10_000_000L, PlaceEngine.departure(100L, 10_000_000L, 50L))
    }

    @Test
    fun stateSurvivesBeingSavedAsText() {
        val state = WatchState(
            stay = Stay(1L, 28.61, 77.21, 4, 99L, "v1", "lib", true),
            moving = true,
            triggerMs = 5L,
            moveStartMs = 6L,
            stillSinceMs = 7L,
        )
        assertEquals(state, WatchState.fromJson(state.toJson()))
        assertEquals(WatchState(), WatchState.fromJson(""))
        assertEquals(WatchState(), WatchState.fromJson("{not json"))
    }
}
