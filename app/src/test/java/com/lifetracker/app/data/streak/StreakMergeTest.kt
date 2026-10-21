package com.lifetracker.app.data.streak

import com.lifetracker.app.data.HabitTombstones
import com.lifetracker.app.data.streak.StreakMerge.LocalHabit
import com.lifetracker.app.data.streak.StreakMerge.LocalNote
import org.junit.Assert.assertEquals
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
}
