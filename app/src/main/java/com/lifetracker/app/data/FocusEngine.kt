package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.lifetracker.app.MainActivity
import com.lifetracker.app.data.streak.FocusConverter
import com.lifetracker.app.data.streak.StreakFocus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Focus settings kept on the phone (Settings > Habits > Focus). */
class FocusSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("focus", Context.MODE_PRIVATE)

    /** Minutes of the long break every 4th break; 0 = no long break. */
    var longBreakMinutes: Int
        get() = prefs.getInt("longBreak", 0)
        set(value) = prefs.edit { putInt("longBreak", value.coerceIn(0, 120)) }

    /** Stopping a Pomodoro round early drops that round (Streak's "whole rounds only"). */
    var wholeRounds: Boolean
        get() = prefs.getBoolean("wholeRounds", false)
        set(value) = prefs.edit { putBoolean("wholeRounds", value) }

    /** Minutes offered for a session with no habit. */
    var minutes: Int
        get() = prefs.getInt("minutes", 25)
        set(value) = prefs.edit { putInt("minutes", value.coerceIn(0, 600)) }

    var breakMinutes: Int
        get() = prefs.getInt("break", 0)
        set(value) = prefs.edit { putInt("break", value.coerceIn(0, 120)) }
}

/** Keeps a finished stretch: the session, its Timeline block and what it does for its habit (Habits H13). */
object FocusRecorder {
    private const val SOURCE_FOCUS = "streak-focus"

    suspend fun save(context: Context, data: FocusSessionData) {
        val app = context.applicationContext
        val db = AppDatabase.get(app)
        val sync = HabitSyncState(app)
        val habit = data.habitId.takeIf { it.isNotBlank() }?.let { db.habitDao().habit(it) }
        for (piece in FocusTimer.split(data)) {
            if (db.focusDao().get(piece.id) != null) continue // saved already
            val today = LocalDate.now()
            var counted = false
            when (val credit = creditFor(db, habit, piece, today)) {
                is FocusCredit.Credit.Tick -> {
                    val key = today.toString()
                    db.habitDao().upsertCompletion(HabitTicks.add(db.habitDao().completion(habit!!.id, key), habit.id, key, credit.amount, minuteNow()))
                    sync.stamp(habit.id)
                }
                is FocusCredit.Credit.Minutes -> {
                    val day = FocusCredit.dayOf(piece)
                    val key = day.toString()
                    db.habitDao().upsertCompletion(HabitTicks.add(db.habitDao().completion(habit!!.id, key), habit.id, key, credit.minutes, null))
                    sync.stamp(habit.id)
                    counted = true
                }
                FocusCredit.Credit.None -> Unit
            }
            db.focusDao().upsert(piece.toEntity(counted))
            sync.stamp(piece.id)
            toTimeline(db, habit, piece)
        }
    }

    private suspend fun creditFor(db: AppDatabase, habit: HabitEntity?, piece: FocusSessionData, today: LocalDate): FocusCredit.Credit {
        if (habit == null) return FocusCredit.Credit.None
        val done = if (habit.kind == HabitStats.KIND_YES_NO) {
            val rules = HabitReminderScheduler.rulesOf(habit, db.habitDao().allCompletions().filter { it.habitId == habit.id }, today)
            HabitReminderScheduler.doneToday(habit, rules, today)
        } else false
        return FocusCredit.decide(habit, piece, alreadyCounted = false, doneToday = done)
    }

    private fun minuteNow(): Int = LocalDateTime.now().let { it.hour * 60 + it.minute }

    private suspend fun toTimeline(db: AppDatabase, habit: HabitEntity?, piece: FocusSessionData) {
        val started = LocalDateTime.ofInstant(Instant.ofEpochMilli(piece.startedAtMillis), ZoneId.systemDefault())
        val title = piece.label.ifBlank { habit?.name ?: "Focus session" }
        val category = if (habit == null) "Study" else FocusConverter.timelineCategory(habit.category)
        val marks = ArrayList<ImportedItemEntity>()
        for (block in FocusConverter.pieces(StreakFocus(piece.id, piece.habitId, piece.targetMinutes, piece.seconds, piece.completed, started, piece.label))) {
            val id = db.entryDao().insert(
                EntryEntity(
                    date = block.date.toString(),
                    startMinute = block.startMinute,
                    endMinute = block.endMinute,
                    category = category,
                    title = title,
                    note = "Focus session",
                ),
            )
            marks += ImportedItemEntity(SOURCE_FOCUS, block.sourceId, id)
        }
        db.importDao().insertAll(marks)
    }
}

