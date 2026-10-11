package com.lifetracker.app.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tells from the phone's accelerometer whether it is still or moving, with no Android in it so it can be tested.
 *
 * Each reading is how much the pull on the phone differs from gravity (m/s squared). The variance of the readings over the
 * last minute is small on a table and large in a pocket on a walk. Moving is declared when the variance stays above
 * [movingVariance] for [movingDwellMs]; still when it stays under [stillVariance] for [stillDwellMs]. Between the two
 * nothing changes, so it does not flip back and forth. A phone resting in a smoothly moving car can look still: the
 * rest of the app only believes "still" when it is not on a trip.
 */
class MotionClassifier(
    private val movingVariance: Double = 0.30,
    private val stillVariance: Double = 0.15,
    private val movingDwellMs: Long = 3_000L,
    private val stillDwellMs: Long = 60_000L,
    private val windowMs: Long = 60_000L,
    private val minSamples: Int = 8,
) {
    /** A change of state: [still] or moving, since [sinceMs] (the same clock as the readings). */
    class Change(val still: Boolean, val sinceMs: Long)

    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<Double>()
    private var aboveSince = -1L
    private var belowSince = -1L
    private var lastShakeMs = -1L

    /** True = still, false = moving, null = not decided yet. */
    var still: Boolean? = null
        private set

    /** Something else (the system's own "significant motion" alert) says the phone is moving. */
    fun markMoving() {
        still = false
        belowSince = -1L
    }

    fun onSample(tMs: Long, magnitude: Double): Change? {
        times.addLast(tMs)
        values.addLast(magnitude)
        while (times.isNotEmpty() && times.first() < tMs - windowMs) {
            times.removeFirst()
            values.removeFirst()
        }
        if (abs(magnitude) > SHAKE) lastShakeMs = tMs
        if (values.size < minSamples) return null

        val mean = values.sum() / values.size
        var squares = 0.0
        for (v in values) squares += (v - mean) * (v - mean)
        val variance = squares / values.size

        when {
            variance > movingVariance -> {
                belowSince = -1L
                if (aboveSince < 0L) aboveSince = tMs
                if (still != false && tMs - aboveSince >= movingDwellMs) {
                    still = false
                    return Change(false, aboveSince)
                }
            }
            variance < stillVariance -> {
                aboveSince = -1L
                if (belowSince < 0L) belowSince = tMs
                if (still != true && tMs - belowSince >= stillDwellMs) {
                    still = true
                    // It went still when it last felt a shake, not when the minute of calm was over.
                    val since = if (lastShakeMs in 0..tMs) lastShakeMs else belowSince
                    return Change(true, since)
                }
            }
            else -> {
                aboveSince = -1L
                belowSince = -1L
            }
        }
        return null
    }

    companion object {
        /** A single reading this far from gravity counts as the phone being shaken or carried. */
        const val SHAKE = 0.8
    }
}

/**
 * Listens to the phone's accelerometer (batched, so it hardly wakes the processor) and to Android's "significant motion"
 * alert, and says when the phone went still or started moving. It only works while the app's location service runs.
 * [onChange] is called on the main thread with the clock time the state began.
 */
class MotionSensor(context: Context, private val onChange: (still: Boolean, sinceMs: Long) -> Unit) {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val significant: Sensor? = manager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    private val classifier = MotionClassifier()
    private var running = false

    val available: Boolean get() = accelerometer != null

    private fun wallClock(elapsedMs: Long): Long = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsedMs)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            if (v.size < 3) return
            val magnitude = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()) - SensorManager.GRAVITY_EARTH
            val change = classifier.onSample(event.timestamp / 1_000_000L, magnitude)
            if (change != null) onChange(change.still, wallClock(change.sinceMs))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val trigger = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            classifier.markMoving()
            onChange(false, System.currentTimeMillis())
            arm()
        }
    }

    private fun arm() {
        val s = significant ?: return
        runCatching { manager.requestTriggerSensor(trigger, s) }
    }

    /** Starts listening. Returns false if the phone has no accelerometer. */
    fun start(): Boolean {
        val sensor = accelerometer ?: return false
        if (running) return true
        running = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, MAX_REPORT_LATENCY_US, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)
        if (running) arm()
        return running
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { manager.unregisterListener(listener) }
        significant?.let { s -> runCatching { manager.cancelTriggerSensor(trigger, s) } }
    }

    companion object {
        /** Readings may be held back this long (microseconds) and delivered together, which saves battery. */
        private const val MAX_REPORT_LATENCY_US = 30_000_000
    }
}
