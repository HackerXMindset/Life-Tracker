package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What you chose about showing goals. The rings on the Timeline and in Habits are off until you switch them on. */
object GoalsSettings {
    private const val FILE = "goals"
    private const val KEY = "show_rings"
    private val rings = MutableStateFlow<Boolean?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Whether the rings are shown. Off unless you switched them on. */
    fun showRings(context: Context): Boolean = rings.value ?: prefs(context).getBoolean(KEY, false)

    fun setShowRings(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY, on) }
        rings.value = on
    }

    /** Changes each time the choice is made, so an open screen can look again. */
    val changes: StateFlow<Boolean?> get() = rings
}
