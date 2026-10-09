package com.lifetracker.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Android runs this about every 15 minutes, but only while the phone is charging. It is what notices
 * a plug-in when the app is not running, and keeps the charging speed readings coming. When the
 * phone is not charging it does not run at all, so it costs no battery.
 */
class ChargeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { ChargeTracker.sample(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "charge-watch"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChargeWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
