package com.lifetracker.app.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli() // 2 Nov 2026 is a Monday

    private fun rule(
        trigger: RulePart,
        conditions: List<RulePart> = emptyList(),
        cooldown: Int = 60,
        enabled: Boolean = true,
        lastRun: Long = 0L,
    ) = Rule("r1", "Test", enabled, trigger, conditions, listOf(RulePart(RuleKinds.NOTIFY).with("title", "x")), cooldown, 0L, lastRun)

    private fun arrive(placeId: String = "home", category: String = "home-kind", ms: Long = at(2, 9)) =
        RuleEvent(RuleKinds.ARRIVE, ms, "arrive:v1", mapOf("placeId" to placeId, "placeName" to "Home", "category" to category))

    private fun verdict(r: Rule, e: RuleEvent, runs: List<Long> = emptyList(), seen: Boolean = false, now: Long = e.atMs) =
        RuleEngine.judge(r, e, runs, seen, now, zone).outcome

    @Test
    fun aPlaceRuleMatchesTheOnePlaceTheKindOrAnyNamedPlace() {
        val one = RulePart(RuleKinds.ARRIVE).with("placeId", "home")
        val kind = RulePart(RuleKinds.ARRIVE).with("category", "home-kind")
        val any = RulePart(RuleKinds.ARRIVE)
        assertTrue(RuleEngine.triggerMatches(one, arrive()))
        assertFalse(RuleEngine.triggerMatches(one, arrive(placeId = "gym")))
        assertTrue(RuleEngine.triggerMatches(kind, arrive(placeId = "x")))
        assertFalse(RuleEngine.triggerMatches(kind, arrive(category = "gym-kind")))
        assertTrue(RuleEngine.triggerMatches(any, arrive(placeId = "whatever")))
        assertFalse(RuleEngine.triggerMatches(any, RuleEvent(RuleKinds.ARRIVE, 0, "k", emptyMap())))
    }

    @Test
    fun arrivingIsNotLeaving() {
        val leave = RulePart(RuleKinds.LEAVE)
        assertFalse(RuleEngine.triggerMatches(leave, arrive()))
        assertTrue(RuleEngine.triggerMatches(leave, RuleEvent(RuleKinds.LEAVE, 0, "k", mapOf("placeId" to "home"))))
    }

    @Test
    fun stayNeedsTheMinutesToHaveBeenReached() {
        val stay = RulePart(RuleKinds.STAY).with("placeId", "home").with("minutes", "30")
        fun e(min: Int) = RuleEvent(RuleKinds.STAY, 0, "s", mapOf("placeId" to "home", "minutes" to min.toString()))
        assertFalse(RuleEngine.triggerMatches(stay, e(29)))
        assertTrue(RuleEngine.triggerMatches(stay, e(30)))
        assertTrue(RuleEngine.triggerMatches(stay, e(200)))
    }

    @Test
    fun timeOfDayMatchesOnlyItsOwnMinute() {
        val t = RulePart(RuleKinds.TIME).with("at", "450")
        assertTrue(RuleEngine.triggerMatches(t, RuleEvent(RuleKinds.TIME, 0, "k", mapOf("at" to "450"))))
        assertFalse(RuleEngine.triggerMatches(t, RuleEvent(RuleKinds.TIME, 0, "k", mapOf("at" to "451"))))
    }

    @Test
    fun aTripRuleCanBeNarrowedToOneWayOfTravelling() {
        val any = RulePart(RuleKinds.TRIP_END)
        val walk = RulePart(RuleKinds.TRIP_END).with("mode", "walk")
        val e = RuleEvent(RuleKinds.TRIP_END, 0, "t", mapOf("mode" to "car"))
        assertTrue(RuleEngine.triggerMatches(any, e))
        assertFalse(RuleEngine.triggerMatches(walk, e))
    }

    @Test
    fun daysAndWindowsHoldBack() {
        val monday = at(2, 9)
        val saturday = at(7, 9)
        val weekdays = RulePart(RuleKinds.DAYS).with("days", "1,2,3,4,5")
        assertEquals(null, RuleEngine.failedCondition(listOf(weekdays), monday, zone))
        assertEquals("Only on weekdays", RuleEngine.failedCondition(listOf(weekdays), saturday, zone))
        val office = RulePart(RuleKinds.WINDOW).with("from", "540").with("to", "1080")
        assertEquals(null, RuleEngine.failedCondition(listOf(office), at(2, 9), zone))
        assertEquals(null, RuleEngine.failedCondition(listOf(office), at(2, 18), zone))
        assertEquals("Only between 09:00 and 18:00", RuleEngine.failedCondition(listOf(office), at(2, 8, 59), zone))
    }

    @Test
    fun aWindowCanRunPastMidnight() {
        assertTrue(RuleEngine.inWindow(23 * 60, 22 * 60, 6 * 60))
        assertTrue(RuleEngine.inWindow(2 * 60, 22 * 60, 6 * 60))
        assertFalse(RuleEngine.inWindow(12 * 60, 22 * 60, 6 * 60))
        assertTrue(RuleEngine.inWindow(600, 600, 600))
    }

    @Test
    fun theVerdictFollowsTheOrderOfTheReasons() {
        val r = rule(RulePart(RuleKinds.ARRIVE).with("placeId", "home"), listOf(RulePart(RuleKinds.DAYS).with("days", "1")))
        val e = arrive()
        assertEquals(RuleEngine.Outcome.RUN, verdict(r, e))
        assertEquals(RuleEngine.Outcome.NOT_MINE, verdict(r, arrive(placeId = "gym")))
        assertEquals(RuleEngine.Outcome.NOT_MINE, verdict(rule(r.trigger, enabled = false), e))
        assertEquals(RuleEngine.Outcome.SEEN, verdict(r, e, seen = true))
        assertEquals(RuleEngine.Outcome.CONDITION, verdict(r, arrive(ms = at(3, 9))))
    }

    @Test
    fun aRuleWaitsOutItsCooldown() {
        val r = rule(RulePart(RuleKinds.ARRIVE), cooldown = 60)
        val e = arrive()
        assertEquals(RuleEngine.Outcome.COOLDOWN, verdict(r, e, runs = listOf(e.atMs - 30_000L)))
        assertEquals(RuleEngine.Outcome.RUN, verdict(r, e, runs = listOf(e.atMs - 61_000L)))
        assertEquals(RuleEngine.Outcome.COOLDOWN, verdict(rule(RulePart(RuleKinds.ARRIVE), cooldown = 60, lastRun = e.atMs - 10_000L), e))
        assertEquals(RuleEngine.Outcome.RUN, verdict(rule(RulePart(RuleKinds.ARRIVE), cooldown = 0), e, runs = listOf(e.atMs - 1L)))
    }

    @Test
    fun aRuleThatRunsTooOftenIsSwitchedOff() {
        val r = rule(RulePart(RuleKinds.ARRIVE), cooldown = 0)
        val e = arrive()
        val nine = (1..9).map { e.atMs - it * 30_000L }
        val ten = (1..10).map { e.atMs - it * 30_000L }
        assertEquals(RuleEngine.Outcome.RUN, verdict(r, e, runs = nine))
        assertEquals(RuleEngine.Outcome.TOO_OFTEN, verdict(r, e, runs = ten))
        // Runs older than the window do not count.
        val old = (1..10).map { e.atMs - 11 * 60_000L - it * 1000L }
        assertEquals(RuleEngine.Outcome.RUN, verdict(r, e, runs = old))
    }

    @Test
    fun oneRuleSettingOffAnotherStopsAfterThreeSteps() {
        val r = rule(RulePart(RuleKinds.ARRIVE))
        val deep = RuleEvent(RuleKinds.ARRIVE, 0, "k", mapOf("placeId" to "home"), depth = RuleEngine.MAX_DEPTH)
        assertEquals(RuleEngine.Outcome.LOOP, verdict(r, deep))
        assertEquals(RuleEngine.Outcome.RUN, verdict(r, RuleEvent(RuleKinds.ARRIVE, 0, "k", mapOf("placeId" to "home"), depth = RuleEngine.MAX_DEPTH - 1)))
    }

    @Test
    fun rememberedKeysAreKeptInOrderAndCapped() {
        assertEquals(listOf("a", "b", "c"), RuleEngine.remember(listOf("a", "b"), "c"))
        assertEquals(listOf("b", "a"), RuleEngine.remember(listOf("a", "b"), "a"))
        assertEquals(listOf("3", "4"), RuleEngine.remember(listOf("1", "2", "3"), "4", cap = 2))
        assertEquals("r1|arrive:v1", RuleEngine.seenKey(rule(RulePart(RuleKinds.ARRIVE)), arrive()))
    }

    // ---- what the phone notices, turned into events ----

    private val home = PlaceEntity("home", "Home", "home-kind", 0.0, 0.0, 100, false, 0L)

    private fun visit(id: String, placeId: String, start: Long, end: Long, ongoing: Boolean, ignored: Boolean = false) =
        PlaceVisitEntity(id, placeId, start, end, 0.0, 0.0, "stop", ongoing, ignored)

    @Test
    fun anOngoingVisitGivesAnArrivalAndAStay() {
        val ev = RuleHooks.visitEvents(listOf(visit("v1", "home", at(2, 9), at(2, 9), true)), listOf(home), at(2, 9, 45))
        assertEquals(listOf(RuleKinds.ARRIVE, RuleKinds.STAY), ev.map { it.kind })
        assertEquals("Home", ev[0].text("placeName"))
        assertEquals("home-kind", ev[0].text("category"))
        assertEquals("45", ev[1].text("minutes"))
        assertEquals(at(2, 9), ev[0].atMs)
    }

    @Test
    fun aFinishedVisitGivesAnArrivalAndALeaveWithItsLength() {
        val ev = RuleHooks.visitEvents(listOf(visit("v1", "home", at(2, 9), at(2, 11), false)), listOf(home), at(2, 11, 5))
        assertEquals(listOf(RuleKinds.ARRIVE, RuleKinds.LEAVE), ev.map { it.kind })
        assertEquals("120", ev[1].text("minutes"))
        assertEquals(at(2, 11), ev[1].atMs)
    }

    @Test
    fun unnamedAndIgnoredVisitsGiveNothing() {
        assertTrue(RuleHooks.visitEvents(listOf(visit("v1", "", 0, 0, true), visit("v2", "home", 0, 0, true, ignored = true)), listOf(home), 0).isEmpty())
    }

    @Test
    fun chargingStartAndStopBecomeEvents() {
        val s = ChargeSessionEntity("1", 100L, 100L, 40, 40, 1, "", 1, 0, 0L, 0L, true)
        val closed = ChargeSessionEntity("0", 10L, 90L, 20, 80, 1, "", 5, 0, 0L, 0L, false)
        val ev = RuleHooks.chargeEvents(s, listOf(closed, s))
        assertEquals(listOf(RuleKinds.CHARGE_OFF, RuleKinds.CHARGE_ON), ev.map { it.kind })
        assertEquals("80", ev[0].text("level"))
        assertEquals("40", ev[1].text("level"))
        assertTrue(RuleHooks.chargeEvents(null, emptyList()).isEmpty())
    }

    @Test
    fun aTripEndCarriesTheWayOfTravellingAndTheDistance() {
        val trip = TripEntity("t1", at(2, 9), at(2, 10), "", "home", 12_345, 30, 45, "", "car", "auto")
        val e = RuleHooks.tripEvent(trip, listOf(home))
        assertEquals(RuleKinds.TRIP_END, e.kind)
        assertEquals("car", e.text("mode"))
        assertEquals("12.3", e.text("km"))
        assertEquals("Home", e.text("placeName"))
        assertEquals("tripend:t1", e.key)
    }

    @Test
    fun timeOfDayRulesFireOnceAfterTheirTimePasses() {
        val r = rule(RulePart(RuleKinds.TIME).with("at", "450")) // 07:30
        val before = RuleHooks.dueTimes(listOf(r), at(2, 7, 0), at(2, 7, 20), zone)
        assertTrue(before.isEmpty())
        val due = RuleHooks.dueTimes(listOf(r), at(2, 7, 20), at(2, 7, 35), zone)
        assertEquals(1, due.size)
        assertEquals(at(2, 7, 30), due[0].atMs)
        assertEquals("time:2026-11-02:450", due[0].key)
        // The next check does not fire it again.
        assertTrue(RuleHooks.dueTimes(listOf(r), at(2, 7, 35), at(2, 7, 50), zone).isEmpty())
    }

    @Test
    fun theFirstCheckOnlyLooksFifteenMinutesBack() {
        val r = rule(RulePart(RuleKinds.TIME).with("at", "450"))
        assertTrue(RuleHooks.dueTimes(listOf(r), 0L, at(2, 12, 0), zone).isEmpty())
        assertEquals(1, RuleHooks.dueTimes(listOf(r), 0L, at(2, 7, 40), zone).size)
    }

    @Test
    fun aCheckThatCrossesMidnightFindsTheRuleOnTheRightDay() {
        val r = rule(RulePart(RuleKinds.TIME).with("at", "10")) // 00:10
        val due = RuleHooks.dueTimes(listOf(r), at(2, 23, 55), at(3, 0, 20), zone)
        assertEquals(1, due.size)
        assertEquals("time:2026-11-03:10", due[0].key)
    }

    @Test
    fun switchedOffRulesAndOtherTriggersGiveNoTimeEvents() {
        assertTrue(RuleHooks.dueTimes(listOf(rule(RulePart(RuleKinds.TIME).with("at", "450"), enabled = false)), at(2, 7, 0), at(2, 8, 0), zone).isEmpty())
        assertTrue(RuleHooks.dueTimes(listOf(rule(RulePart(RuleKinds.ARRIVE))), at(2, 7, 0), at(2, 8, 0), zone).isEmpty())
    }

    @Test
    fun stillHereEventsComeFromOpenVisitsOnly() {
        val ev = RuleHooks.stayEvents(listOf(visit("v1", "home", at(2, 9), at(2, 9), true), visit("v2", "home", at(2, 7), at(2, 8), false)), listOf(home), at(2, 10))
        assertEquals(1, ev.size)
        assertEquals("60", ev[0].text("minutes"))
        assertEquals("stay:v1", ev[0].key)
    }
}
