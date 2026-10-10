package com.lifetracker.app.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Brings steps and sleep up to date: Health Connect, the phone's step counter, then the sleep estimate. */
object HealthSync {
    private const val ESTIMATE_DAYS = 6L

    suspend fun run(context: Context, today: LocalDate = LocalDate.now()) {
        runCatching { HealthConnectSync.sync(context) }
        runCatching { StepCounterSync.sync(context, today) }
        if (HealthSettings(context).estimateSleep) runCatching { estimateSleep(context, today) }
        HealthSettings(context).lastSync = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
    }

    /** Works out the last few nights from phone use. Never touches a night you edited or Health Connect supplied. */
    suspend fun estimateSleep(context: Context, today: LocalDate = LocalDate.now()) {
        val db = AppDatabase.get(context)
        val zone = ZoneId.systemDefault()
        for (back in 0 until ESTIMATE_DAYS) {
            val date = today.minusDays(back)
            val existing = db.sleepDao().night(date.toString())
            if (!SleepStats.mayReplace(existing, SleepStats.ESTIMATE)) continue
            val (from, to) = SleepEstimator.usageWindow(date, zone)
            val usage = db.usageDao().sessionsOnce(from, to)
            val night = SleepEstimator.estimate(date, usage, zone) ?: continue
            db.sleepDao().upsert(SleepNightEntity(date.toString(), night.startMs, night.endMs, SleepStats.ESTIMATE))
        }
    }
}
