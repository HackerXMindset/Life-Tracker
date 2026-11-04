package com.lifetracker.app.ui

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/** Starts the app again from the beginning, so settings that are read once when it starts are read again. */
object AppRestart {
    fun restart(context: Context) {
        val app = context.applicationContext
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) app.startActivity(intent)
        exitProcess(0)
    }
}
