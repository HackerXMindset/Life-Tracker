package com.lifetracker.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F1: moving about inside a named place stays one stay; a trip begins only when you leave it. */
class StaysInsideTest {
    private val min = 60_000L
    private val t0 = 1_760_000_000_000L

    // Home is a 100 m circle. 0.0001 degrees of latitude is about 11 m.
    private val home = PlaceEntity("home", "Home", "home", 28.6100, 77.2100, 100, false, 0L)
    private val shed = PlaceEntity("shed", "Shed", "other", 28.6107, 77.2100, 15, false, 0L)

    private fun fix(minute: Int, lat: Double, lng: Double = 77.2100, acc: Float = 10f) = Fix(lat, lng, acc, t0 + minute * min)

    private class Run(val places: List<PlaceEntity>) {
        var state = WatchState()
        val rows = LinkedHashMap<String, PlaceVisitEntity>()
        var finishes = 0
        fun feed(f: Fix): PlaceEngine.Step {
            val step = PlaceEngine.onFix(state, f, places, rows.values.toList(), f.timeMs)
            step.saves.forEach { rows[it.id] = it }
            if (step.finish != null) finishes++
            state = step.state
            return step
        }
    }

    @After
    fun reset() = TripGuess.forTest(false)

    @Test
    fun walkingAroundInsideAPlaceNeverStartsATrip() {
        val run = Run(listOf(home))
        for (m in 0..3) run.feed(fix(m, 28.6100))
        // 80 m north: inside the 100 m circle but outside the 50 m stay radius.
        for (m in 4..12) run.feed(fix(m, 28.6107))
        for (m in 13..16) run.feed(fix(m, 28.6100))
        assertEquals(1, run.rows.size)
        assertNull(run.state.trip)
        assertEquals(0, run.finishes)
        assertEquals("home", run.rows.values.single().placeId)
        assertEquals(t0 + 16 * min, run.rows.values.single().endMs)
    }

    @Test
    fun leavingThePlaceStartsATrip() {
        val run = Run(listOf(home))
        for (m in 0..3) run.feed(fix(m, 28.6100))
        for (m in 4..6) run.feed(fix(m, 28.6107))
        run.feed(fix(7, 28.6200))
        assertNotNull(run.state.trip)
        assertEquals("home", run.state.trip!!.fromPlaceId)
    }

    @Test
    fun aSmallerPlaceInsideABiggerOneStillGetsItsOwnStay() {
        val run = Run(listOf(home, shed))
        for (m in 0..3) run.feed(fix(m, 28.6100))
        // The shed (15 m circle) lies 78 m north of the house centre: walking in is a new stay at the shed.
        for (m in 4..8) run.feed(fix(m, 28.6107))
        assertTrue(run.rows.values.any { it.placeId == "shed" })
    }

    @Test
    fun stillInsideKnowsTheSmallestPlace() {
        assertTrue(PlaceRules.stillInside(listOf(home), "home", 28.6107, 77.2100, 10f))
        assertFalse(PlaceRules.stillInside(listOf(home, shed), "home", 28.6107, 77.2100, 10f))
        assertFalse(PlaceRules.stillInside(listOf(home), "", 28.6100, 77.2100, 10f))
        assertFalse(PlaceRules.stillInside(listOf(home), "home", 28.6200, 77.2100, 10f))
    }

    // ---- the "Walked about" line ----

    private fun raw(minute: Int, lat: Double, steps: Long = -1L) =
        RawFixEntity(ms = t0 + minute * min, lat = lat, lng = 77.2100, acc = 10f, steps = steps)

    @Test
    fun stepsPerMinuteSayWhenYouWereWalking() {
        val spans = InsideWalk.spans(listOf(raw(0, 28.61, 100), raw(1, 28.61, 190), raw(2, 28.61, 195), raw(3, 28.61, 290)))
        assertEquals(listOf((t0) to (t0 + min), (t0 + 2 * min) to (t0 + 3 * min)), spans)
    }

