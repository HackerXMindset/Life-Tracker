package com.lifetracker.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Makes the wanted areas, then downloads the queue. It only runs while there is a connection (and, when mobile data is off for
 * maps, only on Wi-Fi), so an area wanted while you are offline simply waits and downloads when a connection is back.
 */
class MapDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        return try {
            MapAreas.reconcile(applicationContext, db)
            var done = MapDownloads.runQueue(applicationContext, db)
            // Places or readings may have changed while the queue ran.
            if (MapAreas.reconcile(applicationContext, db) > 0) done = MapDownloads.runQueue(applicationContext, db)
            if (done) Result.success() else Result.retry()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Map", "Map downloads stopped: ${e.message}")
            Result.retry()
        }
    }
}

/** Asks for the map job. */
object MapScheduler {
    private const val NAME = "map-downloads"

    /**
     * Starts the job unless one is already waiting or running. Pass [replace] after a change that a waiting job should notice
     * (the mobile-data switch, the style).
     */
    fun kick(context: Context, replace: Boolean = false) {
        val app = context.applicationContext
        val settings = MapSettings(app)
        val network = if (settings.mobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
        val request = OneTimeWorkRequestBuilder<MapDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
