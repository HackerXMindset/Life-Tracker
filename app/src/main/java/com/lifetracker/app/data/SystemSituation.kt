package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.BatteryManager

/** Reads what is true of the phone right now: charging, car mode, Wi-Fi and battery. Nothing here needs more than the normal permissions. */
object SystemSituation {
    /** The phone is plugged in to a power source. */
    fun charging(context: Context): Boolean {
        val status = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return status != 0
    }

    /** Battery percent, or -1 when not known. */
    fun batteryPercent(context: Context): Int {
        val intent = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) -1 else (level * 100 / scale)
    }

    /** The phone is in car mode (a car dock or Android's driving mode). */
    fun carMode(context: Context): Boolean =
        runCatching {
            val ui = context.applicationContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            ui.currentModeType == Configuration.UI_MODE_TYPE_CAR
        }.getOrDefault(false)

    /** Wi-Fi connected, and its name when Android lets the app see it (it needs the location permission), else null. */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun wifi(context: Context): Pair<Boolean, String?> =
        runCatching {
            val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = manager.connectionInfo
            val connected = info != null && info.networkId != -1
            val raw = info?.ssid
            val name = raw?.takeIf { connected && it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && !it.contains("unknown ssid", ignoreCase = true) }
            connected to name
        }.getOrDefault(false to null)

    fun read(context: Context): Situation {
        val (connected, name) = wifi(context)
        return Situation(
            charging = charging(context),
            carMode = carMode(context),
            wifiConnected = connected,
            wifiName = name,
        )
    }
}
