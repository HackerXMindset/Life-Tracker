package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientWatchTest {
    private val iron = NutrientEntity("iron", "Iron", "mg", "mineral", 18.0, false, true, 0)
    private val b12 = NutrientEntity("vit-b12", "Vitamin B12", "µg", "vitamin", 2.4, false, true, 1)
    private val nutrients = listOf(iron, b12)

    // 100 g of a food with [ironMg] iron per 100 g, remembered on the meal as its nutrient numbers.
    private fun meal(id: String, date: String, ironMg: Double?) = MealEntity(
        id, date, 700, "lunch", "Food", 100.0, 100.0, 10.0, 5.0, 10.0, "app", "{}",
        extra = if (ironMg == null) "" else NutrientValues.encode(mapOf("iron" to ironMg)),
    )

    private fun week(vararg iron: Double?) = iron.mapIndexed { i, v -> meal("m$i", "2026-11-0${i + 1}", v) }

    @Test
    fun anAverageBelowSeventyPercentIsLowOnceFourDaysHaveNumbers() {
        // 9 mg is 50 % of 18 mg.
        val r = NutrientWatch.evaluate(week(9.0, 9.0, 9.0, 9.0), nutrients, setOf("iron"))
        assertTrue(r.enough)
        assertEquals(4, r.dataDays)
        assertTrue(r.rows.single().low)
        assertEquals(9.0, r.rows.single().average, 1e-9)
    }

    @Test
    fun nothingIsCalledLowWithTooFewDays() {
        val r = NutrientWatch.evaluate(week(9.0, 9.0, 9.0), nutrients, setOf("iron"))
        assertFalse(r.enough)
        assertFalse(r.rows.single().low)
    }

    @Test
    fun daysWithoutAnyNumbersDoNotDragTheAverageDown() {
        val r = NutrientWatch.evaluate(week(18.0, 18.0, null, 18.0, null, 18.0), nutrients, setOf("iron"))
        assertEquals(4, r.dataDays)
        assertEquals(18.0, r.rows.single().average, 1e-9)
        assertFalse(r.rows.single().low)
    }

    @Test
    fun onlyThePickedNutrientsAreListed() {
        val r = NutrientWatch.evaluate(week(9.0, 9.0, 9.0, 9.0), nutrients, setOf("vit-b12"))
        assertEquals(listOf("vit-b12"), r.rows.map { it.nutrient.id })
        // None of the foods had B12, so its average is 0 and it is low.
        assertTrue(r.rows.single().low)
    }

    private val library = FoodLibrary.parse(
        "id\ten\tk\tcat\nspinach\tSpinach\t23\tveg\nthyme\tThyme\t101\tspice\nlentils\tLentils\t116\tlegume\nbanana\tBanana\t89\tfruit\nlentils2\tlentils\t110\tlegume\n",
        "id\ten\titems\n",
        "id\tiron\nspinach\t2.7\nthyme\t17\nlentils\t3.3\nbanana\t0.3\nlentils2\t3.0\n",
    )

    @Test
    fun suggestionsAreTheRichestFoodsWithoutSpicesOrRepeats() {
        assertEquals(listOf("Lentils", "Spinach", "Banana"), NutrientWatch.richest(library, "iron").map { it.name })
        assertEquals(listOf("Lentils"), NutrientWatch.richest(library, "iron", limit = 1).map { it.name })
        assertTrue(NutrientWatch.richest(library, "vit-b12").isEmpty())
    }

    @Test
    fun coverageCountsFoodsWithNumbers() {
        assertEquals(5 to 5, NutrientWatch.coverage(library))
    }
}
