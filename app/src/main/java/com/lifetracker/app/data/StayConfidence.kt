package com.lifetracker.app.data

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * How sure the app is that a visit really happened and was where it says, from 0 to 100, worked out from the position
 * readings taken during it. No Android in it, so it can be tested.
 *
 * Each part is between 0 and 1: how long you stayed (a full score at 30 minutes), how tightly the readings sit together
 * (the distance within which nine in ten of them lie, against the 60 m stay radius), how many readings there are (six or
 * more is plenty), how accurate they were, how much of the visit has readings rather than silence (gaps over 10 minutes
 * count against it), and whether the spot is a place you named. An unnamed spot is not suspicious, so that part is left
 * out of its score rather than counted as zero.
 */
object StayConfidence {
    /** A visit scoring below this is shown with an "unsure" tag. */
    const val UNSURE_BELOW = 50

    private const val TARGET_DWELL_MS = 30 * 60_000L
    private const val FULL_DENSITY = 6.0
    private const val SILENCE_MS = 10 * 60_000L

    private const val W_DWELL = 0.30
    private const val W_TIGHT = 0.25
    private const val W_DENSITY = 0.15
    private const val W_ACCURACY = 0.10
    private const val W_COVERAGE = 0.15
    private const val W_PLACE = 0.20

    /** The score for a visit from [startMs] to [endMs], or -1 if there are no [readings] to judge it by. Readings should be cleaned first. */
    fun score(startMs: Long, endMs: Long, readings: List<RawFixEntity>, named: Boolean): Int {
        val fixes = readings.filter { it.ms in startMs..endMs }.sortedBy { it.ms }
        if (fixes.isEmpty()) return -1
        val duration = max(endMs - startMs, 0L)

        val dwell = clamp(duration.toDouble() / TARGET_DWELL_MS)

        val lat = fixes.sumOf { it.lat } / fixes.size
        val lng = fixes.sumOf { it.lng } / fixes.size
        val distances = fixes.map { PlaceRules.distanceM(lat, lng, it.lat, it.lng) }.sorted()
        val radius = distances[max(ceil(0.9 * distances.size).toInt() - 1, 0)]
        val tight = clamp(1.0 - radius / PlaceEngine.STAY_RADIUS_M)

        val density = clamp(fixes.size / FULL_DENSITY)
        val accuracy = clamp(1.0 - fixes.sumOf { it.acc.toDouble() } / fixes.size / 100.0)

        var silence = 0L
        var previous = startMs
        for (f in fixes) {
            if (f.ms - previous > SILENCE_MS) silence += f.ms - previous
            previous = f.ms
        }
        if (endMs - previous > SILENCE_MS) silence += endMs - previous
        val coverage = if (duration <= 0L) 1.0 else clamp(1.0 - silence.toDouble() / duration)

        var sum = W_DWELL * dwell + W_TIGHT * tight + W_DENSITY * density + W_ACCURACY * accuracy + W_COVERAGE * coverage
        var weight = W_DWELL + W_TIGHT + W_DENSITY + W_ACCURACY + W_COVERAGE
        if (named) {
            sum += W_PLACE
            weight += W_PLACE
        }
        return Math.round(100.0 * sum / weight).toInt().coerceIn(0, 100)
    }

    /** Whether a visit with this score gets the "unsure" tag. Unknown (-1) never does. */
    fun isUnsure(score: Int): Boolean = score in 0 until UNSURE_BELOW

    private fun clamp(v: Double): Double = min(1.0, max(0.0, v))
}
