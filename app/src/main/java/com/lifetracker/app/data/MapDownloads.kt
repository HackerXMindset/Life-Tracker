package com.lifetracker.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume

/**
 * Downloads the areas in the queue, one at a time, with the map library's own offline regions. An area that has no progress for
 * three minutes is marked as not finished (with the last problem the library gave) instead of waiting for ever.
 */
object MapDownloads {
    private const val STALL_MS = 3 * 60_000L
    private const val WATCH_MS = 20_000L
    private const val PROGRESS_EVERY_MS = 1_000L

    /** Free space for map downloads, in bytes. */
    fun freeBytes(context: Context): Long =
        runCatching { StatFs(context.applicationContext.filesDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    private class Outcome(val ok: Boolean, val tiles: Long, val bytes: Long, val regionId: Long, val note: String)

    /** Works through the queue. True when nothing is left waiting. */
    suspend fun runQueue(context: Context, db: AppDatabase): Boolean {
        verify(context, db)
        val dao = db.mapDao()
        while (true) {
            val next = dao.pending().firstOrNull() ?: break
            download(context, db, next)
        }
        return dao.pendingCount() == 0
    }

    /** The average tile size of your finished areas (or the guess), and whether it is a real average. */
    suspend fun averageTileBytes(db: AppDatabase): Pair<Long, Boolean> {
        val done = db.mapDao().areas().filter { it.state == AreaState.DONE && it.tiles > 0L && it.bytes > 0L }
        return TileMath.averageTileBytes(done.map { it.tiles to it.bytes }) to done.isNotEmpty()
    }

    private suspend fun download(context: Context, db: AppDatabase, area: MapAreaEntity) = coroutineScope {
        val dao = db.mapDao()
        val scope: CoroutineScope = this
        val (average, real) = averageTileBytes(db)
        val total = TileMath.count(area.bounds, area.minZoom, area.maxZoom)
        val estimate = TileMath.estimateBytes(total, average)
        val problem = OfflineRules.overLimit(total, if (real) estimate else 0L, MapStyles.limitFor(area.styleUrl))
            ?: OfflineRules.storageProblem(estimate, freeBytes(context))
        if (problem != null) {
            dao.upsertArea(area.copy(state = AreaState.FAILED, totalTiles = total, note = problem))
            AppLog.w("Map", "Area ${area.name} not downloaded: $problem")
            return@coroutineScope
        }
        dao.upsertArea(area.copy(state = AreaState.DOWNLOADING, totalTiles = total, note = ""))
        AppLog.i("Map", "Downloading ${area.name} ($total tiles at most)")
        var lastWrite = 0L
        val outcome = withContext(Dispatchers.Main) {
            fetch(context, area) { tiles, bytes, required ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastWrite >= PROGRESS_EVERY_MS) {
                    lastWrite = now
                    scope.launch(Dispatchers.IO) { dao.setProgress(area.id, tiles, bytes, required) }
                }
            }
        }
        val now = System.currentTimeMillis()
        if (outcome.ok) {
            dao.upsertArea(
                area.copy(
                    state = AreaState.DONE, doneMs = now, tiles = outcome.tiles, bytes = outcome.bytes,
                    totalTiles = outcome.tiles, regionId = outcome.regionId, note = "",
                ),
            )
            AppLog.i("Map", "Area ${area.name} done: ${outcome.tiles} tiles, ${TileMath.sizeText(outcome.bytes)}")
        } else {
            dao.upsertArea(
                area.copy(
                    state = AreaState.FAILED, tiles = outcome.tiles, bytes = outcome.bytes,
                    regionId = outcome.regionId, note = outcome.note,
                ),
            )
            AppLog.w("Map", "Area ${area.name} did not finish: ${outcome.note}")
        }
    }

    /** Runs on the main thread. */
    private suspend fun fetch(context: Context, area: MapAreaEntity, progress: (Long, Long, Long) -> Unit): Outcome {
        MapEngine.start(context)
        val manager = OfflineManager.getInstance(context.applicationContext)
        val region = regions(manager)?.firstOrNull { it.id == area.regionId } ?: create(manager, context, area)
            ?: return Outcome(false, 0L, 0L, -1L, "The map library could not start this download.")
        return awaitRegion(region, area, progress)
    }

    private suspend fun regions(manager: OfflineManager): List<OfflineRegion>? = suspendCancellableCoroutine { c ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                c.resume(offlineRegions?.toList().orEmpty())
            }

            override fun onError(error: String?) {
                c.resume(null)
            }
        })
    }

    private suspend fun create(manager: OfflineManager, context: Context, area: MapAreaEntity): OfflineRegion? =
        suspendCancellableCoroutine { c ->
            val definition = OfflineTilePyramidRegionDefinition(
                area.styleUrl,
                LatLngBounds.from(area.north, area.east, area.south, area.west),
                area.minZoom.toDouble(),
                area.maxZoom.toDouble(),
                context.resources.displayMetrics.density.coerceIn(1f, 2f),
            )
            manager.createOfflineRegion(
                definition,
                area.id.toByteArray(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion?) {
                        c.resume(offlineRegion)
                    }

                    override fun onError(error: String?) {
                        c.resume(null)
                    }
                },
            )
        }

    private suspend fun awaitRegion(region: OfflineRegion, area: MapAreaEntity, progress: (Long, Long, Long) -> Unit): Outcome =
        suspendCancellableCoroutine { c ->
            val handler = Handler(Looper.getMainLooper())
            val limitBytes = MapStyles.limitFor(area.styleUrl)?.maxBytes
            var finished = false
            var lastCount = -1L
            var lastMove = SystemClock.elapsedRealtime()
            var lastError = ""
            var tiles = 0L
            var bytes = 0L
            var watchdog: Runnable? = null

            fun finish(outcome: Outcome) {
                if (finished) return
                finished = true
                watchdog?.let { handler.removeCallbacks(it) }
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                if (c.isActive) c.resume(outcome)
            }

            fun see(status: OfflineRegionStatus?) {
                if (status == null || finished) return
                tiles = status.completedTileCount
                bytes = status.completedResourceSize
                if (status.completedResourceCount != lastCount) {
                    lastCount = status.completedResourceCount
                    lastMove = SystemClock.elapsedRealtime()
                }
                progress(status.completedResourceCount, bytes, status.requiredResourceCount)
                if (status.isComplete) {
                    finish(Outcome(true, tiles, bytes, region.id, ""))
                } else if (limitBytes != null && bytes > limitBytes) {
                    val limit = MapStyles.limitFor(area.styleUrl)
                    finish(Outcome(false, tiles, bytes, region.id, (limit?.termsNote ?: "The provider's limit was reached.") + " Download a smaller area."))
                }
            }

            val watch = object : Runnable {
                override fun run() {
                    if (finished) return
                    if (SystemClock.elapsedRealtime() - lastMove > STALL_MS) {
                        finish(Outcome(false, tiles, bytes, region.id, "No progress for 3 minutes" + if (lastError.isNotBlank()) " ($lastError)" else ""))
                    } else {
                        handler.postDelayed(this, WATCH_MS)
                    }
                }
            }
            watchdog = watch

            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus?) {
                    see(status)
                }

                override fun onError(error: OfflineRegionError?) {
                    lastError = error?.message.orEmpty().ifBlank { error?.reason.orEmpty() }
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    finish(Outcome(false, tiles, bytes, region.id, "The map library stopped at $limit tiles."))
                }
            })
            c.invokeOnCancellation {
                handler.post {
                    finished = true
                    handler.removeCallbacks(watch)
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                }
            }
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    see(status)
                }

                override fun onError(error: String?) {
                    lastError = error.orEmpty()
                }
            })
            handler.postDelayed(watch, WATCH_MS)
        }

    /** Areas marked downloaded whose tiles are no longer there (a restore, or cleared app data) go back in the queue. */
    private suspend fun verify(context: Context, db: AppDatabase) {
        val dao = db.mapDao()
        val claimed = dao.areas().filter { it.regionId >= 0L && (it.state == AreaState.DONE || it.state == AreaState.DOWNLOADING) }
        if (claimed.isEmpty()) return
        val present = withContext(Dispatchers.Main) {
            MapEngine.start(context)
            regions(OfflineManager.getInstance(context.applicationContext))
        }?.map { it.id }?.toSet() ?: return
        claimed.filter { it.regionId !in present }.forEach {
            dao.upsertArea(it.copy(state = AreaState.QUEUED, regionId = -1L, tiles = 0L, bytes = 0L, note = ""))
        }
    }

    /** Deletes the downloaded tiles of an area (the one thing in the app that is really deleted); the row stays. */
    suspend fun removeTiles(context: Context, db: AppDatabase, area: MapAreaEntity) {
        if (area.regionId >= 0L) {
            withContext(Dispatchers.Main) {
                MapEngine.start(context)
                val region = regions(OfflineManager.getInstance(context.applicationContext))?.firstOrNull { it.id == area.regionId }
                if (region != null) {
                    suspendCancellableCoroutine<Unit> { c ->
                        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {
                                c.resume(Unit)
                            }

                            override fun onError(error: String?) {
                                c.resume(Unit)
                            }
                        })
                    }
                }
            }
        }
        db.mapDao().upsertArea(area.copy(state = AreaState.REMOVED, regionId = -1L, tiles = 0L, bytes = 0L, note = ""))
        AppLog.i("Map", "Tiles of ${area.name} deleted")
    }
}
