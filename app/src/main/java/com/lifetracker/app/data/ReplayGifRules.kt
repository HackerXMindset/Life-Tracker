package com.lifetracker.app.data

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** The rules for turning a replay into a GIF, with no Android in them so they can be tested. */
object ReplayGifRules {
    /** Frames each second of the GIF. */
    const val FPS = 10

    /** The sizes on offer: the picture is square, this many pixels on a side. */
    val SIZES = listOf("Small" to 240, "Medium" to 360, "Large" to 480)

    /** The lengths on offer, in seconds. */
    val LENGTHS = listOf(10, 20, 30, 60)

    /** The clock time shown in each frame, from [startMs] until the end of the day or [maxFrames], whichever comes first. */
    fun frameTimes(startMs: Long, endMs: Long, minPerS: Int, skip: Boolean, quiet: List<ReplayRules.Quiet>, maxFrames: Int): List<Long> {
        val out = ArrayList<Long>()
        var clock = startMs
        out += clock
        val stepMs = 1000L / FPS
        while (clock < endMs && out.size < maxFrames) {
            clock = ReplayRules.advance(clock, stepMs, minPerS, skip, quiet, endMs)
            out += clock
        }
        return out
    }

    /** The slowest speed (minutes of the day for each real second) that still plays the whole day within [seconds]. */
    fun fitMinPerS(startMs: Long, endMs: Long, skip: Boolean, quiet: List<ReplayRules.Quiet>, seconds: Int): Int {
        val frames = seconds * FPS
        fun reaches(m: Int) = frameTimes(startMs, endMs, m, skip, quiet, frames).last() >= endMs
        var lo = 1
        var hi = 20_000
        if (!reaches(hi)) return hi
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (reaches(mid)) hi = mid else lo = mid + 1
        }
        return lo
    }

    /** The colours: a 6 x 6 x 6 cube (216) padded with black to 256. */
    val PALETTE: IntArray = IntArray(256) { i ->
        if (i < 216) {
            val r = i / 36
            val g = (i / 6) % 6
            val b = i % 6
            ((r * 51) shl 16) or ((g * 51) shl 8) or (b * 51)
        } else {
            0
        }
    }

    /** The palette position nearest to [argb]. */
    fun paletteIndex(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return ((r * 5 + 127) / 255) * 36 + ((g * 5 + 127) / 255) * 6 + ((b * 5 + 127) / 255)
    }

    /** Fits the readings into a [size] x [size] picture with north up and equal scale in both directions. */
    class Projection(samples: List<ReplayRules.Sample>, private val size: Int, margin: Int) {
        private val cLat: Double
        private val cLng: Double
        private val kx: Double
        val scale: Double

        init {
            if (samples.isEmpty()) {
                cLat = 0.0; cLng = 0.0; kx = 1.0; scale = 1.0
            } else {
                val minLat = samples.minOf { it.lat }
                val maxLat = samples.maxOf { it.lat }
                val minLng = samples.minOf { it.lng }
                val maxLng = samples.maxOf { it.lng }
                cLat = (minLat + maxLat) / 2
                cLng = (minLng + maxLng) / 2
                kx = cos(Math.toRadians(cLat))
                val w = (maxLng - minLng) * kx
                val h = maxLat - minLat
                val room = (size - 2 * margin).toDouble()
                // A day spent in one spot still gets a sensible picture (about 200 m across).
                val span = max(max(w, h), 0.002)
                scale = room / span
            }
        }

        fun x(lng: Double): Float = (size / 2.0 + (lng - cLng) * kx * scale).toFloat()
        fun y(lat: Double): Float = (size / 2.0 - (lat - cLat) * scale).toFloat()

        /** How many pixels [metres] on the ground take up. */
        fun pixels(metres: Double): Float = (metres / 111_320.0 * scale).toFloat()
    }

    /** "1.2 MB", "340 KB". */
    fun sizeText(bytes: Long): String = if (bytes >= 1_000_000L) String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0) else "${max(1L, bytes / 1000L)} KB"

    /** Frames for one setting, for the line that says how long the GIF will be. */
    fun framesFor(seconds: Int): Int = min(seconds * FPS, 600)
}
