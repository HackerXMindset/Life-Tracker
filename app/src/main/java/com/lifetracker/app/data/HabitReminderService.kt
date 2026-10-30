package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.lifetracker.app.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Habit reminders (Habits H12): one alarm per reminder, always set for its next moment only, set again each time it rings
 * and whenever habits change. The rules for "when" are in [HabitReminders]; this is the Android side.
 */
object HabitReminderScheduler {
    private const val PREFS = "habit_reminders"
    private const val ARMED = "armed"
    private const val SCHEME = "lifetracker-habit"
    const val ACTION_RING = "com.lifetracker.app.HABIT_REMINDER_RING"
    const val ACTION_DONE = "com.lifetracker.app.HABIT_REMINDER_DONE"
    const val ACTION_SNOOZE = "com.lifetracker.app.HABIT_REMINDER_SNOOZE"
    const val ACTION_SNOOZE_RING = "com.lifetracker.app.HABIT_REMINDER_SNOOZE_RING"
    const val EXTRA_HABIT = "habit"
    const val EXTRA_REMINDER = "reminder"
    const val EXTRA_SNOOZE = "snooze"

    /** Can alarms ring at the exact minute? (Android 12 and newer ask for "Alarms and reminders".) */
    fun canRingExactly(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || runCatching { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }.getOrDefault(false)

    /** Opens Android's page where "Allow setting alarms and reminders" is switched on for this app. */
    fun openExactAlarmSettings(context: Context) {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
        KeepAlive.open(context, intents)
    }

    private fun uri(kind: String, habitId: String, reminderId: String = ""): Uri =
        Uri.parse("$SCHEME://$kind/${Uri.encode(habitId)}/${Uri.encode(reminderId)}")

    private fun alarmIntent(context: Context, action: String, kind: String, habitId: String, reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, HabitReminderReceiver::class.java)
                .setAction(action)
                .setData(uri(kind, habitId, reminderId))
                .putExtra(EXTRA_HABIT, habitId)
                .putExtra(EXTRA_REMINDER, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setAlarm(context: Context, at: LocalDateTime, pending: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (canRingExactly(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        } catch (e: SecurityException) {
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending) }
        }
    }

    private fun cancelAlarm(context: Context, habitId: String, reminderId: String) {
        val pending = alarmIntent(context, ACTION_RING, "ring", habitId, reminderId)
        runCatching { context.getSystemService(AlarmManager::class.java).cancel(pending) }
        pending.cancel()
    }

    /** The records of one habit by day, turned into its rules (the same way the Habits tab does). */
    fun rulesOf(habit: HabitEntity, completions: List<CompletionEntity>, today: LocalDate): HabitRules {
        val counts = HashMap<LocalDate, Double>()
        val times = HashMap<LocalDate, List<Int>>()
        val ticked = HashMap<LocalDate, Set<String>>()
        val steps = if (HabitSteps.isChecklist(habit)) HabitSteps.read(habit.raw) else emptyList()
        for (c in HabitClears.visible(completions, habit.clearedAt)) {
            val day = runCatching { LocalDate.parse(c.date) }.getOrNull() ?: continue
            counts[day] = if (steps.isEmpty()) c.count else HabitSteps.dayCount(c, steps)
            if (steps.isNotEmpty()) HabitSteps.ticked(c, steps).takeIf { it.isNotEmpty() }?.let { ticked[day] = it }
            HabitTicks.times(c).takeIf { it.isNotEmpty() }?.let { times[day] = it }
        }
        return HabitRules.of(habit, counts, today, times, ticked)
    }

    /** Is the habit done today, so a reminder has nothing to say? An Avoid habit and a tracking-only habit never are. */
    fun doneToday(habit: HabitEntity, rules: HabitRules, today: LocalDate): Boolean =
        habit.kind != HabitStats.KIND_RELAPSE && !rules.tracking && (rules.isCompleted(today) || rules.isCovered(today))

    private fun anchorOf(habit: HabitEntity): LocalDate =
        runCatching { LocalDate.parse(habit.startedOn) }.getOrDefault(LocalDate.now())

    /** The next moment this reminder rings after [now], or null. */
    fun nextOf(habit: HabitEntity, reminder: HabitReminders.Reminder, rules: HabitRules, now: LocalDateTime): LocalDateTime? =
        HabitReminders.next(
            reminder,
            now,
            habitDrives = HabitPlan.fromRaw(habit.raw).interval == HabitPlan.EVERY_X_DAYS,
            anchorFallback = anchorOf(habit),
            habitRings = { day -> !rules.isOffDay(day) },
        )

    /** Sets every alarm again from the habits as they are now, and takes away the alarms of reminders that are gone. */
    suspend fun rearmAll(context: Context) {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).habitDao()
        val settings = HabitsSettings(app)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val habits = dao.allHabits().filter { it.deletedAt == 0L && !it.archived }
        val withReminders = if (settings.remindersOn) habits.filter { HabitReminders.read(it.raw).isNotEmpty() } else emptyList()
        val byHabit = if (withReminders.isEmpty()) emptyMap() else dao.allCompletions().groupBy { it.habitId }
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(ARMED, emptySet()).orEmpty()
        val now2 = HashSet<String>()
        for (habit in withReminders) {
            val rules = rulesOf(habit, byHabit[habit.id].orEmpty(), today)
            for (reminder in HabitReminders.read(habit.raw)) {
                val at = nextOf(habit, reminder, rules, now) ?: continue
                setAlarm(app, at, alarmIntent(app, ACTION_RING, "ring", habit.id, reminder.id))
                now2 += "${habit.id}|${reminder.id}"
            }
        }
        for (key in before - now2) {
            val parts = key.split("|", limit = 2)
            if (parts.size == 2) cancelAlarm(app, parts[0], parts[1])
        }
        prefs.edit { putStringSet(ARMED, now2) }
        if (!settings.remindersOn) HabitReminderNotifier.cancelAll(app)
        if (settings.quietWhenDone) dismissDone(app, habits, byHabit.takeIf { it.isNotEmpty() } ?: dao.allCompletions().groupBy { it.habitId }, today)
    }

