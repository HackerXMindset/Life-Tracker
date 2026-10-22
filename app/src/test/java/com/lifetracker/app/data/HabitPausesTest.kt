package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitPausesTest {
    // 2026-10-14 is a Wednesday.
    private val today = LocalDate.of(2026, 10, 14)
    private fun d(n: Int) = LocalDate.of(2026, 10, n)
    private val empty = """{"id":"h","name":"x","vacations":[],"restDays":[]}"""

    private fun spans(raw: String) = HabitPlan.fromRaw(raw).vacations

    @Test
    fun pausingStartsAnOpenHolidayTodayInStreaksShape() {
        val raw = HabitPauses.setPaused(empty, true, today)
        assertTrue(HabitPauses.isPaused(raw))
        assertEquals(listOf(VacationSpan(today, null)), spans(raw))
        assertTrue(raw.contains("2026-10-14T00:00:00.000"))
        // Pausing twice changes nothing.
        assertEquals(spans(raw), spans(HabitPauses.setPaused(raw, true, today.plusDays(2))))
    }

    @Test
    fun resumingEndsTheHolidayYesterday() {
        val paused = HabitPauses.setPaused(empty, true, d(10))
        val resumed = HabitPauses.setPaused(paused, false, today)
        assertFalse(HabitPauses.isPaused(resumed))
        assertEquals(listOf(VacationSpan(d(10), d(13))), spans(resumed))
    }

    @Test
    fun aHolidayThatBeganTodayJustDisappearsWhenResumed() {
        val resumed = HabitPauses.setPaused(HabitPauses.setPaused(empty, true, today), false, today)
        assertEquals(emptyList<VacationSpan>(), spans(resumed))
    }

    @Test
    fun finishedHolidaysAreKeptWhenResuming() {
        val raw = """{"vacations":[{"start":"2026-10-01T00:00:00.000","end":"2026-10-03T00:00:00.000"},{"start":"2026-10-10T00:00:00.000"}]}"""
        val resumed = HabitPauses.setPaused(raw, false, today)
        assertEquals(listOf(VacationSpan(d(1), d(3)), VacationSpan(d(10), d(13))), spans(resumed))
    }

    @Test
    fun otherFieldsOfTheRecordAreKept() {
        val raw = """{"id":"h","name":"Read","bulk":1,"vacations":[{"start":"2026-10-10T00:00:00.000","bulk":true}],"missReasons":{"01-10-2026":"Sick"}}"""
        val out = org.json.JSONObject(HabitPauses.setPaused(raw, false, today))
        assertEquals("Read", out.getString("name"))
        assertEquals("Sick", out.getJSONObject("missReasons").getString("01-10-2026"))
        assertTrue(out.getJSONArray("vacations").getJSONObject(0).getBoolean("bulk"))
    }

    @Test
    fun pausingASingleDayAddsAOneDayHoliday() {
        val raw = HabitPauses.toggleDay(empty, d(5), today)
        assertEquals(listOf(VacationSpan(d(5), d(5))), spans(raw))
        assertTrue(HabitPauses.isInPause(raw, d(5), today))
        assertFalse(HabitPauses.isInPause(raw, d(6), today))
    }

    @Test
    fun bringingBackTheMiddleOfAHolidaySplitsIt() {
        val raw = """{"vacations":[{"start":"2026-10-04T00:00:00.000","end":"2026-10-08T00:00:00.000"}]}"""
        val out = HabitPauses.toggleDay(raw, d(6), today)
        assertEquals(listOf(VacationSpan(d(4), d(5)), VacationSpan(d(7), d(8))), spans(out))
    }

    @Test
    fun bringingBackAnEdgeShortensIt() {
        val raw = """{"vacations":[{"start":"2026-10-04T00:00:00.000","end":"2026-10-08T00:00:00.000"}]}"""
        assertEquals(listOf(VacationSpan(d(5), d(8))), spans(HabitPauses.toggleDay(raw, d(4), today)))
        assertEquals(listOf(VacationSpan(d(4), d(7))), spans(HabitPauses.toggleDay(raw, d(8), today)))
        // A one-day holiday vanishes.
        val one = HabitPauses.toggleDay(empty, d(5), today)
        assertEquals(emptyList<VacationSpan>(), spans(HabitPauses.toggleDay(one, d(5), today)))
    }

    @Test
    fun bringingBackADayInAHolidayThatIsStillGoingKeepsTheRestGoing() {
        val raw = """{"vacations":[{"start":"2026-10-10T00:00:00.000"}]}"""
        val out = HabitPauses.toggleDay(raw, d(12), today)
        assertEquals(listOf(VacationSpan(d(10), d(11)), VacationSpan(d(13), null)), spans(out))
        assertTrue(HabitPauses.isPaused(out))
    }

    @Test
    fun aRestDayOfTheWeekIsLeftAloneByTheDayToggle() {
        val raw = HabitPauses.setRestDays(empty, listOf(3))
        assertTrue(HabitPauses.isRestDay(raw, d(14))) // Wednesday
        assertEquals(emptyList<VacationSpan>(), spans(HabitPauses.toggleDay(raw, d(14), today)))
    }

    @Test
    fun restDaysAreSortedCheckedAndDeduplicated() {
        val raw = HabitPauses.setRestDays(empty, listOf(7, 3, 3, 9, 0))
        assertEquals(listOf(3, 7), HabitPauses.restDays(raw))
        assertEquals(emptyList<Int>(), HabitPauses.restDays(HabitPauses.setRestDays(raw, emptyList())))
    }

    @Test
    fun aPausedDayNeitherBreaksTheStreakNorCountsAsMissed() {
        // Done on the 9th, 10th and 13th; paused the 11th and 12th (and today is not over yet).
        val raw = """{"vacations":[{"start":"2026-10-11T00:00:00.000","end":"2026-10-12T00:00:00.000"}]}"""
        val rules = HabitRules(
            HabitStats.KIND_YES_NO, 1.0, false, HabitPlan.fromRaw(raw),
            listOf(d(9), d(10), d(13)).associateWith { 1.0 }, d(1), today,
        )
        assertEquals(3, rules.currentStreak())
        assertEquals(DayKind.PAUSED, rules.cell(d(11)).kind)
        assertFalse(rules.canMiss(d(12)))
    }

    @Test
    fun aRestDayStopsBreakingTheStreak() {
        val raw = HabitPauses.setRestDays(empty, listOf(7)) // Sunday the 11th
        val rules = HabitRules(
            HabitStats.KIND_YES_NO, 1.0, false, HabitPlan.fromRaw(raw),
            listOf(d(9), d(10), d(12), d(13), d(14)).associateWith { 1.0 }, d(1), today,
        )
        assertEquals(5, rules.currentStreak())
    }
}
