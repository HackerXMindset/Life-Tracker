package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * The "Guess how I travelled" switch. It is off until you turn it on: then a trip shows only the way of travel you set yourself.
 * Read it with [enabled] anywhere (it is loaded when the app starts); change it with [set]. Nothing saved changes when it is switched.
 */
object TripGuess {
    private const val FILE = "trip_guess"
    private const val KEY = "guess_vehicle"

    /** Whether the app's own guess of how a trip was made is shown. */
    @Volatile
    var enabled: Boolean = false
        private set

    fun load(context: Context) {
        enabled = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
        enabled = on
    }

    /** For tests. */
    internal fun forTest(on: Boolean) {
        enabled = on
    }
}
