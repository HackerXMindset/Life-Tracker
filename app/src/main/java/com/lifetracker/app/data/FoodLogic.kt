package com.lifetracker.app.data

import java.util.UUID

/** One food, built in or yours, ready to be logged: its numbers per 100 g (or ml) and how a portion of it is counted. */
data class Loggable(
    /** [FoodRef] text ("a:banana" or "m:...") or "" for a food that was only typed in once. */
    val ref: String,
    val name: String,
    val per100: Per100,
    /** Vitamins, minerals and your own nutrients per 100 g, by nutrient id. */
    val micro: Map<String, Double>,
    val liquid: Boolean,
    val portion: PortionKind?,
    val portionGrams: Double,
    val cat: FoodCat?,
    val cookedYield: Double,
    /** Path of the photo inside the app's assets, or null. */
    val photoAsset: String?,
) {
    val unit: String get() = if (liquid) "ml" else "g"
    val canCook: Boolean get() = cookedYield > 0
    val hasPortion: Boolean get() = portion != null && portionGrams > 0

    /** The amount to start with: one portion, or 100. */
    val startAmount: Double get() = if (hasPortion) portionGrams else 100.0

    /** How many portions [amount] is, or null when the food has no portion size. */
    fun portionsOf(amount: Double): Double? = if (hasPortion) amount / portionGrams else null

    /** The same food as it is once cooked: the numbers are spread over the heavier cooked weight. */
    fun cooked(): Loggable = if (!canCook) this else copy(
        name = "$name (cooked)",
        per100 = per100.dividedBy(cookedYield),
        micro = micro.mapValues { it.value / cookedYield },
        portionGrams = if (portionGrams > 0) portionGrams * cookedYield else 0.0,
        cookedYield = 0.0,
    )
}

/** [Per100] with every number divided by [by] (cooking adds water weight, so 100 g of cooked rice holds less than 100 g of raw). */
fun Per100.dividedBy(by: Double): Per100 = if (by <= 0.0 || by == 1.0) this else Per100(
    kcal / by, protein / by, carbs / by, fat / by, fiber / by, sugar / by, sodiumMg / by, animalProtein / by,
    sat / by, mono / by, poly / by, amino.map { it / by },
)

/** Quick amounts, search, and the step from a food to a meal: logic with no Android in it so it can be tested. */
object FoodLogic {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val DINNER = "dinner"
    const val SNACK = "snack"
    val MEAL_TYPES = listOf(BREAKFAST, LUNCH, DINNER, SNACK)

    fun newMealId(): String = UUID.randomUUID().toString()
    fun newFoodId(): String = FoodRef.MINE + UUID.randomUUID().toString().take(12)
    fun newSavedMealId(): String = FoodRef.SAVED + UUID.randomUUID().toString().take(12)

    /** The meal that fits a time of day, as the app has always guessed it. */
    fun mealTypeFor(minuteOfDay: Int): String = when (minuteOfDay / 60) {
        in 0..10 -> BREAKFAST
        in 11..15 -> LUNCH
        in 16..18 -> SNACK
        else -> DINNER
    }

    fun of(f: LibraryFood, micro: Map<String, Double>) = Loggable(
        ref = f.ref, name = f.name, per100 = f.per100, micro = micro, liquid = f.liquid, portion = f.portion,
        portionGrams = f.portionGrams, cat = f.cat, cookedYield = f.cookedYield, photoAsset = f.photoAsset,
    )

