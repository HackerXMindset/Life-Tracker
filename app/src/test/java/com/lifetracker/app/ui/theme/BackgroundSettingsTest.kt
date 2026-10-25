package com.lifetracker.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackgroundSettingsTest {
    @Test
    fun solidCreamyWhiteIsTheLightDefault() {
        assertEquals(BackgroundMode.Solid, BackgroundStyle().mode)
        assertEquals(0xFFFFFCF7.toInt(), BackgroundStyle().solidArgb(dark = false))
    }

    @Test
    fun lightAndDarkColoursAreStoredSeparately() {
        val style = BackgroundStyle(
            lightSolidArgb = 0xFFFDFBF6.toInt(),
            darkSolidArgb = 0xFF121212.toInt(),
        )
        assertEquals(0xFFFDFBF6.toInt(), style.solidArgb(dark = false))
        assertEquals(0xFF121212.toInt(), style.solidArgb(dark = true))
        assertNotEquals(style.solidArgb(dark = false), style.solidArgb(dark = true))
    }

    @Test
    fun backgroundModesRoundTripByTheirNames() {
        for (mode in BackgroundMode.entries) {
            assertEquals(mode, BackgroundMode.parse(mode.name))
            assertEquals(mode, BackgroundMode.parse(mode.name.lowercase()))
        }
    }

    @Test
    fun unknownBackgroundModeFallsBackToSolid() {
        assertEquals(BackgroundMode.Solid, BackgroundMode.parse(null))
        assertEquals(BackgroundMode.Solid, BackgroundMode.parse("paper-texture"))
        assertEquals(BackgroundMode.Solid, BackgroundMode.parse(""))
    }

    @Test
    fun gradientAndDotsAlsoHaveIndependentLightAndDarkValues() {
        val style = BackgroundStyle()
        assertNotEquals(style.gradientStartArgb(false), style.gradientStartArgb(true))
        assertNotEquals(style.gradientEndArgb(false), style.gradientEndArgb(true))
        assertNotEquals(style.dotsBackgroundArgb(false), style.dotsBackgroundArgb(true))
        assertNotEquals(style.dotsArgb(false), style.dotsArgb(true))
    }
}
