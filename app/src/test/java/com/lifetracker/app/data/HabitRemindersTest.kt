package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitRemindersTest {
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, day, h, m)

    private fun r(
        hour: Int = 8, minute: Int = 0, days: List<Int> = HabitReminders.ALL_DAYS, everyDays: Int = 1,
        everyHours: Int = 0, until: Int? = null, anchor: Long? = null,
    ) = HabitReminders.Reminder("r1", hour, minute, days, "", everyDays, everyHours, until, anchor)

    @Test
    fun aDailyReminderRingsTodayIfLaterElseTomorrow() {
        assertEquals(at(14, 8, 0), HabitReminders.next(r(), at(14, 7, 59)))
        assertEquals(at(15, 8, 0), HabitReminders.next(r(), at(14, 8, 0)))
        assertEquals(at(15, 8, 0), HabitReminders.next(r(), at(14, 20, 0)))
    }

    @Test
    fun chosenWeekdaysOnly() {
        // 14 October 2026 is a Wednesday (3). Monday = 1.
        val mondays = r(days = listOf(1))
        assertEquals(at(19, 8, 0), HabitReminders.next(mondays, at(14, 7, 0)))
        assertNull(HabitReminders.next(r(days = emptyList()), at(14, 7, 0)))
    }

    @Test
    fun everyNDaysCountsFromItsStartDay() {
        val anchor = LocalDate.of(2026, 10, 12).toEpochDay()
        val every3 = r(everyDays = 3, anchor = anchor)
        assertEquals(at(15, 8, 0), HabitReminders.next(every3, at(14, 7, 0)))
        assertEquals(at(18, 8, 0), HabitReminders.next(every3, at(15, 9, 0)))
        // Before the start day the rhythm still holds (12 - 3 = 9).
        assertEquals(at(9, 8, 0), HabitReminders.next(every3, LocalDateTime.of(2026, 10, 8, 12, 0)))
    }

    @Test
    fun hourlyRingsEachSlotThenTheNextDay() {
        val h = r(hour = 9, everyHours = 2, until = 13 * 60)
        assertEquals(listOf(540, 660, 780), HabitReminders.slots(h))
        assertEquals(at(14, 11, 0), HabitReminders.next(h, at(14, 9, 30)))
        assertEquals(at(14, 13, 0), HabitReminders.next(h, at(14, 11, 0)))
        assertEquals(at(15, 9, 0), HabitReminders.next(h, at(14, 13, 0)))
    }

    @Test
    fun hourlyWithNoEndTimeRunsToTheEndOfTheDay() {
        val h = r(hour = 20, everyHours = 1)
        assertEquals(listOf(1200, 1260, 1320, 1380), HabitReminders.slots(h))
    }

    @Test
    fun theHabitDecidesWhichDaysCount() {
        val offDay = LocalDate.of(2026, 10, 15)
        val rings = { d: LocalDate -> d != offDay }
        assertEquals(at(16, 8, 0), HabitReminders.next(r(), at(14, 9, 0), habitRings = rings))
        // A habit that is itself "every N days" ignores the reminder's weekdays.
        val sundays = r(days = listOf(7))
        assertEquals(at(15, 8, 0), HabitReminders.next(sundays, at(14, 9, 0), habitDrives = true))
        assertNull(HabitReminders.next(r(), at(14, 9, 0), habitRings = { false }))
    }

    @Test
    fun readsStreaksOwnShapeAndWritesItBackKeepingUnknownKeys() {
        val raw = """{"name":"x","reminders":[{"id":"a","hour":7,"minute":30,"selectedDays":[1,3,5],"message":"Go","everyDays":1,"everyHours":0,"untilMinute":null,"anchorEpochDay":null,"snoozeMinutes":10,"future":"keep"}]}"""
        val list = HabitReminders.read(raw)
        assertEquals(1, list.size)
        assertEquals(listOf(1, 3, 5), list[0].days)
        assertEquals(10, list[0].snoozeMinutes)
        assertNull(list[0].untilMinute)
        val out = HabitReminders.write(raw, list.map { it.copy(hour = 9) })
        val o = JSONObject(out).getJSONArray("reminders").getJSONObject(0)
        assertEquals(9, o.getInt("hour"))
        assertEquals("keep", o.getString("future"))
        assertEquals("x", JSONObject(out).getString("name"))
        assertEquals(emptyList<HabitReminders.Reminder>(), HabitReminders.read(HabitReminders.write(out, emptyList())))
    }

    @Test
    fun junkIsIgnored() {
        assertEquals(emptyList<HabitReminders.Reminder>(), HabitReminders.read("not json"))
        assertEquals(emptyList<HabitReminders.Reminder>(), HabitReminders.read("""{"reminders":[{"hour":1},{"id":"z"}]}"""))
        assertEquals(HabitReminders.ALL_DAYS, HabitReminders.read("""{"reminders":[{"id":"z","hour":1,"minute":2}]}""")[0].days)
    }

    @Test
    fun problemsAreSaidInWords() {
        assertNull(HabitReminders.problem(r()))
        assertNotNull(HabitReminders.problem(r(days = emptyList())))
        assertNull(HabitReminders.problem(r(days = emptyList(), everyDays = 2)))
        assertNotNull(HabitReminders.problem(r(hour = 9, everyHours = 1, until = 8 * 60)))
    }

    @Test
    fun describesInWords() {
        assertEquals("Every day at 08:00", HabitReminders.describe(r()))
        assertEquals("On weekdays at 08:00", HabitReminders.describe(r(days = listOf(1, 2, 3, 4, 5))))
        assertEquals("Mon, Wed at 08:00", HabitReminders.describe(r(days = listOf(1, 3))))
        assertEquals("Every 2 days at 08:00", HabitReminders.describe(r(everyDays = 2)))
        assertEquals("Every 2 hours from 09:00 to 17:00", HabitReminders.describe(r(hour = 9, everyHours = 2, until = 17 * 60)))
        assertTrue(HabitReminders.describe(r(hour = 9, everyHours = 1, days = listOf(6, 7))).endsWith("on weekends"))
    }

    @Test
    fun theEditorKeepsAndChangesReminders() {
        val today = LocalDate.of(2026, 10, 14)
        val now = LocalDateTime.of(2026, 10, 14, 9, 0)
        val first = HabitEditor.build(HabitForm(name = "Read", reminders = listOf(r(hour = 7))), null, "h1", 0, today, now)
        assertEquals(listOf(r(hour = 7)), HabitReminders.read(first.raw))
        // Editing something else keeps them (the form carries them).
        val again = HabitEditor.build(HabitEditor.formFrom(first).copy(name = "Read more"), first, "h1", 0, today, now)
        assertEquals(listOf(r(hour = 7)), HabitReminders.read(again.raw))
        // Removing them in the form removes them from the record.
        val none = HabitEditor.build(HabitEditor.formFrom(again).copy(reminders = emptyList()), again, "h1", 0, today, now)
        assertEquals(emptyList<HabitReminders.Reminder>(), HabitReminders.read(none.raw))
        // A new habit starts with none.
        assertEquals(emptyList<HabitReminders.Reminder>(), HabitEditor.formFrom(HabitEditor.build(HabitForm(name = "x"), null, "h2", 0, today, now)).reminders)
    }

    @Test
    fun aMissingStartDayUsesTheFallbackSoTheRhythmNeverMoves() {
        val every2 = r(everyDays = 2)
        val fallback = LocalDate.of(2026, 10, 1)
        // 1, 3, 5 ... 15 are ring days whatever day it is asked from.
        assertEquals(at(15, 8, 0), HabitReminders.next(every2, at(14, 9, 0), anchorFallback = fallback))
        assertEquals(at(15, 8, 0), HabitReminders.next(every2, at(13, 9, 0).plusDays(1), anchorFallback = fallback))
        assertEquals(at(17, 8, 0), HabitReminders.next(every2, at(15, 9, 0), anchorFallback = fallback))
    }
}
