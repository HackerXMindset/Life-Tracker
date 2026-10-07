package com.lifetracker.app.data.streak

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreakImportTest {
    // Shaped like a real Streak backup (version 2), with invented content.
    private val sample = """
    {
      "app": "streak", "version": 2, "exportedAt": "2026-10-04T11:04:28.044810",
      "habits": [
        {"id":"h-bin","name":"Chemistry","category":"Self Study","color":4281648985,"order":1,"kind":0,"quantKind":0,
         "unitLabel":"","numberOfCompletionsPerDay":1.0,"incrementAmount":1.0,"startMinute":-1,"durationMinutes":0,
         "createdAt":"2026-09-28T17:31:22.196567","archivedAt":null,
         "completions":{"29-09-2026":{"date":"29-09-2026","numberOfCompletions":1.0,"hour":13},
                        "30-09-2026":{"date":"30-09-2026","numberOfCompletions":1.0,"hour":9,"minute":30}}},
        {"id":"h-cnt","name":"Studied","category":"","color":4294953984,"order":2,"kind":2,"quantKind":0,
         "unitLabel":"Hour","numberOfCompletionsPerDay":8.0,"incrementAmount":1.0,"anyAmount":false,
         "createdAt":"2026-09-29T13:09:42.759237","archivedAt":"2026-09-29T15:46:41.221319",
         "completions":{"22-09-2026":{"date":"22-09-2026","numberOfCompletions":8.0,"hour":13}}}
      ],
      "categories": [{"id":"c1","name":"Self Study","color":4281648985,"icon":"folder","order":4}],
      "notes": [
        {"id":"n1","habitId":"h-bin","date":"29-09-2026","type":0,"text":"No note","minutes":null,"photos":[],"createdAt":"2026-09-29T15:00:40.565027"},
        {"id":"n-bad","habitId":"h-bin","type":9}
      ],
      "focus": [
        {"id":"f1","habitId":"","targetMinutes":0,"seconds":6381,"completed":true,"startedAt":"2026-09-26T14:31:40.605276"},
        {"id":"f2","habitId":"h-bin","targetMinutes":45,"seconds":2700,"completed":true,"startedAt":"2026-09-28T20:00:29.138","label":"Revision"}
      ],
      "todos": [
        {"id":"t1","text":"Do ch-2\nall questions","done":false,"date":"09-10-2026","minutes":540,"estimate":90,"priority":3,"project":"p1","createdAt":"2026-10-03T22:53:12.522228","doneAt":null,"tags":[],"steps":[],"photos":[]},
        {"id":"t2","text":"Undated","done":true,"date":"","minutes":null,"estimate":null,"priority":0,"project":"","createdAt":"2026-09-27T00:52:15.578130","doneAt":"2026-10-03T22:57:44.331534"}
      ],
      "todoTags": [{"id":"p1","name":"Physics","color":4287532691,"icon":"target","order":1,"kind":"project"}],
      "settings": {"profileName": "x"}
    }
    """.trimIndent()

    @Test
    fun parsesTheRealShape() {
        val data = StreakParser.parse(sample)
        assertEquals("2026-10-04T11:04:28.044810", data.exportedAt)
        assertEquals(2, data.habits.size)
        assertEquals(1, data.categories.size)
        assertEquals(2, data.focus.size)
        assertEquals(2, data.todos.size)
        assertEquals(1, data.todoTags.size)

        val chem = data.habits.first { it.id == "h-bin" }
        assertEquals(4281648985L, chem.color)
        assertEquals(LocalDate.of(2026, 9, 28), chem.createdOn)
        assertTrue(!chem.archived)
        assertEquals(2, chem.completions.size)
        val sep30 = chem.completions.first { it.date == LocalDate.of(2026, 9, 30) }
        assertEquals(9 * 60 + 30, sep30.minuteOfDay)
        assertTrue(!chem.raw.contains("completions"))

        val studied = data.habits.first { it.id == "h-cnt" }
        assertTrue(studied.archived)
        assertEquals(8.0, studied.target, 0.0)
        assertEquals("Hour", studied.unitLabel)
    }

    @Test
    fun oneBrokenRecordIsSkippedNotFatal() {
        val data = StreakParser.parse(sample)
        // "n-bad" has no date or text; it must not stop the good note being read.
        assertTrue(data.notes.any { it.id == "n1" })
        assertEquals(LocalDate.of(2026, 9, 29), data.notes.first { it.id == "n1" }.date)
    }

    @Test
    fun todoDatesAndTimesAreKept() {
        val data = StreakParser.parse(sample)
        val dated = data.todos.first { it.id == "t1" }
        assertEquals(LocalDate.of(2026, 10, 9), dated.date)
        assertEquals(540, dated.minutes)
        assertEquals(90, dated.estimate)
        assertNull(data.todos.first { it.id == "t2" }.date)
    }

    @Test
    fun otherAppsFilesAreRefused() {
        try {
            StreakParser.parse("""{"app":"life-tracker","version":1}""")
            fail("should refuse")
        } catch (_: IllegalArgumentException) {
        }
        try {
            StreakParser.parse("not json")
            fail("should refuse")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun session(id: String, start: String, seconds: Int) =
        StreakFocus(id, "", 0, seconds, true, LocalDateTime.parse(start), "")

    @Test
    fun focusSessionBecomesOneEntry() {
        val pieces = FocusConverter.pieces(session("f1", "2026-09-26T14:31:40.605", 6381))
        assertEquals(1, pieces.size)
        assertEquals(LocalDate.of(2026, 9, 26), pieces[0].date)
        assertEquals(14 * 60 + 31, pieces[0].startMinute)
        assertEquals(14 * 60 + 31 + 106, pieces[0].endMinute) // 6381 s rounds to 106 min
        assertEquals("f1", pieces[0].sourceId)
    }

    @Test
    fun focusSessionOverMidnightIsSplit() {
        val pieces = FocusConverter.pieces(session("f9", "2026-09-29T23:30:00", 45 * 60))
        assertEquals(2, pieces.size)
        assertEquals(LocalDate.of(2026, 9, 29), pieces[0].date)
        assertEquals(23 * 60 + 30, pieces[0].startMinute)
        assertEquals(1439, pieces[0].endMinute)
        assertEquals(LocalDate.of(2026, 9, 30), pieces[1].date)
        assertEquals(0, pieces[1].startMinute)
        assertEquals(15, pieces[1].endMinute)
        assertEquals("f9", pieces[0].sourceId)
        assertEquals("f9-2", pieces[1].sourceId)
    }

    @Test
    fun tinySessionsAreIgnored() {
        assertTrue(FocusConverter.pieces(session("f", "2026-09-29T10:00:00", 59)).isEmpty())
    }

    @Test
    fun streakCategoriesMapToTimelineCategories() {
        assertEquals("Study", FocusConverter.timelineCategory("Self Study"))
        assertEquals("Study", FocusConverter.timelineCategory(null))
        assertEquals("Health", FocusConverter.timelineCategory("Mindfulness"))
        assertEquals("Exercise", FocusConverter.timelineCategory("Fitness"))
    }
}
