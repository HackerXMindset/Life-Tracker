package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoMealTest {
    private val food = PhotoFood(
        name = "Dal rice", servingGrams = 300.0, kcal = 450.0, protein = 15.0, carbs = 75.0, fat = 9.0, fiber = 6.0, sugar = 3.0,
        sodiumMg = 600.0, sat = 2.0, mono = 3.0, poly = 1.5, drink = false, ingredients = listOf("Rice 150 g", "Dal 150 g"), weightGuessed = false,
    )

    @Test
    fun aLoggedMealAddsUpToWhatThePhotoReaderSaid() {
        val meal = food.toMeal("  Dal rice ", "lunch", "2026-11-09", 780, id = "x")
        assertEquals("Dal rice", meal.name)
        assertEquals(300.0, meal.grams, 1e-9)
        assertEquals(450.0, meal.kcal, 1e-9)
        assertEquals(15.0, meal.protein, 1e-9)
        assertEquals(75.0, meal.carbs, 1e-9)
        assertEquals(9.0, meal.fat, 1e-9)
        assertEquals(6.0, meal.fiber, 1e-9)
        assertEquals(600.0, meal.sodiumMg, 1e-9)
        assertEquals("lunch", meal.mealType)
        assertEquals(780, meal.minuteOfDay)
    }

    @Test
    fun aSmallerPortionScalesEveryNumber() {
        val half = food.atGrams(150.0)
        assertEquals(150.0, half.servingGrams, 1e-9)
        assertEquals(225.0, half.kcal, 1e-9)
        assertEquals(7.5, half.protein, 1e-9)
        assertEquals(300.0, half.sodiumMg, 1e-9)
        val meal = half.toMeal("Dal rice", "dinner", "2026-11-09", 1200)
        assertEquals(225.0, meal.kcal, 1e-9)
        assertEquals(150.0, meal.kcal100, 1e-9) // per 100 g does not change with the portion
    }

    @Test
    fun anEmptyNameBecomesFoodAndAZeroPortionChangesNothing() {
        assertEquals("Food", food.toMeal("   ", "snack", "2026-11-09", 600).name)
        assertEquals(food, food.atGrams(0.0))
    }
}
