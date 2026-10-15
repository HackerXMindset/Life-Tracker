package com.lifetracker.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperThemeTest {
    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    @Test
    fun textIsReadableOnBothPapers() {
        for (p in listOf(LightPaper, DarkPaper)) {
            assertTrue(contrast(p.ink, p.bg) >= 7.0)
            assertTrue(contrast(p.ink, p.surface) >= 7.0)
            assertTrue(contrast(p.ink2, p.bg) >= 4.5)
            assertTrue(contrast(p.ink2, p.surface) >= 4.5)
            assertTrue(contrast(p.accent, p.bg) >= 4.5)
            assertTrue(contrast(p.accentInk, p.accent) >= 4.5)
        }
    }

    @Test
    fun lightAndDarkAreFlaggedRight() {
        assertFalse(LightPaper.dark)
        assertTrue(DarkPaper.dark)
    }

    @Test
    fun systemModeFollowsThePhone() {
        assertTrue(ThemeModes.isDark(ThemeMode.System, systemDark = true))
        assertFalse(ThemeModes.isDark(ThemeMode.System, systemDark = false))
        assertFalse(ThemeModes.isDark(ThemeMode.Light, systemDark = true))
        assertTrue(ThemeModes.isDark(ThemeMode.Dark, systemDark = false))
    }

    @Test
    fun savedModeReadsBackAndUnknownMeansSystem() {
        for (m in ThemeMode.entries) assertEquals(m, ThemeModes.parse(m.name))
        assertEquals(ThemeMode.System, ThemeModes.parse(null))
        assertEquals(ThemeMode.System, ThemeModes.parse("sepia"))
    }

    @Test
    fun tallyStrokesFillWholeStrokesOnly() {
        assertEquals(0, TallyMath.count(0f, 10f))
        assertEquals(10, TallyMath.count(100f, 10f))
        assertEquals(11, TallyMath.count(101f, 10f))
        assertEquals(0, TallyMath.filled(0f, 10))
        assertEquals(10, TallyMath.filled(1f, 10))
        assertEquals(5, TallyMath.filled(0.5f, 10))
        assertEquals(10, TallyMath.filled(7f, 10))
        assertEquals(0, TallyMath.filled(-3f, 10))
        assertEquals(0, TallyMath.filled(0.5f, 0))
    }
}
