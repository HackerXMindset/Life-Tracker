package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathFitTest {
    private val route = listOf(28.600 to 77.200, 28.610 to 77.200, 28.610 to 77.210)

    @Test
    fun northIsUpAndEverythingFitsInsideThePadding() {
        val frame = PathFrame.of(route, 400f, 300f, padding = 20f)
        val pts = route.map { frame.project(it.first, it.second) }
        // The most northern reading is the highest on the drawing.
        assertTrue(pts[1].y < pts[0].y)
        assertEquals(pts[1].y, pts[2].y, 0.01f)
        // East is to the right.
        assertTrue(pts[2].x > pts[1].x)
        for (p in pts) {
            assertTrue(p.x >= 20f - 0.01f && p.x <= 380f + 0.01f)
            assertTrue(p.y >= 20f - 0.01f && p.y <= 280f + 0.01f)
        }
    }

    @Test
    fun proportionsAreKept() {
        // 0.01 degrees of latitude is 1113 m; 0.01 of longitude at 28.6 degrees is about 977 m.
        val frame = PathFrame.of(route, 400f, 400f, padding = 0f)
        val pts = route.map { frame.project(it.first, it.second) }
        val north = pts[0].y - pts[1].y
        val east = pts[2].x - pts[1].x
        assertEquals(1113.0 / 977.0, (north / east).toDouble(), 0.02)
    }

    @Test
    fun theDrawingIsCentred() {
        val frame = PathFrame.of(route, 800f, 200f, padding = 10f)
        val pts = route.map { frame.project(it.first, it.second) }
        val left = pts.minOf { it.x }
        val right = 800f - pts.maxOf { it.x }
        assertEquals(left, right, 0.5f)
    }

    @Test
    fun aSingleSpotSitsInTheMiddle() {
        val frame = PathFrame.of(listOf(28.6 to 77.2), 300f, 200f)
        val p = frame.project(28.6, 77.2)
        assertEquals(150f, p.x, 0.5f)
        assertEquals(100f, p.y, 0.5f)
    }

    @Test
    fun nothingToShowStillMakesAFrame() {
        val frame = PathFrame.of(emptyList(), 300f, 200f)
        val p = frame.project(0.0, 0.0)
        assertTrue(p.x in 0f..300f && p.y in 0f..200f)
    }

    @Test
    fun aCircleKeepsItsRealSizeAndIsKeptInTheFrame() {
        val circle = PathFrame.Circle(28.6, 77.2, 200.0)
        val frame = PathFrame.of(listOf(28.6 to 77.2), 400f, 400f, padding = 0f, circles = listOf(circle))
        val centre = frame.project(28.6, 77.2)
        val radius = frame.pixels(200.0)
        assertTrue(centre.x - radius >= -0.5f && centre.x + radius <= 400.5f)
        assertTrue(centre.y - radius >= -0.5f && centre.y + radius <= 400.5f)
        // 200 m is the same on the drawing whichever way you measure it: 100 m is half as long.
        assertEquals(radius / 2f, frame.pixels(100.0), 0.01f)
    }

    @Test
    fun theNearestReadingToATapIsFound() {
        val frame = PathFrame.of(route, 400f, 300f, padding = 20f)
        val pts = route.map { frame.project(it.first, it.second) }
        assertEquals(0, frame.nearest(pts, pts[0].x + 3f, pts[0].y - 2f))
        assertEquals(2, frame.nearest(pts, pts[2].x + 30f, pts[2].y + 30f))
        assertEquals(-1, frame.nearest(emptyList(), 1f, 1f))
    }
}
