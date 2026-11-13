package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPartsTest {
    @Test
    fun aLineWithTwoFoodsIsTwoFoods() {
        assertEquals(listOf("Chicken curry", "roti"), FoodParts.split("Chicken curry and roti"))
        assertEquals(listOf("dal", "rice", "salad"), FoodParts.split("dal with rice + salad"))
        assertEquals(listOf("paneer", "naan"), FoodParts.split("paneer, naan"))
    }

    @Test
    fun amountsAreDropped() {
        assertEquals(listOf("rotis", "dal"), FoodParts.split("2 rotis, 1 katori dal"))
        assertEquals(listOf("rice"), FoodParts.split("200g rice"))
        assertEquals(listOf("curd"), FoodParts.split("a bowl of curd"))
        assertEquals(listOf("tea"), FoodParts.split("2x tea"))
    }

    @Test
    fun bracketsAreIgnoredAndAnAmountAloneIsKept() {
        assertEquals(listOf("Chicken curry"), FoodParts.split("Chicken curry (home made)"))
        assertEquals(listOf("2"), FoodParts.split("2"))
        assertTrue(FoodParts.split("  ").isEmpty())
    }

    @Test
    fun sameFoodSameKeyWhateverTheSpelling() {
        assertEquals(FoodParts.key("Roti"), FoodParts.key("rotis"))
        assertEquals(FoodParts.key("egg"), FoodParts.key("Eggs"))
        assertEquals(FoodParts.key("berry"), FoodParts.key("berries"))
        assertEquals("hummus", FoodParts.key("hummus"))
        assertEquals("rice", FoodParts.key("Rice"))
        assertFalse(FoodParts.key("dal") == FoodParts.key("rice"))
    }

    @Test
    fun keysListEachFoodOnce() {
        assertEquals(listOf("chicken curry", "roti"), FoodParts.keys("Chicken curry and 2 rotis and roti"))
    }

    private fun meal(date: String, name: String) =
        MealEntity("$date$name", date, 600, "lunch", name, 100.0, 100.0, 0.0, 0.0, 0.0, "app", "")

    @Test
    fun theHistoryOfARotiFindsItInsideALongerLine() {
        val meals = listOf(meal("2026-11-10", "Chicken curry and roti"), meal("2026-11-11", "2 rotis, dal"), meal("2026-11-12", "Rice"))
        val info = FoodHistory.build(meals, "", "Roti", LocalDate.of(2026, 11, 12))
        assertEquals(2, info.times)
        assertEquals(LocalDate.of(2026, 11, 11), info.last)
    }

    @Test
    fun aWholeLineKeepsItsOwnHistory() {
        val meals = listOf(meal("2026-11-10", "Chicken curry and roti"), meal("2026-11-11", "Roti"))
        val info = FoodHistory.build(meals, "", "Chicken curry and roti", LocalDate.of(2026, 11, 12))
        assertEquals(1, info.times)
    }

    @Test
    fun theTopFoodsCountEachFoodOnItsOwn() {
        val span = StatSpan(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15))
        val meals = listOf(meal("2026-11-09", "Chicken curry and roti"), meal("2026-11-10", "2 rotis, dal"), meal("2026-11-11", "Roti"))
        val top = PeriodStats.build(span, LocalDate.of(2026, 11, 12), meals, { null }, emptyList(), emptyList(), emptyList()).topFoods
        assertEquals("Roti", top[0].name)
        assertEquals(3, top[0].times)
        assertEquals(LocalDate.of(2026, 11, 11), top[0].last)
        assertEquals(listOf("Chicken curry", "Dal"), top.drop(1).map { it.name }.sorted())
    }
}
