package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSanityTest {
    @Test
    fun caloriesFarFromTheMacrosAreFlagged() {
        val high = FoodSanity.macroMismatch(520.0, 20.0, 30.0, 10.0)
        assertNotNull(high)
        assertTrue("more than" in high!! && "290" in high)
        val low = FoodSanity.macroMismatch(200.0, 30.0, 40.0, 20.0)
        assertTrue("less than" in low!!)
    }

    @Test
    fun smallGapsAndMissingNumbersAreLeftAlone() {
        assertNull(FoodSanity.macroMismatch(380.0, 20.0, 40.0, 10.0))
        assertNull(FoodSanity.macroMismatch(140.0, 5.0, 25.0, 2.0))
        assertNull(FoodSanity.macroMismatch(40.0, 0.0, 5.0, 1.0))
        assertNull(FoodSanity.macroMismatch(300.0, 0.0, 0.0, 0.0))
        assertNull(FoodSanity.macroMismatch(0.0, 10.0, 10.0, 10.0))
    }

    @Test
    fun numbersThatCannotExistAreFlagged() {
        assertEquals(1, FoodSanity.impossible100(950.0, 0.0, 0.0, 99.0).size)
        assertTrue(FoodSanity.impossible100(900.0, 0.0, 0.0, 100.0).isEmpty())
        val over = FoodSanity.impossible100(300.0, 50.0, 50.0, 20.0)
        assertEquals(1, over.size)
        assertTrue("120 g" in over[0])
    }

    @Test
    fun aHugeAmountIsFlagged() {
        assertTrue(FoodSanity.bigAmount(2999.0, 100.0).isEmpty())
        assertEquals(1, FoodSanity.bigAmount(3000.0, 100.0).size)
        assertEquals(2, FoodSanity.bigAmount(20000.0, 54000.0).size)
        assertEquals(1, FoodSanity.bigAmount(500.0, 3600.0).size)
    }

    @Test
    fun twentyKilosOfSteakGetsLookAgainLines() {
        val notes = FoodSanity.check(20000.0, 54000.0, 5000.0, 0.0, 3500.0)
        assertTrue(notes.size in 2..3)
        assertTrue("20000 g" in notes[0])
    }

    @Test
    fun anOrdinaryMealHasNoNotes() {
        assertTrue(FoodSanity.check(300.0, 450.0, 18.0, 70.0, 9.0).isEmpty())
        assertTrue(FoodSanity.check(0.0, 450.0, 18.0, 70.0, 9.0).isEmpty())
    }

    @Test
    fun aFoodTypedPer100IsChecked() {
        assertEquals(2, FoodSanity.checkPer100(950.0, 10.0, 10.0, 10.0).size)
        assertTrue(FoodSanity.checkPer100(130.0, 2.7, 28.0, 0.3).isEmpty())
    }
}
