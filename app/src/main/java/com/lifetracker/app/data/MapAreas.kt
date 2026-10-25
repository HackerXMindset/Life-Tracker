package com.lifetracker.app.data

import android.content.Context

/**
 * Keeps the list of offline areas in line with your places and where you have been. It only adds or re-queues rows; the download
 * itself is [MapDownloads]. Safe to run again and again.
 */
object MapAreas {
    /** Makes the areas the settings ask for. Returns how many areas were added or queued again. */
    suspend fun reconcile(context: Context, db: AppDatabase, nowMs: Long = System.currentTimeMillis()): Int {
        val settings = MapSettings(context)
        val style = MapStyleStore.current(context, db)
        var changed = 0
        if (settings.autoPlaces) changed += forPlaces(context, db, settings, style, nowMs)
        if (settings.autoWanted) changed += forReadings(db, settings, style, nowMs)
        if (changed > 0) AppLog.i("Map", "Offline areas: $changed added or queued again")
        return changed
    }

    private suspend fun forPlaces(context: Context, db: AppDatabase, settings: MapSettings, style: ResolvedStyle, nowMs: Long): Int {
        val dao = db.mapDao()
        val minZ = OfflineRules.AUTO_MIN_ZOOM
        val maxZ = OfflineRules.AUTO_MAX_ZOOM
        val existing = dao.areas().filter { it.source == AreaSource.PLACE }
        var changed = 0
        for (place in db.placeDao().activePlaces()) {
            val bounds = OfflineRules.placeBounds(place.lat, place.lng, place.radiusM, settings.placeReachKm.toDouble())
            if (!bounds.valid()) continue
            val sig = OfflineRules.signature(bounds, style.id, minZ, maxZ)
            val row = existing.firstOrNull { it.placeId == place.id && it.styleId == style.id }
            if (row != null && row.sig == sig) continue
            if (row != null && row.regionId >= 0L) MapDownloads.removeTiles(context, db, row)
            dao.upsertArea(
                MapAreaEntity(
                    id = row?.id ?: "m${nowMs}p${place.id}",
                    name = place.name.ifBlank { "A place" },
                    styleId = style.id, styleUrl = style.url,
                    west = bounds.west, south = bounds.south, east = bounds.east, north = bounds.north,
                    minZoom = minZ, maxZoom = maxZ,
                    source = AreaSource.PLACE, placeId = place.id, sig = sig, state = AreaState.QUEUED,
                    createdMs = nowMs, doneMs = 0L, tiles = 0L, bytes = 0L, totalTiles = 0L, regionId = -1L, note = "",
                ),
            )
            changed++
        }
        return changed
    }

    private suspend fun forReadings(db: AppDatabase, settings: MapSettings, style: ResolvedStyle, nowMs: Long): Int {
        val dao = db.mapDao()
        val from = settings.checkedUntilMs
        val fixes = db.rawDao().between(from, nowMs + 1)
        if (fixes.isEmpty()) return 0
        // One reading per small square is enough to ask whether the spot is covered.
        val seen = HashSet<Long>()
        val readings = fixes.filter { seen.add(Math.round(it.lat * 200.0) * 1_000_000L + Math.round(it.lng * 200.0)) }
            .map { it.lat to it.lng }
        val areas = dao.areas().map { it.view() }
        val boxes = OfflineRules.wanted(
            readings, areas, style.id, settings.wantedReachKm.toDouble(),
            OfflineRules.AUTO_MIN_ZOOM, OfflineRules.AUTO_MAX_ZOOM, style.limit,
        )
        var n = 0
        for (b in boxes) {
            if (!b.valid()) continue
            n++
            dao.upsertArea(
                MapAreaEntity(
                    id = "m${nowMs}w$n", name = "Where you went",
                    styleId = style.id, styleUrl = style.url,
                    west = b.west, south = b.south, east = b.east, north = b.north,
                    minZoom = OfflineRules.AUTO_MIN_ZOOM, maxZoom = OfflineRules.AUTO_MAX_ZOOM,
                    source = AreaSource.WANTED, placeId = "", sig = "", state = AreaState.QUEUED,
                    createdMs = nowMs, doneMs = 0L, tiles = 0L, bytes = 0L, totalTiles = 0L, regionId = -1L, note = "",
                ),
            )
        }
        settings.checkedUntilMs = fixes.last().ms + 1
        return n
    }
}
