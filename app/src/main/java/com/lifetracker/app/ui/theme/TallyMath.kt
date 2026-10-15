package com.lifetracker.app.ui.theme

import kotlin.math.ceil
import kotlin.math.floor

/** The counting behind a tally-stroke progress bar: a row of slanted strokes, the first ones drawn dark. */
object TallyMath {
    /** How many strokes fit across [width] when each takes [pitch]. */
    fun count(width: Float, pitch: Float): Int = if (width <= 0f || pitch <= 0f) 0 else ceil(width / pitch).toInt()

    /**
     * How many strokes are dark for a [progress] from 0 to 1. A stroke is dark once the progress has reached its
     * middle, so 0 is none, 1 is all, and a stroke is never half drawn.
     */
    fun filled(progress: Float, count: Int): Int {
        if (count <= 0) return 0
        val p = progress.coerceIn(0f, 1f)
        return floor(p * count + 0.5f).toInt().coerceIn(0, count)
    }
}
