package com.lifetracker.app.data

import kotlin.math.max

/** What the position readings of a trip add up to. */
data class TripStats(
    val distanceM: Int,
    val durationMs: Long,
    val medianKmh: Int,
    val topKmh: Int,
    /** How far from where it began the trip ever got. A "trip" that never got [MIN_FARTHEST_M] away was only GPS drift. */
    val farthestM: Int,
)

/** The arithmetic of trips, free of Android so it can be tested. */
object TripRules {
    /** A trip must get at least this far from where it began (it was 200 m before Polish 4e). */
    const val MIN_FARTHEST_M = 50

    /** If no reading comes for this long during a trip, the trip ended at the last reading. */
    const val TRIP_GAP_MS = 30 * 60_000L

    /** A jump faster than this (252 km/h) is a bad reading and is skipped. */
    private const val MAX_SPEED_MS = 70.0

    fun stats(points: List<TripPointEntity>, startMs: Long, endMs: Long): TripStats {
        val pts = points.filter { it.ms in startMs..endMs }.sortedBy { it.ms }
        if (pts.size < 2) return TripStats(0, max(0L, endMs - startMs), 0, 0, 0)

        var distance = 0.0
        var farthest = 0.0
        val speeds = ArrayList<Double>()
        var last = pts.first()
        for (p in pts.drop(1)) {
            val d = PlaceRules.distanceM(last.lat, last.lng, p.lat, p.lng)
            // Readings that moved less than their own error are noise, not movement.
            if (d < max(10.0, 0.5 * (last.acc + p.acc))) continue
            val seconds = (p.ms - last.ms) / 1000.0
            if (seconds <= 0.0) continue
            val v = d / seconds
            if (v > MAX_SPEED_MS) continue
            // How far from where it began, less the reading's own error, the same way the live trip measures it.
            farthest = max(farthest, PlaceRules.awayM(pts.first().lat, pts.first().lng, p.lat, p.lng, p.acc))
            distance += d
            speeds.add(v * 3.6)
            last = p
        }
        speeds.sort()
        val median = if (speeds.isEmpty()) 0.0 else speeds[speeds.size / 2]
        val top = if (speeds.isEmpty()) 0.0 else speeds[((speeds.size - 1) * 0.95).toInt()]
        return TripStats(distance.toInt(), max(0L, endMs - startMs), median.toInt(), top.toInt(), farthest.toInt())
    }

    fun isRealTrip(stats: TripStats): Boolean = stats.farthestM >= MIN_FARTHEST_M && stats.durationMs >= 60_000L

    /** The fastest a trip made on foot or by bike can honestly be, in km/h. Anything quicker was not made that way. */
    fun maxKmh(mode: String): Double? = when (mode) {
        "walk" -> 8.0
        "run" -> 20.0
        "cycle" -> 35.0
        else -> null
    }

    /** Fewest steps worth trusting a distance to. */
    const val MIN_STEPS = 20

    /** How far the GPS distance may be from the steps distance before the steps are trusted instead (a third). */
    const val DISAGREE = 0.35

    /** A step length used until the app has learned yours or knows your height, in metres. */
    const val DEFAULT_STRIDE_M = 0.70

    /** Step length from height: 0.415 of it. */
    fun strideFromHeightCm(heightCm: Int): Double = heightCm / 100.0 * 0.415

    /** Steps taken during a trip: the counter at its last reading minus at its first, or null when not known (or the phone restarted in between). */
    fun stepsBetween(points: List<TripPointEntity>): Int? {
        val known = points.filter { it.steps >= 0L }.sortedBy { it.ms }
        if (known.size < 2) return null
        val taken = known.last().steps - known.first().steps
        return if (taken in 0..Int.MAX_VALUE.toLong()) taken.toInt() else null
    }

    /**
     * The distance to show for a trip. For a trip made on foot (walk or run) with known [steps], the steps times [strideM] is
     * trusted when the GPS distance differs from it by more than a third (GPS drift nearly always adds distance). Then the
     * speed is checked against the way you travelled: a walk cannot be faster than 8 km/h, so 823 m in 2 minutes is cut to what
     * 2 minutes of walking allows.
     */
    fun adjust(gpsM: Int, durationMs: Long, mode: String, steps: Int?, strideM: Double): Int {
        var m = gpsM.toDouble()
        val onFoot = mode == "walk" || mode == "run"
        if (onFoot && steps != null && steps >= MIN_STEPS) {
            val byStep = steps * strideM
            if (Math.abs(m - byStep) > DISAGREE * byStep) m = byStep
        }
        val limit = maxKmh(mode)
        if (limit != null && durationMs > 0L) m = minOf(m, limit / 3.6 * durationMs / 1000.0)
        return m.toInt()
    }

    /**
     * One measurement of your step length, from a walk where the GPS and the steps can be compared: a real walking pace, enough
     * steps and enough distance. Null when the walk is not good enough to learn from.
     */
    fun strideSample(gpsM: Int, steps: Int?, medianKmh: Int, mode: String): Double? {
        if (mode != "walk" || steps == null || steps < 400 || gpsM < 400 || medianKmh > 7) return null
        val sample = gpsM.toDouble() / steps
        return if (sample in 0.45..0.95) sample else null
    }

    /** Moves the learned step length a fifth of the way towards a new measurement, starting from [old] (0 when nothing is learned yet). */
    fun learnStride(old: Double, sample: Double, fallback: Double): Double {
        val base = if (old > 0.0) old else fallback
        return (base * 0.8 + sample * 0.2).coerceIn(0.45, 0.95)
    }

    /** "4.2 km" or "850 m". */
    fun distanceText(m: Int): String =
        if (m < 1000) "$m m" else String.format(java.util.Locale.ENGLISH, "%.1f km", m / 1000.0)

