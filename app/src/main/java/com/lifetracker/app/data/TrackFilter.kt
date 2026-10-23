package com.lifetracker.app.data

/** How exact the phone is asked to be. Plain words so a setting or a backup file stays readable. */
object AccuracyMode {
    const val HIGH = "high"
    const val BALANCED = "balanced"
    const val LOW_POWER = "low-power"
    val all = listOf(HIGH, BALANCED, LOW_POWER)

    fun label(id: String): String = when (id) {
        HIGH -> "High (GPS)"
        BALANCED -> "Balanced"
        LOW_POWER -> "Low power"
        else -> "Normal"
    }
}

/** The recording settings in force: yours, or a profile's where one applies (see [ProfileRules.effective]). */
data class TrackSettings(
    /** Seconds between readings while recording. */
    val intervalSec: Int = 30,
    /** Fewest metres a reading must be from the last kept one to be kept; 0 keeps every reading. */
    val minMoveM: Int = 0,
    val accuracyMode: String = AccuracyMode.HIGH,
    /** Readings less accurate than this many metres are refused; 0 refuses none. */
    val accuracyLimitM: Int = PlaceRules.MAX_ACCURACY_M.toInt(),
)

/** Why a reading was not kept. The word is what goes in the diary. */
enum class Reject(val word: String) {
    OUT_OF_ORDER("out of order"),
    TOO_INACCURATE("too inaccurate"),
    JUMP("jump"),
    TOO_CLOSE("too close to the last one"),
}

/** What the filter remembers between readings: the last kept one and how many jumps in a row were refused. */
data class FilterState(val last: RawFixEntity? = null, val jumpsInRow: Int = 0)

class FilterVerdict(val state: FilterState, val reject: Reject?)

/** Decides, one reading at a time, which readings are kept while the all-day engine records. */
object LiveFilter {
    /** After this many jumps refused in a row the place they point to is believed (the reading kept before was the bad one). */
    const val RECOVER_AFTER = 3

    fun check(state: FilterState, next: RawFixEntity, accuracyLimitM: Int, minMoveM: Int): FilterVerdict {
        val last = state.last
        if (last != null && next.ms <= last.ms) return FilterVerdict(state, Reject.OUT_OF_ORDER)
        if (accuracyLimitM > 0 && next.acc > accuracyLimitM) return FilterVerdict(state, Reject.TOO_INACCURATE)
        if (last != null) {
            val seconds = (next.ms - last.ms) / 1000.0
            val d = FixFilter.dist(last, next)
            val tooFast = seconds > 0.0 && d / seconds > FixFilter.MAX_SPEED_MS && d > 200.0
            if ((tooFast || FixFilter.isJump(last, next)) && state.jumpsInRow < RECOVER_AFTER) {
                return FilterVerdict(state.copy(jumpsInRow = state.jumpsInRow + 1), Reject.JUMP)
            }
            if (minMoveM > 0 && d < minMoveM) return FilterVerdict(state.copy(jumpsInRow = 0), Reject.TOO_CLOSE)
        }
        return FilterVerdict(FilterState(next, 0), null)
    }
}

/** Speed over the last few readings, so one odd reading does not switch a profile on or off. */
object RollingSpeed {
    const val WINDOW = 5

    /** The speed of each step between neighbouring readings, in m/s: the phone's own speed when it said, else distance over time. */
    internal fun stepSpeeds(fixes: List<RawFixEntity>): List<Double> {
        val out = ArrayList<Double>()
        for (i in 1 until fixes.size) {
            val a = fixes[i - 1]
            val b = fixes[i]
            val seconds = (b.ms - a.ms) / 1000.0
            if (b.speed >= 0f) out.add(b.speed.toDouble())
            else if (seconds > 0.0) out.add(FixFilter.dist(a, b) / seconds)
        }
        return out
    }

    /** Average speed in m/s over the last [WINDOW] readings (oldest first), or null with fewer than two readings. */
    fun avgMs(fixes: List<RawFixEntity>): Double? {
        val window = fixes.takeLast(WINDOW)
        val speeds = stepSpeeds(window)
        return if (speeds.isEmpty()) null else speeds.average()
    }

    fun avgKmh(fixes: List<RawFixEntity>): Double? = avgMs(fixes)?.let { it * 3.6 }
}

/** Whether the phone has been still: every speed under 0.3 m/s across 60 seconds of readings. */
object StillDetector {
    const val THRESHOLD_MS = 0.3
    const val WINDOW_MS = 60_000L

    /** [fixes] oldest first. False when the readings do not cover the whole window yet. */
    fun isStill(fixes: List<RawFixEntity>, windowMs: Long = WINDOW_MS, thresholdMs: Double = THRESHOLD_MS): Boolean {
        if (fixes.size < 2) return false
        val newest = fixes.last().ms
        val from = newest - windowMs
        // Start at the last reading at or before the start of the window, so the window is covered end to end.
        val startIndex = fixes.indexOfLast { it.ms <= from }
        if (startIndex < 0) return false
        val speeds = RollingSpeed.stepSpeeds(fixes.subList(startIndex, fixes.size))
        return speeds.isNotEmpty() && speeds.all { it < thresholdMs }
    }
}
