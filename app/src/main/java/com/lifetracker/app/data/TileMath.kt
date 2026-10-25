package com.lifetracker.app.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** A box on the map. */
class Bounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    fun contains(lat: Double, lng: Double): Boolean = lat in south..north && lng in west..east

    fun union(o: Bounds): Bounds = Bounds(min(west, o.west), min(south, o.south), max(east, o.east), max(north, o.north))

    fun intersects(o: Bounds): Boolean = west <= o.east && o.west <= east && south <= o.north && o.south <= north

    /** True when the box has a size, the right way round and on the map. */
    fun valid(): Boolean =
        west < east && south < north && south >= -85.0 && north <= 85.0 && west >= -180.0 && east <= 180.0
}

/** Map tile arithmetic (the usual web-map grid) and the sizes of downloads. */
object TileMath {
    /** A guess for the size of one tile, used only until you have a finished area to take the real average from. */
    const val GUESS_TILE_BYTES = 20_000L

    private const val KM_PER_DEGREE = 111.32

    private fun span(z: Int): Double = (1L shl z).toDouble()

    fun tileX(lng: Double, z: Int): Int =
        floor((lng.coerceIn(-180.0, 180.0) + 180.0) / 360.0 * span(z)).toInt().coerceIn(0, span(z).toInt() - 1)

    fun tileY(lat: Double, z: Int): Int {
        val rad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * span(z)
        return floor(y).toInt().coerceIn(0, span(z).toInt() - 1)
    }

    /** How many tiles cover [b] from zoom [minZ] to [maxZ], both included. */
    fun count(b: Bounds, minZ: Int, maxZ: Int): Long {
        if (maxZ < minZ) return 0L
        var total = 0L
        for (z in minZ..maxZ) {
            val w = tileX(b.east, z) - tileX(b.west, z) + 1
            val h = tileY(b.south, z) - tileY(b.north, z) + 1
            total += w.toLong() * h.toLong()
        }
        return total
    }

    /** A square of [halfKm] kilometres each way around a spot. */
    fun around(lat: Double, lng: Double, halfKm: Double): Bounds {
        val dLat = halfKm / KM_PER_DEGREE
        val dLng = (halfKm / (KM_PER_DEGREE * max(0.05, cos(Math.toRadians(lat))))).coerceAtMost(179.0)
        return Bounds(
            (lng - dLng).coerceAtLeast(-180.0), (lat - dLat).coerceAtLeast(-85.0),
            (lng + dLng).coerceAtMost(180.0), (lat + dLat).coerceAtMost(85.0),
        )
    }

    /** The average size of a tile in your finished areas (tiles, bytes of each), or the guess when there are none. */
    fun averageTileBytes(done: List<Pair<Long, Long>>): Long {
        val tiles = done.sumOf { it.first }
        val bytes = done.sumOf { it.second }
        return if (tiles > 0L && bytes > 0L) max(1L, bytes / tiles) else GUESS_TILE_BYTES
    }

    fun estimateBytes(tiles: Long, averageTileBytes: Long): Long = tiles * averageTileBytes

    fun sizeText(bytes: Long): String {
        val kb = 1024.0
        val b = bytes.toDouble()
        return when {
            bytes < 1024L -> "$bytes B"
            b < kb * kb -> String.format(java.util.Locale.ENGLISH, "%.0f KB", b / kb)
            b < kb * kb * kb -> String.format(java.util.Locale.ENGLISH, "%.1f MB", b / (kb * kb))
            else -> String.format(java.util.Locale.ENGLISH, "%.2f GB", b / (kb * kb * kb))
        }
    }
}
