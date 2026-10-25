package com.lifetracker.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackgroundSettingsTest {
    @Test
    fun everyBackgroundModeCanBeRestored() {
        BackgroundMode.entries.forEach { mode ->
            assertEquals(mode, BackgroundMode.parse(mode.name))
        }
    }

    @Test
    fun unknownOrMissingModeFallsBackToSolid() {
        assertEquals(BackgroundMode.SOLID, BackgroundMode.parse(null))
        assertEquals(BackgroundMode.SOLID, BackgroundMode.parse("paper"))
        assertEquals(BackgroundMode.SOLID, BackgroundMode.parse("photo"))
    }

    @Test
    fun acceptsShortRgbHex() {
        assertEquals(0xFFFF0000.toInt(), BackgroundSettings.parseHexColor("#F00"))
        assertEquals(0xFFAA33CC.toInt(), BackgroundSettings.parseHexColor("a3c"))
    }

    @Test
    fun acceptsSixAndEightDigitHex() {
        assertEquals(0xFF336699.toInt(), BackgroundSettings.parseHexColor("#336699"))
        assertEquals(0x80336699.toInt(), BackgroundSettings.parseHexColor("#80336699"))
    }

    @Test
    fun rejectsIncompleteOrInvalidHex() {
        assertNull(BackgroundSettings.parseHexColor(""))
        assertNull(BackgroundSettings.parseHexColor("#12"))
        assertNull(BackgroundSettings.parseHexColor("#GGGGGG"))
        assertNull(BackgroundSettings.parseHexColor("#123456789"))
    }

    @Test
    fun colorHexRoundTripsArgbValues() {
        val colours = listOf(0xFFFFFCF7.toInt(), 0xFF1F1A14.toInt(), 0x80336699.toInt(), 0x00000000)
        colours.forEach { colour ->
            assertEquals(colour, BackgroundSettings.parseHexColor(BackgroundSettings.colorToHex(colour)))
        }
    }

    @Test
    fun defaultLightAndDarkBackgroundsAreDistinct() {
        val defaults = BackgroundPreferences()
        assertNotEquals(defaults.solid(dark = false), defaults.solid(dark = true))
        assertNotEquals(defaults.gradientStart(dark = false), defaults.gradientStart(dark = true))
        assertNotEquals(defaults.dotsBackground(dark = false), defaults.dotsBackground(dark = true))
    }
}
