package com.lifetracker.app.data

/** The vitamin watch: how the vitamins and minerals you picked averaged over the last week, and which foods have more of a low one. */
object NutrientWatch {
    /** A nutrient is low when its average is under this share of the daily target. */
    const val LOW_SHARE = 0.7

    /** At least this many days with vitamin numbers are needed before anything is called low. */
    const val MIN_DAYS = 4

    class Row(val nutrient: NutrientEntity, val average: Double, val share: Double?, val low: Boolean)

    /** [dataDays] is how many of the logged days had at least one food with vitamin numbers. */
    class Result(val rows: List<Row>, val dataDays: Int, val enough: Boolean)

    /**
     * Averages the picked nutrients over the days in [meals] that have vitamin numbers (a day where no food had any is left out, so a day of
     * foods without numbers cannot drag an average down). Nothing is called low until [MIN_DAYS] such days exist.
     */
    fun evaluate(meals: List<MealEntity>, nutrients: List<NutrientEntity>, watched: Set<String>): Result {
        val days = meals.groupBy { it.date }.values.filter { day -> day.any { !NutrientMath.isMissing(it) } }
        val totals = days.map { NutrientMath.dayTotals(it) }
        val enough = days.size >= MIN_DAYS
        val rows = nutrients.filter { it.id in watched && !it.hidden }.map { n ->
            val average = if (totals.isEmpty()) 0.0 else totals.sumOf { it[n.id] ?: 0.0 } / totals.size
            val share = NutrientMath.fraction(average, n.target)
            Row(n, average, share, enough && share != null && share < LOW_SHARE)
        }
        return Result(rows, days.size, enough)
    }

    private val NOT_FOR_SUGGESTIONS = setOf(FoodCat.Spice, FoodCat.Oil, FoodCat.Sauce, FoodCat.Pantry)

    /** The [limit] foods of the library with the most of [nutrientId] per 100 g, leaving out spices, oils, sauces and pantry items. */
    fun richest(library: FoodLibraryData, nutrientId: String, limit: Int = 3): List<LibraryFood> =
        library.foods
            .filter { it.cat !in NOT_FOR_SUGGESTIONS && (library.microOf(it.id)[nutrientId] ?: 0.0) > 0.0 }
            .sortedByDescending { library.microOf(it.id)[nutrientId] ?: 0.0 }
            .distinctBy { it.name.trim().lowercase() }
            .take(limit)

    /** How many of the library's foods have any vitamin or mineral numbers, and how many foods there are. */
    fun coverage(library: FoodLibraryData): Pair<Int, Int> = library.foods.count { library.microOf(it.id).isNotEmpty() } to library.foods.size
}
