package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTextTest {
    private fun profile(
        condition: String,
        text: String = "",
        number: Double = 0.0,
        interval: Int = 5,
        minMove: Int = 0,
        accuracy: String = "",
        on: Int = 0,
        off: Int = 0,
        enabled: Boolean = true,
    ) = TrackingProfileEntity("p", "P", condition, text, number, interval, minMove, accuracy, 0, on, off, enabled, 0L, 0L)

    @Test
    fun aSimpleRuleReadsBackAsOneSentence() {
        assertEquals(
            "When charging, record every 5 s, keeping every reading.",
            ProfileText.describe(profile(ProfileCondition.CHARGING)),
        )
    }

    @Test
    fun everyPartOfARuleIsInTheSentence() {
        val p = profile(ProfileCondition.SPEED_ABOVE, number = 25.0, interval = 90, minMove = 15, accuracy = AccuracyMode.HIGH, on = 10, off = 120)
        assertEquals(
            "When moving faster than 25 km/h, record every 1 min 30 s, keeping a reading only when it moved at least 15 m, at high accuracy. " +
                "It switches on after 10 s and stays on for 2 min after it ends.",
            ProfileText.describe(p),
        )
    }

    @Test
    fun eachConditionHasItsOwnWords() {
        assertTrue(ProfileText.describe(profile(ProfileCondition.CAR_MODE)).startsWith("When the phone is in car mode,"))
        assertTrue(ProfileText.describe(profile(ProfileCondition.WIFI)).startsWith("When connected to Wi-Fi,"))
        assertTrue(ProfileText.describe(profile(ProfileCondition.WIFI_NAMED, "Home")).startsWith("When connected to the Wi-Fi \"Home\","))
        assertTrue(ProfileText.describe(profile(ProfileCondition.SPEED_BELOW, number = 5.5)).startsWith("When moving slower than 5.5 km/h,"))
        assertTrue(ProfileText.describe(profile(ProfileCondition.STATIONARY)).startsWith("When the phone has been still,"))
        assertTrue(ProfileText.describe(profile(ProfileCondition.BLUETOOTH, "")).startsWith("When a Bluetooth device is connected,"))
    }

    @Test
    fun aStillProfileAlwaysKeepsEveryReadingWhateverTheLimitSays() {
        assertTrue(ProfileText.describe(profile(ProfileCondition.STATIONARY, minMove = 50)).contains("keeping every reading"))
    }

    @Test
    fun aSwitchedOffProfileSaysSo() {
        assertTrue(ProfileText.describe(profile(ProfileCondition.CHARGING, enabled = false)).endsWith("(Switched off.)"))
    }

    @Test
    fun secondsAreWrittenInTheShortestWay() {
        assertEquals("1 s", ProfileText.seconds(1))
        assertEquals("59 s", ProfileText.seconds(59))
        assertEquals("1 min", ProfileText.seconds(60))
        assertEquals("5 min", ProfileText.seconds(300))
        assertEquals("2 min 5 s", ProfileText.seconds(125))
    }

    @Test
    fun aBluetoothDeviceKeepsItsNameAndItsAddress() {
        val packed = ProfileText.packDevice("AA:BB:CC", "My car")
        assertEquals("AA:BB:CC", ProfileText.deviceAddress(packed))
        assertEquals("My car", ProfileText.deviceName(packed))
        assertEquals("AA:BB:CC", ProfileText.packDevice("AA:BB:CC", ""))
        assertEquals("", ProfileText.deviceName("AA:BB:CC"))
        val p = profile(ProfileCondition.BLUETOOTH, packed)
        assertTrue(ProfileText.describe(p).startsWith("When connected to My car,"))
        // The condition holds by address, in any case.
        assertTrue(ProfileRules.holds(p, Situation(bluetooth = setOf("aa:bb:cc"))))
        assertTrue(!ProfileRules.holds(p, Situation(bluetooth = setOf("DD:EE"))))
    }

    @Test
    fun everyConditionHasALabel() {
        ProfileCondition.all.forEach { assertTrue(ProfileText.conditionLabel(it) != "Unknown") }
        assertEquals("Unknown", ProfileText.conditionLabel("x"))
    }
}
