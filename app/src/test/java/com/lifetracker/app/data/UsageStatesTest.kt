package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatesTest {
    private val m = 60_000L

    private fun ev(time: Long, type: RawType, pkg: String = "") = RawEvent(time, pkg, type)

    private fun spans(list: List<UsageStateEntity>) = list.map { Triple(it.kind, it.startMs, it.endMs) }

    @Test
    fun theScreenIsOffFromOffUntilOn() {
        val out = UsageStates.build(listOf(ev(10 * m, RawType.SCREEN_OFF), ev(50 * m, RawType.SCREEN_ON)), 100 * m)
        assertEquals(listOf(Triple("off", 10 * m, 50 * m)), spans(out))
    }

    @Test
    fun anAppComingUpEndsTheOffPeriodEvenWithoutAScreenOnEvent() {
        val out = UsageStates.build(listOf(ev(10 * m, RawType.SCREEN_OFF), ev(40 * m, RawType.RESUME, "a")), 100 * m)
        assertEquals(listOf(Triple("off", 10 * m, 40 * m)), spans(out))
    }

    @Test
    fun theAlwaysOnDisplayDoesNotEndIt() {
        val out = UsageStates.build(
            listOf(ev(10 * m, RawType.SCREEN_OFF), ev(20 * m, RawType.RESUME, "aod"), ev(50 * m, RawType.SCREEN_ON)),
            100 * m,
            setOf("aod"),
        )
        assertEquals(listOf(Triple("off", 10 * m, 50 * m)), spans(out))
    }

    @Test
    fun aPeriodStillGoingEndsAtTheLastMoment() {
        val out = UsageStates.build(listOf(ev(10 * m, RawType.SCREEN_OFF)), 90 * m)
        assertEquals(listOf(Triple("off", 10 * m, 90 * m)), spans(out))
        assertEquals("off|${10 * m}", out.single().id)
    }

    @Test
    fun aShutDownEndsTheScreenOffPeriodAndStartsAPhoneOffPeriod() {
        val out = UsageStates.build(
            listOf(ev(10 * m, RawType.SCREEN_OFF), ev(30 * m, RawType.SHUTDOWN), ev(90 * m, RawType.STARTUP)),
            200 * m,
        )
        assertEquals(listOf(Triple("off", 10 * m, 30 * m), Triple("shutdown", 30 * m, 90 * m)), spans(out))
    }

    @Test
    fun aPhoneThatDiedWasOffFromItsLastEvent() {
        val out = UsageStates.build(listOf(ev(10 * m, RawType.RESUME, "a"), ev(20 * m, RawType.PAUSE, "a"), ev(120 * m, RawType.STARTUP)), 200 * m)
        assertEquals(listOf(Triple("shutdown", 20 * m, 120 * m)), spans(out))
    }

    @Test
    fun veryShortOffPeriodsAreLeftOut() {
        val out = UsageStates.build(listOf(ev(10 * m, RawType.SCREEN_OFF), ev(10 * m + 10_000L, RawType.SCREEN_ON)), 100 * m)
        assertEquals(emptyList<Triple<String, Long, Long>>(), spans(out))
    }

    @Test
    fun lockingTheScreenAloneIsNotOffline() {
        val out = UsageStates.build(listOf(ev(10 * m, RawType.LOCK), ev(50 * m, RawType.SCREEN_ON)), 100 * m)
        assertEquals(emptyList<Triple<String, Long, Long>>(), spans(out))
    }
}
