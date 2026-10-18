package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWarpTest {
    private val minute = 60_000L
    private val origin = 0L

    @Test
    fun whenNothingIsCrowdedTheScaleIsExact() {
        // Rows at 0, 60 and 180 minutes, each 40 px tall, one pixel per minute.
        val r = TimeWarp.build(listOf(0, 60 * minute, 180 * minute), listOf(40f, 40f, 40f), origin, 1f)
        assertEquals(listOf(0f, 60f, 180f), r.tops)
        assertEquals(120f, r.warp.y(120 * minute), 0.001f)
        assertEquals(250f, r.warp.y(250 * minute), 0.001f)
    }

    @Test
    fun aTallRowPushesTheNextOneDownAndTheTimeBetweenIsStretched() {
        // The first row is 100 px tall but the next one starts 20 minutes later.
        val r = TimeWarp.build(listOf(0, 20 * minute), listOf(100f, 40f), origin, 1f)
        assertEquals(100f, r.tops[1], 0.001f)
        // Ten minutes in is halfway between the two rows.
        assertEquals(50f, r.warp.y(10 * minute), 0.001f)
    }

    @Test
    fun aBusyDayKeepsEveryLineAtItsRowAndNothingOverlaps() {
        // About 70 items, one every 12 minutes, each 80 px tall: far more than the real time allows.
        val starts = (0 until 70).map { it * 12 * minute }
        val heights = List(70) { 80f }
        val r = TimeWarp.build(starts, heights, origin, 1f)
        for (i in starts.indices) {
            // The line for a row starts exactly where the row starts.
            assertEquals(r.tops[i], r.warp.y(starts[i]), 0.001f)
            if (i > 0) assertTrue("rows must not overlap", r.tops[i] >= r.tops[i - 1] + heights[i - 1] - 0.001f)
        }
    }

    @Test
    fun hoursNeverGoBackwardsAndNeverSitCloserThanTheScale() {
        val starts = (0 until 70).map { it * 12 * minute }
        val r = TimeWarp.build(starts, List(70) { 80f }, origin, 1f)
        var previous = r.warp.y(0)
        for (h in 1..14) {
            val y = r.warp.y(h * 60 * minute)
            assertTrue("hour $h must be at least 60 px below the one before", y - previous >= 60f - 0.01f)
            previous = y
        }
    }

    @Test
    fun theScaleComesBackAfterACrowdedStretch() {
        // Six rows 5 minutes apart (crowded), then one 3 hours later.
        val starts = listOf(0L, 5, 10, 15, 20, 25).map { it * minute } + (205 * minute)
        val r = TimeWarp.build(starts, List(7) { 60f }, origin, 1f)
        val gap = r.tops[6] - r.tops[5]
        assertEquals(180f, gap, 0.001f)
    }

    @Test
    fun aLongItemIsDrawnAcrossTheRowsInsideIt() {
        // A 6 hour item from 0 to 360 minutes with three short rows inside it.
        val starts = listOf(0L, 60 * minute, 61 * minute, 62 * minute)
        val r = TimeWarp.build(starts, listOf(50f, 50f, 50f, 50f), origin, 1f)
        val bar = r.warp.y(360 * minute) - r.warp.y(0)
        assertTrue(bar >= 360f)
        assertTrue(r.warp.y(62 * minute) > r.warp.y(60 * minute))
    }

    @Test
    fun beforeTheFirstRowTheScaleIsExact() {
        val r = TimeWarp.build(listOf(90 * minute), listOf(40f), origin, 1f)
        assertEquals(90f, r.tops[0], 0.001f)
        assertEquals(60f, r.warp.y(60 * minute), 0.001f)
    }
}