    /** Takes away the banner of a habit that has been done since (Streak's "quiet when done"). */
    private fun dismissDone(app: Context, habits: List<HabitEntity>, byHabit: Map<String, List<CompletionEntity>>, today: LocalDate) {
        for (habit in habits) {
            if (!HabitReminderNotifier.isShown(app, habit.id)) continue
            if (doneToday(habit, rulesOf(habit, byHabit[habit.id].orEmpty(), today), today)) HabitReminderNotifier.cancel(app, habit.id)
        }
    }

    /** A reminder's alarm went off: show it (unless quiet), then set that habit's next alarm. */
    suspend fun ring(context: Context, habitId: String, reminderId: String) {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).habitDao()
        val settings = HabitsSettings(app)
        val habit = dao.habit(habitId)?.takeIf { it.deletedAt == 0L && !it.archived }
        if (habit != null && settings.remindersOn) {
            val reminder = HabitReminders.read(habit.raw).firstOrNull { it.id == reminderId }
            if (reminder != null) show(app, habit, reminder.message, reminder.snoozeMinutes, settings)
        }
        rearmAll(app)
    }

    /** The snooze time is up: show it again (still quiet if the habit got done meanwhile). */
    suspend fun ringAgain(context: Context, habitId: String, snoozeMinutes: Int) {
        val app = context.applicationContext
        val habit = AppDatabase.get(app).habitDao().habit(habitId)?.takeIf { it.deletedAt == 0L && !it.archived } ?: return
        val settings = HabitsSettings(app)
        if (!settings.remindersOn) return
        val message = HabitReminders.read(habit.raw).firstOrNull()?.message.orEmpty()
        show(app, habit, message, snoozeMinutes, settings)
    }

    private suspend fun show(app: Context, habit: HabitEntity, message: String, snoozeMinutes: Int, settings: HabitsSettings) {
        val dao = AppDatabase.get(app).habitDao()
        val today = LocalDate.now()
        val rules = rulesOf(habit, dao.allCompletions().filter { it.habitId == habit.id }, today)
        if (rules.isOffDay(today)) return
        if (settings.quietWhenDone && doneToday(habit, rules, today)) return
        val streak = rules.currentStreak()
        HabitReminderNotifier.post(app, habit, message, snoozeMinutes, streak, canDone = HabitReminderActions.canDone(habit))
    }

    /** Snooze: ring again in [minutes] minutes (one pending snooze per habit). */
    fun snooze(context: Context, habitId: String, minutes: Int) {
        val app = context.applicationContext
        val pending = PendingIntent.getBroadcast(
            app,
            0,
            Intent(app, HabitReminderReceiver::class.java)
                .setAction(ACTION_SNOOZE_RING)
                .setData(uri("snooze", habitId))
                .putExtra(EXTRA_HABIT, habitId)
                .putExtra(EXTRA_SNOOZE, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(app, LocalDateTime.now().plusMinutes(minutes.toLong()), pending)
        HabitReminderNotifier.cancel(app, habitId)
    }

    /** Watches the habits and their records while the app runs, so alarms follow every change (edit, import, tick). */
    @OptIn(FlowPreview::class)
    fun watch(app: Context, scope: CoroutineScope) {
        val dao = AppDatabase.get(app).habitDao()
        scope.launch(Dispatchers.IO) {
            combine(dao.observeHabits(), dao.observeCompletions()) { _, _ -> Unit }
                .debounce(1_000L)
                .collect { runCatching { rearmAll(app) } }
        }
    }
}

/** What the Done button does (Habits H12): the same as one tap on the card, without ever taking a tick back. */
object HabitReminderActions {
    /** Done is offered for yes/no, counted and tracking-only habits; Avoid habits and checklists open the app instead. */
    fun canDone(habit: HabitEntity): Boolean = habit.kind != HabitStats.KIND_RELAPSE && !HabitSteps.isChecklist(habit)

    suspend fun done(context: Context, habitId: String) {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).habitDao()
        val habit = dao.habit(habitId)?.takeIf { it.deletedAt == 0L } ?: return
        if (!canDone(habit)) return
        val today = LocalDate.now()
        val key = today.toString()
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        val existing = dao.completion(habit.id, key)
        val counted = habit.kind == HabitStats.KIND_COUNTED || HabitRules.isTracking(habit)
        when {
            counted -> dao.upsertCompletion(HabitTicks.add(existing, habit.id, key, if (habit.kind == HabitStats.KIND_COUNTED) habit.step else 1.0, minute))
            existing == null -> dao.upsertCompletion(HabitTicks.add(null, habit.id, key, maxOf(1.0, habit.target), minute))
        }
        HabitSyncState(app).stamp(habit.id)
    }
}

