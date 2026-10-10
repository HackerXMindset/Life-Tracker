package com.lifetracker.app.data

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * One stay: the phone has stayed within about 60 m of [lat], [lng] since [startMs]. [n] position readings have
 * been averaged into that centre; the last one was at [lastSeenMs]. [visitId] is the saved visit row ("" until the
 * stay has lasted long enough to save). [placeId] is the named place the centre is inside, or "".
 */
data class Stay(
    val startMs: Long,
    val lat: Double,
    val lng: Double,
    val n: Int,
    val lastSeenMs: Long,
    val visitId: String = "",
    val placeId: String = "",
    val ignored: Boolean = false,
)

/** Everything [PlaceEngine] needs to remember between position readings. It is kept on the phone as a small JSON text. */
data class WatchState(
    val stay: Stay? = null,
    /** True from when the phone starts moving until it goes still again. */
    val moving: Boolean = false,
    /** The last time something (still/moving, arriving or leaving a ring) asked the app to look at where you are. */
    val triggerMs: Long = 0L,
    /** When the phone last started moving. */
    val moveStartMs: Long = 0L,
    /** When the phone last went still, until a stay uses it as its start. */
    val stillSinceMs: Long = 0L,
) {
    fun toJson(): String {
        val o = JSONObject()
            .put("moving", moving).put("trigger", triggerMs).put("moveStart", moveStartMs).put("stillSince", stillSinceMs)
        if (stay != null) {
            o.put(
                "stay",
                JSONObject()
                    .put("start", stay.startMs).put("lat", stay.lat).put("lng", stay.lng).put("n", stay.n)
                    .put("last", stay.lastSeenMs).put("visit", stay.visitId).put("place", stay.placeId)
                    .put("ignored", stay.ignored),
            )
        }
        return o.toString()
    }

    companion object {
        fun fromJson(text: String): WatchState = try {
            val o = JSONObject(text)
            val s = o.optJSONObject("stay")
            WatchState(
                stay = s?.let {
                    Stay(
                        startMs = it.getLong("start"),
                        lat = it.getDouble("lat"),
                        lng = it.getDouble("lng"),
                        n = it.getInt("n"),
                        lastSeenMs = it.getLong("last"),
                        visitId = it.optString("visit", ""),
                        placeId = it.optString("place", ""),
                        ignored = it.optBoolean("ignored", false),
                    )
                },
                moving = o.optBoolean("moving", false),
                triggerMs = o.optLong("trigger", 0L),
                moveStartMs = o.optLong("moveStart", 0L),
                stillSinceMs = o.optLong("stillSince", 0L),
            )
        } catch (e: Exception) {
            WatchState()
        }
    }
}

/**
 * Turns position readings into stays, with no Android in it so it can be tested.
 *
 * A reading within about 60 m of the current stay's centre belongs to it; one further away ends the stay and
 * starts a new one. A stay of 5 minutes or more is saved as a visit (to the named place whose circle holds its
 * centre, or to no place, which puts it in the review list) and updated with every reading. Readings come every
 * minute while the app does not know where you are; at a named place it takes two readings to be sure and then
 * leaves the watching to Android until you move.
 */
object PlaceEngine {
    /** How far a reading may be from a stay's centre and still belong to it (plus the reading's own error, up to 50 m). */
    const val STAY_RADIUS_M = 60.0

    /** While you are moving, readings are only taken this long, to see whether you really left or are about to stop. */
    const val MOVE_CHECK_MS = 10 * 60_000L

    /** A gap this short between the last reading in a stay and the first one outside means the last reading was the departure. */
    const val CLOSE_WATCH_MS = 5 * 60_000L

    /** A "went still" time older than this is not used as the start of a new stay. */
    private const val STILL_HINT_MAX_MS = 30 * 60_000L

    class Step(val state: WatchState, val saves: List<PlaceVisitEntity>, val keepWatching: Boolean)

    fun onTrigger(state: WatchState, nowMs: Long): WatchState = state.copy(triggerMs = nowMs)

    /** The phone went still at [atMs] (told to us at [nowMs]). */
    fun onStill(state: WatchState, atMs: Long, nowMs: Long): WatchState =
        state.copy(moving = false, stillSinceMs = atMs, triggerMs = nowMs)

    /** The phone started moving at [atMs]. */
    fun onMoving(state: WatchState, atMs: Long, nowMs: Long): WatchState =
        state.copy(moving = true, moveStartMs = atMs, stillSinceMs = 0L, triggerMs = nowMs)

    /** When a stay ended: when you started moving if that fits, else the last reading if it was recent, else when you were noticed gone. */
    fun departure(lastSeenMs: Long, firstOutsideMs: Long, moveStartMs: Long): Long = when {
        moveStartMs in lastSeenMs..firstOutsideMs -> moveStartMs
        firstOutsideMs - lastSeenMs <= CLOSE_WATCH_MS -> lastSeenMs
        else -> firstOutsideMs
    }

