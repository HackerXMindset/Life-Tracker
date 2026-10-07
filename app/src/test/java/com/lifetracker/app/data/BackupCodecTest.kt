package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val entries = listOf(
        EntryEntity(1, "2026-10-04", 540, 660, "Study", "Polity \"revision\"", "Chapter 3\nline two"),
        EntryEntity(2, "2026-10-04", 700, null, "Event", "Moved to Delhi", ""),
        EntryEntity(7, "2026-10-05", 0, 480, "Sleep", "Sleep", "ünïcode ✓"),
    )

    private val full = BackupData(
        version = BackupCodec.FORMAT_VERSION,
        exportedAt = "2026-10-07T09:00:00",
        entries = entries,
        habits = listOf(
            HabitEntity("h1", "Maths", "target", "Self Study", 4281648985L, 2, 0, "Hours", 2.0, 1.0, false, -1, 0, "2026-10-03", false, 3, """{"id":"h1","x":"q\"uote"}"""),
        ),
        completions = listOf(
            CompletionEntity("h1", "2026-10-04", 1.5, null),
            CompletionEntity("h1", "2026-10-05", 2.0, 780),
        ),
        categories = listOf(CategoryEntity("c1", "Self Study", 4281648985L, 5)),
        todoTags = listOf(TodoTagEntity("p1", "Physics", 4287532691L, "project", 1)),
        todos = listOf(
            TodoEntity("t1", "Physics\nDo:", false, "2026-10-09", 540, 90, 3, "p1", "2026-10-03T22:46:25", null, "{}"),
            TodoEntity("t2", "Plain", true, null, null, null, 0, "", "2026-10-03T22:46:25", "2026-10-04T10:00:00", "{}"),
        ),
        notes = listOf(NoteEntity("n1", "h1", "2026-09-29", 0, "No note", null, "2026-09-29T15:00:40")),
        imported = listOf(ImportedItemEntity("streak-focus", "abc", 7)),
    )

    @Test
    fun roundTripKeepsEveryField() {
        val back = BackupCodec.decode(BackupCodec.encode(full))
        assertEquals(full, back)
    }

    @Test
    fun momentKeepsNullEndTime() {
        val back = BackupCodec.decode(BackupCodec.encode(full))
        assertNull(back.entries[1].endMinute)
    }

    @Test
    fun emptyBackupIsValid() {
        val back = BackupCodec.decode(BackupCodec.encode(BackupData(2, "x", emptyList())))
        assertTrue(back.isEmpty)
    }

    @Test
    fun versionOneBackupsStillLoad() {
        val v1 = """{"app":"life-tracker","version":1,"exportedAt":"x","entries":[
            {"id":1,"date":"2026-10-04","startMinute":540,"endMinute":660,"category":"Study","title":"A","note":""}]}"""
        val back = BackupCodec.decode(v1)
        assertEquals(1, back.version)
        assertEquals(1, back.entries.size)
        assertTrue(back.habits.isEmpty())
        assertTrue(back.todos.isEmpty())
    }

    @Test
    fun garbageIsRefused() {
        expectRefusal("hello")
        expectRefusal("{}")
        expectRefusal("""{"app":"streak","version":1,"entries":[]}""")
        expectRefusal("""{"app":"life-tracker","version":99,"entries":[]}""")
        expectRefusal("""{"app":"life-tracker","version":1,"entries":[{"id":1}]}""")
    }

    private fun expectRefusal(text: String) {
        try {
            BackupCodec.decode(text)
            fail("Should have refused: $text")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
