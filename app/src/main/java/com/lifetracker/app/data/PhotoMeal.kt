package com.lifetracker.app.data

/** The same food eaten at [grams] instead of the weight the photo reader guessed; every number follows. */
fun PhotoFood.atGrams(grams: Double): PhotoFood {
    if (servingGrams <= 0.0 || grams <= 0.0) return this
    val k = grams / servingGrams
    return copy(
        servingGrams = grams, kcal = kcal * k, protein = protein * k, carbs = carbs * k, fat = fat * k,
        fiber = fiber * k, sugar = sugar * k, sodiumMg = sodiumMg * k, sat = sat * k, mono = mono * k, poly = poly * k,
        micro = micro.mapValues { it.value * k },
        cookingFatG = cookingFatG * k,
        items = items.map { it.copy(grams = it.grams * k, kcal = it.kcal * k, protein = it.protein * k, carbs = it.carbs * k, fat = it.fat * k) },
    )
}

/**
 * The same food with [grams] of cooking oil, ghee or butter instead of what the reader counted. Each gram is 9 kcal and 1 g of fat; the
 * rest stays as it was. Nothing goes below zero.
 */
fun PhotoFood.withCookingFat(grams: Double): PhotoFood {
    val g = grams.coerceAtLeast(0.0)
    val delta = g - cookingFatG
    return copy(kcal = (kcal + delta * 9.0).coerceAtLeast(0.0), fat = (fat + delta).coerceAtLeast(0.0), cookingFatG = g)
}

/**
 * The part of the plate that is ticked, as one food. [ticked] are positions in [PhotoFood.items]. Every number is scaled by the share of
 * the items' calories that is ticked, since fibre, sugar and the rest are only known for the whole plate.
 */
fun PhotoFood.onlyTicked(ticked: Set<Int>): PhotoFood {
    if (!canSplit) return this
    val total = items.sumOf { it.kcal }
    val part = items.withIndex().filter { it.index in ticked }.sumOf { it.value.kcal }
    if (part >= total - 1e-9) return this
    if (part <= 0.0) return atGrams(servingGrams * 1e-6).copy(items = emptyList())
    val scaled = atGrams(servingGrams * part / total)
    return scaled.copy(items = scaled.items.filterIndexed { i, _ -> i in ticked })
}

/** One food for each ticked item, with the item's own weight and main numbers. The other numbers are shared out by calories. */
fun PhotoFood.separateItems(ticked: Set<Int>): List<PhotoFood> {
    if (!canSplit) return listOf(this)
    val total = items.sumOf { it.kcal }
    return items.withIndex().filter { it.index in ticked && it.value.kcal > 0 }.map { (_, item) ->
        val share = item.kcal / total
        val own = item.protein + item.carbs + item.fat > 0
        val shared = atGrams(servingGrams * share)
        shared.copy(
            name = item.name,
            servingGrams = if (item.grams > 0) item.grams else shared.servingGrams,
            kcal = item.kcal,
            protein = if (own) item.protein else shared.protein,
            carbs = if (own) item.carbs else shared.carbs,
            fat = if (own) item.fat else shared.fat,
            ingredients = emptyList(),
            items = emptyList(),
        )
    }
}

/** What the photo reader said, checked by you, as one logged meal. Meals keep their numbers per 100 g, so the weight matters. */
fun PhotoFood.toMeal(name: String, mealType: String, date: String, minuteOfDay: Int, id: String = FoodLogic.newMealId()): MealEntity {
    val base = per100()
    return MealEntity(
        id = id, date = date, minuteOfDay = minuteOfDay, mealType = mealType,
        name = name.trim().ifEmpty { "Food" }, grams = servingGrams,
        kcal100 = base.kcal, carbs100 = base.carbs, fat100 = base.fat, protein100 = base.protein,
        source = "app", raw = "",
        fiber100 = base.fiber, sugar100 = base.sugar, sodium100 = base.sodiumMg,
        sat100 = base.sat, mono100 = base.mono, poly100 = base.poly,
        extra = NutrientValues.encode(base.micro),
    )
}
