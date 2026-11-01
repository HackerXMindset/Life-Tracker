package com.lifetracker.app.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * The words of a rule, following the usual ones of home-automation apps: a rule has one **trigger** (what happens), any number of
 * **conditions** (only when...) and any number of **actions** (what to do). No Android in this file, so it can be tested.
 */
object RuleKinds {
    // Triggers
    const val ARRIVE = "arrive"
    const val LEAVE = "leave"
    const val STAY = "stay"
    const val CHARGE_ON = "charge_on"
    const val CHARGE_OFF = "charge_off"
    const val TRIP_END = "trip_end"
    const val TIME = "time"
    const val BUTTON = "button"
    const val TRIP_START = "trip_start"
    const val SPEED_OVER = "speed_over"
    const val SPEED_UNDER = "speed_under"
    const val HABIT_TICKED = "habit_ticked"
    const val WIFI_ON = "wifi_on"
    const val WIFI_OFF = "wifi_off"
    const val BT_ON = "bt_on"
    const val BT_OFF = "bt_off"
    const val APP_OPEN = "app_open"
    const val APP_USED = "app_used"
    const val CALL = "call"
    const val STEPS = "steps"
    const val WAKE = "wake"
    const val SLEEP = "sleep"

    /** Events that are not a trigger of their own: one reading of the speed, offered to the two speed triggers. */
    const val SPEED = "speed"

    // Conditions
    const val DAYS = "days"
    const val WINDOW = "window"
    const val CHARGING = "charging"
    const val AT_PLACE = "at_place"
    const val HABIT_NOT_DONE = "habit_not_done"

    /** Not a condition itself: with mode "any", one condition holding is enough instead of all of them. */
    const val MATCH = "match"

    // Actions
    const val TICK_HABIT = "tick_habit"
    const val ENTRY = "entry"
    const val NOTIFY = "notify"
    const val START_FOCUS = "start_focus"
    const val MONEY = "money"
    const val NOTE = "note"
    const val RUN_RULE = "run_rule"

    /** Triggers that are looked at about every 15 minutes (not the moment they happen). */
    val TICKED = setOf(TIME, STAY, WIFI_ON, WIFI_OFF, BT_ON, BT_OFF, APP_OPEN, APP_USED, CALL, STEPS, WAKE, SLEEP)

    /** Every trigger with the words shown in the editor, in the order they are offered. */
    val TRIGGERS: List<Pair<String, String>> = listOf(
        ARRIVE to "I arrive somewhere",
        LEAVE to "I leave somewhere",
        STAY to "I stay somewhere longer than",
        CHARGE_ON to "Charging starts",
        CHARGE_OFF to "Charging stops",
        TRIP_END to "A trip ends",
        TIME to "It is a time of day",
        BUTTON to "I press Run now",
        TRIP_START to "A trip starts",
        SPEED_OVER to "My speed goes above",
        SPEED_UNDER to "My speed drops below (on a trip)",
        HABIT_TICKED to "A habit is ticked",
        WIFI_ON to "Wi-Fi connects",
        WIFI_OFF to "Wi-Fi disconnects",
        BT_ON to "A Bluetooth device connects",
        BT_OFF to "A Bluetooth device disconnects",
        APP_OPEN to "I open an app",
        APP_USED to "I use an app for more than (a day)",
        CALL to "A call comes or goes",
        STEPS to "I reach a number of steps (a day)",
        WAKE to "I wake up",
        SLEEP to "I fall asleep",
    )

    val CONDITIONS: List<Pair<String, String>> = listOf(
        DAYS to "Only on these days",
        WINDOW to "Only between these times",
        CHARGING to "Only if the phone is charging (or not)",
        AT_PLACE to "Only if I am at a place (or not)",
        HABIT_NOT_DONE to "Only if a habit is not done yet today",
    )

    val ACTIONS: List<Pair<String, String>> = listOf(
        TICK_HABIT to "Tick a habit",
        ENTRY to "Add a Timeline entry",
        NOTIFY to "Send me a notification",
        START_FOCUS to "Start a focus timer",
        MONEY to "Log money",
        NOTE to "Add a habit note",
        RUN_RULE to "Run another rule",
    )

    fun triggerLabel(kind: String) = TRIGGERS.firstOrNull { it.first == kind }?.second ?: kind
    fun conditionLabel(kind: String) = CONDITIONS.firstOrNull { it.first == kind }?.second ?: kind
    fun actionLabel(kind: String) = ACTIONS.firstOrNull { it.first == kind }?.second ?: kind
}

/** One piece of a rule (the trigger, a condition or an action): its kind and its settings, all kept as text. */
class RulePart(val kind: String, val params: Map<String, String> = emptyMap()) {
    fun text(key: String, default: String = ""): String = params[key] ?: default
    fun int(key: String, default: Int = 0): Int = params[key]?.toIntOrNull() ?: default
    fun with(key: String, value: String) = RulePart(kind, params + (key to value))
    fun without(key: String) = RulePart(kind, params - key)
}

