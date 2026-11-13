package com.lifetracker.app.data

import java.time.LocalDate

/** How often and when you ate one food. Free of the screen so it can be tested. */
object FoodHistory {
    /** The stretches the info page counts, in days, ending today. */
    val WINDOWS = listOf(7, 30, 90)

    /** [grams] and [kcal] add up only the times this food was logged on its own line, since in a longer line its share is not known. */
    class Window(val days: Int, val times: Int, val daysEaten: Int, val grams: Double = 0.0, val kcal: Double = 0.0)

    /** A different food whose name looks like this one ("moradabadi biryani" next to "masala biryani"), with how often you ate it. */
    class Variant(val name: String, val times: Int, val last: LocalDate)

    /** Everything added up over the times it was logged on its own line. */
    class Totals(
        val times: Int,
        val grams: Double,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val minGrams: Double,
        val maxGrams: Double,
        val minKcal100: Double,
        val maxKcal100: Double,
    )

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
        /** The name without an amount in front ("500g masala biryani" is "Masala biryani"). */
        val title: String = name,
        /** Totals over the times it was logged on its own line; null when it was only ever part of a longer line. */
        val totals: Totals? = null,
        /** Times it was part of a longer line with other foods, so its own weight is not known. */
        val mixedTimes: Int = 0,
        /** How many times for each meal, most often first. */
        val byMeal: List<Pair<String, Int>> = emptyList(),
        /** The minute of the day you usually eat it (the middle one), or null when never. */
        val usualMinute: Int? = null,
        /** Foods with a similar name that you also ate, most often first. */
        val variants: List<Variant> = emptyList(),
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

    /** True when the whole line is this one food, so its weight and calories are its own. */
    private fun alone(meal: MealEntity, foodId: String, name: String): Boolean =
        (foodId.isNotBlank() && meal.foodId == foodId) || FoodParts.keys(meal.name).size <= 1 || fold(meal.name) == fold(name)

    /** The foods with a name that looks like [name] (the same last word, a different food), from every meal. */
    fun variants(meals: List<MealEntity>, name: String): List<Variant> {
        val wanted = FoodParts.keys(name)
        if (wanted.size != 1) return emptyList()
        val key = wanted[0]
        val head = key.substringAfterLast(' ')
        class Seen(val key: String, val shown: String, val date: LocalDate)
        return meals.flatMap { m ->
            val date = LocalDate.parse(m.date)
            FoodParts.split(m.name).map { part -> Seen(FoodParts.key(part), FoodParts.display(part), date) }
        }
            .filter { it.key.isNotEmpty() && it.key != key && it.key.substringAfterLast(' ') == head }
            .groupBy { it.key }
            .map { (_, list) -> Variant(list.first().shown, list.size, list.maxOf { it.date }) }
            .sortedWith(compareBy({ -it.times }, { it.name.lowercase() }))
            .take(6)
    }

    fun build(meals: List<MealEntity>, foodId: String, name: String, today: LocalDate): Info {
        val mine = meals.filter { matches(it, foodId, name) }
            .sortedWith(compareByDescending<MealEntity> { it.date }.thenByDescending { it.minuteOfDay })
        val own = mine.filter { alone(it, foodId, name) }
        val dates = mine.map { LocalDate.parse(it.date) }.toSet()
        val windows = WINDOWS.map { n ->
            val from = today.minusDays(n - 1L)
            fun inside(list: List<MealEntity>) = list.filter { val d = LocalDate.parse(it.date); !d.isBefore(from) && !d.isAfter(today) }
            val all = inside(mine)
            val ownIn = inside(own)
            Window(n, all.size, all.map { it.date }.distinct().size, ownIn.sumOf { it.grams }, ownIn.sumOf { it.kcal })
        }
        var run = 0
        var day = if (today in dates) today else today.minusDays(1)
        while (day in dates) { run++; day = day.minusDays(1) }
        val n = mine.size
        val minutes = mine.map { it.minuteOfDay }.sorted()
        val totals = if (own.isEmpty()) null else Totals(
            times = own.size,
            grams = own.sumOf { it.grams },
            kcal = own.sumOf { it.kcal },
            protein = own.sumOf { it.protein },
            carbs = own.sumOf { it.carbs },
            fat = own.sumOf { it.fat },
            minGrams = own.minOf { it.grams },
            maxGrams = own.maxOf { it.grams },
            minKcal100 = own.minOf { it.kcal100 },
            maxKcal100 = own.maxOf { it.kcal100 },
        )
        val cleaned = FoodParts.split(name).singleOrNull()?.let { FoodParts.display(it) } ?: name
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
            title = cleaned,
            totals = totals,
            mixedTimes = n - own.size,
            byMeal = mine.groupingBy { it.mealType }.eachCount().toList().sortedWith(compareBy({ -it.second }, { it.first })),
            usualMinute = if (minutes.isEmpty()) null else minutes[minutes.size / 2],
            variants = variants(meals, name),
        )
    }
}
