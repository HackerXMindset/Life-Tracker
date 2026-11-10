package com.lifetracker.app.data

import java.time.LocalDate

/** The numbers behind the week window: each day, how the week went, what you ate most, and how your weight moved. */
object WeekReport {
    class Day(val date: LocalDate, val meals: List<MealEntity>, val goal: FoodGoalEntity?) {
        val kcal: Double get() = meals.sumOf { it.kcal }
        val protein: Double get() = meals.sumOf { it.protein }
        val logged: Boolean get() = meals.isNotEmpty()

        /** How far the day is from the calorie goal, as a share of the goal; null when there is no goal. */
        val miss: Double? get() = goal?.takeIf { it.kcal > 0 }?.let { Math.abs(kcal - it.kcal) / it.kcal }
    }

    class Report(
        val days: List<Day>,
        val loggedDays: Int,
        val avgKcal: Double,
        val avgProtein: Double,
        val daysOnTarget: Int,
        /** Days with at least 90 % of the protein goal, and how many logged days had a protein goal at all. */
        val proteinDays: Int,
        val proteinGoalDays: Int,
        /** The logged day closest to its calorie goal, and the one furthest from it (null when there is only one such day). */
        val closest: Day?,
        val furthest: Day?,
        /** Up to five foods with the number of times they were logged. */
        val topFoods: List<Pair<String, Int>>,
        /** Last weigh-in of the week minus the first, when there are at least two; null otherwise. */
        val weightChangeKg: Double?,
    )

    fun build(week: List<LocalDate>, meals: List<MealEntity>, goalFor: (String) -> FoodGoalEntity?, weights: List<WeightEntity>): Report {
        val byDay = meals.groupBy { it.date }
        val days = week.map { d -> Day(d, byDay[d.toString()].orEmpty(), goalFor(d.toString())) }
        val logged = days.filter { it.logged }
        val summary = WeekMath.summary(meals.filter { it.date in week.map { d -> d.toString() } }, goalFor)
        val withGoal = logged.filter { it.miss != null }
        val closest = withGoal.minByOrNull { it.miss!! }
        val furthest = withGoal.maxByOrNull { it.miss!! }?.takeIf { it !== closest }
        val proteinGoal = logged.filter { (it.goal?.protein ?: 0.0) > 0 }
        val top = meals
            .groupBy { it.name.trim().lowercase() }
            .map { (_, list) -> list.first().name.trim() to list.size }
            .sortedWith(compareBy({ -it.second }, { it.first.lowercase() }))
            .take(5)
        val range = week.map { it.toString() }.toSet()
        val weighed = weights.filter { it.date in range }.sortedBy { it.date }
        return Report(
            days = days,
            loggedDays = logged.size,
            avgKcal = summary.avgKcal,
            avgProtein = summary.avgProtein,
            daysOnTarget = summary.daysOnTarget,
            proteinDays = proteinGoal.count { it.protein >= it.goal!!.protein * 0.9 },
            proteinGoalDays = proteinGoal.size,
            closest = closest,
            furthest = furthest,
            topFoods = top,
            weightChangeKg = if (weighed.size >= 2) weighed.last().kg - weighed.first().kg else null,
        )
    }
}
