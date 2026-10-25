package com.lifetracker.app.data

import kotlin.math.cos

/** One stretch of a path between two readings, with the speed over it. */
class SpeedSegment(val fromLat: Double, val fromLng: Double, val toLat: Double, val toLng: Double, val kmh: Double)

/** Colours for speed on the map, and the circle of a place as a drawing. Plain code. */
object PathSpeeds {
    /** One speed band: its colour (0xRRGGBB) and how it reads. */
    class Band(val fromKmh: Double, val rgb: Int, val label: String)

    val bands: List<Band> = listOf(
        Band(0.0, 0x2E9E5B, "under 6 km/h"),
        Band(6.0, 0xD9A400, "6 to 20 km/h"),
        Band(20.0, 0xE06A1B, "20 to 50 km/h"),
        Band(50.0, 0xC62828, "over 50 km/h"),
    )

    fun band(kmh: Double): Band = bands.lastOrNull { kmh >= it.fromKmh } ?: bands.first()

    fun hex(rgb: Int): String = String.format("#%06X", rgb and 0xFFFFFF)

    /** The stretches of a path: each pair of readings, with its speed. Pairs with no time between them take the one before. */
    fun segments(lat: List<Double>, lng: List<Double>, ms: List<Long>): List<SpeedSegment> {
        val n = minOf(lat.size, lng.size)
        if (n < 2) return emptyList()
        val out = ArrayList<SpeedSegment>(n - 1)
        var last = 0.0
        for (i in 1 until n) {
            val dt = if (ms.size >= n) ms[i] - ms[i - 1] else 0L
            val m = PlaceRules.distanceM(lat[i - 1], lng[i - 1], lat[i], lng[i])
            val kmh = if (dt > 0L) m / (dt / 1000.0) * 3.6 else last
            last = kmh
            out += SpeedSegment(lat[i - 1], lng[i - 1], lat[i], lng[i], kmh)
        }
        return out
    }

    /** The outline of a circle of [radiusM] metres around a spot, as (latitude, longitude) pairs; the first point is repeated at the end. */
    fun circle(lat: Double, lng: Double, radiusM: Double, steps: Int = 64): List<Pair<Double, Double>> {
        val n = steps.coerceAtLeast(8)
        val dLat = radiusM / 111_320.0
        val dLng = radiusM / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.05))
        val out = ArrayList<Pair<Double, Double>>(n + 1)
        for (i in 0 until n) {
            val a = 2.0 * Math.PI * i / n
            out += (lat + dLat * Math.sin(a)) to (lng + dLng * Math.cos(a))
        }
        out += out.first()
        return out
    }
}
