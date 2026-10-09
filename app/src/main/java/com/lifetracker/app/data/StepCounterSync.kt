package com.lifetracker.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fallback for steps when Health Connect cannot supply them: reads the phone's own step counter now and then.
 * The counter keeps running by itself (it costs almost nothing); we only take a reading when the app syncs,
 * so the steps since the last reading are added to today.
 */
object StepCounterSync {
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun hasSensor(context: Context): Boolean =
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** The counter's value (steps since the phone last restarted), or null if there is no sensor or no reading in 4 seconds. */
    private suspend fun readCounter(context: Context): Long? = withTimeoutOrNull(4_000L) {
        suspendCancellableCoroutine<Long?> { cont ->
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (sensor == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    manager.unregisterListener(this)
                    if (cont.isActive) cont.resume(event.values[0].toLong())
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            cont.invokeOnCancellation { manager.unregisterListener(listener) }
        }
    }

    /**
     * Adds the steps since the last reading to today. When Health Connect already has steps for today it is
     * trusted instead, but the reading is still noted so the next one starts from the right place.
     */
    suspend fun sync(context: Context, today: LocalDate = LocalDate.now()): Boolean = withContext(Dispatchers.IO) {
        val settings = HealthSettings(context)
        if (!settings.useStepCounter || !hasPermission(context)) return@withContext false
        val counter = readCounter(context) ?: return@withContext false
        val delta = StepStats.sensorDelta(settings.lastCounter.takeIf { it >= 0 }, counter)
        settings.lastCounter = counter
        if (delta <= 0) return@withContext true

        val dao = AppDatabase.get(context).stepDao()
        val key = today.toString()
        val existing = dao.day(key)
        if (existing != null && existing.source == StepStats.HEALTH_CONNECT && existing.steps > 0) return@withContext true
        val total = ((existing?.steps ?: 0).toLong() + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        dao.upsertAll(listOf(StepDayEntity(key, total, StepStats.SENSOR)))
        true
    }
}
