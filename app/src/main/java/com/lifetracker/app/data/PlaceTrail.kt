package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where you were and how you got from one place to the next, as one list. No Android in it, so it can be tested.
 *
 * Stays and journeys alternate: a stay at a place, the journey to the next one, the next stay. A journey is a trip the
 * phone recorded; when you were at one spot and then at another with no trip between, it is an "unknown journey" with
 * the straight-line distance, so you can say how you went.
 */
object PlaceTrail {
    /** Two stops this close together are the same spot, so no journey is shown between them. */
    const val SAME_SPOT_M = 50

    sealed interface Row {
        val key: String
        val startMs: Long
        val endMs: Long
    }

    /** A stay. [named] is false for a spot you have not named yet. */
    class Stay(
        override val key: String,
        override val startMs: Long,
        override val endMs: Long,
        val name: String,
        val named: Boolean,
        val ongoing: Boolean,
        val visit: PlaceVisitEntity,
    ) : Row

    /** The way from one stay to the next. [trip] is null when the phone has no record of how you went. */
    class Journey(
        override val key: String,
        override val startMs: Long,
        override val endMs: Long,
        val trip: TripEntity?,
        val fromName: String,
        val toName: String,
        /** The distance of the recorded trip, or the straight line between the two stays when there is none. */
        val distanceM: Int,
        val from: PlaceVisitEntity?,
        val to: PlaceVisitEntity?,
    ) : Row {
        /** True when nobody (you or a sure guess) has said how you travelled. */
        val unknownMode: Boolean get() = isUnknownMode(trip)
    }

    /** No trip at all, or a trip the phone could only call "vehicle" and you have not answered. */
    fun isUnknownMode(trip: TripEntity?): Boolean = trip == null || (trip.mode == "vehicle" && trip.modeSource != "you")

    fun build(
        visits: List<PlaceVisitEntity>,
        trips: List<TripEntity>,
        placeNames: Map<String, String>,
        nowMs: Long,
    ): List<Row> {
        val stays = ArrayList<Stay>()
        var previousEnd = Long.MIN_VALUE
        visits
            .filter { !it.ignored && PlaceRules.lengthMs(it, nowMs) >= PlaceRules.MIN_STAY_MS }
            .sortedBy { it.startMs }
            .forEach { v ->
                val end = if (v.ongoing) max(nowMs, v.startMs) else v.endMs
                val start = max(v.startMs, previousEnd)
                if (end > start) {
                    stays += Stay(
                        key = "stay" + v.id,
                        startMs = start,
                        endMs = end,
                        name = placeNames[v.placeId]?.takeIf { it.isNotBlank() } ?: "Unnamed spot",
                        named = v.placeId.isNotEmpty(),
                        ongoing = v.ongoing,
                        visit = v,
                    )
                    previousEnd = end
                }
            }

        val sortedTrips = trips.sortedBy { it.startMs }
        var next = 0
        val out = ArrayList<Row>()

        fun journey(t: TripEntity) = Journey(
            key = "trip" + t.id,
            startMs = t.startMs,
            endMs = t.endMs,
            trip = t,
            fromName = placeNames[t.fromPlaceId].orEmpty(),
            toName = placeNames[t.toPlaceId].orEmpty(),
            distanceM = t.distanceM,
            from = null,
            to = null,
        )

        var before: Stay? = null
        for (stay in stays) {
            var tripBetween = false
            while (next < sortedTrips.size && sortedTrips[next].startMs < stay.startMs) {
                out += journey(sortedTrips[next++])
                tripBetween = true
            }
            val a = before
            if (a != null && !tripBetween && differentSpots(a.visit, stay.visit)) {
                out += Journey(
                    key = "unknown" + a.endMs,
                    startMs = a.endMs,
                    endMs = max(stay.startMs, a.endMs),
                    trip = null,
                    fromName = a.name.takeIf { a.named }.orEmpty(),
                    toName = stay.name.takeIf { stay.named }.orEmpty(),
                    distanceM = straightLineM(a.visit, stay.visit),
                    from = a.visit,
                    to = stay.visit,
                )
            }
            out += stay
            before = stay
        }
        while (next < sortedTrips.size) out += journey(sortedTrips[next++])
        return out
    }

    /** Whether you really went somewhere between these two visits. */
    private fun differentSpots(a: PlaceVisitEntity, b: PlaceVisitEntity): Boolean {
        if (a.placeId.isNotEmpty() && a.placeId == b.placeId) return false
        return straightLineM(a, b) > SAME_SPOT_M
    }

    fun straightLineM(a: PlaceVisitEntity, b: PlaceVisitEntity): Int =
        PlaceRules.distanceM(a.lat, a.lng, b.lat, b.lng).roundToInt()

    /**
     * The trip record that remembers your answer for a journey the phone has no trip for. It is a new record
     * (nothing is changed or deleted) and counts as your own answer.
     */
    fun journeyTrip(from: PlaceVisitEntity, to: PlaceVisitEntity, startMs: Long, endMs: Long, distanceM: Int, mode: String): TripEntity {
        val ms = max(0L, endMs - startMs)
        return TripEntity(
            id = "manual" + startMs + "-" + from.id + "-" + to.id,
            startMs = startMs,
            endMs = endMs,
            fromPlaceId = from.placeId,
            toPlaceId = to.placeId,
            distanceM = distanceM,
            medianKmh = if (ms > 0) TripRules.avgKmh(distanceM, ms) else 0,
            topKmh = 0,
            activity = "",
            mode = mode,
            modeSource = "you",
        )
    }

    fun tripFor(j: Journey, mode: String): TripEntity? {
        val a = j.from ?: return null
        val b = j.to ?: return null
        return journeyTrip(a, b, j.startMs, j.endMs, j.distanceM, mode)
    }
}
