package com.lifetracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What is true when a rule is judged, for the conditions that look at the phone now (charging, where I am, which habits are done
 * today). Only filled in as far as the conditions of the rules need it.
 */
class RuleContext(
    val charging: Boolean = false,
    val openPlaceId: String = "",
    val openCategory: String = "",
    val habitsDoneToday: Set<String> = emptySet(),
) {
    companion object {
        val EMPTY = RuleContext()
    }
}

/**
 * Decides whether a rule runs for something that happened, and holds back rules that run too often. No Android in it, so it can
 * be tested. The order of the checks is the order of the reasons you see in the log.
 */
object RuleEngine {
    /** A rule set off by a rule set off by a rule... stops here. */
    const val MAX_DEPTH = 3

    /** A rule that runs this many times inside [BURST_WINDOW_MS] is switched off: something is going round in a circle. */
    const val BURST_RUNS = 10
    const val BURST_WINDOW_MS = 10 * 60_000L

    private val LOOK_BACK_KINDS = setOf(RuleKinds.APP_OPEN, RuleKinds.CALL, RuleKinds.WAKE, RuleKinds.SLEEP)

    enum class Outcome { RUN, NOT_MINE, SEEN, CONDITION, COOLDOWN, LOOP, TOO_OFTEN }

    /** [reason] is the line for the log (empty for a rule that simply was not meant for this). */
    class Verdict(val outcome: Outcome, val reason: String = "")

    private fun placeMatches(t: RulePart, e: RuleEvent): Boolean {
        val placeId = t.text("placeId")
        val category = t.text("category")
        return when {
            placeId.isNotEmpty() -> e.text("placeId") == placeId
            category.isNotEmpty() -> e.text("category") == category
            else -> e.text("placeId").isNotEmpty()
        }
    }

    /** Whether the trigger is about this kind of event (before any condition). */
    fun triggerMatches(t: RulePart, e: RuleEvent): Boolean = when (t.kind) {
        RuleKinds.ARRIVE -> e.kind == RuleKinds.ARRIVE && placeMatches(t, e)
        RuleKinds.LEAVE -> e.kind == RuleKinds.LEAVE && placeMatches(t, e)
        RuleKinds.STAY -> e.kind == RuleKinds.STAY && placeMatches(t, e) && e.int("minutes") >= t.int("minutes", 30)
        RuleKinds.CHARGE_ON -> e.kind == RuleKinds.CHARGE_ON
        RuleKinds.CHARGE_OFF -> e.kind == RuleKinds.CHARGE_OFF
        RuleKinds.TRIP_END -> e.kind == RuleKinds.TRIP_END && (t.text("mode").isEmpty() || t.text("mode") == e.text("mode"))
        RuleKinds.TIME -> e.kind == RuleKinds.TIME && e.int("at", -1) == t.int("at", -2)
        RuleKinds.TRIP_START -> e.kind == RuleKinds.TRIP_START
        RuleKinds.SPEED_OVER -> e.kind == RuleKinds.SPEED && e.int("kmh") >= t.int("kmh", 60)
        RuleKinds.SPEED_UNDER -> e.kind == RuleKinds.SPEED && e.text("onTrip") == "1" && e.int("kmh") <= t.int("kmh", 10)
        RuleKinds.HABIT_TICKED -> e.kind == RuleKinds.HABIT_TICKED && (t.text("habitId").isEmpty() || t.text("habitId") == e.text("habitId"))
        RuleKinds.WIFI_ON -> e.kind == RuleKinds.WIFI_ON && (t.text("name").isEmpty() || t.text("name").equals(e.text("name"), ignoreCase = true))
        RuleKinds.WIFI_OFF -> e.kind == RuleKinds.WIFI_OFF && (t.text("name").isEmpty() || t.text("name").equals(e.text("name"), ignoreCase = true))
        RuleKinds.BT_ON -> e.kind == RuleKinds.BT_ON && (t.text("device").isEmpty() || t.text("device") == e.text("device"))
        RuleKinds.BT_OFF -> e.kind == RuleKinds.BT_OFF && (t.text("device").isEmpty() || t.text("device") == e.text("device"))
        RuleKinds.APP_OPEN -> e.kind == RuleKinds.APP_OPEN && (t.text("pkg").isEmpty() || t.text("pkg") == e.text("pkg"))
        RuleKinds.APP_USED -> e.kind == RuleKinds.APP_USED && t.text("pkg") == e.text("pkg") && e.int("minutes") >= t.int("minutes", 60)
        RuleKinds.CALL -> e.kind == RuleKinds.CALL && (t.text("type").isEmpty() || t.text("type") == e.text("type"))
        RuleKinds.STEPS -> e.kind == RuleKinds.STEPS && e.int("steps") >= t.int("steps", 8000)
        RuleKinds.WAKE -> e.kind == RuleKinds.WAKE
        RuleKinds.SLEEP -> e.kind == RuleKinds.SLEEP
        else -> false
    }

