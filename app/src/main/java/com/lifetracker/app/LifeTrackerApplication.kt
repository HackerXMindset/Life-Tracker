package com.lifetracker.app

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.lifetracker.app.data.ChargePowerReceiver
import com.lifetracker.app.data.ChargeSettings

/** Starts listening for plug and unplug while the app's process is alive. */
class LifeTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
