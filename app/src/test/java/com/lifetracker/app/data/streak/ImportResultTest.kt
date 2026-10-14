package com.lifetracker.app.data.streak

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportResultTest {
    @Test
    fun anImportThatBroughtHabitsSaysSo() {
        val text = ImportResult("backup.json", habits = 5, completionsAdded = 12, focusAdded = 1, todos = 3, datedTodos = 2, notes = 4).describe()
        assertTrue("5 habits" in text)
        assertTrue("12 new habit days" in text)
        assertTrue("4 notes" in text)
        assertFalse("left alone" in text)
    }

    @Test
    fun anImportThatLeftHabitsAloneSaysSoAndLeavesOutTheHabitNumbers() {
        val text = ImportResult(
            "backup.json", habits = 0, completionsAdded = 0, focusAdded = 2, todos = 3, datedTodos = 1, notes = 0, habitsSkipped = true,
        ).describe()
        assertTrue("habits left alone" in text)
        assertTrue("2 focus sessions" in text)
        assertTrue("3 to-dos" in text)
        assertFalse("habit days" in text)
        assertFalse("notes" in text)
        assertTrue(text.endsWith("."))
    }
}
