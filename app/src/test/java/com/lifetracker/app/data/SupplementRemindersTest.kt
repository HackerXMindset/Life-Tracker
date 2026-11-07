package com.lifetracker.app.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupplementRemindersTest {
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    // 2026-11-07 is a Saturday.
    @Test
    fun ringsTodayIfTheTimeIsStillAhead() {
        val s = SupplementEntity("d3", "D3", atMinute = 20 * 60)
        assertEquals(at(7, 20), SupplementReminders.next(s, at(7, 8)))
    }

    @Test
    fun ringsTomorrowIfTodaysTimeHasPassedOrIsNow() {
        val s = SupplementEntity("d3", "D3", atMinute = 8 * 60)
        assertEquals(at(8, 8), SupplementReminders.next(s, at(7, 8)))
        assertEquals(at(8, 8), SupplementReminders.next(s, at(7, 9)))
    }

    @Test
    fun skipsDaysItIsNotDue() {
        // Mondays and Thursdays only: Saturday the 7th, so the next is Monday the 9th.
        val s = SupplementEntity("z", "Zinc", atMinute = 9 * 60, weekdays = SupplementRules.bit(1) or SupplementRules.bit(4))
        assertEquals(at(9, 9), SupplementReminders.next(s, at(7, 10)))
    }

    @Test
    fun everyThirdDayCountsFromTheStartDate() {
        val s = SupplementEntity("x", "X", atMinute = 9 * 60, everyDays = 3, fromDate = "2026-11-05")
        // Due on the 5th, 8th, 11th ...
        assertEquals(at(8, 9), SupplementReminders.next(s, at(7, 10)))
        assertEquals(at(11, 9), SupplementReminders.next(s, at(8, 10)))
    }

    @Test
    fun noTimeArchivedOrFinishedNeverRings() {
        assertNull(SupplementReminders.next(SupplementEntity("a", "A"), at(7, 8)))
        assertNull(SupplementReminders.next(SupplementEntity("b", "B", atMinute = 600, archived = true), at(7, 8)))
        assertNull(SupplementReminders.next(SupplementEntity("c", "C", atMinute = 600, untilDate = "2026-11-06"), at(7, 8)))
    }

    @Test
    fun theMessageIncludesTheDoseWhenThereIsOne() {
        assertEquals("Time to take it.", SupplementReminders.message(SupplementEntity("a", "A")))
        assertEquals("Time to take it: 1 tab", SupplementReminders.message(SupplementEntity("a", "A", dose = " 1 tab ")))
    }
}
