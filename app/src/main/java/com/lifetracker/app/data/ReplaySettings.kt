package com.lifetracker.app.data

import android.content.Context

/** What you chose for the replay, kept between visits: the speed, and smart speed (slow down when busy, speed up when quiet). */
class ReplaySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("replay", Context.MODE_PRIVATE)

    /** The speed in seconds of the day for each real second. */
    var speed: Double
        get() = prefs.getFloat("speed", DEFAULT_SPEED.toFloat()).toDouble().coerceIn(1.0, 86_400.0)
        set(v) = prefs.edit().putFloat("speed", v.toFloat()).apply()

    var smart: Boolean
        get() = prefs.getBoolean("smart", true)
        set(v) = prefs.edit().putBoolean("smart", v).apply()

    /** The speed is multiplied by this when the moment is busy (less than 1 slows the clock down). */
    var busySlow: Double
        get() = prefs.getFloat("busySlow", DEFAULT_BUSY_SLOW.toFloat()).toDouble().coerceIn(0.02, 1.0)
        set(v) = prefs.edit().putFloat("busySlow", v.toFloat()).apply()

    /** The speed is multiplied by this when the moment is quiet. */
    var quietBoost: Double
        get() = prefs.getFloat("quietBoost", DEFAULT_QUIET_BOOST.toFloat()).toDouble().coerceIn(1.0, 20.0)
        set(v) = prefs.edit().putFloat("quietBoost", v.toFloat()).apply()

    fun resetSmart() {
        busySlow = DEFAULT_BUSY_SLOW
        quietBoost = DEFAULT_QUIET_BOOST
    }

    companion object {
        /** 2 minutes of the day for each real second: a walk of an hour takes half a minute at first sight, slower when busy. */
        const val DEFAULT_SPEED = 120.0
        const val DEFAULT_BUSY_SLOW = 0.1
        const val DEFAULT_QUIET_BOOST = 3.0
    }
}
