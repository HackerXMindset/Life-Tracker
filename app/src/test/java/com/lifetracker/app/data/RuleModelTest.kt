package com.lifetracker.app.data

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleModelTest {
    private val rule = Rule(
        id = "r1",
        name = "Gym \"done\"",
        enabled = true,
        trigger = RulePart(RuleKinds.STAY).with("placeId", "gym").with("minutes", "30"),
        conditions = listOf(RulePart(RuleKinds.DAYS).with("days", "1,3,5"), RulePart(RuleKinds.WINDOW).with("from", "360").with("to", "600")),
        actions = listOf(
            RulePart(RuleKinds.TICK_HABIT).with("habitId", "h1"),
            RulePart(RuleKinds.NOTIFY).with("title", "Welcome to {place}").with("text", "line one\nline \"two\" ünï"),
        ),
        cooldownSec = 120,
        createdMs = 5L,
        lastRunMs = 9L,
        runs = 3,
    )

    @Test
    fun aRuleSurvivesBeingSavedAndRead() {
        val back = RuleCodec.toRule(RuleCodec.toEntity(rule, sortOrder = 4))
        assertEquals(rule.id, back.id)
        assertEquals(rule.name, back.name)
        assertEquals(rule.trigger.kind, back.trigger.kind)
        assertEquals(rule.trigger.params, back.trigger.params)
        assertEquals(rule.conditions.map { it.kind to it.params }, back.conditions.map { it.kind to it.params })
        assertEquals(rule.actions.map { it.kind to it.params }, back.actions.map { it.kind to it.params })
        assertEquals(120, back.cooldownSec)
        assertEquals(3, back.runs)
        assertEquals(4, RuleCodec.toEntity(rule, 4).sortOrder)
    }

    @Test
    fun brokenTextNeverCrashes() {
        assertEquals("", RuleCodec.partFromJson("not json").kind)
        assertTrue(RuleCodec.listFromJson("nope").isEmpty())
        assertTrue(RuleCodec.listFromJson("[]").isEmpty())
    }

    @Test
    fun theSameRuleAlwaysMakesTheSameText() {
        val a = RuleCodec.partToJson(RulePart("x").with("b", "1").with("a", "2"))
        val b = RuleCodec.partToJson(RulePart("x").with("a", "2").with("b", "1"))
        assertEquals(a, b)
    }

    @Test
    fun partsReadNumbersWithDefaults() {
        val p = RulePart("x").with("n", "12").with("bad", "zz")
        assertEquals(12, p.int("n"))
        assertEquals(7, p.int("bad", 7))
        assertEquals(0, p.int("missing"))
        assertEquals("d", p.text("missing", "d"))
        assertEquals(mapOf("n" to "12"), p.with("bad", "").without("bad").params - "bad")
    }

    @Test
    fun theWordsDescribeARuleInPlainEnglish() {
        val place = { id: String -> if (id == "gym") "Gym" else "?" }
        val kind = { id: String -> "Study" }
        assertEquals("I have been at Gym for 30 min", RuleSummary.trigger(rule.trigger, place, kind))
        assertEquals("I arrive at any Study place", RuleSummary.trigger(RulePart(RuleKinds.ARRIVE).with("category", "s"), place, kind))
        assertEquals("I leave any place you named", RuleSummary.trigger(RulePart(RuleKinds.LEAVE), place, kind))
        assertEquals("It is 07:30", RuleSummary.trigger(RulePart(RuleKinds.TIME).with("at", "450"), place, kind))
        assertEquals("on Mon, Wed, Fri", RuleSummary.condition(rule.conditions[0]))
        assertEquals("between 06:00 and 10:00", RuleSummary.condition(rule.conditions[1]))
        assertEquals("on weekdays", RuleSummary.condition(RulePart(RuleKinds.DAYS).with("days", "1,2,3,4,5")))
        assertEquals("on weekends", RuleSummary.condition(RulePart(RuleKinds.DAYS).with("days", "6,7")))
        assertEquals("on every day", RuleSummary.condition(RulePart(RuleKinds.DAYS).with("days", "1,2,3,4,5,6,7")))
        assertEquals("tick Reading", RuleSummary.action(rule.actions[0], { "Reading" }, { "?" }))
        assertEquals("notify \"Welcome to {place}\"", RuleSummary.action(rule.actions[1], { "?" }, { "?" }))
        assertEquals("add \"Gym\" to the Timeline as Study", RuleSummary.action(RulePart(RuleKinds.ENTRY).with("title", "Gym").with("category", "s"), { "?" }, { "Study" }))
    }

    @Test
    fun messagesGetTheirBlanksFilled() {
        val e = RuleEvent(
            RuleKinds.ARRIVE, 1_793_613_000_000L, "k", // 2 Nov 2026 09:50 UTC
            mapOf("placeName" to "Home", "minutes" to "45", "mode" to "car", "km" to "12.3"),
        )
        assertEquals("Welcome to Home at 09:50", RuleText.fill("Welcome to {place} at {time}", e, ZoneOffset.UTC))
        assertEquals("45 min by car, 12.3 km", RuleText.fill("{minutes} min by {mode}, {km} km", e, ZoneOffset.UTC))
        assertEquals("nothing to fill", RuleText.fill("nothing to fill", e, ZoneOffset.UTC))
        assertEquals("{unknown}", RuleText.fill("{unknown}", e, ZoneOffset.UTC))
    }

    @Test
    fun everyKindHasAWordInTheEditorLists() {
        listOf(
            RuleKinds.ARRIVE, RuleKinds.LEAVE, RuleKinds.STAY, RuleKinds.CHARGE_ON, RuleKinds.CHARGE_OFF, RuleKinds.TRIP_END, RuleKinds.TIME, RuleKinds.BUTTON,
        ).forEach { assertTrue(it, RuleKinds.triggerLabel(it) != it) }
        listOf(RuleKinds.DAYS, RuleKinds.WINDOW).forEach { assertTrue(it, RuleKinds.conditionLabel(it) != it) }
        listOf(RuleKinds.TICK_HABIT, RuleKinds.ENTRY, RuleKinds.NOTIFY).forEach { assertTrue(it, RuleKinds.actionLabel(it) != it) }
    }
}
