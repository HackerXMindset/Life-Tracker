package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Rules for notes on habits (Habits H3c). Notes live in the `notes` table the Streak import already fills.
 * There is deliberately no way to delete a note here, and an edit can never leave a note empty.
 */
object HabitNotes {
    const val TYPE_NOTE = 0

    /** Newest day first, then newest written first. Notes with no day go last. */
    fun sorted(notes: List<NoteEntity>): List<NoteEntity> =
        notes.sortedWith(
            compareBy<NoteEntity> { it.date.isEmpty() }
                .thenByDescending { it.date }
                .thenByDescending { it.createdAt },
        )

    /** Only the notes of one habit, in the same order as [sorted]. An empty id means every note. */
    fun forHabit(notes: List<NoteEntity>, habitId: String): List<NoteEntity> =
        sorted(if (habitId.isEmpty()) notes else notes.filter { it.habitId == habitId })

    /** Habit ids that have at least one note, for the filter row. */
    fun habitsWithNotes(notes: List<NoteEntity>): List<String> =
        notes.map { it.habitId }.filter { it.isNotEmpty() }.distinct()

    /** A new note, or null when the text is blank. */
    fun create(id: String, habitId: String, date: LocalDate, text: String, now: LocalDateTime): NoteEntity? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        return NoteEntity(id, habitId, date.toString(), TYPE_NOTE, clean, null, now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    /** The note with new text, or null when the text is blank (the old text is then kept). */
    fun edited(note: NoteEntity, text: String): NoteEntity? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        return note.copy(text = clean)
    }

    /** "Today", "Yesterday", "Mon, 12 Oct 2026", or "No day". */
    fun dayLabel(date: String, today: LocalDate): String {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return "No day"
        return when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH))
        }
    }

    fun kindLabel(type: Int): String = listOf("Note", "Planned", "Completed").getOrElse(type) { "Note" }
}
