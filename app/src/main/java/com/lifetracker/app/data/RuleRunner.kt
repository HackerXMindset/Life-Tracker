package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifetracker.app.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/** What the rules remember between runs: which happenings they already acted on, and when time-of-day rules were last checked. */
private class RuleState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rules_state", Context.MODE_PRIVATE)

    var seen: List<String>
        get() = try {
            JSONArray(prefs.getString("seen", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
        set(value) = prefs.edit { putString("seen", JSONArray(value).toString()) }

    var lastTickMs: Long
        get() = prefs.getLong("last_tick", 0L)
        set(value) = prefs.edit { putLong("last_tick", value) }
}

/**
 * Runs the rules when something happens (Rules, E8). Everything goes through [handle], one happening at a time, so two things
 * that happen together never run the same rule twice. A rule that fails never stops another, and never stops the app's own work.
 */
object RuleRunner {
    private const val LOG_KEEP = 300
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Hands events over and returns at once: the rules run on their own time. */
    fun postAll(context: Context, events: List<RuleEvent>) {
        if (events.isEmpty()) return
        val app = context.applicationContext
        scope.launch {
            events.forEach { e ->
                try {
                    handle(app, e)
                } catch (t: Throwable) {
                    AppLog.e("Rules", "A rule could not run for ${e.kind}", t)
                }
            }
        }
    }

    suspend fun handle(context: Context, event: RuleEvent) = lock.withLock {
        if (!Features.isOn(context, Feature.RULES)) return@withLock
        val db = AppDatabase.get(context)
        val dao = db.ruleDao()
        val rules = dao.enabled().map(RuleCodec::toRule)
        if (rules.none { RuleEngine.triggerMatches(it.trigger, event) }) return@withLock
        val state = RuleState(context)
        var seen = state.seen
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val label = describe(event)
        for (rule in rules) {
            if (!RuleEngine.triggerMatches(rule.trigger, event)) continue
            val key = RuleEngine.seenKey(rule, event)
            val verdict = RuleEngine.judge(rule, event, dao.runsSince(rule.id, now - RuleEngine.BURST_WINDOW_MS), key in seen, now, zone)
            when (verdict.outcome) {
                RuleEngine.Outcome.NOT_MINE, RuleEngine.Outcome.SEEN -> Unit
                RuleEngine.Outcome.RUN -> {
                    seen = RuleEngine.remember(seen, key)
                    execute(context, db, rule, event, label, now)
                }
                RuleEngine.Outcome.TOO_OFTEN -> {
                    dao.setEnabled(rule.id, false)
                    log(db, rule, now, label, "off", verdict.reason)
                    AppLog.w("Rules", "${rule.name}: ${verdict.reason}")
                }
                else -> {
                    // Said once for each happening, not at every reading while it goes on.
                    val skipKey = "$key!"
                    if (skipKey !in seen) {
                        seen = RuleEngine.remember(seen, skipKey)
                        log(db, rule, now, label, "skipped", verdict.reason)
                    }
                }
            }
        }
        state.seen = seen
    }

    /** Runs a rule's actions now, whatever its trigger and conditions say: the "Run now" button. */
    suspend fun runNow(context: Context, ruleId: String) = lock.withLock {
        val app = context.applicationContext
        val db = AppDatabase.get(app)
        val rule = db.ruleDao().get(ruleId)?.let(RuleCodec::toRule) ?: return@withLock
        val now = System.currentTimeMillis()
        execute(app, db, rule, RuleEvent(RuleKinds.BUTTON, now, "button:$now"), "Run now", now)
    }

    /** Looks for time-of-day rules that are due and stays that have lasted long enough. Android calls this about every 15 minutes. */
    suspend fun tick(context: Context) {
        val app = context.applicationContext
        if (!Features.isOn(app, Feature.RULES)) return
        val db = AppDatabase.get(app)
        val rules = db.ruleDao().enabled().map(RuleCodec::toRule)
        val state = RuleState(app)
        val now = System.currentTimeMillis()
        val events = RuleHooks.dueTimes(rules, state.lastTickMs, now) +
            (if (rules.any { it.trigger.kind == RuleKinds.STAY }) RuleHooks.stayEvents(db.placeDao().openVisits(), db.placeDao().activePlaces(), now) else emptyList())
        state.lastTickMs = now
        events.forEach { handle(app, it) }
    }

    private fun describe(event: RuleEvent): String {
        val place = event.text("placeName")
        return when (event.kind) {
            RuleKinds.ARRIVE -> "Arrived at $place"
            RuleKinds.LEAVE -> "Left $place"
            RuleKinds.STAY -> "Still at $place after ${event.text("minutes")} min"
            RuleKinds.CHARGE_ON -> "Charging started at ${event.text("level")}%"
            RuleKinds.CHARGE_OFF -> "Charging stopped at ${event.text("level")}%"
            RuleKinds.TRIP_END -> "A ${event.text("mode")} trip ended (${event.text("km")} km)"
            RuleKinds.TIME -> "It was ${RuleSummary.clock(event.int("at"))}"
            else -> event.kind
        }
    }

    private suspend fun execute(context: Context, db: AppDatabase, rule: Rule, event: RuleEvent, label: String, now: Long) {
        val details = mutableListOf<String>()
        var failed = false
        rule.actions.forEach { action ->
            try {
                details += perform(context, db, action, event)
            } catch (t: Exception) {
                failed = true
                details += (t.message ?: t.javaClass.simpleName)
            }
        }
        db.ruleDao().noteRun(rule.id, now)
        log(db, rule, now, label, if (failed) "failed" else "ran", details.joinToString("; ").ifEmpty { "Nothing to do" })
        AppLog.i("Rules", "${rule.name}: $label -> ${details.joinToString("; ")}")
    }

    private suspend fun log(db: AppDatabase, rule: Rule, atMs: Long, event: String, result: String, detail: String) {
        db.ruleDao().addLog(RuleLogEntity(ruleId = rule.id, ruleName = rule.name, atMs = atMs, event = event, result = result, detail = detail))
        db.ruleDao().trimLog(LOG_KEEP)
    }

    /** Does one action and says what it did. Throws a message in plain words when it cannot. */
    private suspend fun perform(context: Context, db: AppDatabase, action: RulePart, event: RuleEvent): String = when (action.kind) {
        RuleKinds.TICK_HABIT -> {
            if (!Features.isOn(context, Feature.HABITS)) error("Habits are switched off")
            val habit = db.habitDao().habit(action.text("habitId"))?.takeIf { it.deletedAt == 0L } ?: error("The habit is gone")
            if (!HabitReminderActions.canDone(habit)) error(habit.name + " cannot be ticked by a rule (Avoid and checklist habits are done by hand)")
            HabitReminderActions.done(context, habit.id)
            "Ticked " + habit.name
        }
        RuleKinds.ENTRY -> {
            val title = RuleText.fill(action.text("title"), event).trim()
            if (title.isEmpty()) error("The entry has no name")
            val category = action.text("category").ifEmpty { db.activityDao().all().firstOrNull { !it.archived }?.id ?: error("There is no activity to put it under") }
            val local = Instant.ofEpochMilli(event.atMs).atZone(ZoneId.systemDefault())
            db.entryDao().insert(
                EntryEntity(
                    date = local.toLocalDate().toString(),
                    startMinute = local.hour * 60 + local.minute,
                    endMinute = null,
                    category = category,
                    title = title,
                    note = RuleText.fill(action.text("note"), event).trim(),
                ),
            )
            "Added \"$title\" to the Timeline"
        }
        RuleKinds.NOTIFY -> {
            val title = RuleText.fill(action.text("title"), event).trim().ifEmpty { "Kairos" }
            RuleNotifier.post(context, title, RuleText.fill(action.text("text"), event).trim())
            "Sent \"$title\""
        }
        else -> error("Unknown action: " + action.kind)
    }
}

/** The banner a rule can send. Its own channel, so you can silence it in Android's settings. */
object RuleNotifier {
    private const val CHANNEL = "rules"

    @SuppressLint("MissingPermission")
    fun post(context: Context, title: String, text: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) error("Notifications are not allowed for this app")
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Rules")
                .setDescription("Messages from your own rules")
                .build(),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .apply { if (text.isNotEmpty()) setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)) }
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(("rule" + title + text).hashCode(), n)
    }
}

/** Android runs this about every 15 minutes, only while a time-of-day or "stay longer than" rule exists. */
class RulesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppLog.track("Rules", "Check time-of-day rules") { RuleRunner.tick(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "rules-tick"

        /** Starts or stops the 15-minute check to match the rules you have now. Safe to call whenever rules or the switch change. */
        suspend fun refresh(context: Context) {
            val app = context.applicationContext
            val needed = Features.isOn(app, Feature.RULES) && AppDatabase.get(app).ruleDao().enabled().any {
                val kind = RuleCodec.partFromJson(it.triggerJson).kind
                kind == RuleKinds.TIME || kind == RuleKinds.STAY
            }
            val manager = WorkManager.getInstance(app)
            if (needed) {
                val request = PeriodicWorkRequestBuilder<RulesWorker>(15, TimeUnit.MINUTES).setConstraints(Constraints.NONE).build()
                manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                manager.cancelUniqueWork(NAME)
            }
        }
    }
}
