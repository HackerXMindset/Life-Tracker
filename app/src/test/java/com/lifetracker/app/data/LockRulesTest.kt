package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRulesTest {
    @Test
    fun modesAndStrictnessAreFoundByKey() {
        assertEquals(LockMode.OFF, LockMode.of(null))
        assertEquals(LockMode.OFF, LockMode.of("nonsense"))
        assertEquals(LockMode.FACE, LockMode.of("face"))
        assertEquals(Strictness.NORMAL, Strictness.of(null))
        assertEquals(Strictness.STRICT, Strictness.of("strict"))
        assertTrue(Strictness.RELAXED.threshold < Strictness.NORMAL.threshold && Strictness.NORMAL.threshold < Strictness.STRICT.threshold)
    }

    @Test
    fun theStayTimesAreTheOnesOffered() {
        assertEquals(listOf(0, 1, 5, 15, 30, 60), LockRules.STAY_OPTIONS.map { it.first })
        assertEquals("Every time", LockRules.stayLabel(0))
        assertEquals("1 hour", LockRules.stayLabel(60))
        assertEquals(5, LockRules.cleanStay(7))
        assertEquals(15, LockRules.cleanStay(15))
    }

    @Test
    fun aScreenLocksAgainAfterTheChosenTime() {
        assertEquals(1_000L, LockRules.lockedAgainAt(1_000L, 0))
        assertEquals(1_000L + 15 * 60_000L, LockRules.lockedAgainAt(1_000L, 15))
        assertEquals(1_000L + 5 * 60_000L, LockRules.lockedAgainAt(1_000L, 99))
    }

    @Test
    fun theTimerDecidesWhetherAScreenIsStillOpen() {
        assertTrue(LockRules.isOpen(LockRules.OPEN, 5_000L))
        assertFalse(LockRules.isOpen(0L, 5_000L))
        assertFalse(LockRules.isOpen(5_000L, 5_000L))
        assertTrue(LockRules.isOpen(5_001L, 5_000L))
        assertEquals(LockRules.OPEN, LockRules.onReturn(10_000L, 5_000L))
        assertEquals(0L, LockRules.onReturn(4_000L, 5_000L))
        // "Every time": left at 5,000 and back at 5,000 or later is locked.
        assertEquals(0L, LockRules.onReturn(LockRules.lockedAgainAt(5_000L, 0), 5_000L))
    }

    @Test
    fun faceScanWithNoFaceFallsBackToThePhoneLock() {
        assertEquals(LockMode.PHONE, LockRules.effectiveMode(LockMode.FACE, false))
        assertEquals(LockMode.FACE, LockRules.effectiveMode(LockMode.FACE, true))
        assertEquals(LockMode.OFF, LockRules.effectiveMode(LockMode.OFF, false))
        assertEquals(LockMode.PHONE, LockRules.effectiveMode(LockMode.PHONE, true))
    }

    @Test
    fun theThirdMissedScanAsksForThePin() {
        assertFalse(LockRules.needsPin(2))
        assertTrue(LockRules.needsPin(3))
        assertTrue(LockRules.needsPin(4))
    }

    @Test
    fun settingsLeftOpenIsNoticed() {
        val food = mapOf("Food" to LockMode.PHONE, "Settings" to LockMode.OFF)
        assertTrue(LockRules.settingsLeftOpen(food, "Settings"))
        assertFalse(LockRules.settingsLeftOpen(food + ("Settings" to LockMode.FACE), "Settings"))
        assertFalse(LockRules.settingsLeftOpen(mapOf("Food" to LockMode.OFF, "Settings" to LockMode.OFF), "Settings"))
        assertFalse(LockRules.settingsLeftOpen(mapOf("Settings" to LockMode.PHONE), "Settings"))
    }
}
