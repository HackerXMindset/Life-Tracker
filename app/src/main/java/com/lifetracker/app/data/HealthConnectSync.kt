package com.lifetracker.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies steps and sleep from Health Connect into the app's own database. Health Connect is Android's
 * shared store for health data. On Android 14 and newer it also counts your phone's steps by itself,
 * once any app has been allowed to read steps.
 */
object HealthConnectSync {
    const val HISTORY_PERMISSION = "android.permission.health.READ_HEALTH_DATA_HISTORY"
    const val PACKAGE = "com.google.android.apps.healthdata"

    /** What the app cannot work without. */
    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    /** What is asked for: the above, plus permission to read more than 30 days back. */
    val REQUEST: Set<String> = PERMISSIONS + HISTORY_PERMISSION

    private const val FIRST_DAYS_WITHOUT_HISTORY = 30L
    private const val FIRST_DAYS_WITH_HISTORY = 400L
    private const val LATER_DAYS = 7L

    /** [HealthConnectClient.SDK_AVAILABLE], [HealthConnectClient.SDK_UNAVAILABLE] or [HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED]. */
    fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun granted(context: Context): Set<String> = withContext(Dispatchers.IO) {
        runCatching { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
    }

    suspend fun hasAccess(context: Context): Boolean =
        status(context) == HealthConnectClient.SDK_AVAILABLE && granted(context).containsAll(PERMISSIONS)

    /** Reads steps and sleep. Returns false if Health Connect is missing or not allowed. */
    suspend fun sync(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (status(context) != HealthConnectClient.SDK_AVAILABLE) return@withContext false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(PERMISSIONS)) return@withContext false

        val settings = HealthSettings(context)
        val days = when {
            settings.healthConnectFirstDone -> LATER_DAYS
            HISTORY_PERMISSION in granted -> FIRST_DAYS_WITH_HISTORY
            else -> FIRST_DAYS_WITHOUT_HISTORY
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val first = today.minusDays(days - 1)
        val db = AppDatabase.get(context)

        // Steps: one total per day. Asking Health Connect to add them up avoids counting the same steps from two apps twice.
        val grouped = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(first.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        val stepDao = db.stepDao()
        val stepRows = ArrayList<StepDayEntity>()
        for (bucket in grouped) {
            val steps = (bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (steps <= 0) continue
            val date = bucket.startTime.toLocalDate().toString()
            stepRows += StepStats.merge(stepDao.day(date), date, steps, StepStats.HEALTH_CONNECT)
        }
        if (stepRows.isNotEmpty()) stepDao.upsertAll(stepRows)

        // Sleep: only if some app (a watch, a sleep app) has written sleep into Health Connect.
        val sessions = ArrayList<Pair<Long, Long>>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        first.atStartOfDay(zone).toInstant(),
                        today.plusDays(1).atStartOfDay(zone).toInstant(),
                    ),
                    pageToken = token,
                ),
            )
            response.records.forEach { sessions += it.startTime.toEpochMilli() to it.endTime.toEpochMilli() }
            token = response.pageToken
        } while (!token.isNullOrEmpty())

        val sleepDao = db.sleepDao()
        for ((date, pair) in SleepStats.nightly(sessions, zone)) {
            val key = date.toString()
            if (SleepStats.mayReplace(sleepDao.night(key), SleepStats.HEALTH_CONNECT)) {
                sleepDao.upsert(SleepNightEntity(key, pair.first, pair.second, SleepStats.HEALTH_CONNECT))
            }
        }
        settings.healthConnectFirstDone = true
        true
    }
}