    fun of(f: FoodEntity, micro: Map<String, Double>) = Loggable(
        ref = f.id, name = f.name,
        per100 = Per100(
            kcal = f.kcal100, protein = f.protein100, carbs = f.carbs100, fat = f.fat100, fiber = f.fiber100, sugar = f.sugar100,
            sodiumMg = f.sodium100, animalProtein = f.animal100, sat = f.sat100, mono = f.mono100, poly = f.poly100,
            amino = f.amino.split(',').mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 9 } ?: emptyList(),
        ),
        micro = micro, liquid = f.liquid, portion = PortionKind.of(f.portionKind), portionGrams = f.portionGrams,
        cat = FoodCat.of(f.category), cookedYield = f.cookedYield, photoAsset = null,
    )

    /** A meal you ate before, to eat again: its own numbers, and the photo of the food it came from when that is built in. */
    fun ofMeal(m: MealEntity, library: FoodLibraryData?): Loggable {
        val fromLibrary = library?.byRef(m.foodId)
        return Loggable(
            ref = m.foodId, name = m.name,
            per100 = Per100(
                kcal = m.kcal100, protein = m.protein100, carbs = m.carbs100, fat = m.fat100, fiber = m.fiber100, sugar = m.sugar100,
                sodiumMg = m.sodium100, animalProtein = m.animal100, sat = m.sat100, mono = m.mono100, poly = m.poly100,
                amino = m.amino.split(',').mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 9 } ?: emptyList(),
            ),
            micro = NutrientValues.decode(m.extra), liquid = fromLibrary?.liquid ?: false, portion = fromLibrary?.portion,
            portionGrams = fromLibrary?.portionGrams ?: 0.0, cat = fromLibrary?.cat, cookedYield = 0.0,
            photoAsset = fromLibrary?.photoAsset,
        ).let { if (it.portionGrams <= 0.0) it.copy(portionGrams = m.grams, portion = it.portion ?: PortionKind.Serving) else it }
    }

    /** The meal row for [grams] of [food]. */
    fun toMeal(food: Loggable, grams: Double, date: String, minuteOfDay: Int, mealType: String, id: String = newMealId()) = MealEntity(
        id = id, date = date, minuteOfDay = minuteOfDay, mealType = mealType, name = food.name, grams = grams,
        kcal100 = food.per100.kcal, carbs100 = food.per100.carbs, fat100 = food.per100.fat, protein100 = food.per100.protein,
        source = "app", raw = "", foodId = food.ref, fiber100 = food.per100.fiber, sugar100 = food.per100.sugar,
        sodium100 = food.per100.sodiumMg, sat100 = food.per100.sat, mono100 = food.per100.mono, poly100 = food.per100.poly,
        animal100 = food.per100.animalProtein, amino = food.per100.amino.joinToString(",") { it.toString() },
        extra = NutrientValues.encode(food.micro),
    )

    /**
     * The food a Quick add makes when you also save it: what you typed is for the whole [grams] (100 when you left it out),
     * so the numbers per 100 g are scaled back, and one portion is the amount you typed.
     */
    fun foodFromQuickAdd(
        name: String, grams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double,
        fiber: Double = 0.0, sugar: Double = 0.0, sodiumMg: Double = 0.0, liquid: Boolean = false, nowMs: Long = 0,
    ): FoodEntity {
        val g = if (grams > 0) grams else 100.0
        val k = 100.0 / g
        return FoodEntity(
            id = newFoodId(), name = name.trim(), kcal100 = kcal * k, protein100 = protein * k, carbs100 = carbs * k, fat100 = fat * k,
            fiber100 = fiber * k, sugar100 = sugar * k, sodium100 = sodiumMg * k, liquid = liquid,
            portionKind = PortionKind.Serving.key, portionGrams = g, createdMs = nowMs,
        )
    }

    /** Your own foods whose name has every word of [query], best match first (names that start with it first). */
    fun searchOwn(foods: List<FoodEntity>, query: String, limit: Int = 40): List<FoodEntity> {
        val words = FoodLibrary.fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return foods.take(limit)
        return foods.mapNotNull { f ->
            val n = FoodLibrary.fold(f.name)
            if (!words.all { n.contains(it) }) null else (if (n.startsWith(words.first())) 0 else 1) to f
        }.sortedWith(compareBy({ it.first }, { it.second.name.length })).take(limit).map { it.second }
    }

    /** The time a new meal gets: right now for today, otherwise the usual time of that meal. */
    fun minuteFor(mealType: String, today: Boolean, nowMinute: Int): Int =
        if (today) nowMinute else when (mealType) {
            BREAKFAST -> 8 * 60
            LUNCH -> 13 * 60
            DINNER -> 20 * 60
            else -> 17 * 60
        }

    /** The foods of a ready dish with the grams of each (cooked ones already turned into their cooked numbers). */
    fun dishParts(dish: ReadyDish, library: FoodLibraryData): List<Pair<Loggable, Double>> = dish.items.mapNotNull { item ->
        val f = library.food(item.foodId) ?: return@mapNotNull null
        val base = of(f, library.microOf(f.id))
        (if (item.cooked) base.cooked() else base) to item.grams
    }

    /** A food of your own made from a meal you logged: its numbers per 100 g, and one serving is the amount you ate. */
    fun foodFromMeal(m: MealEntity, nowMs: Long = 0): FoodEntity = FoodEntity(
        id = newFoodId(), name = m.name, kcal100 = m.kcal100, protein100 = m.protein100, carbs100 = m.carbs100, fat100 = m.fat100,
        fiber100 = m.fiber100, sugar100 = m.sugar100, sodium100 = m.sodium100, sat100 = m.sat100, mono100 = m.mono100,
        poly100 = m.poly100, animal100 = m.animal100, amino = m.amino, portionKind = PortionKind.Serving.key,
        portionGrams = m.grams, createdMs = nowMs,
    )

    /** "1.5 pieces", "2 tbsp", "1 serving"; null when the food has no portion size. */
    fun portionText(food: Loggable, amount: Double): String? {
        val kind = food.portion ?: return null
        val count = food.portionsOf(amount) ?: return null
        val rounded = Math.round(count * 10) / 10.0
        val shown = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
        return shown + " " + if (rounded == 1.0) kind.one else kind.many
    }

    /** Fibre, sugar and sodium eaten in a day. */
    data class Extras(val fiber: Double, val sugar: Double, val sodiumMg: Double, val animalProtein: Double)

    fun extras(meals: List<MealEntity>) = Extras(
        fiber = meals.sumOf { it.fiber }, sugar = meals.sumOf { it.sugar }, sodiumMg = meals.sumOf { it.sodiumMg },
        animalProtein = meals.sumOf { it.animal100 * it.grams / 100 },
    )

    /** How much of the day's calories came from each of protein, carbs and fat (4, 4 and 9 kcal per gram), as shares that add to 1. */
    fun macroShares(m: Macros): Triple<Double, Double, Double> {
        val p = m.protein * 4
        val c = m.carbs * 4
        val f = m.fat * 9
        val all = p + c + f
        return if (all <= 0.0) Triple(0.0, 0.0, 0.0) else Triple(p / all, c / all, f / all)
    }

    /** The seven days of the week (Monday first) that contain [day]. */
    fun weekOf(day: java.time.LocalDate): List<java.time.LocalDate> {
        val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }
}
