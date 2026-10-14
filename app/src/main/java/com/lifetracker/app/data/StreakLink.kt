package com.lifetracker.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Opens the Streak app. Streak accepts a habit ID and shows that habit's page. */
object StreakLink {
    const val PACKAGE = "com.streak.app"
    private const val ACTIVITY = "com.streak.app.MainActivity"
    private const val EXTRA_HABIT = "openHabitId"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** The instruction "open this habit in Streak". */
    fun habitIntent(habitId: String): Intent =
        Intent().setClassName(PACKAGE, ACTIVITY).putExtra(EXTRA_HABIT, habitId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens Streak on the habit. Returns false if Streak is not installed or refuses. */
    fun openHabit(context: Context, habitId: String): Boolean {
        if (!isInstalled(context)) return false
        return try {
            context.startActivity(habitIntent(habitId))
            true
        } catch (e: Exception) {
            false
        }
    }
}
