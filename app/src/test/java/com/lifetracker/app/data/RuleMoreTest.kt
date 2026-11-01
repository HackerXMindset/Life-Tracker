package com.lifetracker.app.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The triggers, conditions and hooks added in E8b. */
class RuleMoreTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli() // 2 Nov 2026 is a Monday

    private fun ev(kind: String, vararg params: Pair<String, String>, key: String = "k", ms: Long = at(2, 9)) = RuleEvent(kind, ms, key, mapOf(*params))

    private fun rule(trigger: RulePart, conditions: List<RulePart> = emptyList(), created: Long = 0L) =
        Rule("r1", "Test", true, trigger, conditions, listOf(RulePart(RuleKinds.NOTIFY).with("title", "x")), 0, created, 0L)

    @Test
    fun speedOverNeedsTheSpeedAndUnderNeedsATrip() {
        val over = RulePart(RuleKinds.SPEED_OVER).with("kmh", "60")
        val under = RulePart(RuleKinds.SPEED_UNDER).with("kmh", "10")
        assertFalse(RuleEngine.triggerMatches(over, ev(RuleKinds.SPEED, "kmh" to "59", "onTrip" to "1")))
        assertTrue(RuleEngine.triggerMatches(over, ev(RuleKinds.SPEED, "kmh" to "60", "onTrip" to "0")))
        assertTrue(RuleEngine.triggerMatches(under, ev(RuleKinds.SPEED, "kmh" to "4", "onTrip" to "1")))
        assertFalse(RuleEngine.triggerMatches(under, ev(RuleKinds.SPEED, "kmh" to "4", "onTrip" to "0")))
        assertFalse(RuleEngine.triggerMatches(under, ev(RuleKinds.SPEED, "kmh" to "30", "onTrip" to "1")))
    }

    @Test
    fun speedEventsAreOnePerTenMinutesAndNeedASpeed() {
        assertNull(RuleHooks.speedEvent(-1f, at(2, 9), true))
        val a = RuleHooks.speedEvent(20f, at(2, 9, 1), true)!!
        val b = RuleHooks.speedEvent(25f, at(2, 9, 9), true)!!
        val c = RuleHooks.speedEvent(25f, at(2, 9, 11), true)!!
        assertEquals("72", a.text("kmh"))
        assertEquals(a.key, b.key)
        assertTrue(a.key != c.key)
    }

    @Test
    fun habitTickedMatchesAnyOrOneHabit() {
        val any = RulePart(RuleKinds.HABIT_TICKED)
        val one = RulePart(RuleKinds.HABIT_TICKED).with("habitId", "gym")
        val e = RuleHooks.habitTicked("gym", "Gym", "2026-11-02", at(2, 9))
        assertTrue(RuleEngine.triggerMatches(any, e))
        assertTrue(RuleEngine.triggerMatches(one, e))
        assertFalse(RuleEngine.triggerMatches(one, RuleHooks.habitTicked("read", "Read", "2026-11-02", at(2, 9))))
        // The same habit on the same day is the same happening.
        assertEquals(e.key, RuleHooks.habitTicked("gym", "Gym", "2026-11-02", at(2, 20)).key)
    }

    @Test
    fun wifiEventsCompareWithTheLastLook() {
        assertTrue(RuleHooks.wifiEvents(null, "Home", 1L).isEmpty())
        assertTrue(RuleHooks.wifiEvents("Home", "Home", 1L).isEmpty())
        assertEquals(listOf(RuleKinds.WIFI_ON), RuleHooks.wifiEvents("", "Home", 1L).map { it.kind })
        assertEquals(listOf(RuleKinds.WIFI_OFF), RuleHooks.wifiEvents("Home", "", 1L).map { it.kind })
        assertEquals(listOf(RuleKinds.WIFI_OFF, RuleKinds.WIFI_ON), RuleHooks.wifiEvents("Home", "Work", 1L).map { it.kind })
        val named = RulePart(RuleKinds.WIFI_ON).with("name", "home")
        assertTrue(RuleEngine.triggerMatches(named, RuleHooks.wifiEvents("", "Home", 1L).first()))
        assertFalse(RuleEngine.triggerMatches(named, RuleHooks.wifiEvents("", "Work", 1L).first()))
        assertEquals("", RuleHooks.wifiEvents("", "on", 1L).first().text("name"))
    }

    @Test
    fun bluetoothEventsNameTheDevicesThatChanged() {
        assertTrue(RuleHooks.bluetoothEvents(null, setOf("a"), 1L) { "x" }.isEmpty())
        val out = RuleHooks.bluetoothEvents(setOf("a", "b"), setOf("b", "c"), 1L) { "dev-$it" }
        assertEquals(listOf(RuleKinds.BT_OFF, RuleKinds.BT_ON), out.map { it.kind })
        assertEquals("dev-a", out[0].text("deviceName"))
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.BT_ON).with("device", "c"), out[1]))
        assertFalse(RuleEngine.triggerMatches(RulePart(RuleKinds.BT_ON).with("device", "z"), out[1]))
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.BT_OFF), out[0]))
    }

    @Test
    fun appsOpenedAreThoseThatBeganInTheWindow() {
        val sessions = listOf(
            UsageSessionEntity("1", "chat", at(2, 9, 1), at(2, 9, 5)),
            UsageSessionEntity("2", "maps", at(2, 9, 6), at(2, 9, 8)),
            UsageSessionEntity("3", "chat", at(2, 8, 0), at(2, 8, 10)),
        )
        val out = RuleHooks.appOpenEvents(sessions, { it.uppercase() }, at(2, 9), at(2, 9, 10))
        assertEquals(listOf("chat", "maps"), out.map { it.text("pkg") })
        assertEquals("CHAT", out[0].text("label"))
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.APP_OPEN).with("pkg", "maps"), out[1]))
        assertFalse(RuleEngine.triggerMatches(RulePart(RuleKinds.APP_OPEN).with("pkg", "maps"), out[0]))
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.APP_OPEN), out[0]))
    }

    @Test
    fun appUsedAddsUpTheDayOnceForEachAppARuleAsksAbout() {
        val sessions = listOf(
            UsageSessionEntity("1", "chat", at(2, 9, 0), at(2, 9, 40)),
            UsageSessionEntity("2", "chat", at(2, 12, 0), at(2, 12, 30)),
            UsageSessionEntity("3", "maps", at(2, 13, 0), at(2, 13, 10)),
        )
        val rules = listOf(rule(RulePart(RuleKinds.APP_USED).with("pkg", "chat").with("minutes", "60")))
        val out = RuleHooks.appUsedEvents(rules, sessions, { it }, "2026-11-02", at(2, 14))
        assertEquals(1, out.size)
        assertEquals("70", out[0].text("minutes"))
        assertEquals("appused:chat:2026-11-02", out[0].key)
        assertTrue(RuleEngine.triggerMatches(rules[0].trigger, out[0]))
        assertFalse(RuleEngine.triggerMatches(RulePart(RuleKinds.APP_USED).with("pkg", "chat").with("minutes", "90"), out[0]))
    }

    @Test
    fun callsAreSortedIntoInOutAndMissed() {
        val calls = listOf(
            CallEntity("a", "1", "Amma", CallStats.INCOMING, at(2, 9, 1), 60),
            CallEntity("b", "2", "Bob", CallStats.OUTGOING, at(2, 9, 2), 30),
            CallEntity("c", "3", "Cy", CallStats.MISSED, at(2, 9, 3), 0),
            CallEntity("d", "4", "Old", CallStats.MISSED, at(2, 7, 0), 0),
        )
        val out = RuleHooks.callEvents(calls, at(2, 9), at(2, 10))
        assertEquals(listOf("in", "out", "missed"), out.map { it.text("type") })
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.CALL).with("type", "missed"), out[2]))
        assertFalse(RuleEngine.triggerMatches(RulePart(RuleKinds.CALL).with("type", "missed"), out[0]))
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.CALL), out[1]))
    }

    @Test
    fun stepsAreOneHappeningADay() {
        assertNull(RuleHooks.stepsEvent(0, "2026-11-02", 1L))
        val e = RuleHooks.stepsEvent(8100, "2026-11-02", 1L)!!
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.STEPS).with("steps", "8000"), e))
        assertFalse(RuleEngine.triggerMatches(RulePart(RuleKinds.STEPS).with("steps", "10000"), e))
        assertEquals("steps:2026-11-02", e.key)
    }

    @Test
    fun wakingAndSleepingComeFromTheNightsEdges() {
        val night = SleepNightEntity("2026-11-02", at(1, 23, 30), at(2, 7, 0), "estimate")
        val skipped = SleepNightEntity("2026-11-01", at(1, 1, 0), at(1, 8, 0), "skipped")
        val out = RuleHooks.sleepEvents(listOf(night, skipped), at(1, 23), at(2, 7, 30))
        assertEquals(listOf(RuleKinds.SLEEP, RuleKinds.WAKE), out.map { it.kind }.sortedBy { if (it == RuleKinds.SLEEP) 0 else 1 })
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.WAKE), out.first { it.kind == RuleKinds.WAKE }))
        assertFalse(RuleEngine.triggerMatches(RulePart(RuleKinds.WAKE), out.first { it.kind == RuleKinds.SLEEP }))
        assertTrue(RuleHooks.sleepEvents(listOf(night), at(2, 8), at(2, 9)).isEmpty())
    }

    @Test
    fun somethingFoundByLookingBackFromBeforeTheRuleWasMadeIsNotNewsToIt() {
        val e = ev(RuleKinds.CALL, "type" to "in", ms = at(2, 9))
        val older = rule(RulePart(RuleKinds.CALL), created = at(2, 8))
        val newer = rule(RulePart(RuleKinds.CALL), created = at(2, 10))
        assertEquals(RuleEngine.Outcome.RUN, RuleEngine.judge(older, e, emptyList(), false, at(2, 9, 10), zone).outcome)
        assertEquals(RuleEngine.Outcome.NOT_MINE, RuleEngine.judge(newer, e, emptyList(), false, at(2, 10, 10), zone).outcome)
    }

    @Test
    fun tripStartNamesWhereItBeganFrom() {
        val places = listOf(PlaceEntity("home", "Home", "home-kind", 0.0, 0.0, 50, false, 0L))
        val e = RuleHooks.tripStartEvent(Trip("t1", at(2, 9), "home", at(2, 9)), places)
        assertEquals("Home", e.text("placeName"))
        assertEquals("tripstart:t1", e.key)
        assertTrue(RuleEngine.triggerMatches(RulePart(RuleKinds.TRIP_START), e))
    }

    // ---- conditions ------------------------------------------------------------------------------------------------------

    private fun fails(conditions: List<RulePart>, ctx: RuleContext) = RuleEngine.failedCondition(conditions, at(2, 9), zone, ctx)

    @Test
    fun chargingConditionFollowsThePhone() {
        val on = RulePart(RuleKinds.CHARGING).with("state", "on")
        val off = RulePart(RuleKinds.CHARGING).with("state", "off")
        assertNull(fails(listOf(on), RuleContext(charging = true)))
        assertNotNull(fails(listOf(on), RuleContext(charging = false)))
        assertNull(fails(listOf(off), RuleContext(charging = false)))
        assertNotNull(fails(listOf(off), RuleContext(charging = true)))
    }

    @Test
    fun atPlaceConditionUnderstandsOnePlaceAKindAndNot() {
        val gym = RulePart(RuleKinds.AT_PLACE).with("placeId", "gym")
        val kind = RulePart(RuleKinds.AT_PLACE).with("category", "study")
        val notHome = RulePart(RuleKinds.AT_PLACE).with("placeId", "home").with("state", "off")
        val anywhere = RulePart(RuleKinds.AT_PLACE)
        assertNull(fails(listOf(gym), RuleContext(openPlaceId = "gym")))
        assertNotNull(fails(listOf(gym), RuleContext(openPlaceId = "home")))
        assertNull(fails(listOf(kind), RuleContext(openPlaceId = "lib", openCategory = "study")))
        assertNotNull(fails(listOf(kind), RuleContext(openPlaceId = "gym", openCategory = "fit")))
        assertNull(fails(listOf(notHome), RuleContext(openPlaceId = "gym")))
        assertNotNull(fails(listOf(notHome), RuleContext(openPlaceId = "home")))
        assertNull(fails(listOf(anywhere), RuleContext(openPlaceId = "gym")))
        assertNotNull(fails(listOf(anywhere), RuleContext()))
    }

    @Test
    fun habitNotDoneYetNeedsTheHabitToBeOpenToday() {
        val c = RulePart(RuleKinds.HABIT_NOT_DONE).with("habitId", "gym")
        assertNull(fails(listOf(c), RuleContext(habitsDoneToday = setOf("read"))))
        assertNotNull(fails(listOf(c), RuleContext(habitsDoneToday = setOf("gym"))))
    }

    @Test
    fun matchAnyNeedsOnlyOneConditionToHold() {
        val charging = RulePart(RuleKinds.CHARGING).with("state", "on")
        val gym = RulePart(RuleKinds.AT_PLACE).with("placeId", "gym")
        val any = RulePart(RuleKinds.MATCH).with("mode", "any")
        val ctx = RuleContext(charging = true, openPlaceId = "home")
        assertNotNull(fails(listOf(charging, gym), ctx))
        assertNull(fails(listOf(charging, gym, any), ctx))
        assertNotNull(fails(listOf(charging, gym, any), RuleContext(charging = false, openPlaceId = "home")))
        // Only the marker: nothing to check.
        assertNull(fails(listOf(any), RuleContext()))
    }

    @Test
    fun conditionsReachTheVerdictAndTheLog() {
        val r = rule(RulePart(RuleKinds.WIFI_ON), listOf(RulePart(RuleKinds.CHARGING).with("state", "on")))
        val e = ev(RuleKinds.WIFI_ON, "name" to "Home")
        val no = RuleEngine.judge(r, e, emptyList(), false, e.atMs, zone, RuleContext(charging = false))
        assertEquals(RuleEngine.Outcome.CONDITION, no.outcome)
        assertTrue(no.reason.contains("charging"))
        assertEquals(RuleEngine.Outcome.RUN, RuleEngine.judge(r, e, emptyList(), false, e.atMs, zone, RuleContext(charging = true)).outcome)
    }

    // ---- words and the saved form ----------------------------------------------------------------------------------------

    @Test
    fun newPartsKeepTheirSettingsThroughTheSavedForm() {
        val parts = listOf(
            RulePart(RuleKinds.START_FOCUS).with("minutes", "25").with("habitId", "study"),
            RulePart(RuleKinds.MONEY).with("amount", "12.5").with("kind", "0").with("name", "Bus"),
            RulePart(RuleKinds.RUN_RULE).with("ruleId", "r9"),
            RulePart(RuleKinds.MATCH).with("mode", "any"),
        )
        val back = RuleCodec.listFromJson(RuleCodec.listToJson(parts))
        assertEquals(parts.map { it.kind }, back.map { it.kind })
        assertEquals("12.5", back[1].text("amount"))
        assertEquals("any", back[3].text("mode"))
    }

    @Test
    fun everyTriggerConditionAndActionHasWords() {
        RuleKinds.TRIGGERS.forEach { (k, label) ->
            assertTrue(label.isNotEmpty())
            val text = RuleSummary.trigger(RulePart(k), { "P" }, { "C" }, { "H" })
            assertTrue("$k has words", text.isNotEmpty() && text != k)
        }
        RuleKinds.CONDITIONS.forEach { (k, _) ->
            val text = RuleSummary.condition(RulePart(k).with("habitId", "h"), { "P" }, { "C" }, { "H" })
            assertTrue("$k has words", text.isNotEmpty() && text != k)
        }
        RuleKinds.ACTIONS.forEach { (k, _) ->
            val text = RuleSummary.action(RulePart(k), { "H" }, { "A" }, { "R" })
            assertTrue("$k has words", text.isNotEmpty() && text != k)
        }
        assertEquals("I reach 5000 steps today", RuleSummary.trigger(RulePart(RuleKinds.STEPS).with("steps", "5000"), { "" }, { "" }))
        assertEquals("while charging", RuleSummary.condition(RulePart(RuleKinds.CHARGING)))
    }

    @Test
    fun theTickedKindsAreTheOnesFoundByLooking() {
        assertTrue(RuleKinds.WIFI_ON in RuleKinds.TICKED)
        assertTrue(RuleKinds.TIME in RuleKinds.TICKED)
        assertFalse(RuleKinds.ARRIVE in RuleKinds.TICKED)
        assertFalse(RuleKinds.HABIT_TICKED in RuleKinds.TICKED)
        // Each trigger offered in the editor is either looked for by the 15-minute check or posted the moment it happens.
        assertEquals(RuleKinds.TRIGGERS.size, RuleKinds.TRIGGERS.map { it.first }.toSet().size)
    }

    @Test
    fun lookBackIsAnHourExceptTheFirstTime() {
        assertEquals(at(2, 9, 45), RuleHooks.lookBack(0L, at(2, 10)))
        assertEquals(at(2, 9), RuleHooks.lookBack(at(2, 9, 50), at(2, 10)))
        assertTrue(RuleText.fill("{habit} {kmh}", ev(RuleKinds.SPEED, "habitName" to "Gym", "kmh" to "70"), zone) == "Gym 70")
    }
}
