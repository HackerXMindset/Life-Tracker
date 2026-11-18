package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconColorTest {
    private fun px(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    @Test
    fun theLogoColourBeatsTheWhiteBehindIt() {
        val pixels = IntArray(100) { if (it < 70) px(255, 255, 255) else px(220, 30, 40) }
        val c = IconColor.dominant(pixels)
        assertTrue(red(c) > 200 && green(c) < 60 && blue(c) < 60)
    }

    @Test
    fun theBiggerColourWins() {
        val pixels = IntArray(100) { if (it < 30) px(20, 60, 230) else px(240, 40, 40) }
        val c = IconColor.dominant(pixels)
        assertTrue(red(c) > blue(c))
    }

    @Test
    fun aSeeThroughIconHasNoColour() {
        assertEquals(IconColor.GREY, IconColor.dominant(IntArray(50) { px(255, 0, 0, a = 0) }))
    }

    @Test
    fun aGreyLogoGivesItsGrey() {
        val c = IconColor.dominant(IntArray(50) { px(120, 120, 120) })
        assertEquals(120, red(c))
        assertEquals(120, blue(c))
    }
}
