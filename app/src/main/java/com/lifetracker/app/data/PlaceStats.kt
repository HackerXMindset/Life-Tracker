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
data class Fix(val lat: Double, val lng: Double, val accuracyM: Float, val timeMs: Long)

/** The phone went still at [startMs]. [lat] and [lng] are null if no position could be read (yet). */
data class StopCandidate(val startMs: Long, val lat: Double?, val lng: Double?)

/**
 * The rules for places, kept free of Android so they can be tested.
 *
 * Two things feed visits: Android's geofences (it tells the app you arrived at and left a named place)
 * and stops (the phone stayed still for a while, wherever that was). Stops at a named place back up the
 * geofences when Android is late or forgets; stops at an unnamed spot wait in the review list.
 */
object PlaceRules {
    /** The phone must stay still this long for a stop to count. */
    const val MIN_STOP_MS = 10 * 60_000L

    /** A visit shorter than this is a drive-by: it is kept, but not listed or counted. */
    const val MIN_VISIT_MS = 5 * 60_000L

    /** Two stops this close in time, at the same spot, are one stay (you walked around the library). */
    const val MERGE_GAP_MS = 20 * 60_000L

    /** Two stops this close together are the same spot. */
    const val MERGE_DISTANCE_M = 150.0

    /** Unnamed stops this close together are grouped in the review list. */
    const val CLUSTER_M = 120.0

    /** A position less accurate than this is not trusted. */
    const val MAX_ACCURACY_M = 200f

    /** How far past a place's edge a position must be before the app decides you left. */
    const val LEFT_SLACK_M = 100.0

    const val DEFAULT_RADIUS = 150
    const val MAX_PLACES = 100
    val RADII = listOf(100, 150, 200, 300)

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

    fun newId(startMs: Long): String = "v$startMs"

    private fun closed(v: PlaceVisitEntity, endMs: Long) = v.copy(endMs = max(endMs, v.startMs), ongoing = false)

    /**
     * You arrived at [place]. [open] are the visits that are still going. Returns the visits to save: the new one,
     * and any other open visit, which ends now (you cannot be in two places). Nothing changes if you were already there.
     */
    fun enter(open: List<PlaceVisitEntity>, place: PlaceEntity, now: Long): List<PlaceVisitEntity> {
        val already = open.any { it.placeId == place.id }
        val others = open.filter { it.placeId != place.id }.map { closed(it, now) }
        if (already) return others
        return others + PlaceVisitEntity(newId(now), place.id, now, now, place.lat, place.lng, "geofence", true, false)
    }

    /** You left [placeId]. Returns the visits to save (the open visit there, now ended). */
    fun exit(open: List<PlaceVisitEntity>, placeId: String, now: Long): List<PlaceVisitEntity> =
        open.filter { it.placeId == placeId }.map { closed(it, now) }

    /**
     * Android sometimes never says you left. When the phone goes still somewhere outside the place of a visit
     * that is still open, that visit ended when you started moving ([departMs]), or now if that is unknown.
     */
    fun closeIfElsewhere(
        open: List<PlaceVisitEntity>,
        places: List<PlaceEntity>,
        lat: Double,
        lng: Double,
        departMs: Long,
        now: Long,
    ): List<PlaceVisitEntity> = open.mapNotNull { v ->
        val place = places.firstOrNull { it.id == v.placeId }
        val away = place == null || distanceM(place.lat, place.lng, lat, lng) > place.radiusM + LEFT_SLACK_M
        if (!away) null else closed(v, if (departMs > v.startMs && departMs <= now) departMs else now)
    }

    private fun endOf(v: PlaceVisitEntity): Long = if (v.ongoing) Long.MAX_VALUE else v.endMs

    private fun closeInTime(v: PlaceVisitEntity, startMs: Long, endMs: Long): Boolean =
        v.startMs <= endMs + MERGE_GAP_MS && endOf(v) >= startMs - MERGE_GAP_MS

    /**
     * The phone was still from [c].startMs until [endMs]. [visits] are the visits around that time.
     * Returns what to save: nothing for a short stop or when the position is unknown; a stay at a watched place
     * (added to the visit Android already reported, if there is one); or a stop at an unnamed spot for the review list.
     */
    fun finishStop(
        c: StopCandidate,
        endMs: Long,
        places: List<PlaceEntity>,
        visits: List<PlaceVisitEntity>,
    ): List<PlaceVisitEntity> {
        val lat = c.lat ?: return emptyList()
        val lng = c.lng ?: return emptyList()
        if (endMs - c.startMs < MIN_STOP_MS) return emptyList()

        val place = placeAt(places, lat, lng)
        if (place != null) {
            val hit = visits.firstOrNull { it.placeId == place.id && closeInTime(it, c.startMs, endMs) }
            return when {
                hit == null -> listOf(
                    PlaceVisitEntity(newId(c.startMs), place.id, c.startMs, endMs, place.lat, place.lng, "stop", false, false),
                )
                hit.ongoing -> emptyList()
                else -> listOf(hit.copy(startMs = min(hit.startMs, c.startMs), endMs = max(hit.endMs, endMs)))
            }
        }

        val same = visits.firstOrNull {
            it.placeId.isEmpty() && closeInTime(it, c.startMs, endMs) && distanceM(it.lat, it.lng, lat, lng) <= MERGE_DISTANCE_M
        }
        return if (same == null) {
            listOf(PlaceVisitEntity(newId(c.startMs), "", c.startMs, endMs, lat, lng, "stop", false, false))
        } else {
            listOf(same.copy(startMs = min(same.startMs, c.startMs), endMs = max(same.endMs, endMs)))
        }
    }

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
        v.placeId.isNotEmpty() && !v.ignored && lengthMs(v, now) >= MIN_VISIT_MS

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
