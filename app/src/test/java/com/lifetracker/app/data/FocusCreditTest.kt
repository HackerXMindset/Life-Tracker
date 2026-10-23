package com.lifetracker.app.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusCreditTest {
    private fun habit(kind: Int = HabitStats.KIND_YES_NO, quant: Int = 0, target: Double = 1.0, raw: String = "{}", archived: Boolean = false, deleted: Long = 0L) =
        HabitEntity("h", "x", "target", "", 0, kind, quant, "", target, 1.0, false, -1, 0, "2026-10-01", archived, 0, raw, deleted)

    private fun session(seconds: Int = 1500, completed: Boolean = true) =
        FocusSessionData("s", "h", 25, seconds, completed, 0L)

    @Test
    fun aCompletedSessionTicksAYesNoHabitOnce() {
        assertEquals(FocusCredit.Credit.Tick(1.0), FocusCredit.decide(habit(), session(), false, false))
        assertEquals(FocusCredit.Credit.Tick(2.0), FocusCredit.decide(habit(target = 2.0), session(), false, false))
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(habit(), session(), false, true))
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(habit(), session(completed = false), false, false))
    }

    @Test
    fun aTimeHabitGetsTheMinutesOnceWhetherOrNotTheGoalWasMet() {
        val time = habit(kind = HabitStats.KIND_COUNTED, quant = FocusCredit.QUANT_TIME, target = 30.0)
        assertEquals(FocusCredit.Credit.Minutes(25.0), FocusCredit.decide(time, session(), false, false))
        assertEquals(FocusCredit.Credit.Minutes(10.0), FocusCredit.decide(time, session(600, completed = false), false, false))
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(time, session(), alreadyCounted = true, doneToday = false))
    }

    @Test
    fun otherHabitsAreLeftAlone() {
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(null, session(), false, false))
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(habit(kind = HabitStats.KIND_RELAPSE), session(), false, false))
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(habit(kind = HabitStats.KIND_COUNTED), session(), false, false)) // not a time habit
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(habit(archived = true), session(), false, false))
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(habit(deleted = 5L), session(), false, false))
        assertEquals(FocusCredit.Credit.None, FocusCredit.decide(habit(raw = """{"substeps":[{"id":"a","title":"x"}]}"""), session(), false, false))
    }

    @Test
    fun theDayIsTheDayItEnded() {
        val zone = ZoneId.of("UTC")
        val start = LocalDateTime.of(2026, 10, 14, 23, 50).atZone(zone).toInstant().toEpochMilli()
        assertEquals(java.time.LocalDate.of(2026, 10, 15), FocusCredit.dayOf(FocusSessionData("s", "h", 25, 1500, true, start), zone))
        assertEquals(java.time.LocalDate.of(2026, 10, 14), FocusCredit.dayOf(FocusSessionData("s", "h", 25, 600, true, start), zone))
    }

    @Test
    fun theHabitRemembersItsSetupAndFocusOnlyInItsOwnRecord() {
        val h = habit(raw = """{"name":"x","futureField":1}""")
        assertEquals(25, FocusCredit.minutesOf(h))
        assertEquals(0, FocusCredit.breakOf(h))
        val set = FocusCredit.withSetup(h, 50, 10)
        assertEquals(50, FocusCredit.minutesOf(set))
        assertEquals(10, FocusCredit.breakOf(set))
        assertTrue(set.raw.contains("futureField"))
        assertFalse(FocusCredit.needsFocusSession(h))
        assertTrue(FocusCredit.needsFocusSession(h.copy(raw = FocusCredit.withFocusOnly(h.raw, true))))
        assertFalse(FocusCredit.needsFocusSession(habit(kind = HabitStats.KIND_RELAPSE, raw = """{"focusOnly":true}""")))
    }
}
