package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientTextTest {
    private fun names(text: String) = IngredientText.parse(text).map { it.name to it.grams }

    @Test
    fun amountsComeFirstOrLastWithCommonUnits() {
        assertEquals(listOf("rice" to 200.0, "chicken" to 150.0), names("200 g rice, 150 g chicken"))
        assertEquals(listOf("rice" to 200.0, "chicken" to 150.0), names("rice 200g and chicken 150 grams"))
        assertEquals(listOf("milk" to 500.0), names("0.5 l of milk"))
        assertEquals(listOf("atta" to 1500.0), names("1,5 kg atta"))
        assertEquals(listOf("ghee" to 10.0), names("ghee: 10 g"))
    }

    @Test
    fun anIngredientWithoutAWeightKeepsItsText() {
        val parts = IngredientText.parse("2 roti; 1 onion + 200 g paneer")
        assertEquals(listOf("2 roti", "1 onion", "paneer"), parts.map { it.name })
        assertNull(parts[0].grams)
        assertEquals(200.0, parts[2].grams!!, 1e-9)
        assertEquals("2 roti", parts[0].original)
    }

    @Test
    fun emptyAndBlankPiecesAreDropped() {
        assertTrue(IngredientText.parse("  , ;  ").isEmpty())
        assertTrue(IngredientText.parse("").isEmpty())
    }

    @Test
    fun aLibraryFoodMatchesOnlyBySameNameOrStart() {
        assertTrue(IngredientText.accepts("Rice", "rice"))
        assertTrue(IngredientText.accepts("Rice, cooked", "rice"))
        assertTrue(IngredientText.accepts("Chicken breast", "chicken"))
        assertFalse(IngredientText.accepts("Rice cake", "cake"))
        assertFalse(IngredientText.accepts("Ricotta", "rice"))
        assertFalse(IngredientText.accepts("Rice", ""))
    }

    @Test
    fun anAiAnswerBecomesIngredientsPer100g() {
        val food = PhotoFood(
            "Dal and rice", 400.0, 600.0, 20.0, 100.0, 10.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, emptyList(), false,
            items = listOf(PhotoItem("Rice", 200.0, 260.0, 5.0, 56.0, 0.6), PhotoItem("Dal", 200.0, 200.0, 12.0, 30.0, 4.0), PhotoItem("Air", 0.0, 0.0, 0.0, 0.0, 0.0)),
        )
        val items = IngredientText.itemsFrom(food)
        assertEquals(listOf("Rice", "Dal"), items.map { it.name })
        assertEquals(130.0, items[0].kcal100, 1e-9)
        assertEquals(200.0, items[0].grams, 1e-9)
        // With no items the whole answer is one ingredient.
        val whole = IngredientText.itemsFrom(food.copy(items = emptyList()))
        assertEquals(1, whole.size)
        assertEquals(150.0, whole[0].kcal100, 1e-9)
    }
}
