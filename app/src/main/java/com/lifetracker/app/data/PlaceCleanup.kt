package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.min

/**
 * Looks at the readings of a stretch of time again, with hindsight, and brings the saved visits and trips in line.
 * It only ever adds a visit or moves a visit's start earlier or its end later: it never deletes or shortens anything,
 * never touches a visit still in progress, and keeps what you decided (the name you gave a spot, "Not a place").
 */
object PlaceCleanup {
    /** Readings this recent are left to the live detector, which is still working on them. */
    const val SETTLE_MS = 30 * 60_000L

    class Plan(val adds: List<PlaceVisitEntity>, val updates: List<PlaceVisitEntity>)

    fun plan(stays: List<BuiltStay>, places: List<PlaceEntity>, saved: List<PlaceVisitEntity>): Plan {
        val adds = ArrayList<PlaceVisitEntity>()
        val updates = LinkedHashMap<String, PlaceVisitEntity>()
        for (s in stays.sortedBy { it.startMs }) {
            val place = PlaceRules.placeAt(places, s.lat, s.lng)
            val placeId = place?.id ?: ""
            val current = saved.map { updates[it.id] ?: it }
            // A stay still in progress belongs to the live detector.
            if (current.any { it.ongoing && s.startMs <= it.endMs + PlaceRules.MERGE_GAP_MS && s.endMs >= it.startMs - PlaceRules.MERGE_GAP_MS }) continue

            fun samePlace(v: PlaceVisitEntity) =
                if (placeId.isNotEmpty()) v.placeId == placeId
                else v.placeId.isEmpty() && PlaceRules.distanceM(v.lat, v.lng, s.lat, s.lng) <= PlaceRules.MERGE_DISTANCE_M

            val matching = current.filter { samePlace(it) && PlaceRules.overlapsInTime(it, s.startMs, s.endMs) }
            if (matching.isEmpty()) {
                // Somewhere else was already recorded at that time: leave it alone.
                if (current.any { it.startMs < s.endMs && PlaceRules.endOf(it) > s.startMs }) continue
                if (adds.any { it.startMs < s.endMs && it.endMs > s.startMs }) continue
                adds.add(
                    PlaceVisitEntity(
                        id = "c" + s.startMs,
                        placeId = placeId,
                        startMs = s.startMs,
                        endMs = s.endMs,
                        lat = place?.lat ?: s.lat,
                        lng = place?.lng ?: s.lng,
                        source = "clean",
                        ongoing = false,
                        ignored = false,
                    ),
                )
                continue
            }
            val primary = matching.minByOrNull { it.startMs } ?: continue
            val others = current.filter { it.id != primary.id }
            // Stretch it, but not into a neighbouring visit.
            val floor = others.filter { it.endMs <= primary.startMs }.maxOfOrNull { it.endMs } ?: Long.MIN_VALUE
            val ceiling = others.filter { it.startMs >= primary.endMs }.minOfOrNull { it.startMs } ?: Long.MAX_VALUE
            val start = max(min(primary.startMs, s.startMs), floor)
            val end = min(max(primary.endMs, s.endMs), ceiling)
            if (start < primary.startMs || end > primary.endMs) {
                updates[primary.id] = primary.copy(startMs = min(start, primary.startMs), endMs = max(end, primary.endMs))
            }
        }
        return Plan(adds, updates.values.toList())
    }

    /** The trip with its distance and speeds worked out again from its readings with the bad ones removed, or null if nothing changed. */
    fun refreshTrip(trip: TripEntity, points: List<TripPointEntity>): TripEntity? {
        if (points.size < 2) return null
        val clean = FixFilter.clean(points.map { RawFixEntity(it.ms, it.lat, it.lng, it.acc) })
            .map { TripPointEntity(trip.id, it.ms, it.lat, it.lng, it.acc) }
        if (clean.size < 2) return null
        val stats = TripRules.stats(clean, trip.startMs, trip.endMs)
        if (stats.distanceM == trip.distanceM && stats.medianKmh == trip.medianKmh && stats.topKmh == trip.topKmh) return null
        return trip.copy(distanceM = stats.distanceM, medianKmh = stats.medianKmh, topKmh = stats.topKmh)
    }
}

/** When to start the position readings again because Android stopped them. */
object PlacesWatchdog {
    private const val MIN_STALE_MS = 4 * 60_000L

    /** The readings are running if they said so and a reading (or the start) was seen recently. */
    fun alive(watching: Boolean, lastBeatMs: Long, nowMs: Long, intervalSec: Int): Boolean =
        watching && nowMs - lastBeatMs <= max(MIN_STALE_MS, intervalSec * 3_000L)

    /** Start them if they should be running and are not. */
    fun shouldStart(
        enabled: Boolean,
        alwaysOn: Boolean,
        wanted: Boolean,
        watching: Boolean,
        lastBeatMs: Long,
        nowMs: Long,
        intervalSec: Int,
    ): Boolean = enabled && (alwaysOn || wanted) && !alive(watching, lastBeatMs, nowMs, intervalSec)
}
