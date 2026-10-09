package com.lifetracker.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Looks at the battery and keeps the charging sessions up to date. It is called from three places:
 * the 15 minute check that Android runs only while the phone is charging ([ChargeWorker]), the
 * plug and unplug broadcasts when the app is running ([ChargePowerReceiver]), and when you open the app.
 * Nothing runs in the background while the phone is not charging.
 */
object ChargeTracker {
    private val lock = Mutex()

    /**
     * Takes one reading and updates the sessions. Returns the session that just started, if one did.
     * [sawUnplug] is true when this was called because the cable was just pulled out, so the end time is exact.
     * [banner] is false when you are already looking at the app.
     */
    suspend fun sample(
        context: Context,
        now: Long = System.currentTimeMillis(),
        sawUnplug: Boolean = false,
        banner: Boolean = true,
    ): ChargeSessionEntity? = withContext(Dispatchers.IO) {
        val settings = ChargeSettings(context)
        if (!settings.track) return@withContext null
        lock.withLock {
            val dao = AppDatabase.get(context).chargeDao()
            val result = ChargeSessions.apply(dao.openSession(), read(context, now), sawUnplug)
            if (result.changed.isNotEmpty()) dao.upsertAll(result.changed)
            val started = result.started
            if (started != null && banner && settings.showBanner) {
                val guess = ChargeStats.guessSource(started.plugType, null, dao.allSessions())
                ChargeNotifier.postStart(context, started, guess)
            }
            started
        }
    }

    private fun read(context: Context, now: Long): ChargeSessions.Sample {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level < 0 || scale <= 0) 0 else level * 100 / scale
        val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.takeIf { it > 0 }
        val charging = plugged != 0
        val ma = if (!charging) null else runCatching {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            ChargeStats.toMilliamps(manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        }.getOrNull()
        return ChargeSessions.Sample(now, charging, plugged, percent, ma, mv)
    }
}