/** The banner of one habit's reminder. */
object HabitReminderNotifier {
    private const val CHANNEL = "habit-reminders"
    private const val ID = 6001

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Habit reminders")
            .setDescription("Reminders for your habits, with Done and Snooze buttons")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun button(context: Context, action: String, habitId: String, snooze: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, HabitReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("lifetracker-habit://$action/${Uri.encode(habitId)}"))
            .putExtra(HabitReminderScheduler.EXTRA_HABIT, habitId)
            .putExtra(HabitReminderScheduler.EXTRA_SNOOZE, snooze),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun isShown(context: Context, habitId: String): Boolean =
        runCatching {
            context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                .any { it.id == ID && it.tag == habitId }
        }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun post(context: Context, habit: HabitEntity, message: String, snoozeMinutes: Int, streak: Int, canDone: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val text = buildString {
            append(message.trim().ifEmpty { "Time for it." })
            if (streak > 0) append("\nStreak: $streak")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(habit.name)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open(context))
            .setAutoCancel(true)
        if (canDone) builder.addAction(0, "Done", button(context, HabitReminderScheduler.ACTION_DONE, habit.id, snoozeMinutes))
        builder.addAction(0, "Snooze $snoozeMinutes min", button(context, HabitReminderScheduler.ACTION_SNOOZE, habit.id, snoozeMinutes))
        runCatching { NotificationManagerCompat.from(context).notify(habit.id, ID, builder.build()) }
    }

    fun cancel(context: Context, habitId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(habitId, ID) }
    }

    fun cancelAll(context: Context) {
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.activeNotifications.filter { it.id == ID }.forEach { manager.cancel(it.tag, it.id) }
        }
    }
}

/** Hears the alarm and the Done / Snooze buttons. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Features.isOn(context, Feature.HABITS)) return
        val action = intent.action ?: return
        val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT) ?: return
        val reminderId = intent.getStringExtra(HabitReminderScheduler.EXTRA_REMINDER).orEmpty()
        val snooze = intent.getIntExtra(HabitReminderScheduler.EXTRA_SNOOZE, HabitReminders.DEFAULT_SNOOZE)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    HabitReminderScheduler.ACTION_RING -> HabitReminderScheduler.ring(app, habitId, reminderId)
                    HabitReminderScheduler.ACTION_SNOOZE_RING -> HabitReminderScheduler.ringAgain(app, habitId, snooze)
                    HabitReminderScheduler.ACTION_DONE -> {
                        HabitReminderActions.done(app, habitId)
                        HabitReminderNotifier.cancel(app, habitId)
                    }
                    HabitReminderScheduler.ACTION_SNOOZE -> HabitReminderScheduler.snooze(app, habitId, snooze)
                }
            } catch (e: Exception) {
                AppLog.e("Habits", "Reminder action $action failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}

/** After a restart, an app update, or a change of clock, time zone or alarm permission: set every alarm again. */
class HabitReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Features.isOn(context, Feature.HABITS)) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HabitReminderScheduler.rearmAll(app)
                FocusEngine.restore(app)
            } catch (e: Exception) {
                AppLog.e("Habits", "Could not set reminders again: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
