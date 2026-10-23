package com.lifetracker.app.data.streak

import com.lifetracker.app.data.CategoryEntity
import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.FocusSessionEntity
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitForm
import com.lifetracker.app.data.NoteEntity
import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakBackupWriterTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val now = LocalDateTime.of(2026, 10, 14, 21, 30, 5)
    private val read = HabitEditor.build(HabitForm(name = "Read", category = "Study"), null, "h1", 0, today, now)
    private val gym = HabitEditor.build(HabitForm(name = "Gym"), null, "h2", 1, today, now)
    private val study = CategoryEntity("c1", "Study", 0xFF2E7D32, 0)
    private val other = CategoryEntity("c2", "Body", 0xFF1565C0, 1)

    private fun build(
        changes: Map<String, Long>,
        completions: List<CompletionEntity> = emptyList(),
        notes: List<NoteEntity> = emptyList(),
    ) = StreakBackupWriter.build("ab12cd34", now, listOf(read, gym), completions, notes, listOf(study, other), changes)

    @Test
    fun deviceIdIsEightHexAndNamesFollowStreaksPattern() {
        val id = StreakBackupWriter.newDeviceId()
        assertTrue(Regex("[0-9a-f]{8}").matches(id))
        val name = StreakBackupWriter.fileName(now, "ab12cd34")
        assertEquals("streak_backup_2026-10-14_21-30-05_ab12cd34.json", name)
        assertTrue(StreakBackupWriter.isOurs(name, "ab12cd34"))
        assertFalse(StreakBackupWriter.isOurs(name, "ffffffff"))
        assertEquals("04-10-2026", StreakBackupWriter.dayKey(LocalDate.of(2026, 10, 4)))
        // Streak's own files (another device) are not ours, whether .json or .zip.
        assertFalse(StreakBackupWriter.isFromDevice("streak_backup_2026-10-14_21-30-05_ffffffff.json", "ab12cd34"))
        assertTrue(StreakBackupWriter.isFromDevice("streak_backup_2026-10-14_21-30-05_ab12cd34.zip", "ab12cd34"))
        assertFalse(StreakBackupWriter.isFromDevice("streak_backup_2026-10-14_21-30-05.json", "ab12cd34"))
    }

    @Test
    fun nothingChangedMeansNoFile() {
        assertNull(build(emptyMap()))
        assertNull(build(mapOf("unknown" to 5L)))
    }

    @Test
    fun onlyChangedHabitsAreSentWithTheirDaysAndStamps() {
        val days = listOf(
            CompletionEntity("h1", "2026-10-04", 1.0, null),
            CompletionEntity("h1", "2026-10-13", 2.0, 8 * 60 + 5),
            CompletionEntity("h2", "2026-10-13", 1.0, null),
        )
        val text = build(mapOf("h1" to 1_000L), days)!!
        val root = JSONObject(text)
        assertEquals("streak", root.getString("app"))
        assertEquals(2, root.getInt("version"))
        assertEquals("ab12cd34", root.getString("device"))
        assertEquals("2026-10-14T21:30:05.000000", root.getString("exportedAt"))
        val habits = root.getJSONArray("habits")
        assertEquals(1, habits.length())
        val h = habits.getJSONObject(0)
        assertEquals("h1", h.getString("id"))
        assertEquals("Read", h.getString("name"))
        val c = h.getJSONObject("completions")
        assertEquals(2, c.length())
        assertEquals(1.0, c.getJSONObject("04-10-2026").getDouble("numberOfCompletions"), 0.0)
        val second = c.getJSONObject("13-10-2026")
        assertEquals("13-10-2026", second.getString("date"))
        assertEquals(8, second.getInt("hour"))
        assertEquals(5, second.getInt("minute"))
        assertFalse(c.getJSONObject("04-10-2026").has("hour"))
        assertEquals(1_000L, root.getJSONObject("changes").getLong("h1"))
        assertFalse(root.getJSONObject("changes").has("h2"))
        assertEquals(0, root.getJSONObject("deleted").length())
    }

    @Test
    fun onlyCategoriesOfSentHabitsAreIncluded() {
        val root = JSONObject(build(mapOf("h1" to 1L))!!)
        val cats = root.getJSONArray("categories")
        assertEquals(1, cats.length())
        assertEquals("Study", cats.getJSONObject(0).getString("name"))
        val none = JSONObject(build(mapOf("h2" to 1L))!!)
        assertEquals(0, none.getJSONArray("categories").length())
    }

    @Test
    fun notesAreWrittenInStreaksShapeAndCanBeSentAlone() {
        val dated = NoteEntity("n1", "h1", "2026-10-13", 0, "felt good", null, "2026-10-13T09:00:00")
        val undated = NoteEntity("n2", "h1", "", 1, "plan", 540, "2026-10-13T10:00:00")
        val root = JSONObject(build(mapOf("n1" to 7L, "n2" to 8L), notes = listOf(dated, undated))!!)
        assertEquals(0, root.getJSONArray("habits").length())
        val notes = root.getJSONArray("notes")
        assertEquals(2, notes.length())
        val n = notes.getJSONObject(0)
        assertEquals("13-10-2026", n.getString("date"))
        assertEquals("felt good", n.getString("text"))
        assertTrue(n.isNull("minutes"))
        assertEquals(0, n.getJSONArray("photos").length())
        assertEquals("", notes.getJSONObject(1).getString("date"))
        assertEquals(540, notes.getJSONObject(1).getInt("minutes"))
        assertEquals(8L, root.getJSONObject("changes").getLong("n2"))
    }

    @Test
    fun theFileReadsBackWithOurOwnStreakParser() {
        val days = listOf(CompletionEntity("h1", "2026-10-13", 1.0, null))
        val note = NoteEntity("n1", "h1", "2026-10-13", 0, "hello", null, "2026-10-13T09:00:00")
        val data = StreakParser.parse(build(mapOf("h1" to 1L, "n1" to 2L), days, listOf(note))!!)
        assertEquals(1, data.habits.size)
        assertEquals("Read", data.habits[0].name)
        assertEquals(LocalDate.of(2026, 10, 13), data.habits[0].completions.single().date)
        assertEquals(LocalDate.of(2026, 10, 13), data.notes.single().date)
        assertEquals("hello", data.notes.single().text)
        assertNotNull(data.categories.singleOrNull { it.name == "Study" })
    }

    @Test
    fun theTimesOfEveryCheckTravelAsStreaksMarks() {
        val days = listOf(
            CompletionEntity("h1", "2026-10-13", 3.0, 905, "480,600,905"),
            CompletionEntity("h1", "2026-10-12", 1.0, null),
        )
        val text = build(mapOf("h1" to 1L), days)!!
        val c = JSONObject(text).getJSONArray("habits").getJSONObject(0).getJSONObject("completions")
        val withTimes = c.getJSONObject("13-10-2026")
        assertEquals(3, withTimes.getJSONArray("marks").length())
        assertEquals(600, withTimes.getJSONArray("marks").getInt(1))
        // The last time is also written the way Streak always has.
        assertEquals(15, withTimes.getInt("hour"))
        assertEquals(5, withTimes.getInt("minute"))
        // A day with no times has no marks.
        assertFalse(c.getJSONObject("12-10-2026").has("marks"))
        // And our own reader gets them back.
        val back = StreakParser.parse(text).habits.single().completions.associateBy { it.date }
        assertEquals(listOf(480, 600, 905), back.getValue(LocalDate.of(2026, 10, 13)).marks)
        assertEquals(emptyList<Int>(), back.getValue(LocalDate.of(2026, 10, 12)).marks)
    }

    @Test
    fun theTickedStepsOfADayTravelAsStreaksSteps() {
        val days = listOf(CompletionEntity("h1", "2026-10-13", 2.0, null, "", "a,b"), CompletionEntity("h1", "2026-10-12", 1.0, null))
        val text = build(mapOf("h1" to 1L), days)!!
        val c = JSONObject(text).getJSONArray("habits").getJSONObject(0).getJSONObject("completions")
        assertEquals("b", c.getJSONObject("13-10-2026").getJSONArray("steps").getString(1))
        assertFalse(c.getJSONObject("12-10-2026").has("steps"))
        val back = StreakParser.parse(text).habits.single().completions.associateBy { it.date }
        assertEquals(listOf("a", "b"), back.getValue(LocalDate.of(2026, 10, 13)).steps)
    }

    @Test
    fun deviceIsReadFromTheFileName() {
        assertEquals("ab12cd34", StreakBackupWriter.deviceOf("streak_backup_2026-10-14_21-30-05_ab12cd34.json"))
        assertEquals("", StreakBackupWriter.deviceOf("streak_backup_2026-10-14_21-30-05.json"))
        assertEquals("", StreakBackupWriter.deviceOf("notes.json"))
    }

    @Test
    fun aDeletedHabitIsOnlyListedAsDeletedNeverSentAsAHabit() {
        val text = StreakBackupWriter.build(
            "ab12cd34", now, listOf(read.copy(deletedAt = 5_000L), gym), emptyList(), emptyList(), listOf(study, other),
            changes = mapOf("h2" to 9L), deleted = mapOf("h1" to 5_000L),
        )!!
        val root = JSONObject(text)
        assertEquals(5_000L, root.getJSONObject("deleted").getLong("h1"))
        assertEquals(1, root.getJSONArray("habits").length())
        assertEquals("h2", root.getJSONArray("habits").getJSONObject(0).getString("id"))
    }

    @Test
    fun aDeletionAloneStillMakesAFile() {
        val text = StreakBackupWriter.build(
            "ab12cd34", now, listOf(read.copy(deletedAt = 5_000L)), emptyList(), emptyList(), listOf(study),
            changes = emptyMap(), deleted = mapOf("h1" to 5_000L),
        )
        assertNotNull(text)
        assertEquals(0, JSONObject(text!!).getJSONArray("habits").length())
    }

    @Test
    fun clearedProgressSendsTheTimeAndOnlyLaterDays() {
        val clearedAt = LocalDate.of(2026, 10, 10).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val days = listOf(
            CompletionEntity("h1", "2026-10-09", 1.0, null),
            CompletionEntity("h1", "2026-10-10", 1.0, null),
            CompletionEntity("h1", "2026-10-12", 1.0, null),
        )
        val text = StreakBackupWriter.build(
            "ab12cd34", now, listOf(read.copy(clearedAt = clearedAt)), days, emptyList(), listOf(study), changes = mapOf("h1" to 1L),
        )!!
        val root = JSONObject(text)
        assertEquals(clearedAt, root.getJSONObject("cleared").getLong("h1"))
        val sent = root.getJSONArray("habits").getJSONObject(0).getJSONObject("completions")
        assertEquals(setOf("12-10-2026"), sent.keys().asSequence().toSet())
    }

    @Test
    fun aFocusSessionIsSentInStreaksShapeOnceItIsNotedAsChanged() {
        val started = LocalDateTime.of(2026, 10, 14, 9, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val mine = FocusSessionEntity("f1", "h1", 25, 1500, true, started, true, "Deep work")
        val plain = FocusSessionEntity("f2", "", 0, 95, false, started)
        val unchanged = FocusSessionEntity("f3", "h1", 25, 600, false, started)
        val text = StreakBackupWriter.build(
            "ab12cd34", now, listOf(read, gym), emptyList(), emptyList(), listOf(study, other),
            mapOf("f1" to 5L, "f2" to 6L), focus = listOf(mine, plain, unchanged),
        )!!
        val root = JSONObject(text)
        val sent = root.getJSONArray("focus")
        assertEquals(2, sent.length())
        val o = sent.getJSONObject(0)
        assertEquals("f1", o.getString("id"))
        assertEquals("2026-10-14T09:30:00.000", o.getString("startedAt"))
        assertTrue(o.getBoolean("counted"))
        assertEquals("Deep work", o.getString("label"))
        assertFalse(sent.getJSONObject(1).has("counted"))
        assertFalse(sent.getJSONObject(1).has("label"))
        assertEquals(5L, root.getJSONObject("changes").getLong("f1"))
        // Our own parser reads it back.
        val back = StreakParser.parse(text).focus
        assertEquals(listOf("f1", "f2"), back.map { it.id })
        assertEquals(1500, back[0].seconds)
        assertTrue(back[0].counted)
        // A session alone is enough to make a file.
        assertNotNull(
            StreakBackupWriter.build("ab12cd34", now, listOf(read), emptyList(), emptyList(), emptyList(), mapOf("f1" to 5L), focus = listOf(mine)),
        )
        assertNull(
            StreakBackupWriter.build("ab12cd34", now, listOf(read), emptyList(), emptyList(), emptyList(), emptyMap(), focus = listOf(mine)),
        )
    }
}
