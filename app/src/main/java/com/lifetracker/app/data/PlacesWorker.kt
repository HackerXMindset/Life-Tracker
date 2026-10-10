package com.lifetracker.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Every 6 hours, hands the places to Android again. Geofences can be lost (restart, location switched off,
 * Google Play services updated) without any message, and this brings them back. It takes a second and uses
 * no location itself.
 */
class PlacesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { PlacesTracker.syncAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "places-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlacesWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
