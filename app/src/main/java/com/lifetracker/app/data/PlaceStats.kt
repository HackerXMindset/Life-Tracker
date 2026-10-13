package com.lifetracker.app.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The kinds of place you can pick. */
object PlaceKinds {
    class Kind(val id: String, val label: String)

    val list = listOf(
        Kind("home", "Home"),
        Kind("study", "Study"),
        Kind("gym", "Gym"),
        Kind("work", "Work"),
        Kind("food", "Food"),
        Kind("shop", "Shop"),
        Kind("people", "Friends and family"),
        Kind("other", "Other"),
    )

    fun label(id: String): String = list.firstOrNull { it.id == id }?.label ?: "Other"
}

/** One reading of where the phone is. */
data class Fix(val lat: Double, val lng: Double, val accuracyM: Float, val timeMs: Long, val speedMs: Float = -1f)

/**
 * The rules for places, kept free of Android so they can be tested.
 *
 * Android's geofences only wake the app up when you come near or leave a place. The real work is done by
 * [PlaceEngine], which reads your position (every minute while you are at a spot the app does not know)
 * and turns it into stays. A stay that is inside a named place's circle is a visit to it; any other stay
 * waits in the review list.
 */
object PlaceRules {
    /** Being in one spot this long makes it a stay (a visit to a place, or a stop to review). Shorter is just passing. */
    const val MIN_STAY_MS = 3 * 60_000L

    /**
     * Two stays at the same spot this close in time are one stay (GPS wobble, or you walked around the library) but only
     * if you never really went away in between: see [DEPARTURE_M]. Coming back after a real trip is a new visit.
     */
    const val MERGE_GAP_MS = 20 * 60_000L

    /** Getting this far from a spot (the same distance, [TripRules.MIN_FARTHEST_M], that makes a trip real) means you really left it. */
    const val DEPARTURE_M = TripRules.MIN_FARTHEST_M.toDouble()

    /** A tidy-up stay and a saved visit are the same one if they overlap or are no further apart than this. */
    const val MATCH_SLACK_MS = 2 * 60_000L

    /** Two stays this close together are the same spot. */
    const val MERGE_DISTANCE_M = 100.0

    /** Unnamed stops this close together are grouped in the review list. */
    const val CLUSTER_M = 100.0

    /** A position less accurate than this is not trusted. */
    const val MAX_ACCURACY_M = 150f

    /** Android cannot watch a circle smaller than this reliably, so the geofence is only a wake-up ring this big. */
    const val GEOFENCE_MIN_M = 150

    const val DEFAULT_RADIUS = 50
    const val MAX_PLACES = 100
    val RADII = listOf(30, 50, 75, 100, 150)

    private const val EARTH_M = 6_371_000.0

    /** Straight-line distance between two points on the map, in metres. */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(p1) * cos(p2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_M * asin(min(1.0, sqrt(a)))
    }

    /** The watched place this point is inside, or null. If it is inside several, the smallest one (a hostel inside a campus). */
    fun placeAt(places: List<PlaceEntity>, lat: Double, lng: Double): PlaceEntity? =
        places
            .filter { !it.archived }
            .map { it to distanceM(it.lat, it.lng, lat, lng) }
            .filter { it.second <= it.first.radiusM }
            .minWithOrNull(compareBy<Pair<PlaceEntity, Double>>({ it.first.radiusM }, { it.second }))
            ?.first

    /** How far a reading is from a spot, less its own error (up to 100 m), so one poor reading does not look like leaving. */
    fun awayM(lat: Double, lng: Double, fixLat: Double, fixLng: Double, accuracyM: Float): Double =
        max(0.0, distanceM(lat, lng, fixLat, fixLng) - min(accuracyM.toDouble(), 100.0))

    fun newId(startMs: Long): String = "v$startMs"

    internal fun endOf(v: PlaceVisitEntity): Long = if (v.ongoing) Long.MAX_VALUE else v.endMs

