package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** How the Habits screen is set up, kept on the phone: which calendar, big or small cards, which category. */
class HabitsSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("habits", Context.MODE_PRIVATE)

    /** Week, Month or Year, as on Streak's cards. Month by default. */
    var mode: HabitMode
        get() = HabitMode.entries.getOrNull(prefs.getInt("mode", HabitMode.Month.ordinal)) ?: HabitMode.Month
        set(value) = prefs.edit { putInt("mode", value.ordinal) }

    /** True for the small cards (the up-down button next to the notes icon in Streak). */
    var compact: Boolean
        get() = prefs.getBoolean("compact", false)
        set(value) = prefs.edit { putBoolean("compact", value) }

    /** The category chip that is selected. Empty means All. */
    var category: String
        get() = prefs.getString("category", "") ?: ""
        set(value) = prefs.edit { putString("category", value) }
}
