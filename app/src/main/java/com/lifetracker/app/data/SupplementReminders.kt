package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.lifetracker.app.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** When a supplement rings. */
object SupplementReminders {
    const val SNOOZE_MINUTES = 10

    /** The next moment after [now] that [s] is due and has a time, or null (no time, archived, or never due again). */
    fun next(s: SupplementEntity, now: LocalDateTime): LocalDateTime? {
        if (s.archived || s.atMinute !in 0..1439) return null
        val time = LocalTime.of(s.atMinute / 60, s.atMinute % 60)
        for (offset in 0..400L) {
            val day = now.toLocalDate().plusDays(offset)
            if (!SupplementRules.dueOn(s, day)) continue
            val at = LocalDateTime.of(day, time)
            if (at.isAfter(now)) return at
        }
        return null
    }

    /** What the banner says under the name. */
    fun message(s: SupplementEntity): String = if (s.dose.isBlank()) "Time to take it." else "Time to take it: ${s.dose.trim()}"
}

/**
 * Supplement reminders: one alarm per supplement that has a time, always set for its next moment only,
 * set again each time it rings and whenever supplements or the switch change.
 */
object SupplementReminderScheduler {
    private const val PREFS = "supplement_reminders"
    private const val ARMED = "armed"
    private const val SCHEME = "lifetracker-supp"
    const val ACTION_RING = "com.lifetracker.app.SUPPLEMENT_RING"
    const val ACTION_TAKEN = "com.lifetracker.app.SUPPLEMENT_TAKEN"
    const val ACTION_SNOOZE = "com.lifetracker.app.SUPPLEMENT_SNOOZE"
    const val ACTION_SNOOZE_RING = "com.lifetracker.app.SUPPLEMENT_SNOOZE_RING"
    const val EXTRA_ID = "supplement"

    private fun pending(context: Context, action: String, kind: String, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SupplementReminderReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("$SCHEME://$kind/${Uri.encode(id)}"))
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelAlarm(context: Context, id: String) {
        val p = pending(context, ACTION_RING, "ring", id)
        runCatching { context.getSystemService(android.app.AlarmManager::class.java).cancel(p) }
        p.cancel()
    }

    fun enabled(context: Context): Boolean =
        Features.isOn(context, Feature.FOOD) && FoodSettings.isOn(context, FoodPart.SUPPLEMENTS) && FoodSettings.suppReminders(context)

    /** Sets every alarm again from the supplements as they are now, and takes away those of supplements that are gone or switched off. */
    suspend fun rearmAll(context: Context) {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).foodExtrasDao()
        val now = LocalDateTime.now()
        val list = if (enabled(app)) dao.allSupplements().filter { !it.archived && it.atMinute >= 0 } else emptyList()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(ARMED, emptySet()).orEmpty()
        val armed = HashSet<String>()
        for (s in list) {
            val at = SupplementReminders.next(s, now) ?: continue
            HabitReminderScheduler.setAlarm(app, at, pending(app, ACTION_RING, "ring", s.id))
            armed += s.id
        }
        for (id in before - armed) cancelAlarm(app, id)
        prefs.edit { putStringSet(ARMED, armed) }
        if (list.isEmpty()) SupplementReminderNotifier.cancelAll(app)
    }

    private suspend fun due(app: Context, id: String): SupplementEntity? {
        val dao = AppDatabase.get(app).foodExtrasDao()
        val s = dao.allSupplements().firstOrNull { it.id == id && !it.archived } ?: return null
        val today = LocalDate.now()
        if (dao.allTaken().any { it.supplementId == id && it.date == today.toString() }) return null
        return s
    }

    /** The alarm went off: show it (unless it was already taken today), then set the next one. */
    suspend fun ring(context: Context, id: String) {
        val app = context.applicationContext
        if (enabled(app)) {
            val s = due(app, id)
            if (s != null && SupplementRules.dueOn(s, LocalDate.now())) SupplementReminderNotifier.post(app, s)
        }
        rearmAll(app)
    }

    /** The snooze time is up: show it again unless it was taken meanwhile. */
    suspend fun ringAgain(context: Context, id: String) {
        val app = context.applicationContext
        if (!enabled(app)) return
        due(app, id)?.let { SupplementReminderNotifier.post(app, it) }
    }

    fun snooze(context: Context, id: String) {
        val app = context.applicationContext
        HabitReminderScheduler.setAlarm(
            app,
            LocalDateTime.now().plusMinutes(SupplementReminders.SNOOZE_MINUTES.toLong()),
            pending(app, ACTION_SNOOZE_RING, "snooze", id),
        )
        SupplementReminderNotifier.cancel(app, id)
    }

    /** The Taken button: the same as ticking it in the app. */
    suspend fun taken(context: Context, id: String) {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).foodExtrasDao()
        if (dao.allSupplements().none { it.id == id }) return
        val now = LocalDateTime.now()
        dao.setTaken(SupplementTakenEntity(id, now.toLocalDate().toString(), now.hour * 60 + now.minute))
        SupplementReminderNotifier.cancel(app, id)
    }

    fun buttonIntent(context: Context, action: String, id: String): PendingIntent = pending(context, action, "button-$action", id)

    /** Keeps the alarms right while the app runs: when a supplement changes, or the switch does. */
    @OptIn(FlowPreview::class)
    fun watch(app: Context, scope: CoroutineScope) {
        val dao = AppDatabase.get(app).foodExtrasDao()
        scope.launch(Dispatchers.IO) {
            combine(dao.observeSupplements(), FoodSettings.changes) { _, _ -> Unit }
                .debounce(1_000L)
                .collect { runCatching { rearmAll(app) } }
        }
    }
}

/** The banner of one supplement's reminder. */
object SupplementReminderNotifier {
    private const val CHANNEL = "supplement-reminders"
    private const val ID = 6002

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Supplement reminders")
            .setDescription("Reminders to take your supplements, with Taken and Snooze buttons")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun post(context: Context, s: SupplementEntity) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val text = SupplementReminders.message(s)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(s.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open(context))
            .setAutoCancel(true)
            .addAction(0, "Taken", SupplementReminderScheduler.buttonIntent(context, SupplementReminderScheduler.ACTION_TAKEN, s.id))
            .addAction(0, "Snooze ${SupplementReminders.SNOOZE_MINUTES} min", SupplementReminderScheduler.buttonIntent(context, SupplementReminderScheduler.ACTION_SNOOZE, s.id))
        runCatching { NotificationManagerCompat.from(context).notify(s.id, ID, builder.build()) }
    }

    fun cancel(context: Context, id: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(id, ID) }
    }

    fun cancelAll(context: Context) {
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.activeNotifications.filter { it.id == ID }.forEach { manager.cancel(it.tag, it.id) }
        }
    }
}

/** Hears the alarm and the Taken / Snooze buttons. */
class SupplementReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Features.isOn(context, Feature.FOOD)) return
        val action = intent.action ?: return
        val id = intent.getStringExtra(SupplementReminderScheduler.EXTRA_ID) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    SupplementReminderScheduler.ACTION_RING -> SupplementReminderScheduler.ring(app, id)
                    SupplementReminderScheduler.ACTION_SNOOZE_RING -> SupplementReminderScheduler.ringAgain(app, id)
                    SupplementReminderScheduler.ACTION_TAKEN -> SupplementReminderScheduler.taken(app, id)
                    SupplementReminderScheduler.ACTION_SNOOZE -> SupplementReminderScheduler.snooze(app, id)
                }
            } catch (e: Exception) {
                AppLog.e("Food", "Supplement reminder action $action failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
