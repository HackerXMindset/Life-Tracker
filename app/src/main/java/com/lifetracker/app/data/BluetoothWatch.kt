package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Paired Bluetooth devices and which are connected now, for the Bluetooth condition of a tracking profile. Names and addresses only. */
object BluetoothWatch {
    /** Addresses of the devices connected now. Only kept up to date while a [Watcher] runs. */
    @Volatile
    var connected: Set<String> = emptySet()
        private set

    /** Android 12 and newer ask for the "Nearby devices" permission; older versions need no question. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun adapter(context: Context): BluetoothAdapter? =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Paired devices as (name, address), by name. Empty without permission or without Bluetooth. */
    @SuppressLint("MissingPermission")
    fun paired(context: Context): List<Pair<String, String>> {
        if (!hasPermission(context)) return emptyList()
        return runCatching {
            adapter(context)?.bondedDevices.orEmpty().map { (it.name ?: "").trim() to it.address }.sortedBy { it.first.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** Keeps [connected] up to date and calls [onChange] on every change, until [stop]. */
    class Watcher(private val context: Context, private val onChange: () -> Unit) {
        private var receiver: BroadcastReceiver? = null

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        fun start(): Boolean {
            if (!hasPermission(context) || receiver != null) return false
            val app = context.applicationContext
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val address = device.address ?: return
                    connected = if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) connected + address else connected - address
                    onChange()
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            val registered = runCatching { ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }.isSuccess
            if (!registered) return false
            receiver = r
            // Devices that were already connected before this started.
            val adapter = adapter(app) ?: return true
            for (kind in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
                runCatching {
                    adapter.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            runCatching { connected = connected + proxy.connectedDevices.map { it.address } }
                            runCatching { adapter.closeProfileProxy(profile, proxy) }
                            onChange()
                        }

                        override fun onServiceDisconnected(profile: Int) = Unit
                    }, kind)
                }
            }
            return true
        }

        fun stop() {
            receiver?.let { r -> runCatching { context.applicationContext.unregisterReceiver(r) } }
            receiver = null
            connected = emptySet()
        }
    }
}