    /** True when [minute] is inside the window, which may run past midnight (22:00 to 06:00). */
    fun inWindow(minute: Int, from: Int, to: Int): Boolean = if (from <= to) minute in from..to else minute >= from || minute <= to

    /** Whether one condition holds. Null means "not a condition that says anything" (for example the match-any marker). */
    private fun holds(c: RulePart, minute: Int, dayOfWeek: Int, ctx: RuleContext): Boolean? = when (c.kind) {
        RuleKinds.DAYS -> RuleSummary.days(c).let { it.isEmpty() || dayOfWeek in it }
        RuleKinds.WINDOW -> inWindow(minute, c.int("from", 0), c.int("to", 1439))
        RuleKinds.CHARGING -> ctx.charging == (c.text("state") != "off")
        RuleKinds.AT_PLACE -> {
            val here = when {
                c.text("placeId").isNotEmpty() -> ctx.openPlaceId == c.text("placeId")
                c.text("category").isNotEmpty() -> ctx.openCategory == c.text("category")
                else -> ctx.openPlaceId.isNotEmpty()
            }
            here == (c.text("state") != "off")
        }
        RuleKinds.HABIT_NOT_DONE -> c.text("habitId") !in ctx.habitsDoneToday
        else -> null
    }

    /**
     * The first condition that does not hold, in words; null when they all hold. With a match-any part in the list, one
     * condition holding is enough.
     */
    fun failedCondition(
        conditions: List<RulePart>,
        atMs: Long,
        zone: ZoneId,
        ctx: RuleContext = RuleContext.EMPTY,
        words: (RulePart) -> String = { RuleSummary.condition(it) },
    ): String? {
        val local = Instant.ofEpochMilli(atMs).atZone(zone)
        val minute = local.hour * 60 + local.minute
        val checks = conditions.mapNotNull { c -> holds(c, minute, local.dayOfWeek.value, ctx)?.let { c to it } }
        if (checks.isEmpty()) return null
        val any = conditions.any { it.kind == RuleKinds.MATCH && it.text("mode") == "any" }
        if (any) return if (checks.any { it.second }) null else "None of these held: " + checks.joinToString(", ") { words(it.first) }
        return checks.firstOrNull { !it.second }?.let { "Only " + words(it.first) }
    }

    /**
     * What to do with [rule] for [event]. [recentRunsMs] are the times it ran in the last [BURST_WINDOW_MS]; [seen] is whether it
     * already ran for this very happening.
     */
    fun judge(
        rule: Rule,
        event: RuleEvent,
        recentRunsMs: List<Long>,
        seen: Boolean,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        ctx: RuleContext = RuleContext.EMPTY,
        words: (RulePart) -> String = { RuleSummary.condition(it) },
    ): Verdict {
        if (!rule.enabled || !triggerMatches(rule.trigger, event)) return Verdict(Outcome.NOT_MINE)
        // Things found by looking back (an app opened, a call, waking up) from before the rule was made are not news to it.
        if (event.kind in LOOK_BACK_KINDS && event.atMs < rule.createdMs) return Verdict(Outcome.NOT_MINE)
        if (seen) return Verdict(Outcome.SEEN)
        if (event.depth >= MAX_DEPTH) return Verdict(Outcome.LOOP, "Stopped: one rule set off another too many times in a row")
        failedCondition(rule.conditions, event.atMs, zone, ctx, words)?.let { return Verdict(Outcome.CONDITION, it) }
        val inBurst = recentRunsMs.count { it >= nowMs - BURST_WINDOW_MS }
        if (inBurst >= BURST_RUNS) return Verdict(Outcome.TOO_OFTEN, "Ran $inBurst times in 10 minutes, so it was switched off")
        val last = recentRunsMs.maxOrNull() ?: rule.lastRunMs.takeIf { it > 0 }
        if (last != null && rule.cooldownSec > 0 && nowMs - last < rule.cooldownSec * 1000L) {
            return Verdict(Outcome.COOLDOWN, "Ran less than ${rule.cooldownSec} s ago")
        }
        return Verdict(Outcome.RUN)
    }

