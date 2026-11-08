package com.lifetracker.app.data

/** The same food eaten at [grams] instead of the weight the photo reader guessed; every number follows. */
fun PhotoFood.atGrams(grams: Double): PhotoFood {
    if (servingGrams <= 0.0 || grams <= 0.0) return this
    val k = grams / servingGrams
    return copy(
        servingGrams = grams, kcal = kcal * k, protein = protein * k, carbs = carbs * k, fat = fat * k,
        fiber = fiber * k, sugar = sugar * k, sodiumMg = sodiumMg * k, sat = sat * k, mono = mono * k, poly = poly * k,
    )
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
    )
}