    /** Whether the app should keep taking position readings right now. */
    fun keepWatching(state: WatchState, nowMs: Long): Boolean {
        if (state.moving) return nowMs - state.triggerMs < MOVE_CHECK_MS
        val stay = state.stay ?: return nowMs - state.triggerMs < MOVE_CHECK_MS
        // At a spot the app does not know it keeps checking until you leave. At a named place two readings are enough.
        return stay.placeId.isEmpty() || stay.n < 2
    }

    /** Takes one position reading. [recent] are the visits saved in the last few hours, so a stay can join one. */
    fun onFix(state: WatchState, fix: Fix, places: List<PlaceEntity>, recent: List<PlaceVisitEntity>, nowMs: Long): Step {
        if (fix.accuracyM > PlaceRules.MAX_ACCURACY_M) return Step(state, emptyList(), keepWatching(state, nowMs))

        val saves = ArrayList<PlaceVisitEntity>()
        var s = state
        val stay = s.stay
        if (stay != null) {
            val d = PlaceRules.distanceM(stay.lat, stay.lng, fix.lat, fix.lng)
            if (d <= STAY_RADIUS_M + min(fix.accuracyM.toDouble(), 50.0)) {
                val n = stay.n + 1
                val lat = stay.lat + (fix.lat - stay.lat) / n
                val lng = stay.lng + (fix.lng - stay.lng) / n
                s = s.copy(
                    stay = stay.copy(
                        lat = lat,
                        lng = lng,
                        n = n,
                        lastSeenMs = max(stay.lastSeenMs, fix.timeMs),
                        placeId = PlaceRules.placeAt(places, lat, lng)?.id ?: "",
                    ),
                )
            } else {
                val end = departure(stay.lastSeenMs, fix.timeMs, s.moveStartMs)
                if (stay.visitId.isNotEmpty() || end - stay.startMs >= PlaceRules.MIN_STAY_MS) {
                    val joined = if (stay.visitId.isEmpty()) join(stay.copy(lastSeenMs = end), recent) else stay
                    saves.add(visit(joined, max(end, joined.startMs), ongoing = false, places))
                }
                s = s.copy(stay = null)
            }
        }

        if (s.stay == null) {
            val still = s.stillSinceMs
            val start = if (still in 1..fix.timeMs && fix.timeMs - still <= STILL_HINT_MAX_MS) still else fix.timeMs
            s = s.copy(
                stay = Stay(
                    startMs = start,
                    lat = fix.lat,
                    lng = fix.lng,
                    n = 1,
                    lastSeenMs = fix.timeMs,
                    placeId = PlaceRules.placeAt(places, fix.lat, fix.lng)?.id ?: "",
                ),
                stillSinceMs = 0L,
            )
        }

        val current = s.stay
        if (current != null && current.lastSeenMs - current.startMs >= PlaceRules.MIN_STAY_MS) {
            val joined = if (current.visitId.isEmpty()) join(current, recent) else current
            s = s.copy(stay = joined)
            saves.add(visit(joined, joined.lastSeenMs, ongoing = true, places))
        }

        val keep = keepWatching(s, nowMs)
        // Moving and the window is over: a stay that never became a visit was only a reading on the road.
        val last = s.stay
        if (!keep && s.moving && last != null && last.visitId.isEmpty()) s = s.copy(stay = null)
        return Step(s, saves, keep)
    }

    /** Gives a stay the id (and the earlier start) of a saved visit it continues, or a new id. */
    private fun join(stay: Stay, recent: List<PlaceVisitEntity>): Stay {
        val hit = recent.firstOrNull { v ->
            !v.ongoing && PlaceRules.closeInTime(v, stay.startMs, stay.lastSeenMs) &&
                if (stay.placeId.isNotEmpty()) {
                    v.placeId == stay.placeId
                } else {
                    v.placeId.isEmpty() && PlaceRules.distanceM(v.lat, v.lng, stay.lat, stay.lng) <= PlaceRules.MERGE_DISTANCE_M
                }
        }
        return if (hit == null) {
            stay.copy(visitId = PlaceRules.newId(stay.startMs))
        } else {
            stay.copy(visitId = hit.id, startMs = min(hit.startMs, stay.startMs), ignored = hit.ignored)
        }
    }

    private fun visit(stay: Stay, endMs: Long, ongoing: Boolean, places: List<PlaceEntity>): PlaceVisitEntity {
        val place = places.firstOrNull { it.id == stay.placeId }
        return PlaceVisitEntity(
            id = stay.visitId,
            placeId = stay.placeId,
            startMs = stay.startMs,
            endMs = max(endMs, stay.startMs),
            lat = place?.lat ?: stay.lat,
            lng = place?.lng ?: stay.lng,
            source = "stop",
            ongoing = ongoing,
            ignored = stay.ignored,
        )
    }
}
