package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodHistoryTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun meal(daysAgo: Int, name: String = "Palak paneer", foodId: String = "", type: String = "lunch", grams: Double = 150.0, minute: Int = 780) =
        MealEntity("m$daysAgo$name$minute", today.minusDays(daysAgo.toLong()).toString(), minute, type, name, grams, 160.0, 0.0, 0.0, 6.0, "app", "", foodId = foodId)

    @Test
    fun aMealMatchesByFoodIdOrByNameIgnoringCase() {
        assertTrue(FoodHistory.matches(meal(0, "Dal", "a:dal"), "a:dal", "something else"))
        assertTrue(FoodHistory.matches(meal(0, "  palak   PANEER "), "", "Palak paneer"))
        assertFalse(FoodHistory.matches(meal(0, "Rice"), "", "Palak paneer"))
        assertFalse(FoodHistory.matches(meal(0, "Rice", "a:rice"), "a:dal", "Dal"))
    }

    @Test
    fun countsTheLastSevenThirtyAndNinetyDaysIncludingToday() {
        val meals = listOf(meal(0), meal(6), meal(7), meal(29), meal(30), meal(89), meal(90), meal(200))
        val info = FoodHistory.build(meals, "", "Palak paneer", today)
        assertEquals(listOf(2, 4, 6), info.windows.map { it.times })
        assertEquals(8, info.times)
    }

    @Test
    fun twoHelpingsADayCountTwiceButOneDay() {
        val meals = listOf(meal(0, minute = 700), meal(0, minute = 1200), meal(1))
        val info = FoodHistory.build(meals, "", "Palak paneer", today)
        assertEquals(3, info.windows[0].times)
        assertEquals(2, info.windows[0].daysEaten)
        assertEquals(2, info.daysEaten)
    }

    @Test
    fun firstAndLastDayAreFound() {
        val info = FoodHistory.build(listOf(meal(40), meal(2), meal(10)), "", "Palak paneer", today)
        assertEquals(today.minusDays(2), info.last)
        assertEquals(today.minusDays(40), info.first)
        assertEquals(today.minusDays(2).toString(), info.entries.first().date)
    }

    @Test
    fun theRunOfDaysInARowEndsTodayOrYesterday() {
        assertEquals(3, FoodHistory.build(listOf(meal(0), meal(1), meal(2), meal(4)), "", "Palak paneer", today).currentRun)
        assertEquals(2, FoodHistory.build(listOf(meal(1), meal(2), meal(4)), "", "Palak paneer", today).currentRun)
        assertEquals(0, FoodHistory.build(listOf(meal(2), meal(3)), "", "Palak paneer", today).currentRun)
    }

    @Test
    fun averagesAndTheUsualMeal() {
        val info = FoodHistory.build(listOf(meal(0, grams = 100.0), meal(1, grams = 200.0, type = "dinner"), meal(2, type = "dinner")), "", "Palak paneer", today)
        assertEquals(150.0, info.avgGrams, 1e-9)
        assertEquals("dinner", info.usualMeal)
        assertEquals(160.0 * 1.5, info.avgKcal, 1e-9)
    }

    @Test
    fun aFoodNeverEatenHasNothing() {
        val info = FoodHistory.build(listOf(meal(0, "Rice")), "", "Palak paneer", today)
        assertEquals(0, info.times)
        assertNull(info.last)
        assertNull(info.usualMeal)
        assertEquals(listOf(0, 0, 0), info.windows.map { it.times })
        assertEquals(0.0, info.avgKcal, 1e-9)
    }
}