    /** The key under which "this rule already ran for this happening" is remembered. */
    fun seenKey(rule: Rule, event: RuleEvent) = rule.id + "|" + event.key

    /** [keys] with [key] added at the end, keeping only the newest [cap]. */
    fun remember(keys: List<String>, key: String, cap: Int = 300): List<String> = (keys.filter { it != key } + key).takeLast(cap)
}

/** Turns what the phone already notices into events for the rules. Pure, so the rules can be tested without a phone. */
object RuleHooks {
    private fun placeParams(placeId: String, places: List<PlaceEntity>): Map<String, String> {
        val p = places.firstOrNull { it.id == placeId } ?: return mapOf("placeId" to placeId)
        return mapOf("placeId" to p.id, "placeName" to p.name, "category" to p.kind)
    }

    /** Arrived, left and "still here" events for visits that were just saved. Visits with no named place or marked ignored give none. */
    fun visitEvents(saves: List<PlaceVisitEntity>, places: List<PlaceEntity>, nowMs: Long): List<RuleEvent> = saves.filter { !it.ignored && it.placeId.isNotEmpty() }.flatMap { v ->
        val params = placeParams(v.placeId, places) + ("visitId" to v.id)
        if (v.ongoing) {
            listOf(
                RuleEvent(RuleKinds.ARRIVE, v.startMs, "arrive:${v.id}", params),
                RuleEvent(RuleKinds.STAY, nowMs, "stay:${v.id}", params + ("minutes" to ((nowMs - v.startMs) / 60_000L).coerceAtLeast(0).toString())),
            )
        } else {
            listOf(
                RuleEvent(RuleKinds.ARRIVE, v.startMs, "arrive:${v.id}", params),
                RuleEvent(RuleKinds.LEAVE, v.endMs, "leave:${v.id}", params + ("minutes" to ((v.endMs - v.startMs) / 60_000L).coerceAtLeast(0).toString())),
            )
        }
    }

    /** Charging started or stopped, from what [ChargeSessions.apply] decided. */
    fun chargeEvents(started: ChargeSessionEntity?, changed: List<ChargeSessionEntity>): List<RuleEvent> {
        val out = mutableListOf<RuleEvent>()
        changed.filter { !it.ongoing }.forEach { out += RuleEvent(RuleKinds.CHARGE_OFF, it.endMs, "chargeoff:${it.id}", mapOf("level" to it.endLevel.toString())) }
        if (started != null) out += RuleEvent(RuleKinds.CHARGE_ON, started.startMs, "chargeon:${started.id}", mapOf("level" to started.startLevel.toString()))
        return out
    }

    fun tripEvent(trip: TripEntity, places: List<PlaceEntity>): RuleEvent {
        val to = placeParams(trip.toPlaceId, places)
        return RuleEvent(
            RuleKinds.TRIP_END,
            trip.endMs,
            "tripend:${trip.id}",
            to + mapOf("mode" to trip.mode, "km" to String.format(java.util.Locale.US, "%.1f", trip.distanceM / 1000.0)),
        )
    }

