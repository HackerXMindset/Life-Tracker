package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEverythingTest {
    private val settings: Map<String, Map<String, Any>> = mapOf(
        "theme" to mapOf("dark" to true, "accent" to 3, "since" to 1_760_000_000_000L, "scale" to 1.25f, "name" to "Ünï \"q\"", "tabs" to setOf("b", "a")),
        "places" to mapOf("enabled" to false),
    )

    private fun data(version: Int = BackupCodec.FORMAT_VERSION) = BackupData(
        version = version,
        exportedAt = "2026-11-04T09:00:00",
        entries = emptyList(),
        settings = settings,
        mapAreas = listOf(
            MapAreaEntity(
                "a1", "Campus", "s1", "https://x/style", 77.1, 28.5, 77.3, 28.7, 8, 14, "place", "p1", "sig", AreaState.DONE,
                1_000L, 2_000L, 500L, 1_000_000L, 600L, 42L, "note",
            ),
        ),
    )

    @Test fun everySettingComesBackWithItsType() {
        val back = BackupCodec.decode(BackupCodec.encode(data()))
        assertEquals(settings, back.settings)
        assertEquals(true, back.settings["theme"]!!["dark"])
        assertTrue(back.settings["theme"]!!["since"] is Long)
        assertTrue(back.settings["theme"]!!["accent"] is Int)
        assertTrue(back.settings["theme"]!!["scale"] is Float)
        assertEquals(setOf("a", "b"), back.settings["theme"]!!["tabs"])
    }

    @Test fun anAreaComesBackAsRememberedButNotDownloaded() {
        val a = BackupCodec.decode(BackupCodec.encode(data())).mapAreas.single()
        assertEquals("Campus", a.name)
        assertEquals(28.7, a.north, 1e-9)
        assertEquals(AreaState.REMOVED, a.state)
        assertEquals(0L, a.tiles)
        assertEquals(0L, a.bytes)
        assertEquals(-1L, a.regionId)
    }

    @Test fun aBackupWithOnlySettingsIsNotEmpty() {
        assertFalse(data().isEmpty)
        assertTrue(BackupData(BackupCodec.FORMAT_VERSION, "x", emptyList()).isEmpty)
    }

    @Test fun anOlderBackupHasNoSettingsAndIsToldSo() {
        val old = """{"app":"life-tracker","version":32,"exportedAt":"x","entries":[]}"""
        val back = BackupCodec.decode(old)
        assertTrue(back.settings.isEmpty())
        assertTrue(back.mapAreas.isEmpty())
        assertFalse(BackupSummary.hasSettings(back))
        assertTrue(BackupSummary.hasSettings(data()))
    }

    @Test fun phoneBoundFilesStayOutOfTheBackup() {
        assertTrue(SettingsBackup.included("theme"))
        assertTrue(SettingsBackup.included("habit_reminders"))
        assertTrue(SettingsBackup.included("trip_guess"))
        assertFalse(SettingsBackup.included("data_sources"))
        assertFalse(SettingsBackup.included("applog"))
        assertFalse(SettingsBackup.included("focus-state"))
        assertFalse(SettingsBackup.included("habit_sync"))
        assertFalse(SettingsBackup.included("WebViewChromiumPrefs"))
        assertFalse(SettingsBackup.included("androidx.work.util.preferences"))
        assertFalse(SettingsBackup.included("com.google.android.gms.appid"))
        assertFalse(SettingsBackup.included(""))
    }

    @Test fun theSummaryListsWhatIsInsideAndSkipsWhatIsEmpty() {
        val lines = BackupSummary.lines(data()).associate { it.label to it.count }
        assertEquals(7, lines["Settings"])
        assertEquals(1, lines.entries.count { it.key.startsWith("Offline map areas") && it.value == 1 })
        assertFalse(lines.containsKey("Trips"))
        assertTrue(BackupSummary.text(data()).contains("Settings: 7"))
    }

    @Test fun theFormatVersionMovedOn() {
        assertTrue(BackupCodec.FORMAT_VERSION >= 33)
    }
}
