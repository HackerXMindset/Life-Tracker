package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic trips along a line (north), one reading every [stepS] seconds, to see what the guess makes of their shape. */
class ModeGuessTest {
    private val t0 = 1_760_000_000_000L
    private val perDeg = 111_320.0

    /** [legs] are (seconds, km/h): the trip moves at that speed for that long (0 = standing). */
    private fun trip(legs: List<Pair<Int, Double>>, stepS: Int = 10, jitter: Double = 0.0): List<TripPointEntity> {
        val out = ArrayList<TripPointEntity>()
        var lat = 25.0
        var ms = t0
        var n = 0
        out += TripPointEntity("t", ms, lat, 81.0, 5f)
        for ((seconds, kmh) in legs) {
            var left = seconds
            while (left > 0) {
                n++
                // Real readings are never perfectly even: a fixed pattern of up to +/- jitter, the same every run.
                val wobble = ((n * 7919) % 21 - 10) / 10.0
                val dt = minOf(stepS, left)
                lat += (if (kmh > 0.0) kmh * (1 + jitter * wobble) else 0.0) / 3.6 * dt / perDeg
                ms += dt * 1000L
                out += TripPointEntity("t", ms, lat, 81.0, 5f)
                left -= dt
            }
        }
        return out
    }

    private fun guess(points: List<TripPointEntity>, activity: Map<String, Long> = emptyMap(), learned: List<TripModes.Learned> = emptyList()): String {
        val f = TripFeatures.of(points)
        val stats = TripRules.stats(points, points.first().ms, points.last().ms)
        return TripModes.guess(stats, activity, "", "", learned, f)
    }

    @Test fun tooFewReadingsGiveNoFeatures() {
        assertNull(TripFeatures.of(trip(listOf(20 to 5.0)).take(2)))
        assertNotNull(TripFeatures.of(trip(listOf(300 to 5.0))))
    }

    @Test fun aWalkIsAWalk() {
        assertEquals("walk", guess(trip(listOf(900 to 5.0))))
    }

    @Test fun aSteadyRideIsACycle() {
        assertEquals("cycle", guess(trip(listOf(1200 to 16.0))))
    }

    @Test fun aLongSmoothFastRunWithWaitsAtStationsIsATrain() {
        // 25 min at 110 km/h, 4 min at a station, 25 min at 100 km/h, 3 min at a station, 20 min at 105.
        val legs = listOf(1500 to 110.0, 240 to 0.0, 1500 to 100.0, 180 to 0.0, 1200 to 105.0)
        assertEquals("train", guess(trip(legs, stepS = 30, jitter = 0.02)))
    }

    @Test fun aLongSmoothExpressWithNoStopIsATrainToo() {
        assertEquals("train", guess(trip(listOf(3600 to 120.0), stepS = 30, jitter = 0.02)))
    }

    @Test fun aMetroWithShortHopsIsATrain() {
        val hop = listOf(60 to 20.0, 120 to 45.0, 40 to 25.0, 35 to 0.0)
        val legs = (0 until 6).flatMap { hop }
        assertEquals("train", guess(trip(legs, stepS = 5, jitter = 0.03)))
    }

    @Test fun stopAndGoTrafficAtCityPaceIsABus() {
        val leg = listOf(30 to 0.0, 20 to 12.0, 40 to 30.0, 15 to 15.0, 25 to 0.0, 35 to 25.0, 30 to 0.0, 45 to 35.0)
        val legs = (0 until 5).flatMap { leg }
        assertEquals("bus", guess(trip(legs, stepS = 5, jitter = 0.2)))
    }

    @Test fun aFastRoadTripWithChangingSpeedIsACar() {
        val legs = (0 until 12).flatMap { listOf(60 to 95.0, 30 to 60.0, 45 to 80.0) }
        assertEquals("car", guess(trip(legs, stepS = 5, jitter = 0.1)))
    }

    @Test fun aCityDriveWithAFewLightsIsACar() {
        val legs = (0 until 8).flatMap { listOf(40 to 35.0, 10 to 0.0, 30 to 28.0, 20 to 45.0) }
        assertEquals("car", guess(trip(legs, stepS = 5, jitter = 0.15)))
    }

    @Test fun anAeroplaneIsAFlight() {
        assertEquals("flight", guess(trip(listOf(300 to 300.0, 1800 to 780.0, 300 to 250.0), stepS = 30, jitter = 0.02)))
    }

    @Test fun androidSaysWalkingButTheSpeedIs40IsNotAWalk() {
        val points = trip(listOf(1200 to 45.0))
        assertTrue(guess(points, mapOf("WALKING" to 900_000L)) != "walk")
    }

    @Test fun yourOwnAnswersBeatTheShape() {
        val learned = listOf(
            TripModes.Learned("", "", "auto", 38, 52),
            TripModes.Learned("", "", "auto", 40, 55),
        )
        val points = trip((0 until 12).flatMap { listOf(60 to 40.0, 30 to 38.0, 45 to 42.0) }, stepS = 5, jitter = 0.12)
        assertEquals("auto", guess(points, learned = learned))
    }

    @Test fun theNewModesHaveALabel() {
        listOf("flight", "boat", "scooter", "skateboard").forEach { assertTrue(TripModes.label(it) != "Vehicle (not sure which)") }
    }
}
