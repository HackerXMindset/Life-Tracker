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
        // 600x (10 min/s) at 10 frames a second is one minute of the day per frame.
        val t = ReplayGifRules.frameTimes(0, 5 * min, 600.0, false, emptyList(), 1000)
        assertEquals(6, t.size)
        assertEquals(5 * min, t.last())
        assertEquals(0L, t.first())
    }

    @Test
    fun theFrameCountIsCapped() {
        val t = ReplayGifRules.frameTimes(0, day, 60.0, false, emptyList(), 50)
        assertEquals(50, t.size)
        assertTrue(t.last() < day)
    }

    @Test
    fun fitFindsTheSlowestSpeedThatPlaysTheWholeDayInTime() {
        val m = ReplayGifRules.fitSpeed(0, day, false, emptyList(), 20)
        // 200 frames (199 steps of 0.1 s) must cover 86 400 s: at least 4 341.7, so 4 342.
        assertEquals(4342.0, m, 0.0)
        val played = ReplayGifRules.frameTimes(0, day, m, false, emptyList(), 200)
        assertEquals(day, played.last())
        val slower = ReplayGifRules.frameTimes(0, day, m - 1, false, emptyList(), 200)
        assertTrue(slower.last() < day)
    }

    @Test
    fun skippingQuietStretchesLetsASlowerSpeedFit() {
        val quiet = listOf(Quiet(0, 12 * 60 * min))
        val without = ReplayGifRules.fitSpeed(0, day, false, emptyList(), 20)
        val with = ReplayGifRules.fitSpeed(0, day, true, quiet, 20)
        assertTrue(with < without)
    }

    @Test
    fun smartSpeedChangesHowLongTheFramesTake() {
        // A still day: the quiet boost makes it run faster, so fewer frames reach the end at the same speed.
        val samples = listOf(Sample(0, 12.0, 77.0), Sample(10 * min, 12.0, 77.0), Sample(20 * min, 12.0, 77.0))
        val plain = ReplayGifRules.frameTimes(0, 20 * min, 60.0, false, emptyList(), 10_000)
        val smart = ReplayGifRules.frameTimes(0, 20 * min, 60.0, false, emptyList(), 10_000, ReplayRules.Smart(0.1, 5.0), samples, emptyList())
        assertTrue(smart.size < plain.size)
        assertEquals(20 * min, smart.last())
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

    @Test
    fun theMapPictureShowsTheSameStretchAsThePath() {
        // About 2 km of road across a 360 px picture: roughly zoom 12, and a longer path needs a lower zoom.
        val short = (0..10).map { Sample(it * 60_000L, 12.0 + it * 0.0018, 77.0) }
        val long = (0..10).map { Sample(it * 60_000L, 12.0 + it * 0.18, 77.0) }
        val z = ReplayGifRules.Projection(short, 360, 36).mapZoom()
        assertTrue("was $z", z in 12.0..14.5)
        assertTrue(ReplayGifRules.Projection(long, 360, 36).mapZoom() < z - 5)
        // A day in one spot (about 200 m) is close in but never beyond the map's own limit.
        assertTrue(ReplayGifRules.Projection(listOf(Sample(0, 12.0, 77.0)), 240, 24).mapZoom() <= 18.0)
    }
}
