package com.lifetracker.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifetracker.app.data.streak.StreakFolderWriter
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Sends the habit changes to Streak's folder by itself (Habits H5d), when the switch in Settings, Habits is on.
 * Each change asks for a send a few seconds later, and a newer change replaces the waiting one, so ticking several
 * days in a row writes one file, not one per tap. Sending the same content twice is skipped by the writer.
 */
class StreakSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = HabitSyncState(applicationContext)
        if (!sync.autoSend) return Result.success()
        return try {
            val result = StreakFolderWriter.send(applicationContext)
            // "Nothing to send" and "Already sent" are normal and not worth a line of their own.
            if (result.wrote) sync.lastSendNote = "${LocalDateTime.now().toString().take(16).replace('T', ' ')}: ${result.message}"
            else if (!result.message.startsWith("Already") && !result.message.startsWith("Nothing")) {
                sync.lastSendNote = "Not sent: ${result.message}"
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_TRIES) {
                Result.retry()
            } else {
                sync.lastSendNote = "Not sent: ${e.message ?: "something went wrong"}"
                Result.success()
            }
        }
    }

    companion object {
        private const val NAME = "streak-send"
        private const val MAX_TRIES = 3

        /** How long to wait after a change, so a burst of changes is sent as one file. */
        const val QUIET_SECONDS = 15L

        /** Asks for a send in [delaySeconds]. A send already waiting is replaced, so the wait restarts. */
        fun schedule(context: Context, delaySeconds: Long) {
            val request = OneTimeWorkRequestBuilder<StreakSendWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
