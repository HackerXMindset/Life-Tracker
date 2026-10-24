package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTextTest {
    @Test fun cleanWifiDropsBlanksAndRepeats() {
        assertEquals("Home-5G\nOffice", PauseText.cleanWifi("  Home-5G \n\n home-5g\nOffice\n  "))
    }

    @Test fun addWifiKeepsOnceAndIgnoresBlank() {
        assertEquals("A\nB", PauseText.addWifi("A", "B"))
        assertEquals("A", PauseText.addWifi("A", "a"))
        assertEquals("A", PauseText.addWifi("A", "  "))
    }

    @Test fun problemsAreCaught() {
        assertNull(PauseText.problem(PauseSettings()))
        assertNotNull(PauseText.problem(PauseSettings(heartbeatMin = -1)))
        assertNotNull(PauseText.problem(PauseSettings(heartbeatMin = 1441)))
        assertNotNull(PauseText.problem(PauseSettings(entryDelaySec = -2)))
        assertNotNull(PauseText.problem(PauseSettings(entryDelaySec = 3601)))
        assertNull(PauseText.problem(PauseSettings(heartbeatMin = 1440, entryDelaySec = 3600)))
        assertNull(PauseText.problem(PauseSettings(entryDelaySec = 0)))
    }

    @Test fun offReadsAsNotAZone() {
        assertTrue(PauseText.describe(PauseSettings(pauseHere = false, motionless = true)).startsWith("Not a pause zone"))
    }

    @Test fun sentenceNamesEveryPart() {
        val s = PauseSettings(true, "Home-5G\nHome-2G", true, 10, 105)
        assertEquals(
            "Recording pauses 1 min 45 s after you arrive. The GPS also switches off on the Wi-Fi Home-5G or Home-2G and when you are still. " +
                "A reading every 10 min shows you are still here. A reading at the centre is added when you leave. The stay still shows as a visit.",
            PauseText.describe(s),
        )
    }

    @Test fun automaticAndImmediateDelays() {
        assertTrue(PauseText.describe(PauseSettings(true)).startsWith("Recording pauses after about 3½ readings."))
        assertTrue(PauseText.describe(PauseSettings(true, entryDelaySec = 0)).startsWith("Recording pauses as soon as you arrive."))
    }

    @Test fun settingsComeFromAPlace() {
        val p = PlaceEntity("p1", "Home", "home", 1.0, 2.0, 50, false, 1L, pauseHere = true, pauseWifi = "X", pauseMotionless = true, heartbeatMin = 5, entryDelaySec = 30)
        assertEquals(PauseSettings(true, "X", true, 5, 30), PauseSettings.of(p))
    }
}
