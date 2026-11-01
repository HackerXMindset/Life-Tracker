package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSettingsSnapshotTest {
    @Test
    fun everyKindOfSettingComesBackAsItWas() {
        val text = SyncSettingsSnapshot.encode(
            mapOf(
                "theme" to mapOf("dark" to true, "accent" to 7, "big" to 1234567890123L, "scale" to 1.25f, "name" to "paper", "tabs" to setOf("b", "a")),
            ),
        )
        val back = SyncSettingsSnapshot.decode(text)["theme"]!!.associateBy { it.key }
        assertEquals(true, back["dark"]!!.value)
        assertEquals(7, back["accent"]!!.value)
        assertEquals(1234567890123L, back["big"]!!.value)
        assertEquals(1.25f, back["scale"]!!.value)
        assertEquals("paper", back["name"]!!.value)
        assertEquals(setOf("a", "b"), back["tabs"]!!.value)
        assertEquals("set", back["tabs"]!!.kind)
    }

    @Test
    fun onlyTheListedFilesAreEverCopied() {
        val text = SyncSettingsSnapshot.encode(
            mapOf("theme" to mapOf("dark" to true), "places" to mapOf("tracking" to true), "sync" to mapOf("device_id" to "x")),
        )
        assertEquals(listOf("theme"), SyncSettingsSnapshot.decode(text).keys.toList())
        // Even a hand-made message that names another file is ignored on the receiving phone.
        val sneaky = """{"places":{"tracking":{"t":"b","v":true}},"map":{"style":{"t":"s","v":"x"}}}"""
        assertEquals(listOf("map"), SyncSettingsSnapshot.decode(sneaky).keys.toList())
    }

    @Test
    fun trackingPermissionsAndTheSyncLoginAreNotInTheList() {
        listOf("places", "sync", "usage", "calls", "charging", "health", "data_sources", "export", "habit_sync", "focus", "applog").forEach {
            assertFalse(it, it in SyncSettingsSnapshot.FILES)
        }
        assertTrue("theme" in SyncSettingsSnapshot.FILES)
    }

    @Test
    fun unknownKindsAreLeftOut() {
        val text = SyncSettingsSnapshot.encode(mapOf("theme" to mapOf("odd" to listOf(1, 2), "ok" to 1)))
        assertEquals(listOf("ok"), SyncSettingsSnapshot.decode(text)["theme"]!!.map { it.key })
        assertNull(SyncSettingsSnapshot.decode("""{"theme":{"x":{"t":"zzz","v":1}}}""")["theme"]!!.firstOrNull())
    }

    @Test
    fun theSameSettingsAlwaysMakeTheSameText() {
        val a = SyncSettingsSnapshot.encode(mapOf("theme" to mapOf("b" to 1, "a" to 2), "map" to mapOf("z" to true)))
        val b = SyncSettingsSnapshot.encode(mapOf("map" to mapOf("z" to true), "theme" to mapOf("a" to 2, "b" to 1)))
        assertEquals(a, b)
    }
}
