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
        trackUse(db, settings, style, nowMs)
        // With the limit full and deleting switched off, no new map is asked for until there is room again.
        if (!MapCleanup.full(context, db)) {
            if (settings.autoPlaces) changed += forPlaces(context, db, settings, style, nowMs)
            if (settings.autoWanted) changed += forReadings(db, settings, style, nowMs)
        }
        if (changed > 0) AppLog.i("Map", "Offline areas: $changed added or queued again")
        return changed
    }

    /** Notes, for each area, when you were last inside it, so the maps used least can be told from the ones in use. */
    private suspend fun trackUse(db: AppDatabase, settings: MapSettings, style: ResolvedStyle, nowMs: Long) {
        val from = if (settings.usedUntilMs > 0L) settings.usedUntilMs else nowMs - MapLimits.UNUSED_DAYS * 86_400_000L
        val fixes = db.rawDao().between(from, nowMs + 1)
        if (fixes.isEmpty()) return
        val squares = LinkedHashMap<Long, Triple<Double, Double, Long>>()
        for (f in fixes) {
            val key = Math.round(f.lat * 200.0) * 1_000_000L + Math.round(f.lng * 200.0)
            val cur = squares[key]
            if (cur == null || f.ms > cur.third) squares[key] = Triple(f.lat, f.lng, f.ms)
        }
        settings.markUsed(MapLimits.usage(squares.values.toList(), db.mapDao().areas(), style.id))
        settings.usedUntilMs = fixes.last().ms + 1
    }

    private suspend fun forPlaces(context: Context, db: AppDatabase, settings: MapSettings, style: ResolvedStyle, nowMs: Long): Int {
        val dao = db.mapDao()
        val minZ = OfflineRules.AUTO_MIN_ZOOM
        val maxZ = OfflineRules.AUTO_MAX_ZOOM
        val existing = dao.areas().filter { it.source == AreaSource.PLACE }
        var changed = 0
        // The last visit to each place, read only when a place has no map yet and it matters.
        var lastVisit: Map<String, Long>? = null
        for (place in db.placeDao().activePlaces()) {
            val bounds = OfflineRules.placeBounds(place.lat, place.lng, place.radiusM, settings.placeReachKm.toDouble())
            if (!bounds.valid()) continue
            val sig = OfflineRules.signature(bounds, style.id, minZ, maxZ)
            val row = existing.firstOrNull { it.placeId == place.id && it.styleId == style.id }
            if (row != null && row.sig == sig) continue
            // A place nobody goes to does not need a map of its own (a place you just made does).
            if (row == null && settings.skipUnusedPlaces) {
                val visits = lastVisit ?: db.placeDao().allVisits().filter { it.placeId.isNotEmpty() }
                    .groupBy { it.placeId }.mapValues { e -> e.value.maxOf { it.startMs } }.also { lastVisit = it }
                if (!MapLimits.placeWanted(place.createdMs, visits[place.id], nowMs)) continue
            }
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
