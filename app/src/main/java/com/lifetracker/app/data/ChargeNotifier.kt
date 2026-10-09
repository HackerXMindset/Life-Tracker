package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifetracker.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The banner that appears when you plug in, with one button for each kind of charger. */
object ChargeNotifier {
    private const val CHANNEL = "charging"
    private const val NOTIFICATION_ID = 4001
    const val ACTION_TAG = "com.lifetracker.app.CHARGE_TAG"
    const val EXTRA_ID = "session"
    const val EXTRA_SOURCE = "source"

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Charging")
            .setDescription("Asks what you plugged your phone into")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun tag(context: Context, id: String, source: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, ChargeTagReceiver::class.java)
            .setAction(ACTION_TAG)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_SOURCE, source),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Pops up "Charging started" with Wall / Power bank / Laptop buttons. [guess] is what you usually pick for this kind of plug. */
    @SuppressLint("MissingPermission")
    fun postStart(context: Context, session: ChargeSessionEntity, guess: String?) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val text = buildString {
            append("${session.startLevel}% · ${ChargeStats.plugLabel(session.plugType)}. What are you charging with?")
            if (guess != null) append(" Usually: ${ChargeStats.sourceLabel(guess)}.")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Charging started")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(10 * 60 * 1000L)
        listOf(ChargeStats.WALL, ChargeStats.POWER_BANK, ChargeStats.LAPTOP).forEachIndexed { i, source ->
            builder.addAction(0, ChargeStats.sourceLabel(source), tag(context, session.id, source, 10 + i))
        }
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    /** Replaces the banner with a short "saved" note that goes away on its own. */
    @SuppressLint("MissingPermission")
    fun postSaved(context: Context, source: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Saved: ${ChargeStats.sourceLabel(source)}")
            .setContentIntent(open(context))
            .setAutoCancel(true)
            .setTimeoutAfter(5_000L)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}

/** Handles a tap on one of the banner's buttons: saves what you charged with. */
class ChargeTagReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChargeNotifier.ACTION_TAG) return
        val id = intent.getStringExtra(ChargeNotifier.EXTRA_ID) ?: return
        val source = intent.getStringExtra(ChargeNotifier.EXTRA_SOURCE) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.get(app).chargeDao().setSource(id, source)
                ChargeNotifier.postSaved(app, source)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Hears the plug and unplug broadcasts while the app's process is alive (Android does not deliver
 * them to apps that are not running), which gives exact start and end times when it can.
 */
class ChargePowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val unplugged = intent.action == Intent.ACTION_POWER_DISCONNECTED
        if (!unplugged && intent.action != Intent.ACTION_POWER_CONNECTED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ChargeTracker.sample(app, sawUnplug = unplugged)
            } finally {
                pending.finish()
            }
        }
    }
}
