package com.lifetracker.app.data

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A spot on the drawing, in pixels from the top left. */
class DrawPoint(val x: Float, val y: Float)

/**
 * Turns positions into a drawing: the frame is the smallest box that holds everything (and the circles you give it), and every
 * position is placed in it with north up, keeping true proportions (a degree of longitude is shorter than one of latitude away
 * from the equator). It is the plain background used until the map arrives, and the way a point is picked on a path.
 */
class PathFrame private constructor(
    private val minLat: Double,
    private val maxLat: Double,
    private val minLng: Double,
    private val maxLng: Double,
    private val width: Float,
    private val height: Float,
    private val padding: Float,
) {
    private val midLat = (minLat + maxLat) / 2.0
    private val lngScale = cos(Math.toRadians(midLat)).coerceAtLeast(0.01)
    private val spanX = max((maxLng - minLng) * lngScale, 1e-9)
    private val spanY = max(maxLat - minLat, 1e-9)

    /** Pixels for one degree of latitude. */
    private val scale: Double = min((width - 2 * padding) / spanX, (height - 2 * padding) / spanY).let { if (it.isFinite() && it > 0) it else 1.0 }
    private val offsetX = (width - spanX * scale) / 2.0
    private val offsetY = (height - spanY * scale) / 2.0

    fun project(lat: Double, lng: Double): DrawPoint =
        DrawPoint(
            (offsetX + (lng - minLng) * lngScale * scale).toFloat(),
            (offsetY + (maxLat - lat) * scale).toFloat(),
        )

    /** How many pixels [metres] is on this drawing. */
    fun pixels(metres: Double): Float = (metres / METRES_PER_DEGREE * scale).toFloat()

    /** The index of the position nearest to the spot (x, y), or -1 when there are none. */
    fun nearest(points: List<DrawPoint>, x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        points.forEachIndexed { i, p ->
            val d = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    class Circle(val lat: Double, val lng: Double, val radiusM: Double)

    companion object {
        const val METRES_PER_DEGREE = 111_320.0

        /** A frame for [positions] (lat, lng) and [circles]; with nothing to show it is a small box around 0, 0. */
        fun of(
            positions: List<Pair<Double, Double>>,
            width: Float,
            height: Float,
            padding: Float = 12f,
            circles: List<Circle> = emptyList(),
        ): PathFrame {
            var lat0 = Double.MAX_VALUE
            var lat1 = -Double.MAX_VALUE
            var lng0 = Double.MAX_VALUE
            var lng1 = -Double.MAX_VALUE
            for ((lat, lng) in positions) {
                lat0 = min(lat0, lat); lat1 = max(lat1, lat)
                lng0 = min(lng0, lng); lng1 = max(lng1, lng)
            }
            for (c in circles) {
                val dLat = c.radiusM / METRES_PER_DEGREE
                val dLng = dLat / cos(Math.toRadians(c.lat)).coerceAtLeast(0.01)
                lat0 = min(lat0, c.lat - dLat); lat1 = max(lat1, c.lat + dLat)
                lng0 = min(lng0, c.lng - dLng); lng1 = max(lng1, c.lng + dLng)
            }
            if (lat0 > lat1) {
                lat0 = -0.0005; lat1 = 0.0005; lng0 = -0.0005; lng1 = 0.0005
            }
            // A single spot (or a path that never moved) still gets a box to sit in: about 100 m across.
            if (lat1 - lat0 < 0.0005) {
                val mid = (lat0 + lat1) / 2.0
                lat0 = mid - 0.00025; lat1 = mid + 0.00025
            }
            if (lng1 - lng0 < 0.0005) {
                val mid = (lng0 + lng1) / 2.0
                lng0 = mid - 0.00025; lng1 = mid + 0.00025
            }
            return PathFrame(lat0, lat1, lng0, lng1, width, height, padding)
        }
    }
}