    /** Average speed over the whole trip, in km/h. */
    fun avgKmh(distanceM: Int, durationMs: Long): Int =
        if (durationMs <= 0L) 0 else (distanceM * 3600.0 / durationMs).toInt()
}

/** How you travelled: the choices, the app's guess, and what it learns from your corrections. */
object TripModes {
    class Mode(val id: String, val label: String)

    val list = listOf(
        Mode("walk", "Walk"),
        Mode("run", "Run"),
        Mode("cycle", "Cycle"),
        Mode("bike", "Motorbike"),
        Mode("auto", "Auto-rickshaw"),
        Mode("car", "Car"),
        Mode("bus", "Bus"),
        Mode("train", "Train or metro"),
        Mode("other", "Other"),
        Mode("flight", "Flight"),
        Mode("boat", "Boat"),
        Mode("scooter", "Scooter"),
        Mode("skateboard", "Skateboard"),
        Mode("vehicle", "Vehicle (not sure which)"),
    )

    fun label(id: String): String = list.firstOrNull { it.id == id }?.label ?: "Vehicle (not sure which)"

    /** "WALKING=60000,IN_VEHICLE=540000" -> map. */
    fun parseActivity(text: String): Map<String, Long> =
        text.split(",").mapNotNull { part ->
            val bits = part.split("=")
            val ms = bits.getOrNull(1)?.toLongOrNull()
            if (bits.size == 2 && ms != null && bits[0].isNotBlank()) bits[0] to ms else null
        }.toMap()

    fun activityText(map: Map<String, Long>): String =
        map.filter { it.value > 0L && it.key != "STILL" }.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

    /** A past trip whose mode you set yourself. */
    class Learned(
        val from: String,
        val to: String,
        val mode: String,
        val medianKmh: Int,
        val topKmh: Int,
        val distanceM: Int = 0,
        val durationMs: Long = 0L,
    )

    fun learned(t: TripEntity) = Learned(t.fromPlaceId, t.toPlaceId, t.mode, t.medianKmh, t.topKmh, t.distanceM, t.endMs - t.startMs)

    /**
     * The app's guess. First what you said about trips between the same two named places, then Android's walking,
     * running and cycling signals and the speeds; for anything in a vehicle it looks for the trips you labelled that
     * had the most similar speeds, and otherwise says "vehicle" until you tell it which.
     */
    fun guess(stats: TripStats, activity: Map<String, Long>, from: String, to: String, learned: List<Learned>, features: TripFeatures? = null): String {
        if (from.isNotEmpty() && to.isNotEmpty()) {
            val same = learned.filter { (it.from == from && it.to == to) || (it.from == to && it.to == from) }
            if (same.size >= 2) {
                val top = same.groupingBy { it.mode }.eachCount().maxByOrNull { it.value }
                if (top != null && top.value.toDouble() / same.size >= 0.6) return top.key
            }
        }

        // Above any road speed for most of the trip: the usual speed figures drop such jumps as bad readings, the features do not.
        if (features != null && features.p95Kmh >= 250 && features.movingMedianKmh >= 150) return "flight"

        val onFoot = (activity["WALKING"] ?: 0L)
        val running = (activity["RUNNING"] ?: 0L)
        val cycling = (activity["ON_BICYCLE"] ?: 0L)
        val driving = (activity["IN_VEHICLE"] ?: 0L)
        val total = onFoot + running + cycling + driving
        val median = stats.medianKmh
        val top = stats.topKmh

        // Android's own signal is believed only when the speed does not argue with it (a "walk" at 30 km/h was a vehicle).
        fun plausible(mode: String) = if (features == null) true else when (mode) {
            "walk" -> median <= 9 && top <= 16
            "run" -> median <= 16 && top <= 26
            "cycle" -> median <= 30 && top <= 45
            else -> true
        }
        if (total > 0L) {
            val best = listOf("walk" to onFoot, "run" to running, "cycle" to cycling, "vehicle" to driving).maxByOrNull { it.second }!!
            if (best.second * 2 >= total) {
                if (best.first != "vehicle" && plausible(best.first)) return best.first
                if (best.first == "vehicle") return vehicleGuess(median, top, learned, features, activity)
            }
        }
        return when {
            median < 7 && top < 12 -> "walk"
            median < 9 && top < 16 -> "run"
            median < 22 && top < 35 && (features == null || features.stopShare < 0.2) -> "cycle"
            else -> vehicleGuess(median, top, learned, features, activity)
        }
    }

    /** A vehicle trip: first the trips you labelled that were most like it, then what the shape of the trip says. */
    private fun vehicleGuess(median: Int, top: Int, learned: List<Learned>, features: TripFeatures?, activity: Map<String, Long>): String {
        val fromYou = learnedVehicle(median, top, learned)
        if (fromYou != "vehicle") return fromYou
        return if (features != null) ModeGuess.fromFeatures(features, activity) else "vehicle"
    }

    private fun learnedVehicle(median: Int, top: Int, learned: List<Learned>): String {
        val near = learned
            .filter { it.mode != "walk" && it.mode != "run" && it.mode != "cycle" }
            .map { it to Math.abs(it.medianKmh - median) / 10.0 + Math.abs(it.topKmh - top) / 20.0 }
            .filter { it.second < 1.0 }
            .sortedBy { it.second }
            .take(3)
        if (near.size >= 2) {
            val votes = HashMap<String, Double>()
            for ((l, d) in near) votes[l.mode] = (votes[l.mode] ?: 0.0) + 1.0 / (0.1 + d)
            val winner = votes.maxByOrNull { it.value }
            if (winner != null && winner.value / votes.values.sum() >= 0.5) return winner.key
        }
        return "vehicle"
    }
}
