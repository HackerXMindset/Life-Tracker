package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PieAppsTest {
    private val min = 60_000L

    @Test
    fun oneAppTakesWholePeriod() {
        val parts = PieApps.split(listOf(0L to 30 * min), listOf("Chrome" to 30 * min))
        assertEquals(listOf(Triple("Chrome", 0L, 30 * min)), parts)
    }

    @Test
    fun appsShareTheTimeAsTheyHadOnScreen() {
        val parts = PieApps.split(listOf(0L to 10 * min, 20 * min to 40 * min), listOf("Chrome" to 20 * min, "Maps" to 10 * min))
        val per = parts.groupBy { it.first }.mapValues { (_, v) -> v.sumOf { it.third - it.second } }
        assertEquals(20 * min, per["Chrome"])
        assertEquals(10 * min, per["Maps"])
        // Nothing is made up or lost: the parts cover exactly the spans.
        assertEquals(30 * min, parts.sumOf { it.third - it.second })
        assertTrue(parts.all { it.third > it.second })
    }

    @Test
    fun aPeriodWithNoAppListKeepsNothingToSplit() {
        assertEquals(emptyList<Triple<String, Long, Long>>(), PieApps.split(listOf(0L to min), emptyList()))
    }

    @Test
    fun theBiggestAppKeepsTheCategoryColourAndOthersGetLighter() {
        val c = 0xFF2E7D32.toInt()
        assertEquals(c, PieApps.shade(c, 0, 3))
        val second = PieApps.shade(c, 1, 3)
        val third = PieApps.shade(c, 2, 3)
        fun green(x: Int) = (x shr 8) and 0xFF
        assertTrue(green(second) > green(c))
        assertTrue(green(third) > green(second))
        assertEquals(c, PieApps.shade(c, 0, 1))
    }

    @Test
    fun appsAreRankedByTheirTimeInTheWholeDay() {
        val c = 0xFF2E7D32.toInt()
        val stretches = listOf(
            PieApps.Stretch("Study", c, "Notes", 0, 10 * min),
            PieApps.Stretch("Study", c, "Anki", 20 * min, 50 * min),
            PieApps.Stretch("Study", c, "Notes", 60 * min, 65 * min),
            PieApps.Stretch("Social", 0xFF1565C0.toInt(), "Chat", 70 * min, 80 * min),
        )
        val out = PieApps.shaded(stretches)
        assertEquals(c, out[1].second)
        assertTrue(out[0].second != c)
        assertEquals(out[0].second, out[2].second)
        assertEquals(0xFF1565C0.toInt(), out[3].second)
    }

    @Test
    fun appSlicesOfOneCategorySitTogetherInThePie() {
        val green = 0xFF2E7D32.toInt()
        val pieces = listOf(
            DayPieMath.Piece("Notes", green, 0, 10 * min, "Study"),
            DayPieMath.Piece("Chat", 0xFF1565C0.toInt(), 10 * min, 40 * min, "Social"),
            DayPieMath.Piece("Anki", green, 40 * min, 70 * min, "Study"),
            DayPieMath.Piece("Sleep", 0xFF000000.toInt(), 70 * min, 80 * min),
        )
        val order = DayPieMath.slices(pieces).map { it.label }
        // Study (40 min in total) comes first with its two apps side by side, then Social, then Sleep.
        assertEquals(listOf("Anki", "Notes", "Chat", "Sleep"), order)
    }

    @Test
    fun segmentsKeepTheirFamilyWhenFlattened() {
        val segs = listOf(DaySegments.Seg(0, 10 * min, 1, "Anki", false, "Study"))
        assertEquals("Study", DaySegments.flatten(segs, 0, 100 * min).single().family)
    }
}
