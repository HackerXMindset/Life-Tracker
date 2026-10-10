package com.lifetracker.app.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepSleepStatsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val date = LocalDate.of(2026, 10, 9)

    private fun at(d: LocalDate, h: Int, m: Int = 0): Long = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun use(startDay: LocalDate, sh: Int, sm: Int, endDay: LocalDate, eh: Int, em: Int, pkg: String = "com.a") =
        UsageSessionEntity(UsageSessionEntity.idFor(pkg, at(startDay, sh, sm)), pkg, at(startDay, sh, sm), at(endDay, eh, em))

    // ---- steps ----

    @Test fun aDaysStepsNeverGoDown() {
        val old = StepDayEntity("2026-10-09", 8000, StepStats.HEALTH_CONNECT)
        assertEquals(8000, StepStats.merge(old, "2026-10-09", 3000, StepStats.HEALTH_CONNECT).steps)
        assertEquals(9000, StepStats.merge(old, "2026-10-09", 9000, StepStats.HEALTH_CONNECT).steps)
        assertEquals(500, StepStats.merge(null, "2026-10-09", 500, StepStats.SENSOR).steps)
    }

    @Test fun counterDeltaHandlesFirstReadingAndRestarts() {
        assertEquals(0L, StepStats.sensorDelta(null, 5000))
        assertEquals(0L, StepStats.sensorDelta(-1, 5000))
        assertEquals(350L, StepStats.sensorDelta(5000, 5350))
        assertEquals(120L, StepStats.sensorDelta(5000, 120)) // the phone restarted
        assertEquals(0L, StepStats.sensorDelta(5000, 5000))
    }

    @Test fun stepTextAndTotals() {
        assertEquals("12,345", StepStats.text(12345))
        val t = StepStats.totals(
            listOf(
                StepDayEntity("2026-10-07", 6000, "x"),
                StepDayEntity("2026-10-08", 0, "x"),
                StepDayEntity("2026-10-09", 10000, "x"),
            ),
        )
        assertEquals(2, t.days)
        assertEquals(16000L, t.total)
        assertEquals(8000, t.average)
        assertEquals(0, StepStats.totals(emptyList()).average)
    }

    // ---- sleep rules ----

    @Test fun editsBeatHealthConnectBeatsEstimates() {
        val estimate = SleepNightEntity("d", 0, 1, SleepStats.ESTIMATE)
        val hc = estimate.copy(source = SleepStats.HEALTH_CONNECT)
        val manual = estimate.copy(source = SleepStats.MANUAL)
        val skipped = estimate.copy(source = SleepStats.SKIPPED)
        assertTrue(SleepStats.mayReplace(null, SleepStats.ESTIMATE))
        assertTrue(SleepStats.mayReplace(estimate, SleepStats.ESTIMATE))
        assertTrue(SleepStats.mayReplace(estimate, SleepStats.HEALTH_CONNECT))
        assertFalse(SleepStats.mayReplace(hc, SleepStats.ESTIMATE))
        assertTrue(SleepStats.mayReplace(hc, SleepStats.HEALTH_CONNECT))
        assertFalse(SleepStats.mayReplace(manual, SleepStats.ESTIMATE))
        assertFalse(SleepStats.mayReplace(manual, SleepStats.HEALTH_CONNECT))
        assertFalse(SleepStats.mayReplace(skipped, SleepStats.ESTIMATE))
        assertFalse(SleepStats.mayReplace(skipped, SleepStats.HEALTH_CONNECT))
    }

    @Test fun minutesAndAverageIgnoreSkippedNights() {
        val a = SleepNightEntity("a", at(date.minusDays(1), 23), at(date, 7), SleepStats.ESTIMATE)
        val b = SleepNightEntity("b", at(date, 0), at(date, 6), SleepStats.MANUAL)
        val c = SleepNightEntity("c", at(date, 0), at(date, 12), SleepStats.SKIPPED)
        assertEquals(480, SleepStats.minutes(a))
        assertEquals(0, SleepStats.minutes(c))
        assertEquals(420, SleepStats.averageMinutes(listOf(a, b, c)))
        assertEquals(0, SleepStats.averageMinutes(emptyList()))
    }

    @Test fun pickedTimesBecomeANight() {
        // 11 pm to 7 am: the evening before, then the morning of the wake-up date.
        val (s, e) = SleepStats.build(date, 23 * 60, 7 * 60, zone)!!
        assertEquals(at(date.minusDays(1), 23), s)
        assertEquals(at(date, 7), e)
        // 1 am to 6 am: all on the wake-up date.
        val (s2, e2) = SleepStats.build(date, 60, 6 * 60, zone)!!
        assertEquals(at(date, 1), s2)
        assertEquals(at(date, 6), e2)
        assertNull(SleepStats.build(date, 420, 420, zone))
    }

    @Test fun healthConnectSessionsKeepTheLongestPerWakeDate() {
        val night = at(date.minusDays(1), 23) to at(date, 6, 30)
        val nap = at(date, 14) to at(date, 14, 40)
        val shortSleep = at(date, 2) to at(date, 3)
        val other = at(date.minusDays(1), 22) to at(date, 0, 30) // woke before midnight-ish: belongs to the same wake date, but is shorter
        val map = SleepStats.nightly(listOf(nap, night, shortSleep, other), zone)
        assertEquals(night, map[date])
        assertEquals(1, map.size)
    }

    // ---- sleep estimate ----

    @Test fun findsTheLongNightlyGap() {
        val prev = date.minusDays(1)
        val usage = listOf(
            use(prev, 19, 0, prev, 19, 30),
            use(prev, 22, 0, prev, 23, 40), // last use before bed
            use(date, 7, 15, date, 7, 40), // first use in the morning
            use(date, 12, 0, date, 12, 20),
        )
        val n = SleepEstimator.estimate(date, usage, zone)!!
        assertEquals(at(prev, 23, 40), n.startMs)
        assertEquals(at(date, 7, 15), n.endMs)
    }

    @Test fun aQuickCheckInTheMiddleOfTheNightDoesNotSplitSleep() {
        val prev = date.minusDays(1)
        val usage = listOf(
            use(prev, 22, 30, prev, 23, 30),
            use(date, 3, 10, date, 3, 12), // 2 minutes at 3 am
            use(date, 7, 0, date, 7, 20),
        )
        val n = SleepEstimator.estimate(date, usage, zone)!!
        assertEquals(at(prev, 23, 30), n.startMs)
        assertEquals(at(date, 7, 0), n.endMs)
    }

    @Test fun aLongWakeInTheMiddleOfTheNightDoesSplitIt() {
        val prev = date.minusDays(1)
        val usage = listOf(
            use(prev, 22, 0, prev, 22, 30),
            use(date, 2, 0, date, 3, 0), // an hour awake
            use(date, 8, 0, date, 8, 10),
        )
        val n = SleepEstimator.estimate(date, usage, zone)!!
        // The longer of the two gaps (3:00 to 8:00 is 5 h, 22:30 to 2:00 is 3.5 h).
        assertEquals(at(date, 3, 0), n.startMs)
        assertEquals(at(date, 8, 0), n.endMs)
    }

    @Test fun noMorningUseMeansNoEstimateYet() {
        val prev = date.minusDays(1)
        val usage = listOf(use(prev, 22, 0, prev, 23, 0))
        assertNull(SleepEstimator.estimate(date, usage, zone))
    }

    @Test fun shortAndDaytimeGapsAreNotSleep() {
        val usage = listOf(
            use(date, 9, 0, date, 9, 30),
            use(date, 11, 0, date, 11, 10), // a 1.5 h gap
            use(date, 14, 30, date, 15, 0), // a 3.3 h daytime gap that starts after 10 am
        )
        assertNull(SleepEstimator.estimate(date, usage, zone))
    }

    @Test fun anAbsurdlyLongGapIsNotSleep() {
        val prev = date.minusDays(1)
        val usage = listOf(use(prev, 19, 0, prev, 19, 30), use(date, 14, 0, date, 14, 20)) // 18.5 hours
        assertNull(SleepEstimator.estimate(date, usage, zone))
    }

    @Test fun usageWindowCoversTheNight() {
        val (from, to) = SleepEstimator.usageWindow(date, zone)
        assertEquals(at(date.minusDays(1), 12), from)
        assertEquals(at(date, 20), to)
        assertNull(SleepEstimator.estimate(date, emptyList(), zone))
    }
}
