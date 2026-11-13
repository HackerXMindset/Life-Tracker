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

class FoodHistoryDetailTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun meal(daysAgo: Int, name: String, grams: Double = 300.0, kcal100: Double = 140.0, type: String = "lunch", minute: Int = 780) =
        MealEntity("$daysAgo$name$minute", today.minusDays(daysAgo.toLong()).toString(), minute, type, name, grams, kcal100, 20.0, 5.0, 6.0, "app", "")

    @Test
    fun anAmountInFrontOfTheNameIsNotPartOfTheFood() {
        val meals = listOf(meal(1, "500g masala biryani", 500.0), meal(3, "Masala biryani", 300.0))
        val info = FoodHistory.build(meals, "", "500g masala biryani", today)
        assertEquals("Masala biryani", info.title)
        assertEquals(2, info.times)
    }

    @Test
    fun totalsAddUpTheWeightAndTheNumbers() {
        val meals = listOf(meal(1, "Masala biryani", 500.0, 141.0), meal(3, "Masala biryani", 300.0, 139.0))
        val t = FoodHistory.build(meals, "", "Masala biryani", today).totals!!
        assertEquals(800.0, t.grams, 1e-9)
        assertEquals(500.0 * 1.41 + 300.0 * 1.39, t.kcal, 1e-9)
        assertEquals(300.0, t.minGrams, 1e-9)
        assertEquals(500.0, t.maxGrams, 1e-9)
        assertEquals(139.0, t.minKcal100, 1e-9)
        assertEquals(141.0, t.maxKcal100, 1e-9)
        assertEquals(800.0, FoodHistory.build(meals, "", "Masala biryani", today).windows[0].grams, 1e-9)
    }

    @Test
    fun aFoodInsideALongerLineIsCountedButItsWeightIsLeftOut() {
        val meals = listOf(meal(1, "Roti", 60.0), meal(2, "Chicken curry and roti", 400.0))
        val info = FoodHistory.build(meals, "", "Roti", today)
        assertEquals(2, info.times)
        assertEquals(1, info.mixedTimes)
        assertEquals(60.0, info.totals!!.grams, 1e-9)
    }

    @Test
    fun aFoodThatWasOnlyEverPartOfALongerLineHasNoTotals() {
        val info = FoodHistory.build(listOf(meal(1, "Chicken curry and roti", 400.0)), "", "Roti", today)
        assertEquals(1, info.times)
        assertNull(info.totals)
    }

    @Test
    fun similarNamedFoodsStayApartButAreListed() {
        val meals = listOf(
            meal(1, "Masala biryani"), meal(2, "Moradabadi biryani"), meal(3, "Moradabadi biryani"), meal(4, "Chicken biryani"),
            meal(5, "Biryani"), meal(6, "Palak paneer"),
        )
        val info = FoodHistory.build(meals, "", "Masala biryani", today)
        assertEquals(1, info.times)
        assertEquals(listOf("Moradabadi biryani", "Biryani", "Chicken biryani"), info.variants.map { it.name })
        assertEquals(2, info.variants[0].times)
        assertEquals(today.minusDays(2), info.variants[0].last)
    }

    @Test
    fun aLongerLineHasNoVariantsList() {
        assertTrue(FoodHistory.variants(listOf(meal(1, "Masala biryani")), "Dal and rice").isEmpty())
    }

    @Test
    fun theUsualMinuteAndMealsAreCounted() {
        val meals = listOf(meal(1, "Dal", minute = 600, type = "breakfast"), meal(2, "Dal", minute = 780), meal(3, "Dal", minute = 800))
        val info = FoodHistory.build(meals, "", "Dal", today)
        assertEquals(780, info.usualMinute)
        assertEquals(listOf("lunch" to 2, "breakfast" to 1), info.byMeal)
    }
}
