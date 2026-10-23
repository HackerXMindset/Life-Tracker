package com.lifetracker.app

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.lifetracker.app.data.AppLog
import com.lifetracker.app.data.ChargePowerReceiver
import com.lifetracker.app.data.ChargeSettings
import com.lifetracker.app.data.FocusEngine
import com.lifetracker.app.data.HabitReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Starts listening for plug and unplug while the app's process is alive. */
class LifeTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        // Habit reminders follow every change to the habits while the app's process is alive (Habits H12).
        HabitReminderScheduler.watch(this, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        // A focus session that was running comes back with its alarm and banner (Habits H13).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { FocusEngine.restore(this@LifeTrackerApplication) } }
        if (ChargeSettings(this).track) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            runCatching {
                ContextCompat.registerReceiver(this, ChargePowerReceiver(), filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
        }
    }
}
