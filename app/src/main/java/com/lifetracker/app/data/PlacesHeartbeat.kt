package com.lifetracker.app.data

/**
 * The safety-net position check (Live logs build L2): every 15 minutes, when the continuous readings are not running, the app
 * asks the phone for one position, so a stay or a move is noticed even if Android sent no message. Kept free of Android so it is tested.
 */
object PlacesHeartbeat {
    /** Checks closer together than this are skipped (the 15-minute check can run a little early). */
    const val MIN_GAP_MS = 10 * 60_000L

    /** A position older than this is not used: it says where you were, not where you are. */
    const val MAX_AGE_MS = 5 * 60_000L

    fun due(enabled: Boolean, switchOn: Boolean, hasLocation: Boolean, readingsRunning: Boolean, nowMs: Long, lastMs: Long): Boolean =
        enabled && switchOn && hasLocation && !readingsRunning && nowMs - lastMs >= MIN_GAP_MS

    /** Whether a position taken at [fixMs] is fresh enough at [nowMs] (a small clock difference ahead is allowed). */
    fun usable(fixMs: Long, nowMs: Long): Boolean = nowMs - fixMs in -60_000L..MAX_AGE_MS
}
