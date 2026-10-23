package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    private fun profile(
        id: String,
        condition: String,
        text: String = "",
        number: Double = 0.0,
        interval: Int = 5,
        priority: Int = 0,
        on: Int = 0,
        off: Int = 0,
        enabled: Boolean = true,
        deletedAt: Long = 0L,
        minMove: Int = 0,
        accuracy: String = "",
    ) = TrackingProfileEntity(
        id, id, condition, text, number, interval, minMove, accuracy, priority, on, off, enabled, deletedAt, 0L,
    )

    @Test
    fun everyConditionHoldsOnlyWhenItIsTrue() {
        val s = Situation(
            charging = true, carMode = true, bluetooth = setOf("AA:BB"), wifiConnected = true, wifiName = "\"Home Net\"",
            speedKmh = 30.0, still = true,
        )
        val none = Situation()
        assertTrue(ProfileRules.holds(profile("a", ProfileCondition.CHARGING), s))
        assertFalse(ProfileRules.holds(profile("a", ProfileCondition.CHARGING), none))
        assertTrue(ProfileRules.holds(profile("b", ProfileCondition.CAR_MODE), s))
        assertTrue(ProfileRules.holds(profile("c", ProfileCondition.BLUETOOTH, "aa:bb"), s))
        assertFalse(ProfileRules.holds(profile("c", ProfileCondition.BLUETOOTH, "CC:DD"), s))
        assertFalse(ProfileRules.holds(profile("c", ProfileCondition.BLUETOOTH, ""), s))
        assertTrue(ProfileRules.holds(profile("d", ProfileCondition.WIFI), s))
        assertFalse(ProfileRules.holds(profile("d", ProfileCondition.WIFI), none))
        assertTrue(ProfileRules.holds(profile("e", ProfileCondition.WIFI_NAMED, "home net"), s))
        assertFalse(ProfileRules.holds(profile("e", ProfileCondition.WIFI_NAMED, "Other"), s))
        assertFalse(ProfileRules.holds(profile("e", ProfileCondition.WIFI_NAMED, ""), s))
        assertTrue(ProfileRules.holds(profile("f", ProfileCondition.SPEED_ABOVE, number = 25.0), s))
        assertFalse(ProfileRules.holds(profile("f", ProfileCondition.SPEED_ABOVE, number = 35.0), s))
        assertTrue(ProfileRules.holds(profile("g", ProfileCondition.SPEED_BELOW, number = 35.0), s))
        assertFalse(ProfileRules.holds(profile("g", ProfileCondition.SPEED_BELOW, number = 25.0), s))
        // With no speed known neither speed condition holds.
        assertFalse(ProfileRules.holds(profile("f", ProfileCondition.SPEED_ABOVE, number = 1.0), none))
        assertFalse(ProfileRules.holds(profile("g", ProfileCondition.SPEED_BELOW, number = 99.0), none))
        assertTrue(ProfileRules.holds(profile("h", ProfileCondition.STATIONARY), s))
        assertFalse(ProfileRules.holds(profile("x", "something-new"), s))
    }

    @Test
    fun aProfileSwitchesOnAfterItsDelayAndOffAfterItsOtherDelay() {
        val p = profile("drive", ProfileCondition.CHARGING, on = 10, off = 60)
        var pick = ProfileRules.step(ProfileState(), listOf(p), Situation(charging = true), 1_000L)
        assertNull(pick.chosen)
        assertEquals(11_000L, pick.nextCheckMs)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(charging = true), 11_000L)
        assertEquals("drive", pick.chosen?.id)
        assertNull(pick.nextCheckMs)
        // The condition stops: still on until the switch-off delay runs out.
        pick = ProfileRules.step(pick.state, listOf(p), Situation(), 12_000L)
        assertEquals("drive", pick.chosen?.id)
        assertEquals(72_000L, pick.nextCheckMs)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(), 71_999L)
        assertEquals("drive", pick.chosen?.id)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(), 72_000L)
        assertNull(pick.chosen)
    }

    @Test
    fun aConditionThatReturnsBeforeTheSwitchOffDelayKeepsTheProfile() {
        val p = profile("a", ProfileCondition.CHARGING, off = 60)
        var pick = ProfileRules.step(ProfileState(), listOf(p), Situation(charging = true), 1_000L)
        assertEquals("a", pick.chosen?.id)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(), 5_000L)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(charging = true), 30_000L)
        // The countdown starts again the next time it stops.
        pick = ProfileRules.step(pick.state, listOf(p), Situation(), 40_000L)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(), 90_000L)
        assertEquals("a", pick.chosen?.id)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(), 100_000L)
        assertNull(pick.chosen)
    }

    @Test
    fun aConditionThatStopsBeforeTheSwitchOnDelayNeverSwitchesOn() {
        val p = profile("a", ProfileCondition.CHARGING, on = 30)
        var pick = ProfileRules.step(ProfileState(), listOf(p), Situation(charging = true), 1_000L)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(), 10_000L)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(charging = true), 20_000L)
        // Held since 20 s, not since 1 s.
        pick = ProfileRules.step(pick.state, listOf(p), Situation(charging = true), 40_000L)
        assertNull(pick.chosen)
        pick = ProfileRules.step(pick.state, listOf(p), Situation(charging = true), 50_000L)
        assertNotNull(pick.chosen)
    }

    @Test
    fun theHighestPriorityWinsAndTheFirstWinsATie() {
        val low = profile("low", ProfileCondition.CHARGING, priority = 1)
        val high = profile("high", ProfileCondition.WIFI, priority = 9)
        val twin = profile("twin", ProfileCondition.CHARGING, priority = 1)
        val both = Situation(charging = true, wifiConnected = true)
        assertEquals("high", ProfileRules.step(ProfileState(), listOf(high, low), both, 1L).chosen?.id)
        assertEquals("low", ProfileRules.step(ProfileState(), listOf(low, twin), Situation(charging = true), 1L).chosen?.id)
    }

    @Test
    fun switchedOffAndHiddenProfilesNeverApply() {
        val off = profile("off", ProfileCondition.CHARGING, enabled = false)
        val hidden = profile("hidden", ProfileCondition.CHARGING, deletedAt = 5L)
        assertNull(ProfileRules.step(ProfileState(), listOf(off, hidden), Situation(charging = true), 1L).chosen)
    }

    @Test
    fun aProfileTakenOutOfTheListIsForgotten() {
        val p = profile("a", ProfileCondition.CHARGING)
        val pick = ProfileRules.step(ProfileState(), listOf(p), Situation(charging = true), 1L)
        val after = ProfileRules.step(pick.state, emptyList(), Situation(charging = true), 2L)
        assertTrue(after.state.marks.isEmpty())
        assertNull(after.chosen)
    }

    @Test
    fun aProfileChangesTheIntervalMovementAndAccuracyAndNothingElse() {
        val base = TrackSettings(intervalSec = 30, minMoveM = 5, accuracyMode = AccuracyMode.BALANCED, accuracyLimitM = 80)
        val p = profile("car", ProfileCondition.CAR_MODE, interval = 2, minMove = 0, accuracy = AccuracyMode.HIGH)
        val e = ProfileRules.effective(base, p)
        assertEquals(2, e.settings.intervalSec)
        assertEquals(AccuracyMode.HIGH, e.settings.accuracyMode)
        assertEquals(80, e.settings.accuracyLimitM)
        assertEquals("car", e.profileId)
        // No accuracy level in the profile keeps yours.
        assertEquals(AccuracyMode.BALANCED, ProfileRules.effective(base, p.copy(accuracyMode = "")).settings.accuracyMode)
        assertEquals(20, ProfileRules.effective(base, p.copy(minMoveM = 20)).settings.minMoveM)
        // Nothing in force: your own settings.
        assertEquals(Effective(base, ""), ProfileRules.effective(base, null))
    }

    @Test
    fun aStillProfileAlwaysKeepsEveryReading() {
        val base = TrackSettings(minMoveM = 5)
        val p = profile("still", ProfileCondition.STATIONARY, interval = 600, minMove = 50)
        assertEquals(0, ProfileRules.effective(base, p).settings.minMoveM)
    }

    @Test
    fun profilesAreCheckedBeforeTheyAreSaved() {
        assertNull(ProfileRules.problem(profile("ok", ProfileCondition.CHARGING)))
        assertNotNull(ProfileRules.problem(profile("x", "nope")))
        assertNotNull(ProfileRules.problem(profile("w", ProfileCondition.WIFI_NAMED, "")))
        assertNull(ProfileRules.problem(profile("w", ProfileCondition.WIFI_NAMED, "Home")))
        assertNotNull(ProfileRules.problem(profile("b", ProfileCondition.BLUETOOTH, "")))
        assertNotNull(ProfileRules.problem(profile("s", ProfileCondition.SPEED_ABOVE, number = 0.0)))
        assertNull(ProfileRules.problem(profile("s", ProfileCondition.SPEED_ABOVE, number = 20.0)))
        assertNotNull(ProfileRules.problem(profile("i", ProfileCondition.CHARGING, interval = 0)))
        assertNotNull(ProfileRules.problem(profile("m", ProfileCondition.CHARGING, minMove = -1)))
        assertNotNull(ProfileRules.problem(profile("d", ProfileCondition.CHARGING, on = -1)))
        assertNotNull(ProfileRules.problem(profile("a", ProfileCondition.CHARGING, accuracy = "perfect")))
    }
}