    /**
     * "It is 07:30" events for every time-of-day rule whose time fell after [lastTickMs] and up to [nowMs]. The first check
     * (no earlier one) looks only 15 minutes back, so switching a rule on never fires something from hours ago.
     */
    fun dueTimes(rules: List<Rule>, lastTickMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<RuleEvent> {
        val from = window(lastTickMs, nowMs)
        val out = linkedMapOf<String, RuleEvent>()
        val days = listOf(Instant.ofEpochMilli(from).atZone(zone).toLocalDate(), Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()).distinct()
        rules.filter { it.enabled && it.trigger.kind == RuleKinds.TIME }.forEach { r ->
            val at = r.trigger.int("at", -1)
            if (at !in 0..1439) return@forEach
            days.forEach { day: LocalDate ->
                val moment = day.atStartOfDay(zone).plusMinutes(at.toLong()).toInstant().toEpochMilli()
                if (moment > from && moment <= nowMs) {
                    out["time:$day:$at"] = RuleEvent(RuleKinds.TIME, moment, "time:$day:$at", mapOf("at" to at.toString()))
                }
            }
        }
        return out.values.toList()
    }

    /** "Still here" events for the visits that are open now, for stay-longer-than rules. */
    fun stayEvents(open: List<PlaceVisitEntity>, places: List<PlaceEntity>, nowMs: Long): List<RuleEvent> =
        open.filter { !it.ignored && it.placeId.isNotEmpty() && it.ongoing }.map { v ->
            RuleEvent(
                RuleKinds.STAY,
                nowMs,
                "stay:${v.id}",
                placeParams(v.placeId, places) + mapOf("visitId" to v.id, "minutes" to ((nowMs - v.startMs) / 60_000L).coerceAtLeast(0).toString()),
            )
        }

    // ---- E8b: more things that happen --------------------------------------------------------------------------------------

    /** A trip started: the engine had none, and now has one. */
    fun tripStartEvent(trip: Trip, places: List<PlaceEntity>): RuleEvent =
        RuleEvent(RuleKinds.TRIP_START, trip.startMs, "tripstart:${trip.id}", placeParams(trip.fromPlaceId, places))

    /** One reading of the speed. The key changes every 10 minutes, so a long drive over the limit says so once in 10 minutes. */
    fun speedEvent(speedMs: Float, atMs: Long, onTrip: Boolean): RuleEvent? {
        if (speedMs < 0f) return null
        val kmh = Math.round(speedMs * 3.6f)
        return RuleEvent(RuleKinds.SPEED, atMs, "speed:" + (atMs / 600_000L), mapOf("kmh" to kmh.toString(), "onTrip" to if (onTrip) "1" else "0"))
    }

    /** A habit was ticked. One happening a day for each habit, so ticking again later in the day does not set a rule off again. */
    fun habitTicked(habitId: String, habitName: String, date: String, atMs: Long, depth: Int = 0): RuleEvent =
        RuleEvent(RuleKinds.HABIT_TICKED, atMs, "habit:$habitId:$date", mapOf("habitId" to habitId, "habitName" to habitName), depth)

    /** Wi-Fi came or went since the last look. [last] and [now] are "" for not connected, else the network name (or "on" when not shown). */
    fun wifiEvents(last: String?, now: String, atMs: Long): List<RuleEvent> {
        if (last == null || last == now) return emptyList()
        val out = mutableListOf<RuleEvent>()
        if (last.isNotEmpty()) out += RuleEvent(RuleKinds.WIFI_OFF, atMs, "wifioff:$atMs", mapOf("name" to last.takeIf { it != "on" }.orEmpty()))
        if (now.isNotEmpty()) out += RuleEvent(RuleKinds.WIFI_ON, atMs, "wifion:$atMs", mapOf("name" to now.takeIf { it != "on" }.orEmpty()))
        return out
    }

    /** Bluetooth devices that connected or disconnected since the last look. [names] gives the name of an address. */
    fun bluetoothEvents(last: Set<String>?, now: Set<String>, atMs: Long, names: (String) -> String): List<RuleEvent> {
        if (last == null) return emptyList()
        val out = mutableListOf<RuleEvent>()
        (last - now).sorted().forEach { out += RuleEvent(RuleKinds.BT_OFF, atMs, "btoff:$it:$atMs", mapOf("device" to it, "deviceName" to names(it))) }
        (now - last).sorted().forEach { out += RuleEvent(RuleKinds.BT_ON, atMs, "bton:$it:$atMs", mapOf("device" to it, "deviceName" to names(it))) }
        return out
    }

    /** Apps opened (a session that began) between [fromMs] and [toMs]. */
    fun appOpenEvents(sessions: List<UsageSessionEntity>, labels: (String) -> String, fromMs: Long, toMs: Long): List<RuleEvent> =
        sessions.filter { it.startMs > fromMs && it.startMs <= toMs }.map {
            RuleEvent(RuleKinds.APP_OPEN, it.startMs, "app:${it.pkg}:${it.startMs}", mapOf("pkg" to it.pkg, "label" to labels(it.pkg)))
        }

    /** Minutes used today for each app the rules ask about, offered as one happening a day for each app. */
    fun appUsedEvents(rules: List<Rule>, sessions: List<UsageSessionEntity>, labels: (String) -> String, date: String, nowMs: Long): List<RuleEvent> {
        val pkgs = rules.filter { it.enabled && it.trigger.kind == RuleKinds.APP_USED }.map { it.trigger.text("pkg") }.filter { it.isNotEmpty() }.toSet()
        return pkgs.mapNotNull { pkg ->
            val minutes = sessions.filter { it.pkg == pkg }.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) } / 60_000L
            if (minutes <= 0) null else RuleEvent(RuleKinds.APP_USED, nowMs, "appused:$pkg:$date", mapOf("pkg" to pkg, "label" to labels(pkg), "minutes" to minutes.toString()))
        }
    }

    /** Calls that began between [fromMs] and [toMs]. Type words: "in", "out", "missed". Other kinds of call are left out. */
    fun callEvents(calls: List<CallEntity>, fromMs: Long, toMs: Long): List<RuleEvent> =
        calls.filter { it.startMs > fromMs && it.startMs <= toMs }.mapNotNull { c ->
            val type = when (c.type) {
                CallStats.INCOMING -> "in"
                CallStats.OUTGOING -> "out"
                CallStats.MISSED -> "missed"
                else -> return@mapNotNull null
            }
            RuleEvent(RuleKinds.CALL, c.startMs, "call:${c.id}", mapOf("type" to type, "name" to c.name))
        }

    /** The steps of today, one happening a day. */
    fun stepsEvent(steps: Int, date: String, nowMs: Long): RuleEvent? =
        if (steps <= 0) null else RuleEvent(RuleKinds.STEPS, nowMs, "steps:$date", mapOf("steps" to steps.toString()))

    /** Woke up or fell asleep, from nights whose end or start fell between [fromMs] and [toMs]. */
    fun sleepEvents(nights: List<SleepNightEntity>, fromMs: Long, toMs: Long): List<RuleEvent> {
        val out = mutableListOf<RuleEvent>()
        nights.filter { it.source != "skipped" }.forEach { n ->
            if (n.endMs > fromMs && n.endMs <= toMs) out += RuleEvent(RuleKinds.WAKE, n.endMs, "wake:${n.date}:${n.endMs}")
            if (n.startMs > fromMs && n.startMs <= toMs) out += RuleEvent(RuleKinds.SLEEP, n.startMs, "sleep:${n.date}:${n.startMs}")
        }
        return out
    }

    /** How far a check looks back for things that were saved a little late (app sessions, calls, sleep): an hour, 15 minutes the first time. */
    fun lookBack(lastTickMs: Long, nowMs: Long): Long = if (lastTickMs <= 0L) nowMs - 15 * 60_000L else nowMs - 60 * 60_000L

    /** The window of a check for things that happen at a set time: since the last one, never more than an hour, 15 minutes on the very first. */
    fun window(lastTickMs: Long, nowMs: Long): Long = if (lastTickMs <= 0L) nowMs - 15 * 60_000L else maxOf(lastTickMs, nowMs - 60 * 60_000L)
}
