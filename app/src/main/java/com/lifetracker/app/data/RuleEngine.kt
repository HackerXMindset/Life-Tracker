package com.lifetracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
        else -> false
    }

    /** True when [minute] is inside the window, which may run past midnight (22:00 to 06:00). */
    fun inWindow(minute: Int, from: Int, to: Int): Boolean = if (from <= to) minute in from..to else minute >= from || minute <= to

    /** The first condition that does not hold, in words; null when they all hold. */
    fun failedCondition(conditions: List<RulePart>, atMs: Long, zone: ZoneId): String? {
        val local = Instant.ofEpochMilli(atMs).atZone(zone)
        val minute = local.hour * 60 + local.minute
        conditions.forEach { c ->
            when (c.kind) {
                RuleKinds.DAYS -> {
                    val days = RuleSummary.days(c)
                    if (days.isNotEmpty() && local.dayOfWeek.value !in days) return "Only " + RuleSummary.condition(c)
                }
                RuleKinds.WINDOW -> if (!inWindow(minute, c.int("from", 0), c.int("to", 1439))) return "Only " + RuleSummary.condition(c)
            }
        }
        return null
    }

    /**
     * What to do with [rule] for [event]. [recentRunsMs] are the times it ran in the last [BURST_WINDOW_MS]; [seen] is whether it
     * already ran for this very happening.
     */
    fun judge(rule: Rule, event: RuleEvent, recentRunsMs: List<Long>, seen: Boolean, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Verdict {
        if (!rule.enabled || !triggerMatches(rule.trigger, event)) return Verdict(Outcome.NOT_MINE)
        if (seen) return Verdict(Outcome.SEEN)
        if (event.depth >= MAX_DEPTH) return Verdict(Outcome.LOOP, "Stopped: one rule set off another too many times in a row")
        failedCondition(rule.conditions, event.atMs, zone)?.let { return Verdict(Outcome.CONDITION, it) }
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
        val from = if (lastTickMs <= 0L) nowMs - 15 * 60_000L else maxOf(lastTickMs, nowMs - 60 * 60_000L)
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
}
