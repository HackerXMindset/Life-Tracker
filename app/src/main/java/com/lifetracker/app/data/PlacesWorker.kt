package com.lifetracker.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes: starts the position readings again if Android stopped them while they should be running,
 * every 6 hours hands the places to Android again (geofences can be lost without any message: restart, location
 * switched off, Google Play services updated) and every 6 hours tidies the last days' visits and trips from the stored readings.
 * It takes a moment and uses no location itself.
 */
class PlacesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = PlacesSettings(context)
        if (!settings.enabled) return Result.success()
        val now = System.currentTimeMillis()
        AppLog.i("Watchdog", "Background check ran")
        if (now - settings.lastRegisterMs > SIX_HOURS_MS) runCatching { PlacesTracker.syncAll(context) }
        runCatching { PlacesTracker.watchdog(context, now) }
        if (now - settings.lastCleanupMs > SIX_HOURS_MS) {
            PlacesTracker.cleanupRecent(context, now)
            runCatching { PlacesTracker.prefetchNames(context) }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "places-refresh"
        private const val SIX_HOURS_MS = 6 * 60 * 60_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlacesWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