/** A whole rule. [cooldownSec] is the least time between two runs of it. */
class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: RulePart,
    val conditions: List<RulePart>,
    val actions: List<RulePart>,
    val cooldownSec: Int = DEFAULT_COOLDOWN_SEC,
    val createdMs: Long = 0L,
    val lastRunMs: Long = 0L,
    val runs: Int = 0,
) {
    companion object {
        const val DEFAULT_COOLDOWN_SEC = 60
    }
}

/** Something that happened, offered to every rule. [key] names the one thing that happened, so it is never acted on twice. */
class RuleEvent(
    val kind: String,
    val atMs: Long,
    val key: String,
    val params: Map<String, String> = emptyMap(),
    /** 0 for something that really happened; one more for each rule that was set off by another. */
    val depth: Int = 0,
) {
    fun text(key: String, default: String = ""): String = params[key] ?: default
    fun int(key: String, default: Int = 0): Int = params[key]?.toIntOrNull() ?: default
}

/** Turning rules into the text kept in the database and back. */
object RuleCodec {
    private fun partJson(p: RulePart): JSONObject {
        val o = JSONObject().put("kind", p.kind)
        val params = JSONObject()
        p.params.toSortedMap().forEach { (k, v) -> params.put(k, v) }
        return o.put("params", params)
    }

    private fun part(o: JSONObject): RulePart {
        val params = linkedMapOf<String, String>()
        o.optJSONObject("params")?.let { p -> p.keys().forEach { k -> params[k] = p.optString(k, "") } }
        return RulePart(o.optString("kind", ""), params)
    }

    fun partToJson(p: RulePart): String = partJson(p).toString()

    fun partFromJson(text: String): RulePart = try {
        part(JSONObject(text))
    } catch (e: org.json.JSONException) {
        RulePart("")
    }

    fun listToJson(parts: List<RulePart>): String = JSONArray().also { a -> parts.forEach { a.put(partJson(it)) } }.toString()

    fun listFromJson(text: String): List<RulePart> = try {
        val a = JSONArray(text)
        (0 until a.length()).map { part(a.getJSONObject(it)) }
    } catch (e: org.json.JSONException) {
        emptyList()
    }

    fun toRule(e: RuleEntity) = Rule(
        id = e.id,
        name = e.name,
        enabled = e.enabled,
        trigger = partFromJson(e.triggerJson),
        conditions = listFromJson(e.conditionsJson),
        actions = listFromJson(e.actionsJson),
        cooldownSec = e.cooldownSec,
        createdMs = e.createdMs,
        lastRunMs = e.lastRunMs,
        runs = e.runs,
    )

    fun toEntity(r: Rule, sortOrder: Int = 0) = RuleEntity(
        id = r.id,
        name = r.name,
        enabled = r.enabled,
        triggerJson = partToJson(r.trigger),
        conditionsJson = listToJson(r.conditions),
        actionsJson = listToJson(r.actions),
        cooldownSec = r.cooldownSec,
        createdMs = r.createdMs,
        lastRunMs = r.lastRunMs,
        runs = r.runs,
        sortOrder = sortOrder,
    )
}

