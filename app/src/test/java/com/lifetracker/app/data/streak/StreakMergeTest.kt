package com.lifetracker.app.data.streak

import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.HabitTombstones
import com.lifetracker.app.data.streak.StreakMerge.LocalHabit
import com.lifetracker.app.data.streak.StreakMerge.LocalNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakMergeTest {
    private val none = HabitTombstones.State(emptyMap(), emptyMap())

    private fun plan(
        deleted: Map<String, Long> = emptyMap(),
        cleared: Map<String, Long> = emptyMap(),
        changes: Map<String, Long> = emptyMap(),
        fileHabits: Set<String> = emptySet(),
        fileNotes: Set<String> = emptySet(),
        habits: List<LocalHabit> = listOf(LocalHabit("h1", 0, 0)),
        notes: List<LocalNote> = listOf(LocalNote("n1", "h1", 0)),
        state: HabitTombstones.State = none,
    ) = StreakMerge.plan(deleted, cleared, changes, fileHabits, fileNotes, habits, notes, state)

    @Test fun aHabitDeletedInStreakIsHiddenWithItsNotes() {
        val p = plan(deleted = mapOf("h1" to 100L))
        assertEquals(mapOf("h1" to 100L), p.hideHabits)
        assertEquals(mapOf("n1" to 100L), p.hideNotes)
        assertEquals(mapOf("h1" to 100L), p.remember)
    }

    @Test fun aLoneNoteDeletedInStreakIsHidden() {
        val p = plan(deleted = mapOf("n1" to 100L))
        assertTrue(p.hideHabits.isEmpty())
        assertEquals(mapOf("n1" to 100L), p.hideNotes)
    }

    @Test fun aDeletionIsIgnoredIfYouChangedTheItemLater() {
        val p = plan(deleted = mapOf("h1" to 100L), state = HabitTombstones.State(mapOf("h1" to 200L), emptyMap()))
        assertTrue(p.hideHabits.isEmpty())
        assertTrue(p.remember.isEmpty())
    }

    @Test fun aDeletionAlreadyKnownIsNotAppliedTwice() {
        val p = plan(deleted = mapOf("h1" to 100L), state = HabitTombstones.State(emptyMap(), mapOf("h1" to 100L)))
        assertTrue(p.isEmpty)
    }

    @Test fun aHabitDeletedHereComesBackWhenStreakChangesItLater() {
        val p = plan(
            changes = mapOf("h1" to 60L),
            fileHabits = setOf("h1"),
            habits = listOf(LocalHabit("h1", 50, 0)),
            notes = listOf(LocalNote("n1", "h1", 50), LocalNote("n2", "h1", 10)),
        )
        assertEquals(setOf("h1"), p.showHabits)
        // Only the note that was deleted together with the habit comes back, not the one deleted earlier.
        assertEquals(setOf("n1"), p.showNotes)
    }

    @Test fun anOlderChangeInStreakDoesNotBringItBack() {
        val p = plan(changes = mapOf("h1" to 40L), fileHabits = setOf("h1"), habits = listOf(LocalHabit("h1", 50, 0)), notes = emptyList())
        assertTrue(p.showHabits.isEmpty())
    }

    @Test fun aChangeOlderThanStreaksOwnDeletionDoesNotBringItBack() {
        val p = plan(
            deleted = mapOf("h1" to 70L),
            changes = mapOf("h1" to 60L),
            fileHabits = setOf("h1"),
            habits = listOf(LocalHabit("h1", 50, 0)),
            notes = emptyList(),
        )
        assertTrue(p.showHabits.isEmpty())
    }

    @Test fun aNoteDeletedWithItsHabitDoesNotComeBackAlone() {
        val p = plan(
            changes = mapOf("n1" to 60L),
            fileNotes = setOf("n1"),
            habits = listOf(LocalHabit("h1", 50, 0)),
            notes = listOf(LocalNote("n1", "h1", 50)),
        )
        assertTrue(p.showNotes.isEmpty())
    }

    @Test fun clearedProgressOnlyMovesForward() {
        assertEquals(mapOf("h1" to 500L), plan(cleared = mapOf("h1" to 500L)).clears)
        assertTrue(plan(cleared = mapOf("h1" to 500L), habits = listOf(LocalHabit("h1", 0, 600))).clears.isEmpty())
        assertTrue(plan(cleared = mapOf("unknown" to 500L)).clears.isEmpty())
    }

    // ---- days and their times (H7)

    private val oct4 = java.time.LocalDate.of(2026, 10, 4)

    @Test fun aDayOnlyStreakHasIsTakenWithItsTimes() {
        val day = StreakMerge.day(null, "h", StreakCompletion(oct4, 2.0, 600, listOf(480, 600)))!!
        assertEquals(2.0, day.count, 0.0)
        assertEquals("480,600", day.marks)
        assertEquals(600, day.minuteOfDay)
    }

    @Test fun aBiggerAmountInStreakBringsItsTimesAndKeepsOursIfItHadNone() {
        val ours = CompletionEntity("h", "2026-10-04", 1.0, 480, "480")
        val withTimes = StreakMerge.day(ours, "h", StreakCompletion(oct4, 3.0, 900, listOf(480, 700, 900)))!!
        assertEquals("480,700,900", withTimes.marks)
        val noTimes = StreakMerge.day(ours, "h", StreakCompletion(oct4, 3.0, null, emptyList()))!!
        assertEquals(3.0, noTimes.count, 0.0)
        assertEquals("480", noTimes.marks)
        assertEquals(480, noTimes.minuteOfDay)
    }

    @Test fun aSmallerAmountInStreakChangesNothingHere() {
        val ours = CompletionEntity("h", "2026-10-04", 3.0, 900, "480,700,900")
        assertNull(StreakMerge.day(ours, "h", StreakCompletion(oct4, 1.0, 480, listOf(480))))
    }

    @Test fun theSameAmountTakesStreaksTimesOnlyWhenWeHaveNone() {
        val bare = CompletionEntity("h", "2026-10-04", 2.0, null, "")
        val taken = StreakMerge.day(bare, "h", StreakCompletion(oct4, 2.0, 700, listOf(600, 700)))!!
        assertEquals("600,700", taken.marks)
        assertEquals(2.0, taken.count, 0.0)
        // Times set here are never overwritten by an equal day.
        val mine = CompletionEntity("h", "2026-10-04", 2.0, 615, "500,615")
        assertNull(StreakMerge.day(mine, "h", StreakCompletion(oct4, 2.0, 700, listOf(600, 700))))
    }

    @Test fun aBiggerDayInStreakBringsItsStepsAndAnEqualOneFillsInMissingSteps() {
        val ours = CompletionEntity("h", "2026-10-04", 1.0, null, "", "a")
        assertEquals("a,b", StreakMerge.day(ours, "h", StreakCompletion(oct4, 2.0, null, emptyList(), listOf("a", "b")))!!.steps)
        // A day with no step list (from before steps were kept) takes Streak's list when the amount is the same.
        val old = CompletionEntity("h", "2026-10-04", 2.0, null, "", "")
        assertEquals("a,b", StreakMerge.day(old, "h", StreakCompletion(oct4, 2.0, null, emptyList(), listOf("a", "b")))!!.steps)
        // Our own list is never replaced by an equal day.
        assertNull(StreakMerge.day(ours, "h", StreakCompletion(oct4, 1.0, null, emptyList(), listOf("b"))))
    }
}
