package com.lifetracker.app.data

import kotlin.math.max

/** How an offline area came to be. */
object AreaSource {
    const val HAND = "hand"
    const val PLACE = "place"
    const val WANTED = "wanted"

    fun label(source: String): String = when (source) {
        HAND -> "by hand"
        PLACE -> "for a place"
        WANTED -> "where you went"
        else -> source
    }
}

/** Where an offline area is in its life. */
object AreaState {
    const val QUEUED = "queued"
    const val DOWNLOADING = "downloading"
    const val DONE = "done"
    const val FAILED = "failed"

    /** The tiles were deleted. The row stays, so a place's area is not made again unless the place changes. */
    const val REMOVED = "removed"
}

/** What the planning code needs to know about an area. */
class AreaView(val id: String, val styleId: String, val bounds: Bounds, val state: String)

/** Plain rules for offline areas: what is covered, which new areas are wanted, and what is too big. */
object OfflineRules {
    /** Zoom range of areas made by themselves (and the first choice for areas by hand). */
    const val AUTO_MIN_ZOOM = 8
    const val AUTO_MAX_ZOOM = 14

    /** Two boxes are joined only if the joined box is at most this many times the size of the two together, so a diagonal road is not one huge box. */
    private const val MERGE_WASTE = 1.3

    /** True when a reading is inside a usable area of the same style. A failed or removed area covers nothing. */
    fun covered(areas: List<AreaView>, styleId: String, lat: Double, lng: Double): Boolean =
        areas.any {
            it.styleId == styleId && it.state != AreaState.FAILED && it.state != AreaState.REMOVED && it.bounds.contains(lat, lng)
        }

    /** The box of a place's own area: [reachKm] each way, or the place's circle when that is bigger. */
    fun placeBounds(lat: Double, lng: Double, radiusM: Int, reachKm: Double): Bounds =
        TileMath.around(lat, lng, max(reachKm, radiusM / 1000.0))

    /** Changes when anything that shapes a place's area changes, so the area is made again. */
    fun signature(b: Bounds, styleId: String, minZ: Int, maxZ: Int): String =
        String.format(java.util.Locale.ENGLISH, "%s|%.5f,%.5f,%.5f,%.5f|%d-%d", styleId, b.west, b.south, b.east, b.north, minZ, maxZ)

    /**
     * New areas wanted for readings that no area covers. Each uncovered reading asks for a box of [halfKm] around it; boxes that
     * touch are joined while the joined box is not much bigger than the two together (and stays inside the provider's [limit]).
     */
    fun wanted(
        readings: List<Pair<Double, Double>>,
        areas: List<AreaView>,
        styleId: String,
        halfKm: Double,
        minZ: Int,
        maxZ: Int,
        limit: MapStyles.Limit?,
    ): List<Bounds> {
        val boxes = ArrayList<Bounds>()
        for ((lat, lng) in readings) {
            if (covered(areas, styleId, lat, lng)) continue
            if (boxes.any { it.contains(lat, lng) }) continue
            var box = TileMath.around(lat, lng, halfKm)
            var again = true
            while (again) {
                again = false
                for (i in boxes.indices) {
                    val other = boxes[i]
                    if (!box.intersects(other)) continue
                    val joined = box.union(other)
                    val parts = TileMath.count(box, minZ, maxZ) + TileMath.count(other, minZ, maxZ)
                    val whole = TileMath.count(joined, minZ, maxZ)
                    if (whole <= parts * MERGE_WASTE && (limit == null || whole <= limit.maxTiles)) {
                        box = joined
                        boxes.removeAt(i)
                        again = true
                        break
                    }
                }
            }
            boxes += box
        }
        return boxes
    }

    /** A plain sentence when [tiles] (about [bytes]) is more than the provider allows in one download, else null. */
    fun overLimit(tiles: Long, bytes: Long, limit: MapStyles.Limit?): String? {
        if (limit == null) return null
        if (tiles <= limit.maxTiles && bytes <= limit.maxBytes) return null
        return limit.termsNote + " This area needs about " + tiles + " tiles (about " + TileMath.sizeText(bytes) +
            "). Make it smaller, lower the highest zoom, or use a style of your own."
    }

    /** A plain sentence when the download does not fit in the free space (both numbers shown), else null. */
    fun storageProblem(needBytes: Long, freeBytes: Long): String? =
        if (needBytes <= freeBytes) null
        else "Not enough free space: this needs about " + TileMath.sizeText(needBytes) + " and the phone has " +
            TileMath.sizeText(freeBytes) + " free."

    /** The line shown for an area in the list. */
    fun stateText(state: String, source: String, tiles: Long, totalTiles: Long, bytes: Long, note: String): String = when (state) {
        AreaState.DONE -> "Downloaded · " + tiles + " tiles · " + TileMath.sizeText(bytes)
        AreaState.DOWNLOADING ->
            if (totalTiles > 0L) "Downloading · " + (tiles * 100L / totalTiles).coerceIn(0L, 100L) + "%" else "Downloading"
        AreaState.QUEUED -> if (source == AreaSource.WANTED) "Waiting for a connection or a turn" else "Waiting for its turn"
        AreaState.FAILED -> "Did not finish" + if (note.isNotBlank()) ": $note" else ""
        AreaState.REMOVED -> "Tiles deleted"
        else -> state
    }
}
