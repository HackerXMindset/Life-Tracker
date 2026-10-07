package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodStatsTest {
    private fun meal(id: String, name: String, grams: Double, kcal100: Double, date: String = "2026-10-04", minute: Int = 700) =
        MealEntity(id, date, minute, "lunch", name, grams, kcal100, 10.0, 5.0, 20.0, "app", "{}")

    @Test
    fun caloriesAreAmountTimesPer100g() {
        val m = meal("1", "Rice", 50.0, 390.0)
        assertEquals(195.0, m.kcal, 1e-9)
        assertEquals(5.0, m.carbs, 1e-9)
        assertEquals(10.0, m.protein, 1e-9)
    }

    @Test
    fun dayTotalsAddUp() {
        // The same arithmetic OpenNutriTracker uses: a 400 "serving" roti at 106 kcal per 100 is 424 kcal.
        val t = FoodStats.totals(
            listOf(
                meal("1", "Chole", 100.0, 650.0),
                meal("2", "Rice", 50.0, 390.0),
                meal("3", "Roti", 400.0, 106.0),
            ),
        )
        assertEquals(650.0 + 195.0 + 424.0, t.kcal, 1e-9)
    }

    @Test
    fun emptyDayIsZero() {
        assertEquals(Macros(0.0, 0.0, 0.0, 0.0), FoodStats.totals(emptyList()))
    }

    @Test
    fun goalIsTheLatestOneOnOrBeforeTheDay() {
        val goals = listOf(
            FoodGoalEntity("2026-10-04", 2672.0, 399.0, 74.0, 99.0),
            FoodGoalEntity("2026-10-05", 2609.0, 391.0, 72.0, 97.0),
        )
        assertNull(FoodStats.goalFor(goals, LocalDate.of(2026, 10, 3)))
        assertEquals(2672.0, FoodStats.goalFor(goals, LocalDate.of(2026, 10, 4))!!.kcal, 0.0)
        assertEquals(2609.0, FoodStats.goalFor(goals, LocalDate.of(2026, 10, 9))!!.kcal, 0.0)
    }

    @Test
    fun recentMealsAreDistinctByNameNewestFirst() {
        val list = FoodStats.recentDistinct(
            listOf(
                meal("1", "Biryani", 100.0, 800.0, date = "2026-10-04"),
                meal("2", "biryani ", 100.0, 800.0, date = "2026-10-06"),
                meal("3", "Dosa", 100.0, 443.0, date = "2026-10-05"),
            ),
            limit = 8,
        )
        assertEquals(listOf("2", "3"), list.map { it.id })
    }
}
