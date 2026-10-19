package com.lifetracker.app.data

import com.lifetracker.app.ui.IconCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineCorrectionsTest {
    private val min = 60_000L

    private fun gap(a: Long, b: Long, kind: DayLayout.GapKind = DayLayout.GapKind.NO_DATA) =
        DayLayout.Gap("g$a", a * min, b * min, "note", kind)

    @Test fun back_to_back_no_data_becomes_one_row_with_its_pieces() {
        val rows = DayLayout.joinGaps(listOf(gap(0, 11), gap(12, 26), gap(30, 43), gap(44, 61)))
        assertEquals(1, rows.size)
        val g = rows[0] as DayLayout.Gap
        assertEquals(0L, g.startMs)
        assertEquals(61 * min, g.endMs)
        assertEquals(4, g.parts.size)
        assertEquals((11 + 14 + 13 + 17) * min, g.knownMs)
    }

    @Test fun different_kinds_stay_apart() {
        val rows = DayLayout.joinGaps(listOf(gap(0, 10), gap(10, 20, DayLayout.GapKind.OFFLINE), gap(20, 30)))
        assertEquals(3, rows.size)
    }

    @Test fun a_single_gap_is_untouched() {
        val g = gap(0, 10)
        val rows = DayLayout.joinGaps(listOf(g))
        assertTrue(rows[0] === g)
        assertEquals(10 * min, g.knownMs)
        assertEquals(1, g.parts.size)
    }

    @Test fun charging_overlap_counts_only_the_time_inside() {
        val spans = listOf(10 * min to 20 * min, 40 * min to 50 * min)
        assertEquals(5 * min, ChargeOverlap.overlapMs(0, 15 * min, spans))
        assertEquals(10 * min, ChargeOverlap.overlapMs(5 * min, 45 * min, spans) - 5 * min)
        assertEquals(0L, ChargeOverlap.overlapMs(21 * min, 39 * min, spans))
    }

    @Test fun the_bolt_needs_a_minute_of_charging_or_all_of_a_short_row() {
        val spans = listOf(10 * min to 20 * min)
        assertTrue(ChargeOverlap.during(5 * min, 12 * min, spans))
        assertFalse(ChargeOverlap.during(19 * min + 30_000L, 30 * min, spans))
        // A moment (a meal at 15:00) inside charging counts; one outside does not.
        assertTrue(ChargeOverlap.during(15 * min, 15 * min, spans))
        assertFalse(ChargeOverlap.during(25 * min, 25 * min, spans))
        // A 30 second call fully inside charging counts.
        assertTrue(ChargeOverlap.during(12 * min, 12 * min + 30_000L, spans))
        assertFalse(ChargeOverlap.during(0, 100 * min, emptyList()))
    }

    @Test fun built_in_activities_are_unique_and_every_icon_has_a_drawing() {
        val all = DefaultActivities.list + DefaultActivities.extra
        assertEquals(28, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.name.lowercase() }.toSet().size)
        val outlines = IconCatalog.parse(File("src/main/res/raw/icon_paths.txt").readText())
        all.forEach {
            assertTrue("${it.name} has an icon", it.icon.isNotEmpty())
            assertTrue("${it.name}: ${it.icon}", outlines.containsKey(IconCatalog.norm(it.icon)))
        }
        // The eight that were always there keep their ids and names.
        assertEquals("Screen and leisure", DefaultActivities.list.first { it.id == "Screen" }.name)
        assertEquals(8, DefaultActivities.list.size)
    }
}
