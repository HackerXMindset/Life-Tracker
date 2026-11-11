package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatMatchTest {
    private fun meal(id: String, name: String, date: String, kcal100: Double = 150.0, minute: Int = 780) = MealEntity(
        id = id, date = date, minuteOfDay = minute, mealType = "lunch", name = name, grams = 300.0,
        kcal100 = kcal100, carbs100 = 20.0, fat100 = 5.0, protein100 = 6.0, source = "app", raw = "",
    )

    @Test
    fun wordsDropCountsFillersAccentsAndPlurals() {
        assertEquals(setOf("dal", "tadka", "roti"), RepeatMatch.words("3 Rotis with Dal Tadka"))
        assertEquals(setOf("paneer", "tikka"), RepeatMatch.words("Panéer Tikka"))
        assertTrue(RepeatMatch.words("200 g").isEmpty())
    }

    @Test
    fun theSameDishMatchesAndADifferentOneDoesNot() {
        assertEquals(1.0, RepeatMatch.similarity("Dal tadka with 2 rotis", "dal tadka, roti"), 1e-9)
        assertTrue(RepeatMatch.similarity("Dal tadka rice", "Dal tadka roti") < 0.67)
        assertEquals(0.0, RepeatMatch.similarity("", "Dal"), 1e-9)
    }

    @Test
    fun theMostRecentOfTheBestMatchesWins() {
        val old = meal("a", "Dal tadka roti", "2026-10-01")
        val newer = meal("b", "Dal tadka roti", "2026-11-05")
        val other = meal("c", "Chicken biryani", "2026-11-08")
        assertEquals("b", RepeatMatch.find("Dal tadka with 3 rotis", listOf(old, other, newer))?.id)
        assertNull(RepeatMatch.find("Masala dosa", listOf(old, other, newer)))
    }

    @Test
    fun aMealWithNoCaloriesIsNeverOffered() {
        assertNull(RepeatMatch.find("Dal tadka roti", listOf(meal("a", "Dal tadka roti", "2026-11-05", kcal100 = 0.0))))
    }
}
