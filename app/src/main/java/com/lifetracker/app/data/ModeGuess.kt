package com.lifetracker.app.data

import kotlin.math.abs
import kotlin.math.max

/**
 * What the shape of a trip says about how it was made, worked out from its position readings alone: how fast while moving, how
 * steady, how often and how long it stood still, how smooth the speed changes were and how straight the way was.
 */
class TripFeatures(
    /** Median and 95th percentile of the speed while moving (above 1 m/s), in km/h. */
    val movingMedianKmh: Int,
    val p95Kmh: Int,
    /** How much the moving speed varies: spread between the quarter and three-quarter marks divided by the median. Low is steady. */
    val steadiness: Double,
    /** Share of the covered time spent standing (under 1 m/s), 0 to 1. */
    val stopShare: Double,
    /** Stretches of standing at least 30 s long. */
    val stops: Int,
    /** The longest single wait in seconds. A few minutes in the middle of a trip is a station, not a traffic light. */
    val longestStopS: Int,
    /** Average change of speed between readings in m/s per second. Trains and planes are smooth, buses and cars are not. */
    val meanAccel: Double,
    /** Straight line from start to end divided by the length of the way, 0 to 1. A loop is low, a train line near 1. */
    val straightness: Double,
    val distanceM: Int,
    val durationMs: Long,
) {
    companion object {
        /** Readings further apart than this say little about the speed in between. */
        private const val DENSE_S = 120.0

        /** Faster than this (1260 km/h) is a bad reading, not a trip. */
        private const val MAX_JUMP_MS = 350.0

        fun of(points: List<TripPointEntity>): TripFeatures? {
            // Only the obviously bad readings go: the usual cleaning would also throw away a flight, which is faster than any road jump it allows.
            val pts = points.filter { it.acc <= PlaceRules.MAX_ACCURACY_M }.sortedBy { it.ms }.distinctBy { it.ms }
            if (pts.size < 3) return null
            var path = 0.0
            val speeds = ArrayList<Double>()
            var coveredS = 0.0
            var standingS = 0.0
            var run = 0.0
            var stops = 0
            var longest = 0.0
            var accelSum = 0.0
            var accelN = 0
            var prevV = Double.NaN
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val dt = (b.ms - a.ms) / 1000.0
                if (dt <= 0.0) continue
                val d = PlaceRules.distanceM(a.lat, a.lng, b.lat, b.lng)
                if (d / dt > MAX_JUMP_MS) continue
                if (d >= max(5.0, 0.5 * (a.acc + b.acc))) path += d
                if (dt > DENSE_S) {
                    prevV = Double.NaN
                    if (run >= 30.0) stops++
                    run = 0.0
                    continue
                }
                val v = if (d < max(5.0, 0.5 * (a.acc + b.acc))) 0.0 else d / dt
                coveredS += dt
                if (v < 1.0) {
                    standingS += dt
                    run += dt
                    longest = max(longest, run)
                } else {
                    if (run >= 30.0) stops++
                    run = 0.0
                    speeds += v * 3.6
                }
                if (!prevV.isNaN() && dt <= 60.0) {
                    accelSum += abs(v - prevV) / dt
                    accelN++
                }
                prevV = v
            }
            if (run >= 30.0) stops++
            if (speeds.size < 3 || coveredS <= 0.0) return null
            speeds.sort()
            fun at(q: Double) = speeds[((speeds.size - 1) * q).toInt()]
            val median = at(0.5)
            val straight = PlaceRules.distanceM(pts.first().lat, pts.first().lng, pts.last().lat, pts.last().lng)
            return TripFeatures(
                movingMedianKmh = median.toInt(),
                p95Kmh = at(0.95).toInt(),
                steadiness = if (median > 0.0) (at(0.75) - at(0.25)) / median else 9.0,
                stopShare = standingS / coveredS,
                stops = stops,
                longestStopS = longest.toInt(),
                meanAccel = if (accelN == 0) 0.0 else accelSum / accelN,
                straightness = if (path > 0.0) (straight / path).coerceIn(0.0, 1.0) else 0.0,
                distanceM = path.toInt(),
                durationMs = pts.last().ms - pts.first().ms,
            )
        }
    }
}

/** The way of travelling the shape of a trip points to, when there is nothing you told the app to go on. */
object ModeGuess {
    /** Flights are far above anything on the ground, and sustained. */
    private const val FLIGHT_P95_KMH = 250

    fun fromFeatures(f: TripFeatures, activity: Map<String, Long>): String {
        val med = f.movingMedianKmh
        val p95 = f.p95Kmh
        val km = f.distanceM / 1000.0

        if (p95 >= FLIGHT_P95_KMH && med >= 150) return "flight"

        // Long and fast, along one line, with a wait of a few minutes at a station (or very smooth for a long way): a train.
        // A car on a motorway changes speed more and does not wait in the middle of a trip.
        if (km >= 15 && med >= 40 && p95 >= 70 && f.straightness >= 0.5 &&
            (f.longestStopS >= 120 || (f.meanAccel < 0.2 && f.steadiness < 0.25 && km >= 30))
        ) return "train"

        // Metro or local train: short hops, regular short stops, very smooth, never very fast.
        if (km in 2.0..45.0 && f.stops >= 2 && f.meanAccel < 0.2 && med in 25..70 && p95 <= 100 && f.stopShare in 0.08..0.45) return "train"

        // Slow and heavily stop-and-go: a bus (a car in a jam looks the same, and your answers teach the difference).
        if (med in 8..40 && p95 <= 70 && f.stops >= 3 && f.stopShare in 0.12..0.5 && f.meanAccel >= 0.2) return "bus"

        if (med >= 25 || p95 >= 45) return "car"
        return "vehicle"
    }
}
