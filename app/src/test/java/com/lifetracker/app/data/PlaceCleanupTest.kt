package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCleanupTest {
    private val min = 60_000L
    private val t0 = 1_760_000_000_000L

    // The library is a 50 m circle at 28.6100, 77.2100.
    private val library = PlaceEntity("lib", "Library", "study", 28.6100, 77.2100, 50, false, 0L)
    private val places = listOf(library)

    private fun stay(from: Int, to: Int, lat: Double, lng: Double) =
        BuiltStay(t0 + from * min, t0 + to * min, lat, lng, to - from + 1, 0L)

    private fun visit(id: String, placeId: String, from: Int, to: Int, lat: Double = 28.61, lng: Double = 77.21, ongoing: Boolean = false, ignored: Boolean = false) =
        PlaceVisitEntity(id, placeId, t0 + from * min, t0 + to * min, lat, lng, "stop", ongoing, ignored)

    @Test
    fun aStayNobodyRecordedIsAdded() {
        val plan = PlaceCleanup.plan(listOf(stay(0, 20, 28.7, 77.3)), places, emptyList())
        assertEquals(1, plan.adds.size)
        val v = plan.adds[0]
        assertEquals("c" + t0, v.id)
        assertEquals("", v.placeId)
        assertEquals(t0, v.startMs)
        assertEquals(t0 + 20 * min, v.endMs)
        assertEquals("clean", v.source)
        assertFalse(v.ongoing)
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun aStayInsideANamedPlaceIsAVisitToIt() {
        val plan = PlaceCleanup.plan(listOf(stay(0, 20, 28.6101, 77.2100)), places, emptyList())
        assertEquals("lib", plan.adds.single().placeId)
        assertEquals(28.6100, plan.adds.single().lat, 0.0)
    }

    @Test
    fun aSavedVisitIsStretchedAndKeepsWhatYouDecided() {
        val saved = listOf(visit("v1", "lib", 5, 10, ignored = true))
        val plan = PlaceCleanup.plan(listOf(stay(0, 30, 28.6100, 77.2100)), places, saved)
        assertTrue(plan.adds.isEmpty())
        val v = plan.updates.single()
        assertEquals("v1", v.id)
        assertEquals(t0, v.startMs)
        assertEquals(t0 + 30 * min, v.endMs)
        assertTrue(v.ignored)
    }

    @Test
    fun aVisitIsNeverShrunk() {
        val saved = listOf(visit("v1", "lib", 0, 60))
        val plan = PlaceCleanup.plan(listOf(stay(10, 20, 28.6100, 77.2100)), places, saved)
        assertTrue(plan.adds.isEmpty())
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun stretchingStopsAtTheNeighbouringVisits() {
        val saved = listOf(
            visit("before", "x", 0, 8),
            visit("v1", "lib", 10, 20),
            visit("after", "y", 25, 40),
        )
        val plan = PlaceCleanup.plan(listOf(stay(0, 40, 28.6100, 77.2100)), places, saved)
        val v = plan.updates.single()
        assertEquals("v1", v.id)
        assertEquals(t0 + 8 * min, v.startMs)
        assertEquals(t0 + 25 * min, v.endMs)
    }

    @Test
    fun aVisitStillInProgressIsLeftToTheLiveDetector() {
        val saved = listOf(visit("v1", "lib", 10, 20, ongoing = true))
        val plan = PlaceCleanup.plan(listOf(stay(0, 30, 28.6100, 77.2100), stay(40, 60, 28.7, 77.3)), places, saved)
        assertTrue(plan.adds.isEmpty())
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun somewhereElseAlreadyRecordedAtThatTimeIsNotOverwritten() {
        val saved = listOf(visit("gym", "gym", 0, 30, 28.62, 77.22))
        val plan = PlaceCleanup.plan(listOf(stay(5, 25, 28.6100, 77.2100)), places, saved)
        assertTrue(plan.adds.isEmpty())
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun anUnnamedStopNearASavedUnnamedStopIsTheSameStop() {
        val saved = listOf(visit("v1", "", 5, 15, 28.7000, 77.3000))
        val plan = PlaceCleanup.plan(listOf(stay(0, 20, 28.7003, 77.3002)), places, saved)
        assertTrue(plan.adds.isEmpty())
        assertEquals(t0 + 20 * min, plan.updates.single().endMs)
    }

    @Test
    fun runningTheSameTidyTwiceChangesNothingTheSecondTime() {
        val first = PlaceCleanup.plan(listOf(stay(0, 20, 28.7, 77.3)), places, emptyList())
        val second = PlaceCleanup.plan(listOf(stay(0, 20, 28.7, 77.3)), places, first.adds)
        assertTrue(second.adds.isEmpty())
        assertTrue(second.updates.isEmpty())
    }

    // ---- trips

    private fun routePoints(): List<TripPointEntity> =
        (0..10).map { TripPointEntity("t1", t0 + it * 30_000L, 28.6 + it * 0.001, 77.2, 10f) }

    @Test
    fun aTripIsWorkedOutAgainWithoutTheBadReading() {
        val points = routePoints().toMutableList()
        points[5] = points[5].copy(lat = 29.1)
        val trip = TripEntity("t1", t0, t0 + 300_000L, "", "", 99_999, 500, 900, "", "vehicle", "auto")
        val fixed = PlaceCleanup.refreshTrip(trip, points)
        assertNotNull(fixed)
        assertEquals(1112.0, fixed!!.distanceM.toDouble(), 10.0)
        assertEquals(13, fixed.medianKmh)
        // What you and the app decided about it stays.
        assertEquals("vehicle", fixed.mode)
        assertEquals("auto", fixed.modeSource)
    }

    @Test
    fun aTripThatIsAlreadyRightIsLeftAlone() {
        val points = routePoints()
        val stats = TripRules.stats(points, t0, t0 + 300_000L)
        val trip = TripEntity("t1", t0, t0 + 300_000L, "", "", stats.distanceM, stats.medianKmh, stats.topKmh, "", "cycle", "you")
        assertNull(PlaceCleanup.refreshTrip(trip, points))
        assertNull(PlaceCleanup.refreshTrip(trip, points.take(1)))
    }

    // ---- watchdog

    private val now = t0

    @Test
    fun theReadingsAreRestartedWhenTheyWentQuiet() {
        // Always on, 2 minute pace, last seen 10 minutes ago.
        assertTrue(PlacesWatchdog.shouldStart(true, true, false, true, now - 10 * min, now, 120))
        // Fresh.
        assertFalse(PlacesWatchdog.shouldStart(true, true, false, true, now - 1 * min, now, 120))
        // Said to be running but nothing for a long time at a slow pace is still fine up to three intervals.
        assertFalse(PlacesWatchdog.shouldStart(true, true, false, true, now - 12 * min, now, 300))
        // Not running at all.
        assertTrue(PlacesWatchdog.shouldStart(true, true, false, false, now - 1 * min, now, 120))
    }

    @Test
    fun theReadingsAreOnlyRestartedWhenWanted() {
        assertFalse(PlacesWatchdog.shouldStart(false, true, true, false, 0L, now, 60))
        assertFalse(PlacesWatchdog.shouldStart(true, false, false, false, 0L, now, 60))
        assertTrue(PlacesWatchdog.shouldStart(true, false, true, false, 0L, now, 60))
        assertFalse(PlacesWatchdog.shouldStart(true, false, true, true, now - 1 * min, now, 60))
    }

    @Test
    fun aWalkIsCheckedAgainstTheStepsAndTheSpeedOfAWalk() {
        // Readings drifted to 13 km/h but the phone counted only 380 steps: that is about 266 m.
        val points = routePoints().mapIndexed { i, p -> p.copy(steps = 5_000L + i * 38) }
        val trip = TripEntity("t1", t0, t0 + 300_000L, "", "", 1112, 13, 13, "", "walk", "you")
        val fixed = PlaceCleanup.refreshTrip(trip, points, 0.70)
        assertNotNull(fixed)
        assertEquals(266.0, fixed!!.distanceM.toDouble(), 2.0)
        assertEquals("walk", fixed.mode)
    }
}
