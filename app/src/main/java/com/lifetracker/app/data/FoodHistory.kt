package com.lifetracker.app.data

import java.time.LocalDate

/** How often and when you ate one food. Free of the screen so it can be tested. */
object FoodHistory {
    /** The stretches the info page counts, in days, ending today. */
    val WINDOWS = listOf(7, 30, 90)

    class Window(val days: Int, val times: Int, val daysEaten: Int)

    class Info(
        val name: String,
        /** Every time it was logged, newest first. */
        val entries: List<MealEntity>,
        val times: Int,
        val daysEaten: Int,
        val first: LocalDate?,
        val last: LocalDate?,
        val windows: List<Window>,
        /** Days in a row up to today (or up to yesterday, if not yet eaten today). */
        val currentRun: Int,
        val avgGrams: Double,
        val avgKcal: Double,
        val avgProtein: Double,
        /** The meal it is most often eaten as, or null when never. */
        val usualMeal: String?,
    )

    private fun fold(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** True when [meal] is this food: the same food id, the same name ignoring case and spacing, or [name] is one food that the meal's name lists. */
    fun matches(meal: MealEntity, foodId: String, name: String): Boolean {
        if (foodId.isNotBlank() && meal.foodId == foodId) return true
        if (fold(meal.name) == fold(name)) return true
        // A food on its own is found inside meals that list it with others ("chicken curry and roti" counts for the roti too).
        val wanted = FoodParts.keys(name)
        return wanted.size == 1 && wanted[0] in FoodParts.keys(meal.name)
    }

    fun build(meals: List<MealEntity>, foodId: String, name: String, today: LocalDate): Info {
        val mine = meals.filter { matches(it, foodId, name) }
            .sortedWith(compareByDescending<MealEntity> { it.date }.thenByDescending { it.minuteOfDay })
        val dates = mine.map { LocalDate.parse(it.date) }.toSet()
        val windows = WINDOWS.map { n ->
            val from = today.minusDays(n - 1L)
            val inside = mine.filter { val d = LocalDate.parse(it.date); !d.isBefore(from) && !d.isAfter(today) }
            Window(n, inside.size, inside.map { it.date }.distinct().size)
        }
        var run = 0
        var day = if (today in dates) today else today.minusDays(1)
        while (day in dates) { run++; day = day.minusDays(1) }
        val n = mine.size
        return Info(
            name = name,
            entries = mine,
            times = n,
            daysEaten = dates.size,
            first = dates.minOrNull(),
            last = dates.maxOrNull(),
            windows = windows,
            currentRun = run,
            avgGrams = if (n == 0) 0.0 else mine.sumOf { it.grams } / n,
            avgKcal = if (n == 0) 0.0 else mine.sumOf { it.kcal } / n,
            avgProtein = if (n == 0) 0.0 else mine.sumOf { it.protein } / n,
            usualMeal = mine.groupingBy { it.mealType }.eachCount().maxWithOrNull(compareBy({ it.value }, { it.key }))?.key,
        )
    }
}
