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

    /** Wi-Fi at the last check: "" not connected, else its name (or "on"). Null before the first check. */
    var wifi: String?
        get() = prefs.getString("wifi", null)
        set(value) = prefs.edit { if (value == null) remove("wifi") else putString("wifi", value) }

    /** Bluetooth devices connected at the last check. Null before the first check. */
    var bluetooth: Set<String>?
        get() = prefs.getStringSet("bt", null)?.toSet().takeIf { prefs.contains("bt") }
        set(value) = prefs.edit { if (value == null) remove("bt") else putStringSet("bt", value) }

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
        var loaded: RuleContext? = null
        for (rule in rules) {
            if (!RuleEngine.triggerMatches(rule.trigger, event)) continue
            val key = RuleEngine.seenKey(rule, event)
            val verdict = RuleEngine.judge(
                rule, event, dao.runsSince(rule.id, now - RuleEngine.BURST_WINDOW_MS), key in seen, now, zone,
                if (rule.conditions.any { it.kind in CONTEXT_KINDS }) (loaded ?: contextFor(context, db, rules).also { loaded = it }) else RuleContext.EMPTY,
            )
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

    private val CONTEXT_KINDS = setOf(RuleKinds.CHARGING, RuleKinds.AT_PLACE, RuleKinds.HABIT_NOT_DONE)

    /** What is true right now, for the conditions that look at the phone. Read once for each happening. */
    private suspend fun contextFor(context: Context, db: AppDatabase, rules: List<Rule>): RuleContext {
        val kinds = rules.flatMap { it.conditions }.filter { it.kind in CONTEXT_KINDS }
        val open = if (kinds.any { it.kind == RuleKinds.AT_PLACE }) {
            val visit = db.placeDao().openVisits().filter { !it.ignored && it.placeId.isNotEmpty() }.maxByOrNull { it.startMs }
            visit?.let { v -> db.placeDao().activePlaces().firstOrNull { it.id == v.placeId } }
        } else {
            null
        }
        val today = java.time.LocalDate.now().toString()
        val done = kinds.filter { it.kind == RuleKinds.HABIT_NOT_DONE }.map { it.text("habitId") }.filter { it.isNotEmpty() }.distinct()
            .filter { db.habitDao().completion(it, today) != null }.toSet()
        return RuleContext(
            charging = kinds.any { it.kind == RuleKinds.CHARGING } && SystemSituation.charging(context),
            openPlaceId = open?.id.orEmpty(),
            openCategory = open?.kind.orEmpty(),
            habitsDoneToday = done,
        )
    }

    /** Runs a rule's actions now, whatever its trigger and conditions say: the "Run now" button. */
    suspend fun runNow(context: Context, ruleId: String) = lock.withLock {
        val app = context.applicationContext
        val db = AppDatabase.get(app)
        val rule = db.ruleDao().get(ruleId)?.let(RuleCodec::toRule) ?: return@withLock
        val now = System.currentTimeMillis()
        execute(app, db, rule, RuleEvent(RuleKinds.BUTTON, now, "button:$now"), "Run now", now)
    }

    /**
     * The check Android runs about every 15 minutes: time-of-day rules that are due, stays that lasted long enough, and the
     * things that are found by looking (Wi-Fi, Bluetooth, apps, calls, steps, sleep). Only what a rule asks for is looked at.
     */
    suspend fun tick(context: Context) {
        val app = context.applicationContext
        if (!Features.isOn(app, Feature.RULES)) return
        val db = AppDatabase.get(app)
        val rules = db.ruleDao().enabled().map(RuleCodec::toRule)
        val state = RuleState(app)
        val now = System.currentTimeMillis()
        val kinds = rules.map { it.trigger.kind }.toSet()
        val events = mutableListOf<RuleEvent>()
        events += RuleHooks.dueTimes(rules, state.lastTickMs, now)
        if (RuleKinds.STAY in kinds) events += RuleHooks.stayEvents(db.placeDao().openVisits(), db.placeDao().activePlaces(), now)

        if (RuleKinds.WIFI_ON in kinds || RuleKinds.WIFI_OFF in kinds) {
            val (connected, name) = SystemSituation.wifi(app)
            val nowWifi = if (!connected) "" else (name ?: "on")
            events += RuleHooks.wifiEvents(state.wifi, nowWifi, now)
            state.wifi = nowWifi
        }
        if (RuleKinds.BT_ON in kinds || RuleKinds.BT_OFF in kinds) {
            val nowSet = BluetoothWatch.connected
            val paired = BluetoothWatch.paired(app).associate { it.second to it.first }
            events += RuleHooks.bluetoothEvents(state.bluetooth, nowSet, now) { paired[it].orEmpty().ifEmpty { "a device" } }
            state.bluetooth = nowSet
        }
        val from = RuleHooks.lookBack(state.lastTickMs, now)
        if (RuleKinds.APP_OPEN in kinds || RuleKinds.APP_USED in kinds) {
            val labels = db.usageDao().allApps().associate { it.pkg to it.label }
            val name = { pkg: String -> labels[pkg].orEmpty().ifEmpty { pkg } }
            if (RuleKinds.APP_OPEN in kinds) events += RuleHooks.appOpenEvents(db.usageDao().sessionsOnce(from, now), name, from, now)
            if (RuleKinds.APP_USED in kinds) {
                val today = java.time.LocalDate.now()
                val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                events += RuleHooks.appUsedEvents(rules, db.usageDao().sessionsOnce(start, now), name, today.toString(), now)
            }
        }
        if (RuleKinds.CALL in kinds) events += RuleHooks.callEvents(db.callDao().allCalls().takeLast(60), from, now)
        if (RuleKinds.STEPS in kinds) {
            val today = java.time.LocalDate.now().toString()
            db.stepDao().day(today)?.let { d -> RuleHooks.stepsEvent(d.steps, today, now)?.let { events += it } }
        }
        if (RuleKinds.WAKE in kinds || RuleKinds.SLEEP in kinds) {
            val nights = db.sleepDao().allNights().takeLast(4)
            events += RuleHooks.sleepEvents(nights, from, now)
        }
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
            RuleKinds.TRIP_START -> "A trip started" + (event.text("placeName").takeIf { it.isNotEmpty() }?.let { " from $it" } ?: "")
            RuleKinds.SPEED -> "Speed was ${event.text("kmh")} km/h"
            RuleKinds.HABIT_TICKED -> "${event.text("habitName")} was ticked"
            RuleKinds.WIFI_ON -> "Wi-Fi connected" + (event.text("name").takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")
            RuleKinds.WIFI_OFF -> "Wi-Fi disconnected" + (event.text("name").takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")
            RuleKinds.BT_ON -> "${event.text("deviceName")} connected"
            RuleKinds.BT_OFF -> "${event.text("deviceName")} disconnected"
            RuleKinds.APP_OPEN -> "Opened ${event.text("label")}"
            RuleKinds.APP_USED -> "Used ${event.text("label")} for ${event.text("minutes")} min today"
            RuleKinds.CALL -> when (event.text("type")) { "in" -> "Took a call"; "out" -> "Made a call"; else -> "Missed a call" }
            RuleKinds.STEPS -> "${event.text("steps")} steps today"
            RuleKinds.WAKE -> "Woke up"
            RuleKinds.SLEEP -> "Fell asleep"
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
            HabitReminderActions.done(context, habit.id, event.depth + 1)
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
        RuleKinds.START_FOCUS -> {
            if (!Features.isOn(context, Feature.HABITS)) error("Habits and Focus are switched off")
            if (FocusEngine.state(context).value != null) error("A focus timer is already running")
            val minutes = action.int("minutes", 25).coerceIn(1, 240)
            val habitId = action.text("habitId").takeIf { id -> id.isNotEmpty() && db.habitDao().habit(id)?.deletedAt == 0L }.orEmpty()
            val label = RuleText.fill(action.text("label"), event).trim().ifEmpty { "Focus" }
            FocusEngine.start(context, habitId, label, minutes, action.int("breakMinutes", 5).coerceIn(0, 60))
            "Started a $minutes min focus timer"
        }
        RuleKinds.MONEY -> {
            if (!Features.isOn(context, Feature.MONEY)) error("Money is switched off")
            val amount = action.text("amount").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: error("The amount is not a number above 0")
            val kind = if (action.int("kind", KIND_EXPENSE) == KIND_INCOME) KIND_INCOME else KIND_EXPENSE
            val categories = db.moneyDao().allCategories().filter { it.kind == kind }
            val category = categories.firstOrNull { it.id == action.text("categoryId") } ?: categories.firstOrNull() ?: error("There is no money category to put it under")
            val name = RuleText.fill(action.text("name"), event).trim().ifEmpty { category.name }
            val date = Instant.ofEpochMilli(event.atMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            db.moneyDao().upsertEntry(
                MoneyEntryEntity(id = MoneyStats.newId(), date = date, kind = kind, categoryId = category.id, name = name, amount = amount, note = "", itemId = ""),
            )
            "Logged $amount for $name"
        }
        RuleKinds.NOTE -> {
            if (!Features.isOn(context, Feature.HABITS)) error("Habits are switched off")
            val habit = db.habitDao().habit(action.text("habitId"))?.takeIf { it.deletedAt == 0L } ?: error("The habit is gone")
            val text = RuleText.fill(action.text("text"), event).trim()
            val now = java.time.LocalDateTime.now()
            val note = HabitNotes.create(java.util.UUID.randomUUID().toString(), habit.id, now.toLocalDate(), text, now) ?: error("The note has no text")
            db.planDao().upsertNote(note)
            "Added a note to " + habit.name
        }
        RuleKinds.RUN_RULE -> {
            val other = db.ruleDao().get(action.text("ruleId"))?.let(RuleCodec::toRule) ?: error("The other rule is gone")
            if (event.depth + 1 >= RuleEngine.MAX_DEPTH) error("Stopped: one rule set off another too many times in a row")
            execute(
                context, db, other,
                RuleEvent(event.kind, event.atMs, event.key + ">" + other.id, event.params, event.depth + 1),
                "Run by another rule: " + describe(event), System.currentTimeMillis(),
            )
            "Ran " + other.name
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

/** Android runs this about every 15 minutes, only while a rule exists that is found by looking (a time of day, a stay, Wi-Fi, Bluetooth, apps, calls, steps, sleep). */
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
                RuleCodec.partFromJson(it.triggerJson).kind in RuleKinds.TICKED
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
