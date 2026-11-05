package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedMealItemsTest {
    private val banana = SavedMealItem(
        "a:banana", "Banana \"ripe\"", 120.0, 89.0, 1.1, 22.8, 0.3,
        fiber100 = 2.6, sodium100 = 1.0, amino = "0.077,0.028,0.068,0.05,0.017,0.058,0.028,0.009,0.047",
    )

    @Test
    fun itemsComeBackExactly() {
        val items = listOf(banana, SavedMealItem("", "Chai", 150.0, 40.0, 1.0, 6.0, 1.0))
        assertEquals(items, SavedMealItems.decode(SavedMealItems.encode(items)))
    }

    @Test
    fun zeroValuesAreLeftOutOfTheText() {
        val text = SavedMealItems.encode(listOf(SavedMealItem("", "Chai", 150.0, 40.0, 1.0, 6.0, 1.0)))
        assertTrue(!text.contains("\"fi\"") && !text.contains("\"aa\""))
    }

    @Test
    fun aDamagedTextGivesAnEmptyList() {
        assertTrue(SavedMealItems.decode("not json").isEmpty())
        assertTrue(SavedMealItems.decode("").isEmpty())
    }

    @Test
    fun anItemBecomesAMealWithItsNumbers() {
        val meal = banana.asMeal("id1", "2026-11-06", 480, "breakfast")
        assertEquals(106.8, meal.kcal, 0.001)
        assertEquals("a:banana", meal.foodId)
        assertEquals(3.12, meal.fiber, 0.001)
        assertEquals(banana, SavedMealItem.of(meal))
    }
}
