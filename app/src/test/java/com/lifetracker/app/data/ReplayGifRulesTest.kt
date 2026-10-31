package com.lifetracker.app.data

import com.lifetracker.app.data.ReplayRules.Quiet
import com.lifetracker.app.data.ReplayRules.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGifRulesTest {
    private val min = 60_000L
    private val day = 24 * 60 * min

    @Test
    fun framesAdvanceByTheSpeedAndStopAtTheEnd() {
        // 10 min/s at 10 frames a second is one minute of the day per frame.
        val t = ReplayGifRules.frameTimes(0, 5 * min, 10, false, emptyList(), 1000)
        assertEquals(6, t.size)
        assertEquals(5 * min, t.last())
        assertEquals(0L, t.first())
    }

    @Test
    fun theFrameCountIsCapped() {
        val t = ReplayGifRules.frameTimes(0, day, 1, false, emptyList(), 50)
        assertEquals(50, t.size)
        assertTrue(t.last() < day)
    }

    @Test
    fun fitFindsTheSlowestSpeedThatPlaysTheWholeDayInTime() {
        val m = ReplayGifRules.fitMinPerS(0, day, false, emptyList(), 20)
        // 24 hours in 20 seconds (200 frames, so 199 steps) needs 73 minutes a second.
        assertEquals(73, m)
        val played = ReplayGifRules.frameTimes(0, day, m, false, emptyList(), 200)
        assertEquals(day, played.last())
        val slower = ReplayGifRules.frameTimes(0, day, m - 1, false, emptyList(), 200)
        assertTrue(slower.last() < day)
    }

    @Test
    fun skippingQuietStretchesLetsASlowerSpeedFit() {
        val quiet = listOf(Quiet(0, 12 * 60 * min))
        val without = ReplayGifRules.fitMinPerS(0, day, false, emptyList(), 20)
        val with = ReplayGifRules.fitMinPerS(0, day, true, quiet, 20)
        assertTrue(with < without)
    }

    @Test
    fun coloursGoToTheNearestOfTheCube() {
        assertEquals(0, ReplayGifRules.paletteIndex(0xFF000000.toInt()))
        assertEquals(215, ReplayGifRules.paletteIndex(0xFFFFFFFF.toInt()))
        assertEquals(36 * 5, ReplayGifRules.paletteIndex(0xFFFF0000.toInt()))
        assertEquals(0xFFFFFF, ReplayGifRules.PALETTE[215])
        assertEquals(256, ReplayGifRules.PALETTE.size)
    }

    @Test
    fun theProjectionKeepsNorthUpAndEverythingInside() {
        val samples = listOf(Sample(0, 12.0, 77.0), Sample(1, 12.1, 77.2))
        val p = ReplayGifRules.Projection(samples, 360, 30)
        assertTrue(p.y(12.1) < p.y(12.0))
        assertTrue(p.x(77.2) > p.x(77.0))
        for (s in samples) {
            assertTrue(p.x(s.lng) in 29f..331f)
            assertTrue(p.y(s.lat) in 29f..331f)
        }
        assertTrue(p.pixels(1000.0) > 0f)
    }

    @Test
    fun aDayInOneSpotStillGetsAPicture() {
        val p = ReplayGifRules.Projection(listOf(Sample(0, 12.0, 77.0)), 240, 20)
        assertEquals(120f, p.x(77.0), 0.01f)
        assertEquals(120f, p.y(12.0), 0.01f)
    }

    @Test
    fun sizesAreShownInPlainWords() {
        assertEquals("340 KB", ReplayGifRules.sizeText(340_000))
        assertEquals("1.2 MB", ReplayGifRules.sizeText(1_200_000))
        assertEquals(200, ReplayGifRules.framesFor(20))
        assertEquals(600, ReplayGifRules.framesFor(60))
    }
}
