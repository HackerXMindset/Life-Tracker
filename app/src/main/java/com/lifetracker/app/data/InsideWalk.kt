package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.min

/**
 * Walking about inside a named place (the house, the library). It is not a trip: the stay goes on, and the Timeline only adds a
 * small line such as "Walked about 15 min". No Android in here so it can be tested.
 */
object InsideWalk {
    /** Two readings further apart than this say nothing about what happened between them. */
    const val MAX_GAP_MS = 3 * 60_000L

    /** Steps a minute that count as walking. */
    const val MIN_STEPS_PER_MIN = 40.0

    /** Metres a second that count as walking when there are no step counts. */
    const val MIN_SPEED_MS = 0.7

    /** Less than this is not worth a line. */
    const val MIN_SHOWN_MS = 3 * 60_000L

    /** The stretches (start to end) between two readings in which you were walking. */
    fun spans(fixes: List<RawFixEntity>): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        val sorted = fixes.sortedBy { it.ms }
        for (i in 1 until sorted.size) {
            val a = sorted[i - 1]
            val b = sorted[i]
            val dt = b.ms - a.ms
            if (dt <= 0L || dt > MAX_GAP_MS) continue
            val walking = if (a.steps >= 0L && b.steps >= a.steps) {
                (b.steps - a.steps) / (dt / 60_000.0) >= MIN_STEPS_PER_MIN
            } else {
                val metres = PlaceRules.distanceM(a.lat, a.lng, b.lat, b.lng)
                metres / (dt / 1000.0) >= MIN_SPEED_MS || b.speed >= MIN_SPEED_MS
            }
            if (walking) out += a.ms to b.ms
        }
        return out
    }

    /** How much of [spans] lies between [from] and [to]. */
    fun walkedMs(spans: List<Pair<Long, Long>>, from: Long, to: Long): Long =
        spans.sumOf { max(0L, min(it.second, to) - max(it.first, from)) }

    /**
     * Trips that began and ended at the same named place and never left it. Older versions of the app wrote these down as trips;
     * now they are only a walk inside the stay. [points] are the route readings of each trip, by trip id.
     */
    fun insideTrips(trips: List<TripEntity>, points: Map<String, List<TripPointEntity>>, places: List<PlaceEntity>): Set<String> {
        val out = HashSet<String>()
        for (t in trips) {
            if (t.fromPlaceId.isEmpty() || t.fromPlaceId != t.toPlaceId) continue
            val route = points[t.id].orEmpty()
            if (route.isEmpty()) continue
            if (route.all { PlaceRules.stillInside(places, t.fromPlaceId, it.lat, it.lng, it.acc) }) out += t.id
        }
        return out
    }

    /** "Walked about 15 min", or null when it was too little to mention. */
    fun text(ms: Long): String? =
        if (ms < MIN_SHOWN_MS) null else "Walked about " + PlaceRules.durationText(ms)
}
