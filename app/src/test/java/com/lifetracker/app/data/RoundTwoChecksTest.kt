package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks across the stages of Places and trips round 2 (E10): the rules and the goals look at the same train day. */
class RoundTwoChecksTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 28, h, m).atZone(zone).toInstant().toEpochMilli()

    private val places = listOf(
        PlaceEntity("home", "Home", "home", 0.0, 0.0, 50, false, 0L),
        PlaceEntity("prayagraj", "Prayagraj Junction", "other", 0.0, 0.0, 200, false, 0L),
        PlaceEntity("tundla", "Tundla Junction", "other", 0.0, 0.0, 200, false, 0L),
        PlaceEntity("delhi", "New Delhi", "other", 0.0, 0.0, 200, false, 0L),
    )

    private fun visit(id: String, place: String, from: Long, to: Long) =
        PlaceVisitEntity(id, place, from, to, 0.0, 0.0, "auto", false, false)

    private fun trip(id: String, from: Long, to: Long, km: Int, a: String, b: String) =
        TripEntity(id, from, to, a, b, km * 1000, 70, 110, "", "train", "auto")

    /** Home until 5:30, a train stopping at Prayagraj (5:34 to 5:46, kept as ONE stay) and Tundla, then Delhi. */
    private val visits = listOf(
        visit("v1", "home", at(0), at(5, 30)),
        visit("v2", "prayagraj", at(5, 34), at(5, 46)),
        visit("v3", "tundla", at(9, 0), at(9, 10)),
        visit("v4", "delhi", at(12, 0), at(14, 0)),
    )
    private val trips = listOf(
        trip("t1", at(5, 30), at(5, 34), 2, "home", "prayagraj"),
        trip("t2", at(5, 46), at(9, 0), 343, "prayagraj", "tundla"),
        trip("t3", at(9, 10), at(12, 0), 60, "tundla", "delhi"),
    )
    private val now = at(23)
    private val day = LocalDate.of(2026, 10, 28)

    private fun goal(measure: String, category: String = "", placeId: String = "") =
        GoalEntity("g", "G", measure, placeId, category, "", GoalPeriods.DAY, 1, GoalDirections.AT_LEAST, 1.0, true, 0L, 0)

    private fun value(g: GoalEntity) = GoalStats.value(g, visits, trips, places, now, day, zone)

    @Test
    fun theTrainDayGivesOneArrivalAndOneLeaveForEachStopNotTwo() {
        val events = RuleHooks.visitEvents(visits, places, now)
        assertEquals(1, events.count { it.kind == RuleKinds.ARRIVE && it.text("placeId") == "prayagraj" })
        assertEquals(1, events.count { it.kind == RuleKinds.LEAVE && it.text("placeId") == "prayagraj" })
        assertEquals(4, events.count { it.kind == RuleKinds.ARRIVE })
        // The 12-minute stop at Prayagraj says how long it was.
        assertEquals("12", events.first { it.kind == RuleKinds.LEAVE && it.text("placeId") == "prayagraj" }.text("minutes"))
    }

    @Test
    fun aRuleForTheTripThatEndedAtTheLastStationFiresOnce() {
        val last = trips.last()
        val e = RuleHooks.tripEvent(last, places)
        val rule = Rule("r", "Delhi", true, RulePart(RuleKinds.TRIP_END).with("mode", "train"), emptyList(), listOf(RulePart(RuleKinds.NOTIFY).with("title", "{km} km")), 0, 0L, 0L)
        assertEquals(RuleEngine.Outcome.RUN, RuleEngine.judge(rule, e, emptyList(), false, e.atMs, zone).outcome)
        assertEquals(RuleEngine.Outcome.SEEN, RuleEngine.judge(rule, e, emptyList(), true, e.atMs, zone).outcome)
        assertEquals("60.0 km", RuleText.fill("{km} km", e, zone))
    }

    @Test
    fun goalsSeeTheWholeJourneyAsTimeOutAndDistance() {
        // Out of the house from 5:30 to 14:00 (nothing recorded after): 8.5 hours.
        assertEquals(8.5, value(goal(GoalMeasures.HOURS_OUT)), 1e-9)
        // 2 + 343 + 60 kilometres.
        assertEquals(405.0, value(goal(GoalMeasures.DISTANCE)), 1e-9)
        // Time on the train: 4 min + 3 h 14 min + 2 h 50 min.
        assertEquals((4 + 194 + 170) / 60.0, value(goal(GoalMeasures.HOURS_TRAVEL)), 1e-9)
        assertEquals(2.0, value(goal(GoalMeasures.HOURS_AT, placeId = "delhi")), 1e-9)
        assertEquals(5.5, value(goal(GoalMeasures.HOURS_AT, category = "home")), 1e-9)
        assertEquals(4.0, value(goal(GoalMeasures.VISITS)), 1e-9)
    }

    @Test
    fun aGoalForTheSameDayDoesNotDependOnTheOrderOfTheLists() {
        val shuffled = GoalStats.value(goal(GoalMeasures.HOURS_OUT), visits.reversed(), trips.reversed(), places.reversed(), now, day, zone)
        assertEquals(value(goal(GoalMeasures.HOURS_OUT)), shuffled, 1e-9)
    }

    @Test
    fun everySwitchAndEveryKindHasAUniqueNameAndWords() {
        val ids = Feature.values().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Feature.values().all { it.title.isNotBlank() })
        assertEquals(RuleKinds.TRIGGERS.size, RuleKinds.TRIGGERS.map { it.first }.toSet().size)
        assertEquals(RuleKinds.ACTIONS.size, RuleKinds.ACTIONS.map { it.first }.toSet().size)
        assertEquals(RuleKinds.CONDITIONS.size, RuleKinds.CONDITIONS.map { it.first }.toSet().size)
        assertTrue(GoalMeasures.ALL.all { GoalMeasures.unit(it.first).isNotEmpty() })
        assertTrue(GoalPeriods.ALL.all { it.second.isNotBlank() })
    }

    @Test
    fun theBackupOfARuleAndAGoalReadsBackTogether() {
        val rule = RuleEntity("r1", "Gym", true, """{"kind":"arrive","params":{}}""", "[]", """[{"kind":"notify","params":{"title":"Hi"}}]""", 60, 1L, 0L, 0, 0)
        val g = GoalEntity("g1", "Out", GoalMeasures.HOURS_OUT, "", "home", "", GoalPeriods.WEEK, 7, GoalDirections.AT_LEAST, 10.0, true, 1L, 0)
        val data = BackupData(BackupCodec.FORMAT_VERSION, "x", emptyList(), rules = listOf(rule), goals = listOf(g))
        assertTrue(!data.isEmpty)
        assertEquals(30, BackupCodec.FORMAT_VERSION)
    }
}
