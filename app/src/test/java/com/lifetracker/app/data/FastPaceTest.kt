package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastPaceTest {
    private fun fix(sec: Long, lat: Double, speed: Float = -1f) = RawFixEntity(sec * 1000L, lat, 77.0, 5f, speed)

    @Test
    fun theCapOnlyFasterThanTheBasePaceAndOnlyWhileRecent() {
        assertEquals(10, FastPace.cap(60, 1_000_000L, 1_030_000L))
        assertEquals(5, FastPace.cap(5, 1_000_000L, 1_030_000L))
        assertEquals(60, FastPace.cap(60, 1_000_000L, 1_000_000L + FastPace.HOLD_MS + 1))
        assertEquals(60, FastPace.cap(60, 0L, 1_000L))
        assertEquals(60, FastPace.cap(60, 5_000L, 0L))
    }

    @Test
    fun fastIsFromTwentyFiveKmh() {
        assertTrue(FastPace.isFast(7.0f))
        assertTrue(!FastPace.isFast(6.9f))
        assertTrue(!FastPace.isFast(-1f))
    }

    @Test
    fun twoFastStepsSwitchItOnButOneJumpDoesNot() {
        val spike = listOf(fix(0, 12.0), fix(30, 12.0), fix(60, 12.002), fix(90, 12.002))
        assertEquals(0L, FastPace.lastFastMs(spike))
        // About 20 m/s northwards, readings 30 s apart.
        val train = (0..5).map { fix(it * 30L, 12.0 + it * 0.0054) }
        assertEquals(150_000L, FastPace.lastFastMs(train))
    }

    @Test
    fun theNextSpeedIsTakenFromThePhonesOwnSpeedWhenItGaveOne() {
        val withSpeed = listOf(fix(0, 12.0, 10f), fix(10, 12.0, 11f), fix(20, 12.0, 12f))
        assertEquals(20_000L, FastPace.lastFastMs(withSpeed))
    }

    @Test
    fun gentleModeGoesDenseOnFastMovementAndKeepsItUntilTheHoldRunsOut() {
        val state = WatchState(moving = true, fastMs = 1_000_000L)
        assertEquals(10, PlaceEngine.intervalSec(state, 120, 30, 1_060_000L))
        assertEquals(30, PlaceEngine.intervalSec(state, 120, 30, 1_000_000L + FastPace.HOLD_MS + 1))
        assertEquals(30, PlaceEngine.intervalSec(state, 120, 30))
        assertEquals(1_000_000L, WatchState.fromJson(state.toJson()).fastMs)
    }
}
