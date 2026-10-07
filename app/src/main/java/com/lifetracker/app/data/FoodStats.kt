package com.lifetracker.app.data

import java.time.LocalDate

/** Daily totals of calories and macros. */
data class Macros(val kcal: Double, val carbs: Double, val fat: Double, val protein: Double)

/** Food arithmetic, free of the phone and database so it can be tested. */
object FoodStats {
    const val DEFAULT_WATER_GOAL_ML = 3000

    fun totals(meals: List<MealEntity>): Macros = Macros(
        kcal = meals.sumOf { it.kcal },
        carbs = meals.sumOf { it.carbs },
        fat = meals.sumOf { it.fat },
        protein = meals.sumOf { it.protein },
    )

    /** The goal in force on [date]: its own row, otherwise the latest one before it, otherwise null. */
    fun goalFor(goals: List<FoodGoalEntity>, date: LocalDate): FoodGoalEntity? {
        val key = date.toString()
        return goals.filter { it.date <= key }.maxByOrNull { it.date }
    }

    /** Meals you have eaten before, one per name, most recent first, ready to be added again in one tap. */
    fun recentDistinct(meals: List<MealEntity>, limit: Int): List<MealEntity> =
        meals.sortedWith(compareByDescending<MealEntity> { it.date }.thenByDescending { it.minuteOfDay })
            .distinctBy { it.name.trim().lowercase() }
            .take(limit)
}
