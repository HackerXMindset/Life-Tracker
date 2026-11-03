package com.lifetracker.app.data

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The shape of a place's area. A place is a circle until you draw it freehand on the map: then [PlaceEntity.polygon] holds the
 * corners ("lat,lng;lat,lng;...") and [PlaceEntity.lat], [PlaceEntity.lng] and [PlaceEntity.radiusM] hold the circle that just
 * contains it (for Android's wake-up ring, offline maps and names). Only the stay detection looks at the real shape. No Android in here.
 */
object PlaceShape {
    const val MIN_CORNERS = 3
    const val MAX_CORNERS = 40
    private const val M_PER_DEG = 111_320.0

    fun parse(text: String): List<Pair<Double, Double>> =
        if (text.isBlank()) emptyList() else text.split(';').mapNotNull { part ->
            val bits = part.split(',')
            val lat = bits.getOrNull(0)?.trim()?.toDoubleOrNull()
            val lng = bits.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (bits.size == 2 && lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
        }

    fun format(points: List<Pair<Double, Double>>): String =
        points.joinToString(";") { String.format(java.util.Locale.ENGLISH, "%.6f,%.6f", it.first, it.second) }

    /** Whether the corners make a usable area. */
    fun valid(points: List<Pair<Double, Double>>): Boolean = points.size in MIN_CORNERS..MAX_CORNERS && areaM2(points) > 100.0

    /** Metres east and north of [lat0], [lng0]. */
    private fun local(lat0: Double, lng0: Double, lat: Double, lng: Double): Pair<Double, Double> =
        ((lng - lng0) * M_PER_DEG * cos(Math.toRadians(lat0))) to ((lat - lat0) * M_PER_DEG)

    fun areaM2(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0
        val o = points[0]
        val p = points.map { local(o.first, o.second, it.first, it.second) }
        var sum = 0.0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            sum += a.first * b.second - b.first * a.second
        }
        return kotlin.math.abs(sum) / 2.0
    }

    /** The middle of the area (the average of the corners when the area is too thin to tell). */
    fun centre(points: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (points.isEmpty()) return 0.0 to 0.0
        val o = points[0]
        val p = points.map { local(o.first, o.second, it.first, it.second) }
        var a2 = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            val cross = a.first * b.second - b.first * a.second
            a2 += cross
            cx += (a.first + b.first) * cross
            cy += (a.second + b.second) * cross
        }
        val east: Double
        val north: Double
        if (kotlin.math.abs(a2) < 1e-6) {
            east = p.map { it.first }.average()
            north = p.map { it.second }.average()
        } else {
            east = cx / (3 * a2)
            north = cy / (3 * a2)
        }
        return (o.first + north / M_PER_DEG) to (o.second + east / (M_PER_DEG * cos(Math.toRadians(o.first))))
    }

    /** The radius, in whole metres, of the circle round [centre] that holds every corner. */
    fun boundingRadiusM(points: List<Pair<Double, Double>>, centre: Pair<Double, Double>): Int =
        kotlin.math.ceil(points.maxOfOrNull { PlaceRules.distanceM(centre.first, centre.second, it.first, it.second) } ?: 0.0).toInt()

    /** Whether the point is inside the corners (the edge counts as inside). */
    fun contains(points: List<Pair<Double, Double>>, lat: Double, lng: Double): Boolean {
        if (points.size < 3) return false
        val p = points.map { local(lat, lng, it.first, it.second) }
        var inside = false
        var j = p.size - 1
        for (i in p.indices) {
            val a = p[i]
            val b = p[j]
            // The point is the origin: a ray going east from it crosses the edge a-b when the edge straddles y = 0 and lies to the east.
            if ((a.second > 0) != (b.second > 0)) {
                val x = a.first + (0 - a.second) * (b.first - a.first) / (b.second - a.second)
                if (x > 0) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** How far the point is from the nearest edge, in metres. */
    fun edgeDistanceM(points: List<Pair<Double, Double>>, lat: Double, lng: Double): Double {
        if (points.isEmpty()) return Double.MAX_VALUE
        val p = points.map { local(lat, lng, it.first, it.second) }
        var best = Double.MAX_VALUE
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            val dx = b.first - a.first
            val dy = b.second - a.second
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, (-a.first * dx - a.second * dy) / len2))
            val px = a.first + t * dx
            val py = a.second + t * dy
            best = min(best, sqrt(px * px + py * py))
        }
        return best
    }

    /** Whether the point is in the area of [place], allowing [slackM] metres for the reading's own error. */
    fun within(place: PlaceEntity, lat: Double, lng: Double, slackM: Double = 0.0): Boolean {
        if (place.polygon.isEmpty()) return PlaceRules.distanceM(place.lat, place.lng, lat, lng) <= place.radiusM + slackM
        val corners = parse(place.polygon)
        if (corners.size < MIN_CORNERS) return PlaceRules.distanceM(place.lat, place.lng, lat, lng) <= place.radiusM + slackM
        // The circle that holds the area is a quick no for far points.
        if (PlaceRules.distanceM(place.lat, place.lng, lat, lng) > place.radiusM + slackM + 1.0) return false
        return contains(corners, lat, lng) || edgeDistanceM(corners, lat, lng) <= slackM
    }

    /** How far the point is from the place: from the centre of a circle, or from the edge of a drawn area (0 inside). */
    fun distanceToM(place: PlaceEntity, lat: Double, lng: Double): Double {
        val corners = parse(place.polygon)
        if (corners.size < MIN_CORNERS) return PlaceRules.distanceM(place.lat, place.lng, lat, lng)
        return if (contains(corners, lat, lng)) 0.0 else edgeDistanceM(corners, lat, lng)
    }

    /** A starting shape to adjust: [sides] corners round a circle. */
    fun fromCircle(lat: Double, lng: Double, radiusM: Double, sides: Int = 8): List<Pair<Double, Double>> =
        (0 until sides).map { i ->
            val a = Math.toRadians(360.0 * i / sides)
            (lat + radiusM * kotlin.math.sin(a) / M_PER_DEG) to (lng + radiusM * cos(a) / (M_PER_DEG * cos(Math.toRadians(lat))))
        }
}