/**
 * The running focus session (Habits H13): one state saved on the phone, changed only through here, so the screen, the
 * notification buttons and the end alarm all agree. The end alarm and the banner work with the app closed.
 */
object FocusEngine {
    private const val PREFS = "focus-state"
    private const val KEY = "state"
    private val mutex = Mutex()
    private val flow = MutableStateFlow<FocusState?>(null)
    @Volatile private var loaded = false

    /** The session as it is now (null when none runs). Safe to collect from the screen. */
    fun state(context: Context): StateFlow<FocusState?> {
        ensureLoaded(context)
        return flow
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            flow.value = FocusTimer.fromJson(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
            loaded = true
        }
    }

    private fun persist(context: Context, state: FocusState?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { if (state == null) remove(KEY) else putString(KEY, FocusTimer.toJson(state)) }
        flow.value = state
    }

    private val newId: () -> String = { UUID.randomUUID().toString() }

    suspend fun start(context: Context, habitId: String, label: String, minutes: Int, breakMinutes: Int) {
        val app = context.applicationContext
        ensureLoaded(app)
        mutex.withLock {
            if (flow.value != null) return
            val settings = FocusSettings(app)
            val s = FocusTimer.start(habitId, label, minutes, breakMinutes, settings.longBreakMinutes, settings.wholeRounds, System.currentTimeMillis())
            persist(app, s)
            if (habitId.isNotBlank()) rememberSetup(app, habitId, minutes, breakMinutes) else {
                settings.minutes = minutes
                settings.breakMinutes = breakMinutes
            }
            refresh(app, s)
        }
    }

    /** A habit remembers the minutes and break last used for it (Streak's `focusMinutes`, `focusBreakMinutes`). */
    private suspend fun rememberSetup(app: Context, habitId: String, minutes: Int, breakMinutes: Int) {
        val dao = AppDatabase.get(app).habitDao()
        val habit = dao.habit(habitId) ?: return
        if (FocusCredit.minutesOf(habit) == minutes && FocusCredit.breakOf(habit) == breakMinutes) return
        dao.upsertHabit(FocusCredit.withSetup(habit, minutes, breakMinutes))
        HabitSyncState(app).stamp(habitId)
    }

    suspend fun pause(context: Context) = change(context) { s, now -> FocusTimer.pause(s, now) }
    suspend fun resume(context: Context) = change(context) { s, now -> FocusTimer.resume(s, now) }
    suspend fun addMinute(context: Context) = change(context) { s, now -> FocusTimer.addMinute(s, now) }
    suspend fun reset(context: Context) = change(context) { s, now -> FocusTimer.reset(s, now) }

    private suspend fun change(context: Context, f: (FocusState, Long) -> FocusState) {
        val app = context.applicationContext
        ensureLoaded(app)
        mutex.withLock {
            val s = flow.value ?: return
            val next = f(s, System.currentTimeMillis())
            persist(app, next)
            refresh(app, next)
        }
    }

    /** Continue (after a break) or Skip break. A focus round ended early is kept. */
    suspend fun next(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        mutex.withLock {
            val s = flow.value ?: return
            val end = FocusTimer.advance(s, System.currentTimeMillis(), newId)
            end.saved?.let { FocusRecorder.save(app, it) }
            persist(app, end.state)
            refresh(app, end.state)
        }
    }

    /** Ends the session. Returns the session kept, if any. [completed] null means "as far as the goal goes". */
    suspend fun stop(context: Context, completed: Boolean? = null): FocusSessionData? {
        val app = context.applicationContext
        ensureLoaded(app)
        return mutex.withLock {
            val s = flow.value ?: return null
            val now = System.currentTimeMillis()
            val kept = FocusTimer.stop(s, now, completed ?: FocusTimer.metGoal(s, now), newId)
            kept?.let { FocusRecorder.save(app, it) }
            persist(app, null)
            refresh(app, null)
            kept
        }
    }

