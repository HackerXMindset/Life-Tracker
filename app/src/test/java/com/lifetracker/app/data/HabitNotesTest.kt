package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HabitNotesTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val now = LocalDateTime.of(2026, 10, 14, 9, 30)

    private fun note(id: String, habit: String, date: String, created: String = "2026-10-01T08:00:00") =
        NoteEntity(id, habit, date, 0, "text $id", null, created)

    @Test
    fun newestDayFirstUndatedLast() {
        val list = listOf(
            note("a", "h1", "2026-10-10"),
            note("b", "h1", ""),
            note("c", "h2", "2026-10-13"),
            note("d", "h1", "2026-10-10", created = "2026-10-10T20:00:00"),
        )
        assertEquals(listOf("c", "d", "a", "b"), HabitNotes.sorted(list).map { it.id })
    }

    @Test
    fun filterByHabitKeepsOrderAndEmptyIdMeansAll() {
        val list = listOf(note("a", "h1", "2026-10-10"), note("c", "h2", "2026-10-13"), note("d", "h1", "2026-10-12"))
        assertEquals(listOf("d", "a"), HabitNotes.forHabit(list, "h1").map { it.id })
        assertEquals(3, HabitNotes.forHabit(list, "").size)
        assertEquals(listOf("h1", "h2"), HabitNotes.habitsWithNotes(list))
    }

    @Test
    fun createTrimsAndRefusesBlank() {
        val n = HabitNotes.create("n1", "h1", today, "  felt good \n", now)
        assertNotNull(n)
        assertEquals("felt good", n!!.text)
        assertEquals("2026-10-14", n.date)
        assertEquals("h1", n.habitId)
        assertEquals(HabitNotes.TYPE_NOTE, n.type)
        assertNull(n.minutes)
        assertEquals("2026-10-14T09:30:00", n.createdAt)
        assertNull(HabitNotes.create("n2", "h1", today, "   ", now))
    }

    @Test
    fun editChangesOnlyTheTextAndNeverBlanksIt() {
        val old = NoteEntity("n1", "h1", "2026-10-10", 1, "old", 540, "2026-10-10T07:00:00")
        val changed = HabitNotes.edited(old, " new text ")!!
        assertEquals(old.copy(text = "new text"), changed)
        assertNull(HabitNotes.edited(old, "  "))
    }

    @Test
    fun dayLabels() {
        assertEquals("Today", HabitNotes.dayLabel("2026-10-14", today))
        assertEquals("Yesterday", HabitNotes.dayLabel("2026-10-13", today))
        assertEquals("Mon, 12 Oct 2026", HabitNotes.dayLabel("2026-10-12", today))
        assertEquals("No day", HabitNotes.dayLabel("", today))
        assertEquals("Completed", HabitNotes.kindLabel(2))
        assertEquals("Note", HabitNotes.kindLabel(9))
    }
}
