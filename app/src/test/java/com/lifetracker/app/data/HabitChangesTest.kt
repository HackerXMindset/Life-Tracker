package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitChangesTest {
    @Test
    fun stampingAddsAndNeverGoesBackwards() {
        val first = HabitChanges.stamp(emptyMap(), listOf("a", "b"), 100L)
        assertEquals(mapOf("a" to 100L, "b" to 100L), first)
        val later = HabitChanges.stamp(first, listOf("a"), 250L)
        assertEquals(250L, later["a"])
        assertEquals(100L, later["b"])
        val older = HabitChanges.stamp(later, listOf("a"), 50L)
        assertEquals(250L, older["a"])
    }

    @Test
    fun blankIdsAreIgnored() {
        assertTrue(HabitChanges.stamp(emptyMap(), listOf("", "  "), 1L).isEmpty())
    }

    @Test
    fun savesAndReadsBack() {
        val map = mapOf("a" to 1_760_000_000_000L, "b" to 5L)
        assertEquals(map, HabitChanges.parse(HabitChanges.toJson(map)))
    }

    @Test
    fun damagedOrEmptyTextGivesAnEmptyList() {
        assertTrue(HabitChanges.parse(null).isEmpty())
        assertTrue(HabitChanges.parse("").isEmpty())
        assertTrue(HabitChanges.parse("not json").isEmpty())
        assertEquals(mapOf("ok" to 3L), HabitChanges.parse("""{"ok":3,"bad":"x","neg":-4}"""))
    }
}
