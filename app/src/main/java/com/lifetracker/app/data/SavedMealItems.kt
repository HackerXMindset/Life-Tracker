package com.lifetracker.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One food inside a saved meal: how much of it, with its own copy of the numbers so the meal never changes by itself. */
data class SavedMealItem(
    val foodId: String,
    val name: String,
    val grams: Double,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val fiber100: Double = 0.0,
    val sugar100: Double = 0.0,
    val sodium100: Double = 0.0,
    val sat100: Double = 0.0,
    val mono100: Double = 0.0,
    val poly100: Double = 0.0,
    val animal100: Double = 0.0,
    val amino: String = "",
    /** Vitamins and minerals per 100 g, see [NutrientValues]. */
    val extra: String = "",
) {
    val kcal: Double get() = kcal100 * grams / 100
    val protein: Double get() = protein100 * grams / 100
    val carbs: Double get() = carbs100 * grams / 100
    val fat: Double get() = fat100 * grams / 100

    /** This item as a meal on [date] at [minuteOfDay] under [mealType]. */
    fun asMeal(id: String, date: String, minuteOfDay: Int, mealType: String) = MealEntity(
        id = id, date = date, minuteOfDay = minuteOfDay, mealType = mealType, name = name, grams = grams,
        kcal100 = kcal100, carbs100 = carbs100, fat100 = fat100, protein100 = protein100, source = "app", raw = "",
        foodId = foodId, fiber100 = fiber100, sugar100 = sugar100, sodium100 = sodium100, sat100 = sat100,
        mono100 = mono100, poly100 = poly100, animal100 = animal100, amino = amino, extra = extra,
    )

    companion object {
        fun of(meal: MealEntity) = SavedMealItem(
            foodId = meal.foodId, name = meal.name, grams = meal.grams, kcal100 = meal.kcal100, protein100 = meal.protein100,
            carbs100 = meal.carbs100, fat100 = meal.fat100, fiber100 = meal.fiber100, sugar100 = meal.sugar100,
            sodium100 = meal.sodium100, sat100 = meal.sat100, mono100 = meal.mono100, poly100 = meal.poly100,
            animal100 = meal.animal100, amino = meal.amino, extra = meal.extra,
        )
    }
}

/** The text stored in [SavedMealEntity.items]: a JSON list of [SavedMealItem]. Zero values are left out. */
object SavedMealItems {
    fun encode(items: List<SavedMealItem>): String {
        val out = JSONArray()
        for (i in items) {
            val o = JSONObject().put("f", i.foodId).put("n", i.name).put("g", i.grams)
                .put("k", i.kcal100).put("p", i.protein100).put("c", i.carbs100).put("a", i.fat100)
            fun opt(key: String, v: Double) { if (v != 0.0) o.put(key, v) }
            opt("fi", i.fiber100); opt("su", i.sugar100); opt("na", i.sodium100); opt("sa", i.sat100)
            opt("mu", i.mono100); opt("po", i.poly100); opt("an", i.animal100)
            if (i.amino.isNotEmpty()) o.put("aa", i.amino)
            if (i.extra.isNotEmpty()) o.put("x", i.extra)
            out.put(o)
        }
        return out.toString()
    }

    /** Reads the list back. A text that is damaged gives an empty list. */
    fun decode(text: String): List<SavedMealItem> = try {
        val arr = JSONArray(text)
        (0 until arr.length()).map { n ->
            val o = arr.getJSONObject(n)
            SavedMealItem(
                foodId = o.optString("f"), name = o.getString("n"), grams = o.getDouble("g"),
                kcal100 = o.optDouble("k", 0.0), protein100 = o.optDouble("p", 0.0), carbs100 = o.optDouble("c", 0.0),
                fat100 = o.optDouble("a", 0.0), fiber100 = o.optDouble("fi", 0.0), sugar100 = o.optDouble("su", 0.0),
                sodium100 = o.optDouble("na", 0.0), sat100 = o.optDouble("sa", 0.0), mono100 = o.optDouble("mu", 0.0),
                poly100 = o.optDouble("po", 0.0), animal100 = o.optDouble("an", 0.0), amino = o.optString("aa"), extra = o.optString("x"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
