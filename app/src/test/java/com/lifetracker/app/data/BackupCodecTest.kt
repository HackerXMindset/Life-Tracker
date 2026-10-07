package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val sample = listOf(
        EntryEntity(1, "2026-10-04", 540, 660, "Study", "Polity \"revision\"", "Chapter 3\nline two"),
        EntryEntity(2, "2026-10-04", 700, null, "Event", "Moved to Delhi", ""),
        EntryEntity(7, "2026-10-05", 0, 480, "Sleep", "Sleep", "ünïcode ✓"),
    )

    @Test
    fun roundTripKeepsEveryField() {
        val text = BackupCodec.encode(sample, "2026-10-07T09:00:00")
        val back = BackupCodec.decode(text)
        assertEquals(sample, back.entries)
        assertEquals(1, back.version)
        assertEquals("2026-10-07T09:00:00", back.exportedAt)
    }

    @Test
    fun momentKeepsNullEndTime() {
        val back = BackupCodec.decode(BackupCodec.encode(sample, "x"))
        assertNull(back.entries[1].endMinute)
    }

    @Test
    fun emptyBackupIsValid() {
        assertTrue(BackupCodec.decode(BackupCodec.encode(emptyList(), "x")).entries.isEmpty())
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
