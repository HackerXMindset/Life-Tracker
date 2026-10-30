package com.lifetracker.app.data

/** A trip worked out from the readings, with the readings it was worked out from. */
class BuiltTrip(val trip: TripEntity, val points: List<TripPointEntity>)

/**
 * Builds the trips that GPS all day made but the live detector did not write down (for example a trip that passed through a
 * pause zone, or came after a long silence). It uses the same rules as [TripCutter] and puts your merges and splits on top
 * ([TripEditRules]). It only ever adds: a trip that is already there, hidden or not, is never changed and nothing is built over it.
 */
object TripBuilder {
    /** A place is the start or end of a trip when you were there this close (in time) to the first or last reading. */
    const val PLACE_SLACK_MS = 10 * 60_000L

    /**
     * [fixes] are the readings of the stretch being looked at, [stays] the stays found in them, [existing] every trip already saved
     * and [visits] the saved visits (they give the place at each end). [settledBeforeMs] is the time up to which the readings are
     * complete: a trip that ends within one silence of it may still be going, so it waits for the next time.
     */
    fun plan(
        fixes: List<RawFixEntity>,
        stays: List<BuiltStay>,
        existing: List<TripEntity>,
        visits: List<PlaceVisitEntity>,
        edits: List<TripEditEntity>,
        learned: List<TripModes.Learned>,
        strideM: Double,
        settledBeforeMs: Long,
        gapMs: Long = TripCutter.GAP_MS,
    ): List<BuiltTrip> {
        val segments = TripEditRules.trips(FixFilter.clean(fixes), stays, edits.filter { it.undoneMs == 0L }, gapMs)
        val taken = existing.sortedBy { it.startMs }
        val built = ArrayList<BuiltTrip>()
        for (s in segments) {
            if (s.endMs > settledBeforeMs - gapMs) continue
            if (taken.any { s.startMs < it.endMs && s.endMs > it.startMs }) continue
            val id = "b${s.startMs}"
            val points = s.points.map { TripPointEntity(id, it.ms, it.lat, it.lng, it.acc, it.steps) }
            val stats = TripRules.stats(points, s.startMs, s.endMs)
            if (!s.manual && !TripRules.isRealTrip(stats)) continue
            val from = placeNear(visits, s.startMs)
            val to = placeNear(visits, s.endMs)
            val mode = TripModes.guess(stats, emptyMap(), from, to, learned, TripFeatures.of(points))
            val distance = TripRules.adjust(stats.distanceM, stats.durationMs, mode, TripRules.stepsBetween(points), strideM)
            built += BuiltTrip(
                TripEntity(id, s.startMs, s.endMs, from, to, distance, stats.medianKmh, stats.topKmh, "", mode, "auto"),
                points,
            )
        }
        return built
    }

    /** The named place you were at, or close to in time, at [ms]; an empty text when there is none. */
    fun placeNear(visits: List<PlaceVisitEntity>, ms: Long): String {
        var best: PlaceVisitEntity? = null
        var bestGap = Long.MAX_VALUE
        for (v in visits) {
            if (v.placeId.isEmpty() || v.ignored) continue
            val gap = when {
                ms < v.startMs -> v.startMs - ms
                ms > v.endMs -> ms - v.endMs
                else -> 0L
            }
            if (gap < bestGap) {
                best = v
                bestGap = gap
            }
        }
        return if (best != null && bestGap <= PLACE_SLACK_MS) best.placeId else ""
    }
}