    /** Whether [v] is close enough in time to a stay from [startMs] to [endMs] to be the same stay. */
    internal fun closeInTime(v: PlaceVisitEntity, startMs: Long, endMs: Long): Boolean =
        v.startMs <= endMs + MERGE_GAP_MS && endOf(v) >= startMs - MERGE_GAP_MS

    /** Whether [v] overlaps a stay from [startMs] to [endMs], or is only a moment away from it. */
    internal fun overlapsInTime(v: PlaceVisitEntity, startMs: Long, endMs: Long): Boolean =
        v.startMs <= endMs + MATCH_SLACK_MS && endOf(v) >= startMs - MATCH_SLACK_MS

    /** Unnamed stops that happened in about the same spot. */
    data class Cluster(val visits: List<PlaceVisitEntity>) {
        val lat: Double = visits.map { it.lat }.average()
        val lng: Double = visits.map { it.lng }.average()
        val totalMs: Long = visits.sumOf { it.endMs - it.startMs }
        val firstMs: Long = visits.minOf { it.startMs }
        val lastMs: Long = visits.maxOf { it.endMs }
        val ids: List<String> = visits.map { it.id }
    }

    /** Groups unnamed stops by where they were, the most frequent spot first. */
    fun clusters(unknown: List<PlaceVisitEntity>): List<Cluster> {
        val groups = mutableListOf<MutableList<PlaceVisitEntity>>()
        val centres = mutableListOf<Pair<Double, Double>>()
        for (v in unknown.sortedBy { it.startMs }) {
            val i = centres.indexOfFirst { distanceM(it.first, it.second, v.lat, v.lng) <= CLUSTER_M }
            if (i >= 0) {
                groups[i].add(v)
                centres[i] = groups[i].map { it.lat }.average() to groups[i].map { it.lng }.average()
            } else {
                groups.add(mutableListOf(v))
                centres.add(v.lat to v.lng)
            }
        }
        return groups.map { Cluster(it) }
            .sortedWith(compareByDescending<Cluster> { it.visits.size }.thenByDescending { it.totalMs })
    }

    /** Whether a visit is listed and counted: named, not hidden and not just a drive-by. */
    fun isShown(v: PlaceVisitEntity, now: Long): Boolean =
        v.placeId.isNotEmpty() && !v.ignored && lengthMs(v, now) >= MIN_STAY_MS

    fun lengthMs(v: PlaceVisitEntity, now: Long): Long =
        (if (v.ongoing) max(now, v.startMs) else v.endMs) - v.startMs

    class PlaceTotal(val placeId: String, val ms: Long, val visits: Int)

    /** Time per place between [fromMs] and [toMs], longest first. A stay across midnight is split between the days. */
    fun totals(visits: List<PlaceVisitEntity>, fromMs: Long, toMs: Long, now: Long): List<PlaceTotal> {
        val ms = LinkedHashMap<String, Long>()
        val count = HashMap<String, Int>()
        for (v in visits) {
            if (!isShown(v, now)) continue
            val end = v.startMs + lengthMs(v, now)
            val s = max(v.startMs, fromMs)
            val e = min(end, toMs)
            if (e <= s) continue
            ms[v.placeId] = (ms[v.placeId] ?: 0L) + (e - s)
            count[v.placeId] = (count[v.placeId] ?: 0) + 1
        }
        return ms.map { PlaceTotal(it.key, it.value, count[it.key] ?: 0) }.sortedByDescending { it.ms }
    }

    /** Reads "28.6139, 77.2090" (what Google Maps copies) into latitude and longitude, or null. */
    fun parseCoordinates(text: String): Pair<Double, Double>? {
        val numbers = Regex("-?\\d+(?:\\.\\d+)?").findAll(text).map { it.value.toDouble() }.toList()
        if (numbers.size != 2) return null
        val (lat, lng) = numbers
        return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
    }

    /** "28.6139, 77.2090" */
    fun coordinatesText(lat: Double, lng: Double): String = String.format(java.util.Locale.ENGLISH, "%.5f, %.5f", lat, lng)

    fun durationText(ms: Long): String = ChargeStats.durationText(ms)
}