    @Test
    fun withoutStepsMovingFasterThanAStrollCounts() {
        val spans = InsideWalk.spans(listOf(raw(0, 28.6100), raw(1, 28.6107), raw(2, 28.6107)))
        assertEquals(1, spans.size)
    }

    @Test
    fun aLongGapBetweenReadingsIsNotCounted() {
        assertTrue(InsideWalk.spans(listOf(raw(0, 28.6100, 0), raw(10, 28.6200, 1000))).isEmpty())
    }

    @Test
    fun walkedTextNeedsThreeMinutes() {
        assertNull(InsideWalk.text(2 * min))
        assertEquals("Walked about " + PlaceRules.durationText(15 * min), InsideWalk.text(15 * min))
    }

    // ---- old trips that never left the place ----

    private fun trip(id: String, from: String, to: String) =
        TripEntity(id, t0 + 10 * min, t0 + 20 * min, from, to, 120, 3, 5, "", "walk", "auto")

    private fun point(id: String, minute: Int, lat: Double) = TripPointEntity(id, t0 + minute * min, lat, 77.2100, 10f)

    @Test
    fun aTripFromHomeToHomeThatStayedInsideIsOnlyAWalk() {
        val inside = trip("a", "home", "home")
        val out = trip("b", "home", "home")
        val points = mapOf(
            "a" to listOf(point("a", 10, 28.6100), point("a", 15, 28.6107)),
            "b" to listOf(point("b", 10, 28.6100), point("b", 15, 28.6200)),
        )
        assertEquals(setOf("a"), InsideWalk.insideTrips(listOf(inside, out, trip("c", "home", "park")), points, listOf(home)))
    }

    @Test
    fun anInsideTripFoldsIntoOneStayAndAddsTheWalk() {
        val visits = listOf(
            PlaceVisitEntity("v1", "home", 0, 12 * min, 0.0, 0.0, "stop", false, false),
            PlaceVisitEntity("v2", "home", 18 * min, 60 * min, 0.0, 0.0, "stop", false, false),
        )
        val t = trip("a", "home", "home").copy(startMs = 12 * min, endMs = 18 * min)
        val plain = DayLayout.build(emptyList(), visits, listOf(t), mapOf("home" to "Home"), 0, 24 * 60 * min, 25 * 60 * min)
        assertTrue(plain.any { it is DayLayout.Travel })
        val folded = DayLayout.build(emptyList(), visits, listOf(t), mapOf("home" to "Home"), 0, 24 * 60 * min, 25 * 60 * min, insideTripIds = setOf("a"))
        val place = folded.single() as DayLayout.Place
        assertEquals(6 * min, place.walkedMs)
    }

    // ---- guessing off ----

    private fun guessed(mode: String, source: String = "auto") = TripEntity("g", 0, 10 * min, "", "", 500, 5, 6, "", mode, source)

    @Test
    fun withGuessingOffATripShowsOnlyYourAnswer() {
        TripGuess.forTest(false)
        assertEquals("Trip", TripModes.title(guessed("walk")))
        assertEquals("Trip", TripModes.title(guessed("vehicle")))
        assertEquals("Walk", TripModes.title(guessed("walk", "you")))
        assertFalse(TripModes.styledUnknown(guessed("vehicle")))
        assertTrue(TripModes.asksHow(guessed("walk")))
        assertFalse(TripModes.asksHow(guessed("walk", "you")))
        assertTrue(TripModes.isYours(guessed("walk", "you")))
        assertFalse(TripModes.isYours(guessed("walk")))
    }

    @Test
    fun withGuessingOnTheOldWordsComeBack() {
        TripGuess.forTest(true)
        assertTrue(TripModes.title(guessed("walk")).endsWith("(guess)"))
        assertEquals("Unknown way of travel", TripModes.title(guessed("vehicle")))
        assertTrue(TripModes.styledUnknown(guessed("vehicle")))
        assertFalse(TripModes.asksHow(guessed("walk")))
        assertTrue(TripModes.asksHow(guessed("vehicle")))
    }
}