    /** Applies any phase that has reached its end (the alarm calls this, and so does the screen). Safe to call any time. */
    suspend fun checkEnd(context: Context) {
        val app = context.applicationContext
        ensureLoaded(app)
        mutex.withLock {
            var s = flow.value ?: return
            var alert = PhaseEnd.Alert.None
            var guard = 0
            while (FocusTimer.phaseDue(s, System.currentTimeMillis()) && guard++ < 8) {
                val end = FocusTimer.onPhaseEnd(s, System.currentTimeMillis(), newId)
                end.saved?.let { FocusRecorder.save(app, it) }
                s = end.state
                if (end.alert != PhaseEnd.Alert.None) alert = end.alert
            }
            if (s !== flow.value) {
                persist(app, s)
                refresh(app, s)
                FocusNotifier.alert(app, s, alert)
            } else {
                refresh(app, s) // makes sure the alarm and banner are right (for instance after a restart)
            }
        }
    }

    /** Sets the end alarm and the banner for [state] (null: none). */
    private fun refresh(app: Context, state: FocusState?) {
        FocusAlarm.sync(app, state)
        FocusNotifier.sync(app, state)
    }

    /** After the app starts: put the alarm and banner back for a session that was running. */
    suspend fun restore(context: Context) = checkEnd(context)
}

/** The end alarm. It uses Android's alarm-clock call: exact even in battery saving, no extra permission needed. */
object FocusAlarm {
    const val ACTION_END = "com.lifetracker.app.FOCUS_END"

    private fun pending(app: Context): PendingIntent = PendingIntent.getBroadcast(
        app, 0,
        Intent(app, FocusReceiver::class.java).setAction(ACTION_END).setData(Uri.parse("lifetracker-focus://end")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun open(app: Context): PendingIntent = PendingIntent.getActivity(
        app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun sync(app: Context, state: FocusState?) {
        val alarms = app.getSystemService(AlarmManager::class.java)
        val at = state?.let { FocusTimer.phaseEndMillis(it) }
        if (at == null) {
            runCatching { alarms.cancel(pending(app)) }
            return
        }
        runCatching { alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, open(app)), pending(app)) }
    }
}

/** The ongoing banner (a foreground service) and the alert when a phase ends. */
object FocusNotifier {
    const val CHANNEL_RUNNING = "focus-running"
    const val CHANNEL_ALERT = "focus-alert"
    const val ID_RUNNING = 6101
    const val ID_ALERT = 6102
    const val ACTION_PAUSE = "com.lifetracker.app.FOCUS_PAUSE"
    const val ACTION_RESUME = "com.lifetracker.app.FOCUS_RESUME"
    const val ACTION_MINUTE = "com.lifetracker.app.FOCUS_MINUTE"
    const val ACTION_NEXT = "com.lifetracker.app.FOCUS_NEXT"
    const val ACTION_STOP = "com.lifetracker.app.FOCUS_STOP"

