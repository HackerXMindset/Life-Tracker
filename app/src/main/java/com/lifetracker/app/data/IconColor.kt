package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.min

/**
 * The colour an app's logo is known by, worked out from its pixels: the strongest colour in the picture, not the white or grey
 * behind it. No Android in here, so it can be tested; the screen hands in the pixels of the icon.
 */
object IconColor {
    private const val HUES = 12

    /** Used when the icon has no colour at all (a black or white or grey logo). */
    const val GREY = 0xFF738078.toInt()

    /** The main colour of [pixels] (ARGB), or a mid grey-green when there is none. Grey logos give their own grey. */
    fun dominant(pixels: IntArray): Int {
        val weight = DoubleArray(HUES)
        val r = DoubleArray(HUES)
        val g = DoubleArray(HUES)
        val b = DoubleArray(HUES)
        var greyR = 0.0
        var greyG = 0.0
        var greyB = 0.0
        var greyN = 0.0
        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            if (a < 200) continue
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            val hi = max(pr, max(pg, pb))
            val lo = min(pr, min(pg, pb))
            val sat = if (hi == 0) 0.0 else (hi - lo).toDouble() / hi
            // Near white and near black are the background, not the logo.
            val value = hi / 255.0
            if (sat < 0.25 || value < 0.2) {
                if (value in 0.2..0.9) {
                    greyR += pr; greyG += pg; greyB += pb; greyN += 1.0
                }
                continue
            }
            val bucket = (hue(pr, pg, pb, hi, lo) / 360.0 * HUES).toInt().coerceIn(0, HUES - 1)
            val w = sat * value
            weight[bucket] += w
            r[bucket] += pr * w
            g[bucket] += pg * w
            b[bucket] += pb * w
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: 0
        if (weight[best] > 0.0) {
            return argb((r[best] / weight[best]).toInt(), (g[best] / weight[best]).toInt(), (b[best] / weight[best]).toInt())
        }
        if (greyN > 0.0) return argb((greyR / greyN).toInt(), (greyG / greyN).toInt(), (greyB / greyN).toInt())
        return GREY
    }

    private fun hue(r: Int, g: Int, b: Int, hi: Int, lo: Int): Double {
        val d = (hi - lo).toDouble()
        if (d == 0.0) return 0.0
        val h = when (hi) {
            r -> ((g - b) / d) % 6
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } * 60
        return if (h < 0) h + 360 else h
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
