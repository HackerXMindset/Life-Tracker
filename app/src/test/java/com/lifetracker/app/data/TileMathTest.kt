package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {
    @Test
    fun theWholeWorldAtZoomOneIsFourTiles() {
        assertEquals(4L, TileMath.count(Bounds(-179.9, -84.0, 179.9, 84.0), 1, 1))
    }

    @Test
    fun aKnownTile() {
        // London (51.5, -0.12) at zoom 10 is tile 511, 340.
        assertEquals(511, TileMath.tileX(-0.12, 10))
        assertEquals(340, TileMath.tileY(51.5, 10))
    }

    @Test
    fun aSmallBoxIsOneTilePerZoomUntilItGrows() {
        val b = TileMath.around(51.5, -0.12, 0.1)
        assertEquals(1L, TileMath.count(b, 5, 5))
        assertTrue(TileMath.count(b, 5, 16) > 11L)
    }

    @Test
    fun moreZoomsMeanMoreTiles() {
        val b = TileMath.around(28.6, 77.2, 3.0)
        assertTrue(TileMath.count(b, 8, 15) > TileMath.count(b, 8, 14))
        assertEquals(0L, TileMath.count(b, 9, 8))
    }

    @Test
    fun aSquareAroundASpotIsTheRightSize() {
        val b = TileMath.around(0.0, 10.0, 11.132)
        assertEquals(0.2, b.north - b.south, 0.001)
        assertTrue(b.contains(0.0, 10.0))
        assertFalse(b.contains(1.0, 10.0))
        assertTrue(b.valid())
    }

    @Test
    fun theAverageComesFromYourOwnAreasOrTheGuess() {
        assertEquals(TileMath.GUESS_TILE_BYTES, TileMath.averageTileBytes(emptyList()))
        assertEquals(1_000L, TileMath.averageTileBytes(listOf(10L to 5_000L, 30L to 35_000L)))
        assertEquals(40_000L, TileMath.estimateBytes(2, 20_000))
    }

    @Test
    fun sizesReadInPlainUnits() {
        assertEquals("512 B", TileMath.sizeText(512))
        assertEquals("2 KB", TileMath.sizeText(2048))
        assertEquals("1.5 MB", TileMath.sizeText(1_572_864))
        assertEquals("2.00 GB", TileMath.sizeText(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun aBoxThatIsBackwardsIsNotValid() {
        assertFalse(Bounds(10.0, 0.0, 5.0, 1.0).valid())
        assertFalse(Bounds(0.0, 0.0, 1.0, 90.0).valid())
    }
}