    private fun ensureChannels(app: Context) {
        val manager = NotificationManagerCompat.from(app)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_RUNNING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Focus timer").setDescription("Shows the running focus timer").setShowBadge(false).build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERT, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Focus alerts").setDescription("Rings when a focus round or break is over")
                .setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
                .setVibrationEnabled(true).setVibrationPattern(longArrayOf(0, 400, 200, 400, 200, 400))
                .build(),
        )
    }

    private fun open(app: Context): PendingIntent = PendingIntent.getActivity(
        app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun button(app: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        app, 0,
        Intent(app, FocusReceiver::class.java).setAction(action).setData(Uri.parse("lifetracker-focus://$action")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun habitName(app: Context, habitId: String): String? =
        if (habitId.isBlank()) null else AppDatabase.get(app).habitDao().habit(habitId)?.name

    /** What the phase is called: "Focus · round 2", "Break", "Paused", "Tap Continue", "Done". */
    fun phaseText(s: FocusState): String {
        val round = if (FocusTimer.isPomodoro(s)) "  ·  round ${s.round}" else ""
        return when {
            s.finished -> "Done"
            s.awaiting -> "Break is over: tap Continue$round"
            s.isBreak -> (if (FocusTimer.isLongBreak(s)) "Long break" else "Break") + round + if (FocusTimer.isRunning(s)) "" else "  ·  paused"
            !FocusTimer.isRunning(s) -> "Paused$round"
            FocusTimer.isFlow(s) -> "Stopwatch"
            else -> "Focus$round"
        }
    }

    /** The ongoing banner for [s]. The countdown is drawn by Android itself, so the app does not tick. */
    fun build(app: Context, s: FocusState, title: String): Notification {
        ensureChannels(app)
        val now = System.currentTimeMillis()
        val builder = NotificationCompat.Builder(app, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentIntent(open(app))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val label = s.label.takeIf { it.isNotBlank() }
        builder.setContentText(listOfNotNull(label, phaseText(s)).joinToString("  ·  "))
        val live = FocusTimer.isRunning(s) && !s.awaiting && !s.finished
        if (live) {
            if (FocusTimer.isFlow(s)) {
                builder.setUsesChronometer(true).setShowWhen(true).setWhen(now - FocusTimer.elapsedSeconds(s, now) * 1000L)
            } else {
                builder.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                    .setWhen(FocusTimer.phaseEndMillis(s) ?: now)
            }
        } else {
            builder.setShowWhen(false)
            builder.setSubText(FocusTimer.clock(FocusTimer.displaySeconds(s, now)))
        }
        when {
            s.finished -> builder.addAction(0, "End", button(app, ACTION_STOP))
            s.awaiting -> builder.addAction(0, "Continue", button(app, ACTION_NEXT)).addAction(0, "End", button(app, ACTION_STOP))
            else -> {
                if (FocusTimer.isRunning(s)) builder.addAction(0, "Pause", button(app, ACTION_PAUSE))
                else builder.addAction(0, "Resume", button(app, ACTION_RESUME))
                if (s.isBreak) builder.addAction(0, "Skip break", button(app, ACTION_NEXT))
                else if (!FocusTimer.isFlow(s)) builder.addAction(0, "+1 min", button(app, ACTION_MINUTE))
                builder.addAction(0, "End", button(app, ACTION_STOP))
            }
        }
        return builder.build()
    }

    /** Puts the banner up (or takes it away). The service keeps it alive while the app is in the background. */
    @SuppressLint("MissingPermission")
    fun sync(app: Context, state: FocusState?) {
        val intent = Intent(app, FocusService::class.java)
        if (state == null) {
            runCatching { app.stopService(intent) }
            runCatching { NotificationManagerCompat.from(app).cancel(ID_RUNNING) }
            return
        }
        // Starting the service shows the banner; if Android does not allow that right now, post the banner on its own.
        val started = runCatching { ContextCompat.startForegroundService(app, intent) }.isSuccess
        if (!started) CoroutineScope(Dispatchers.IO).launch { post(app, state) }
    }

    @SuppressLint("MissingPermission")
    suspend fun post(app: Context, state: FocusState) {
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        val title = habitName(app, state.habitId) ?: "Focus"
        runCatching { NotificationManagerCompat.from(app).notify(ID_RUNNING, build(app, state, title)) }
    }

    /** The ring when a round, a break or a countdown is over. */
    @SuppressLint("MissingPermission")
    fun alert(app: Context, state: FocusState, alert: PhaseEnd.Alert) {
        if (alert == PhaseEnd.Alert.None) return
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        ensureChannels(app)
        val (title, text) = when (alert) {
            PhaseEnd.Alert.CountdownDone -> "Time's up" to "Your focus session is done and saved."
            PhaseEnd.Alert.BreakStarts -> "Round done" to "Break time. The next round starts when you tap Continue."
            else -> "Break is over" to "Tap Continue for the next round."
        }
        val notification = NotificationCompat.Builder(app, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open(app))
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60 * 1000L)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(ID_ALERT, notification) }
    }
}

/** Keeps the app in the foreground while a session runs, so the phone's cleaner leaves the timer alone. */
class FocusService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext
        val state = FocusEngine.state(app).value
        if (state == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Show a banner at once (Android wants one within a few seconds), then put the habit's name on it.
        runCatching {
            val first = FocusNotifier.build(app, state, "Focus")
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FocusNotifier.ID_RUNNING, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(FocusNotifier.ID_RUNNING, first)
            }
        }
        if (state.habitId.isNotBlank()) CoroutineScope(Dispatchers.IO).launch { FocusNotifier.post(app, state) }
        return START_STICKY
    }
}

/** Hears the end alarm and the banner's buttons. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    FocusAlarm.ACTION_END -> FocusEngine.checkEnd(app)
                    FocusNotifier.ACTION_PAUSE -> FocusEngine.pause(app)
                    FocusNotifier.ACTION_RESUME -> FocusEngine.resume(app)
                    FocusNotifier.ACTION_MINUTE -> FocusEngine.addMinute(app)
                    FocusNotifier.ACTION_NEXT -> FocusEngine.next(app)
                    FocusNotifier.ACTION_STOP -> FocusEngine.stop(app)
                }
            } catch (e: Exception) {
                AppLog.e("Focus", "Focus action $action failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
