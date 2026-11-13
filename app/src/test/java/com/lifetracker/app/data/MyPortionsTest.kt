package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyPortionsTest {
    @Test
    fun aLineNeedsANameAndASensibleWeight() {
        assertEquals(MyPortion("roti", 45.0), MyPortions.clean("  roti ", 45.0))
        assertNull(MyPortions.clean("", 45.0))
        assertNull(MyPortions.clean("roti", null))
        assertNull(MyPortions.clean("roti", 0.0))
        assertNull(MyPortions.clean("roti", 5000.1))
        assertEquals(MyPortion("katori of dal", 180.0), MyPortions.clean("katori\tof \n dal|", 180.0))
    }

    @Test
    fun aNameIsKeptOnceAndLastWeightWins() {
        val list = MyPortions.normalize(listOf(MyPortion("Roti", 40.0), MyPortion("katori", 150.0), MyPortion("roti", 45.0)))
        assertEquals(listOf(MyPortion("roti", 45.0), MyPortion("katori", 150.0)), list)
    }

    @Test
    fun theListSurvivesBeingSavedAndRead() {
        val list = listOf(MyPortion("roti", 45.0), MyPortion("katori of dal", 180.5))
        assertEquals(list, MyPortions.decode(MyPortions.encode(list)))
        assertTrue(MyPortions.decode("").isEmpty())
        assertEquals(listOf(MyPortion("a", 1.0)), MyPortions.decode("junk line\n\nb\tx\na\t1"))
    }

    @Test
    fun atMostThirtyAreKept() {
        val many = (1..40).map { MyPortion("food $it", it.toDouble()) }
        assertEquals(MyPortions.MAX, MyPortions.normalize(many).size)
    }

    @Test
    fun theReaderIsToldYourSizes() {
        assertEquals("", MyPortions.promptLine(emptyList()))
        val line = MyPortions.promptLine(listOf(MyPortion("roti", 45.0), MyPortion("katori of dal", 180.5)))
        assertTrue("1 roti = 45 g" in line)
        assertTrue("1 katori of dal = 180.5 g" in line)
        assertTrue(line.startsWith("\n\n"))
    }

    @Test
    fun theSuggestionsAreValidAndDistinct() {
        assertTrue(MyPortions.SUGGESTED.all { MyPortions.clean(it.name, it.grams) != null })
        assertEquals(MyPortions.SUGGESTED.size, MyPortions.SUGGESTED.map { it.name }.toSet().size)
        assertFalse(SettingsBackup.included("food_ai"))
        assertTrue(SettingsBackup.included(MyPortions.FILE))
    }
}
