package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** What the Timeline shows and asks. Every switch is on until you turn it off, and none of them changes saved data. */
class TimelineSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("timeline", Context.MODE_PRIVATE)

    /** Show the stretches with nothing recorded (No data, Offline, Phone off). */
    var showGaps: Boolean
        get() = prefs.getBoolean("show_gaps", true)
        set(value) = prefs.edit { putBoolean("show_gaps", value) }

    /** Join app use that comes back within 5 minutes into one period. Off shows every stretch as its own row. */
    var groupRepeatedOpens: Boolean
        get() = prefs.getBoolean("group_repeated_opens", true)
        set(value) = prefs.edit { putBoolean("group_repeated_opens", value) }

    /** Show the quick buttons that ask how you travelled under a journey whose way is not known. */
    var askHowTravelled: Boolean
        get() = prefs.getBoolean("ask_how_travelled", true)
        set(value) = prefs.edit { putBoolean("ask_how_travelled", value) }

    /** A category you give a single-app period on the Timeline is also kept for that app on every day. */
    var rememberCategoryChanges: Boolean
        get() = prefs.getBoolean("remember_category_changes", true)
        set(value) = prefs.edit { putBoolean("remember_category_changes", value) }
}
