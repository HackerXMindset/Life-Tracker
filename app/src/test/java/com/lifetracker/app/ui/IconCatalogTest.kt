package com.lifetracker.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCatalogTest {
    private val outlines = IconCatalog.parse(File("src/main/res/raw/icon_paths.txt").readText())

    @Test fun names_match_whatever_the_capitals_or_dashes() {
        assertEquals(IconCatalog.norm("bookOpen"), IconCatalog.norm("book-open"))
        assertEquals(IconCatalog.norm("Book open"), IconCatalog.norm("bookopen"))
    }

    @Test fun emoji_is_told_from_a_name() {
        assertTrue(IconCatalog.isEmoji("🏃"))
        assertTrue(IconCatalog.isEmoji("☀️"))
        assertTrue(IconCatalog.isEmoji("❤️‍🔥"))
        assertFalse(IconCatalog.isEmoji("dumbbell"))
        assertFalse(IconCatalog.isEmoji(""))
    }

    @Test fun lines_are_read_into_names_and_outlines() {
        val m = IconCatalog.parse("dumbbell\tM1 1L2 2z\nbad line without a tab\nbookOpen\tM3 3h4\n")
        assertEquals(2, m.size)
        assertEquals("M1 1L2 2z", m[IconCatalog.norm("dumbbell")])
        assertEquals("M3 3h4", m["bookopen"])
    }

    @Test fun every_picture_in_the_picker_has_a_drawing() {
        val missing = IconSets.icons.flatMap { it.second }.filter { !outlines.containsKey(IconCatalog.norm(it)) }
        assertTrue("no drawing for: $missing", missing.isEmpty())
        assertTrue(IconSets.icons.flatMap { it.second }.toSet().size >= 250)
    }

    @Test fun every_emoji_in_the_picker_is_an_emoji() {
        IconSets.emojis.forEach { (cat, list) ->
            assertTrue("empty $cat", list.isNotEmpty())
            list.forEach { assertTrue("$it in $cat", IconCatalog.isEmoji(it)) }
        }
    }

    @Test fun drawings_are_plain_outline_text() {
        outlines.values.forEach { assertTrue(it.startsWith("M") || it.startsWith("m")) }
        assertNotNull(outlines[IconCatalog.norm("sparkles")])
    }
}

class ColorMathTest {
    @Test fun a_colour_survives_the_trip_through_hue_saturation_and_value() {
        listOf(0xFFFF3B30L, 0xFF0A84FFL, 0xFF34C759L, 0xFF8E8E93L, 0xFFFFFFFFL, 0xFF1C1C1EL).forEach { c ->
            val hsv = colorLongToHsv(c)
            val back = hsvToColorLong(hsv[0], hsv[1], hsv[2])
            val dr = Math.abs(((c shr 16) and 0xFF) - ((back shr 16) and 0xFF))
            val dg = Math.abs(((c shr 8) and 0xFF) - ((back shr 8) and 0xFF))
            val db = Math.abs((c and 0xFF) - (back and 0xFF))
            assertTrue("$c -> $back", dr <= 1 && dg <= 1 && db <= 1)
        }
    }

    @Test fun pure_red_is_hue_zero_and_blue_is_240() {
        assertEquals(0f, colorLongToHsv(0xFFFF0000L)[0], 0.01f)
        assertEquals(240f, colorLongToHsv(0xFF0000FFL)[0], 0.01f)
        assertEquals(0xFF00FF00L, hsvToColorLong(120f, 1f, 1f))
    }

    @Test fun the_hex_text_has_six_digits() {
        assertEquals("#0A84FF", hexText(0xFF0A84FFL))
        assertEquals("#000000", hexText(0xFF000000L))
    }
}
