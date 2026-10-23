package com.lifetracker.app.data

/** Where a pause zone stands. [zoneId] is empty while you are in none. */
data class PauseState(
    val zoneId: String = "",
    /** When you were first seen inside the zone. */
    val sinceMs: Long = 0L,
    val paused: Boolean = false,
    /** The time of the last heartbeat reading, or 0 for none yet. */
    val lastHeartbeatMs: Long = 0L,
)

/**
 * What to do now. [save]: keep the readings. [gpsOff]: the position readings can stop altogether (the motion sensor and the
 * Wi-Fi watch what to do next). [heartbeatNow]: take one reading now and mark it as a heartbeat. [anchor]: put a reading at this
 * place's centre (you just left it).
 */
class PauseStep(
    val state: PauseState,
    val save: Boolean,
    val gpsOff: Boolean,
    val heartbeatNow: Boolean,
    val anchor: PlaceEntity?,
    val status: String,
)

/** Pause zones: places where the dense readings stop. The stay itself is still a visit. */
object PauseZoneRules {
    /** Without an entry delay of your own the app keeps recording for this many times the interval after arriving. */
    const val ENTRY_FACTOR = 3.5

    fun entryDelayMs(zone: PlaceEntity, intervalSec: Int): Long =
        if (zone.entryDelaySec >= 0) zone.entryDelaySec * 1000L else (intervalSec * 1000L * ENTRY_FACTOR).toLong()

    /** The Wi-Fi names of a zone, one per line, ready to compare. */
    fun wifiKeys(zone: PlaceEntity): Set<String> =
        zone.pauseWifi.lines().map { ProfileRules.wifiKey(it) }.filter { it.isNotEmpty() }.toSet()

    /** The pause zone a point is in (a place with Pause here on), or null. */
    fun zoneAt(places: List<PlaceEntity>, lat: Double, lng: Double): PlaceEntity? =
        PlaceRules.placeAt(places.filter { it.pauseHere }, lat, lng)

    fun step(
        state: PauseState,
        places: List<PlaceEntity>,
        lat: Double,
        lng: Double,
        wifiName: String?,
        motionless: Boolean,
        nowMs: Long,
        intervalSec: Int,
    ): PauseStep {
        val zone = zoneAt(places, lat, lng)
        val previous = if (state.zoneId.isEmpty()) null else places.firstOrNull { it.id == state.zoneId }
        // Left a zone you had been paused in: a clean start point for the trip that follows.
        val anchor = if (state.paused && (zone == null || zone.id != state.zoneId)) previous else null
        if (zone == null) {
            return PauseStep(PauseState(), save = true, gpsOff = false, heartbeatNow = false, anchor = anchor, status = "Recording")
        }
        if (zone.id != state.zoneId) {
            return PauseStep(
                PauseState(zone.id, nowMs), save = true, gpsOff = false, heartbeatNow = false, anchor = anchor,
                status = "Recording (arrived at ${zone.name})",
            )
        }
        if (nowMs - state.sinceMs < entryDelayMs(zone, intervalSec)) {
            return PauseStep(state, save = true, gpsOff = false, heartbeatNow = false, anchor = null, status = "Recording (arrived at ${zone.name})")
        }
        val onWifi = wifiName != null && ProfileRules.wifiKey(wifiName) in wifiKeys(zone)
        val still = zone.pauseMotionless && motionless
        val gpsOff = onWifi || still
        val baseline = if (state.lastHeartbeatMs > 0L) state.lastHeartbeatMs else state.sinceMs
        val beat = zone.heartbeatMin > 0 && nowMs - baseline >= zone.heartbeatMin * 60_000L
        val next = state.copy(paused = true, lastHeartbeatMs = if (beat) nowMs else state.lastHeartbeatMs)
        val status = when {
            onWifi -> "Paused: Wi-Fi"
            still -> "Paused: no movement"
            else -> "Paused: inside ${zone.name}"
        }
        return PauseStep(next, save = false, gpsOff = gpsOff, heartbeatNow = beat, anchor = null, status = status)
    }
}
