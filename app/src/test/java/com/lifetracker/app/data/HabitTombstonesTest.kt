package com.lifetracker.app.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTombstonesTest {
    private val empty = HabitTombstones.State(emptyMap(), emptyMap())

    @Test fun aChangeMovesForwardOnlyAndUndeletes() {
        val s = HabitTombstones.deleted(HabitTombstones.changed(empty, listOf("h1"), 100), listOf("h1"), 200)
        assertEquals(emptyMap<String, Long>(), s.changes)
        assertEquals(mapOf("h1" to 200L), s.deleted)
        val back = HabitTombstones.changed(s, listOf("h1", " "), 300)
        assertEquals(mapOf("h1" to 300L), back.changes)
        assertTrue(back.deleted.isEmpty())
        // An older time never replaces a newer one.
        assertEquals(300L, HabitTombstones.changed(back, listOf("h1"), 250).changes["h1"])
    }

    @Test fun deletingTakesItOutOfTheChangedList() {
        val s = HabitTombstones.deleted(HabitTombstones.changed(empty, listOf("h1", "n1"), 100), listOf("h1", "n1"), 150)
        assertTrue(s.changes.isEmpty())
        assertEquals(setOf("h1", "n1"), s.deleted.keys)
    }

    @Test fun deletionsAreForgottenAfterOneHundredTwentyDays() {
        val day = 24L * 60 * 60 * 1000
        val now = 1_000 * day
        val kept = HabitTombstones.forgetOld(mapOf("old" to now - 121 * day, "recent" to now - 119 * day), now)
        assertEquals(setOf("recent"), kept.keys)
    }

    @Test fun aDeletionFromStreakIsAppliedUnlessKnownOrEditedLater() {
        assertTrue(HabitTombstones.shouldApply(100, null, null))
        assertFalse(HabitTombstones.shouldApply(100, 200L, null)) // edited here after the deletion
        assertTrue(HabitTombstones.shouldApply(100, 100L, null)) // edited at the same moment: the deletion wins
        assertFalse(HabitTombstones.shouldApply(100, null, 100L)) // already known
        assertTrue(HabitTombstones.shouldApply(150, null, 100L)) // a newer deletion
    }

    @Test fun clearedProgressOnlyCountsLaterDays() {
        val utc = ZoneId.of("UTC")
        val clearedAt = LocalDate.of(2026, 10, 10).atTime(12, 0).atZone(utc).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 10), HabitClears.lastClearedDay(clearedAt, utc))
        assertFalse(HabitClears.counts(clearedAt, LocalDate.of(2026, 10, 10), utc))
        assertFalse(HabitClears.counts(clearedAt, LocalDate.of(2026, 10, 9), utc))
        assertTrue(HabitClears.counts(clearedAt, LocalDate.of(2026, 10, 11), utc))
        assertTrue(HabitClears.counts(0L, LocalDate.of(2020, 1, 1), utc))
    }

    @Test fun visibleDaysDropClearedOnesButNeverEraseAnything() {
        val utc = ZoneId.of("UTC")
        val clearedAt = LocalDate.of(2026, 10, 10).atTime(12, 0).atZone(utc).toInstant().toEpochMilli()
        val days = listOf(
            CompletionEntity("h1", "2026-10-09", 1.0, null),
            CompletionEntity("h1", "2026-10-10", 1.0, null),
            CompletionEntity("h1", "2026-10-12", 1.0, null),
            CompletionEntity("h1", "not a date", 1.0, null),
        )
        assertEquals(listOf("2026-10-12", "not a date"), HabitClears.visible(days, clearedAt, utc).map { it.date })
        assertEquals(4, days.size)
        assertEquals(days, HabitClears.visible(days, 0L, utc))
    }
}
