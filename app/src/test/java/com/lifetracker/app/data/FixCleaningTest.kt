package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixCleaningTest {
    private val min = 60_000L
    private val t0 = 1_760_000_000_000L

    // 0.001 degrees of latitude is about 111 m.
    private fun fix(minute: Int, lat: Double, lng: Double = 77.2, acc: Float = 10f, speed: Float = -1f) =
        RawFixEntity(t0 + minute * min, lat, lng, acc, speed)

    private fun still(from: Int, to: Int, lat: Double, lng: Double = 77.2) = (from..to).map { fix(it, lat, lng) }

    // ---- FixFilter

    @Test
    fun aSingleSpikeInTheMiddleIsRemoved() {
        val list = listOf(fix(0, 28.6), fix(1, 28.6), fix(2, 28.9), fix(3, 28.6), fix(4, 28.6))
        val clean = FixFilter.clean(list)
        assertEquals(listOf(0L, 1L, 3L, 4L), clean.map { (it.ms - t0) / min })
    }

    @Test
    fun aBadFirstOrLastReadingIsRemoved() {
        val first = listOf(fix(0, 29.0), fix(1, 28.6), fix(2, 28.6), fix(3, 28.6))
        assertEquals(listOf(1L, 2L, 3L), FixFilter.clean(first).map { (it.ms - t0) / min })
        val last = listOf(fix(0, 28.6), fix(1, 28.6), fix(2, 28.6), fix(3, 29.0))
        assertEquals(listOf(0L, 1L, 2L), FixFilter.clean(last).map { (it.ms - t0) / min })
    }

    @Test
    fun twoBadReadingsInARowAreTooFastToBeTrue() {
        val list = listOf(fix(0, 28.6), fix(1, 28.6), fix(2, 28.7), fix(3, 28.7), fix(4, 28.6), fix(5, 28.6))
        val clean = FixFilter.clean(list)
        assertEquals(listOf(0L, 1L, 4L, 5L), clean.map { (it.ms - t0) / min })
    }

    @Test
    fun ifTheNewPlaceKeepsAgreeingItIsTrustedAfterThreeMisses() {
        // 44 km away: too fast for a minute, for two, and for three; the fourth is believed.
        val list = listOf(fix(0, 28.6), fix(1, 28.6)) + (2..6).map { fix(it, 29.0) }
        val clean = FixFilter.clean(list)
        assertEquals(listOf(0L, 1L, 5L, 6L), clean.map { (it.ms - t0) / min })
    }

    @Test
    fun inaccurateAndRepeatedReadingsAreDropped() {
        val list = listOf(fix(0, 28.6), fix(1, 28.6, acc = 400f), fix(2, 28.6), fix(2, 28.6))
        assertEquals(2, FixFilter.clean(list).size)
    }

    @Test
    fun aJumpThePhoneDidNotFeelIsRecognised() {
        val home = fix(0, 28.6, speed = 0f)
        // 1.5 km in a minute is 25 m/s, and the phone says it is going 0.4 m/s.
        assertTrue(FixFilter.isJump(home, fix(1, 28.6135, speed = 0.4f)))
        // Without a speed from the phone nothing can be said.
        assertFalse(FixFilter.isJump(home, fix(1, 28.6135)))
        // A real drive: the phone itself says 25 m/s.
        assertFalse(FixFilter.isJump(home, fix(1, 28.6135, speed = 25f)))
        // Too small, too long ago, or measured against a coarse reading.
        assertFalse(FixFilter.isJump(home, fix(1, 28.6005, speed = 0f)))
        assertFalse(FixFilter.isJump(home, fix(10, 28.6135, speed = 0f)))
        assertFalse(FixFilter.isJump(fix(0, 28.6, acc = 300f), fix(1, 28.6135, speed = 0f)))
    }

    @Test
    fun aSuddenJumpWithNoSpeedBehindItIsDroppedButAMoveThatKeepsAgreeingIsBelieved() {
        val list = (0..3).map { fix(it, 28.6, speed = 0f) } + (4..7).map { fix(it, 28.6135, speed = 0f) }
        val clean = FixFilter.clean(list)
        assertEquals(listOf(0L, 1L, 2L, 3L, 5L, 6L, 7L), clean.map { (it.ms - t0) / min })
    }

    @Test
    fun aFastDriveWithItsOwnSpeedIsKept() {
        // 1.5 km a minute is 90 km/h, and the phone says so.
        val list = (0..10).map { fix(it, 28.6 + it * 0.0135, speed = 25f) }
        assertEquals(11, FixFilter.clean(list).size)
    }

    @Test
    fun aRealDriveIsKept() {
        // 0.004 degrees (444 m) every minute is 27 km/h.
        val list = (0..10).map { fix(it, 28.6 + it * 0.004) }
        assertEquals(11, FixFilter.clean(list).size)
    }

    // ---- StayBuilder

    @Test
    fun tenMinutesInOnePlaceIsOneStay() {
        val stays = StayBuilder.build(still(0, 9, 28.6))
        assertEquals(1, stays.size)
        assertEquals(t0, stays[0].startMs)
        assertEquals(t0 + 9 * min, stays[0].endMs)
        assertEquals(10, stays[0].n)
        assertEquals(28.6, stays[0].lat, 0.0001)
    }

    @Test
    fun shortRunsAndWalkingMakeNoStay() {
        assertTrue(StayBuilder.build(still(0, 2, 28.6)).isEmpty())
        val walking = (0..20).map { fix(it, 28.6 + it * 0.002) }
        assertTrue(StayBuilder.build(walking).isEmpty())
    }

    @Test
    fun jitterInsideTheRadiusStaysOneStay() {
        val list = (0..12).map { fix(it, 28.6 + (if (it % 2 == 0) 0.0002 else -0.0002), 77.2 + (if (it % 3 == 0) 0.0002 else 0.0)) }
        assertEquals(1, StayBuilder.build(list).size)
    }

    @Test
    fun theSamePlaceEitherSideOfALongSilenceIsOneStay() {
        val list = still(0, 10, 28.6) + still(130, 140, 28.6)
        val stays = StayBuilder.build(list)
        assertEquals(1, stays.size)
        assertEquals(t0, stays[0].startMs)
        assertEquals(t0 + 140 * min, stays[0].endMs)
        assertEquals(120 * min, stays[0].bridgedMs)
    }

    @Test
    fun aSilenceThatEndsSomewhereElseStaysAHole() {
        val list = still(0, 10, 28.6) + still(130, 140, 28.7)
        val stays = StayBuilder.build(list)
        assertEquals(2, stays.size)
        assertEquals(t0 + 10 * min, stays[0].endMs)
        assertEquals(t0 + 130 * min, stays[1].startMs)
    }

    @Test
    fun threeMinutesIsTheShortestStay() {
        assertEquals(1, StayBuilder.build(still(0, 3, 28.6)).size)
    }

    @Test
    fun aWalkAwayAndBackIsTwoStaysEvenWhenTheWalkWasTooShortToBeAStay() {
        // Library for 10 minutes, 2 minutes 222 m away, library again for 10 minutes: you really left.
        val list = still(0, 9, 28.6) + still(10, 12, 28.602) + still(13, 22, 28.6)
        val stays = StayBuilder.build(list)
        assertEquals(2, stays.size)
        assertEquals(t0 + 9 * min, stays[0].endMs)
        assertEquals(t0 + 13 * min, stays[1].startMs)
    }

    @Test
    fun aWobbleThatNeverGotFarIsOneStay() {
        // Two poor readings (90 m error) 133 m off: outside the radius, but with that much error not really away. Then back.
        val list = still(0, 9, 28.6) + (10..11).map { fix(it, 28.6012, acc = 90f) } + still(12, 21, 28.6)
        val stays = StayBuilder.build(list)
        assertEquals(1, stays.size)
        assertEquals(t0 + 21 * min, stays[0].endMs)
    }

    @Test
    fun aShopYouStayedAtIsItsOwnStayBetweenTwoVisits() {
        val list = still(0, 9, 28.6) + still(10, 14, 28.602) + still(15, 24, 28.6)
        val stays = StayBuilder.build(list)
        assertEquals(3, stays.size)
        assertEquals(t0 + 10 * min, stays[1].startMs)
        assertEquals(t0 + 14 * min, stays[1].endMs)
    }

    @Test
    fun theCentreTrustsAccurateReadingsMore() {
        val list = still(0, 8, 28.6).map { it.copy(acc = 5f) } + listOf(fix(9, 28.6004, acc = 100f), fix(10, 28.6004, acc = 100f))
        val stay = StayBuilder.build(list).single()
        // The plain average would be about 28.600073; the weighted one stays closer to 28.6.
        assertTrue(stay.lat < 28.60006)
    }

    // ---- FixStats

    @Test
    fun readingsAreCountedPerHour() {
        val now = t0 + 10 * 60 * min
        val list = listOf(
            RawFixEntity(now - 5 * min, 1.0, 1.0, 10f),
            RawFixEntity(now - 10 * min, 1.0, 1.0, 10f),
            RawFixEntity(now - 90 * min, 1.0, 1.0, 10f),
            RawFixEntity(now - 30 * 60 * min, 1.0, 1.0, 10f),
        )
        val hourly = FixStats.hourly(list, now)
        assertEquals(24, hourly.size)
        assertEquals(2, hourly[23])
        assertEquals(1, hourly[22])
        assertEquals(3, hourly.sum())
    }

    @Test
    fun theLongestSilenceIsFoundIncludingTheEnds() {
        val list = listOf(fix(10, 1.0), fix(20, 1.0), fix(100, 1.0), fix(105, 1.0))
        val gap = FixStats.longestGap(list, t0, t0 + 110 * min)
        assertNotNull(gap)
        assertEquals(t0 + 20 * min, gap!!.startMs)
        assertEquals(80 * min, gap.lengthMs)
        assertNull(FixStats.longestGap(emptyList(), t0, t0 + 10 * min))
    }
}
