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
        meals = listOf(
            MealEntity("m1", "2026-10-04", 752, "breakfast", "Chole kulche", 100.0, 650.0, 100.0, 18.0, 21.0, "ont", "{\"a\":1}"),
        ),
        foodGoals = listOf(FoodGoalEntity("2026-10-04", 2672.88, 399.47, 73.97, 99.86)),
        water = listOf(WaterEntity("2026-10-07", 1250)),
        activityTypes = listOf(
            ActivityTypeEntity("Study", "Study", 0xFF17785AL, 480, 0, false, 2),
            ActivityTypeEntity("c-abc", "Reading \"fiction\"", 0xFFD6457FL, 90, 1, true, 8),
        ),
        moneyCategories = listOf(
            MoneyCategoryEntity("x-food", "Food", 0xFFC28410L, KIND_EXPENSE, 0, false),
            MoneyCategoryEntity("c-1", "Chai \"time\"", 0xFF2E9E6BL, KIND_INCOME, 3, true),
        ),
        moneyItems = listOf(
            MoneyItemEntity("i1", "Rent", KIND_EXPENSE, "x-bills", 8000.0, MoneyItemEntity.MONTHLY, 5, "2026-09-01", "", "2026-10", false, 0),
            MoneyItemEntity("i2", "Lassi", KIND_EXPENSE, "x-food", 40.5, MoneyItemEntity.OFTEN, 0, "", "", "", false, 1),
        ),
        moneyEntries = listOf(
            MoneyEntryEntity("auto|i1|2026-10", "2026-10-05", KIND_EXPENSE, "x-bills", "Rent", 8000.0, "", "i1"),
            MoneyEntryEntity("e1", "2026-10-07", KIND_INCOME, "i-pocket", "Pocket \"money\"", 5000.0, "line\ntwo", ""),
        ),
        usageSessions = listOf(
            UsageSessionEntity(UsageSessionEntity.idFor("com.a", 1_760_000_000_000L), "com.a", 1_760_000_000_000L, 1_760_000_060_000L),
            UsageSessionEntity(UsageSessionEntity.idFor("com.b.c", 1_760_000_100_000L), "com.b.c", 1_760_000_100_000L, 1_760_000_400_000L),
        ),
        usageApps = listOf(
            UsageAppEntity("com.a", "Anki \"cards\"", "Study", false),
            UsageAppEntity("com.b.c", "Launcher", "", true),
        ),
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
    fun versionTwoBackupsStillLoad() {
        val v2 = """{"app":"life-tracker","version":2,"exportedAt":"x","entries":[],"habits":[],"completions":[]}"""
        val back = BackupCodec.decode(v2)
        assertEquals(2, back.version)
        assertTrue(back.meals.isEmpty())
        assertTrue(back.water.isEmpty())
    }

    @Test
    fun versionThreeBackupsStillLoad() {
        val v3 = """{"app":"life-tracker","version":3,"exportedAt":"x","entries":[],"meals":[],"water":[]}"""
        val back = BackupCodec.decode(v3)
        assertEquals(3, back.version)
        assertTrue(back.activityTypes.isEmpty())
    }

    @Test
    fun versionFourBackupsStillLoad() {
        val v4 = """{"app":"life-tracker","version":4,"exportedAt":"x","entries":[],"activityTypes":[]}"""
        val back = BackupCodec.decode(v4)
        assertEquals(4, back.version)
        assertTrue(back.moneyEntries.isEmpty())
        assertTrue(back.moneyItems.isEmpty())
    }

    @Test
    fun versionFiveBackupsStillLoad() {
        val v5 = """{"app":"life-tracker","version":5,"exportedAt":"x","entries":[],"moneyItems":[]}"""
        val back = BackupCodec.decode(v5)
        assertEquals(5, back.version)
        assertTrue(back.usageSessions.isEmpty())
        assertTrue(back.usageApps.isEmpty())
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