/** The words that describe a rule in a list, from the rule alone and a way to look up the names it points at. */
object RuleSummary {
    fun clock(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", (minute / 60) % 24, minute % 60)

    private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun daysText(days: Set<Int>): String = when {
        days.size == 7 -> "every day"
        days == setOf(1, 2, 3, 4, 5) -> "weekdays"
        days == setOf(6, 7) -> "weekends"
        else -> days.sorted().joinToString(", ") { DAY_NAMES[(it - 1).coerceIn(0, 6)] }
    }

    fun days(p: RulePart): Set<Int> = p.text("days").split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    /** "Home", "any kind of Study place", "anywhere you named". */
    fun where(p: RulePart, placeName: (String) -> String, categoryName: (String) -> String): String = when {
        p.text("placeId").isNotEmpty() -> placeName(p.text("placeId"))
        p.text("category").isNotEmpty() -> "any " + categoryName(p.text("category")) + " place"
        else -> "any place you named"
    }

    fun trigger(p: RulePart, placeName: (String) -> String, categoryName: (String) -> String, habitName: (String) -> String = { "a habit" }): String = when (p.kind) {
        RuleKinds.ARRIVE -> "I arrive at " + where(p, placeName, categoryName)
        RuleKinds.LEAVE -> "I leave " + where(p, placeName, categoryName)
        RuleKinds.STAY -> "I have been at " + where(p, placeName, categoryName) + " for " + p.int("minutes", 30) + " min"
        RuleKinds.CHARGE_ON -> "Charging starts"
        RuleKinds.CHARGE_OFF -> "Charging stops"
        RuleKinds.TRIP_END -> "A trip ends" + (p.text("mode").takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")
        RuleKinds.TIME -> "It is " + clock(p.int("at", 0))
        RuleKinds.BUTTON -> "I press Run now"
        RuleKinds.TRIP_START -> "A trip starts"
        RuleKinds.SPEED_OVER -> "My speed goes above " + p.int("kmh", 60) + " km/h"
        RuleKinds.SPEED_UNDER -> "My speed drops below " + p.int("kmh", 10) + " km/h on a trip"
        RuleKinds.HABIT_TICKED -> if (p.text("habitId").isEmpty()) "Any habit is ticked" else habitName(p.text("habitId")) + " is ticked"
        RuleKinds.WIFI_ON -> "Wi-Fi connects" + (p.text("name").takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")
        RuleKinds.WIFI_OFF -> "Wi-Fi disconnects" + (p.text("name").takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")
        RuleKinds.BT_ON -> (p.text("deviceName").ifEmpty { "A Bluetooth device" }) + " connects"
        RuleKinds.BT_OFF -> (p.text("deviceName").ifEmpty { "A Bluetooth device" }) + " disconnects"
        RuleKinds.APP_OPEN -> "I open " + (p.text("label").ifEmpty { "any app" })
        RuleKinds.APP_USED -> "I have used " + (p.text("label").ifEmpty { "an app" }) + " for " + p.int("minutes", 60) + " min today"
        RuleKinds.CALL -> when (p.text("type")) {
            "in" -> "I take a call"
            "out" -> "I make a call"
            "missed" -> "I miss a call"
            else -> "A call comes or goes"
        }
        RuleKinds.STEPS -> "I reach " + p.int("steps", 8000) + " steps today"
        RuleKinds.WAKE -> "I wake up"
        RuleKinds.SLEEP -> "I fall asleep"
        else -> p.kind
    }

    fun condition(p: RulePart, placeName: (String) -> String = { it }, categoryName: (String) -> String = { it }, habitName: (String) -> String = { it }): String = when (p.kind) {
        RuleKinds.DAYS -> "on " + daysText(days(p))
        RuleKinds.WINDOW -> "between " + clock(p.int("from", 0)) + " and " + clock(p.int("to", 0))
        RuleKinds.CHARGING -> if (p.text("state") == "off") "while not charging" else "while charging"
        RuleKinds.AT_PLACE -> (if (p.text("state") == "off") "while not at " else "while at ") + where(p, placeName, categoryName)
        RuleKinds.HABIT_NOT_DONE -> habitName(p.text("habitId")) + " is not done yet today"
        RuleKinds.MATCH -> "any of these"
        else -> p.kind
    }

    fun action(
        p: RulePart,
        habitName: (String) -> String,
        activityName: (String) -> String,
        ruleName: (String) -> String = { "another rule" },
    ): String = when (p.kind) {
        RuleKinds.TICK_HABIT -> "tick " + habitName(p.text("habitId"))
        RuleKinds.ENTRY -> "add \"" + p.text("title") + "\" to the Timeline" + (p.text("category").takeIf { it.isNotEmpty() }?.let { " as " + activityName(it) } ?: "")
        RuleKinds.NOTIFY -> "notify \"" + p.text("title") + "\""
        RuleKinds.START_FOCUS -> "start a " + p.int("minutes", 25) + " min focus timer" + (p.text("habitId").takeIf { it.isNotEmpty() }?.let { " for " + habitName(it) } ?: "")
        RuleKinds.MONEY -> (if (p.int("kind", 0) == 1) "log income of " else "log spending of ") + p.text("amount") + " (\"" + p.text("name") + "\")"
        RuleKinds.NOTE -> "add a note to " + habitName(p.text("habitId"))
        RuleKinds.RUN_RULE -> "run " + ruleName(p.text("ruleId"))
        else -> p.kind
    }
}

/** Fills the {place}, {time}, {minutes}, {mode}, {km}, {habit}, {app}, {name}, {steps} and {kmh} in a message with what the event knows. */
object RuleText {
    fun fill(template: String, event: RuleEvent, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = Instant.ofEpochMilli(event.atMs).atZone(zone)
        val values = mapOf(
            "place" to event.text("placeName"),
            "time" to RuleSummary.clock(local.hour * 60 + local.minute),
            "minutes" to event.text("minutes"),
            "mode" to event.text("mode"),
            "km" to event.text("km"),
            "habit" to event.text("habitName"),
            "app" to event.text("label"),
            "name" to event.text("name").ifEmpty { event.text("deviceName") },
            "steps" to event.text("steps"),
            "kmh" to event.text("kmh"),
        )
        var out = template
        values.forEach { (k, v) -> out = out.replace("{$k}", v) }
        return out
    }
}
