package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayPieMathTest {
    private val min = 60_000L
    private fun piece(label: String, startMin: Long, endMin: Long, argb: Int = 1) =
        DayPieMath.Piece(label, argb, startMin * min, endMin * min)

    @Test
    fun slicesAddUpTimePerNameLongestFirst() {
        val s = DayPieMath.slices(listOf(piece("Study", 0, 60), piece("Sleep", 60, 360), piece("Study", 400, 460)))
        assertEquals(listOf("Sleep", "Study"), s.map { it.label })
        assertEquals(360 * min - 60 * min, s[0].ms)
        assertEquals(120 * min, s[1].ms)
    }

    @Test
    fun emptyStretchesAreLeftOut() {
        assertEquals(0, DayPieMath.slices(listOf(piece("A", 10, 10))).size)
    }

    @Test
    fun sweepsAreProportionalAndAddUpToACircle() {
        val s = DayPieMath.slices(listOf(piece("A", 0, 300), piece("B", 300, 400)))
        val sw = DayPieMath.sweeps(s)
        assertEquals(0f, sw[0].first, 0.001f)
        assertEquals(270f, sw[0].second, 0.01f)
        assertEquals(270f, sw[1].first, 0.01f)
        assertEquals(90f, sw[1].second, 0.01f)
    }

    @Test
    fun hitFindsTheSliceUnderAnAngle() {
        val s = DayPieMath.slices(listOf(piece("A", 0, 300), piece("B", 300, 400)))
        assertEquals(0, DayPieMath.hit(s, 10f))
        assertEquals(0, DayPieMath.hit(s, 269f))
        assertEquals(1, DayPieMath.hit(s, 271f))
        assertEquals(1, DayPieMath.hit(s, 359.9f))
        assertEquals(0, DayPieMath.hit(s, 370f))
        assertNull(DayPieMath.hit(emptyList(), 10f))
    }

    @Test
    fun percentIsWholeNumber() {
        assertEquals(25, DayPieMath.percent(1, 4))
        assertEquals(33, DayPieMath.percent(1, 3))
        assertEquals(0, DayPieMath.percent(1, 0))
    }

    @Test
    fun relatedSharesTimeBetweenTasksAndPlaces() {
        val tasks = listOf(piece("Study", 0, 120), piece("Sleep", 120, 300))
        val places = listOf(piece("Home", 0, 200), piece("Library", 200, 300))
        val atStudy = DayPieMath.related("Study", tasks, places)
        assertEquals(listOf("Home" to 120 * min), atStudy)
        val atHome = DayPieMath.related("Home", places, tasks)
        assertEquals(listOf("Study" to 120 * min, "Sleep" to 80 * min), atHome)
        assertEquals(listOf("Library" to 100 * min), DayPieMath.related("Sleep", tasks, places).filter { it.first == "Library" })
    }
}
