package com.lifetracker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Vitamins, minerals and anything else you want to count (database version 34).
 *
 * The list of nutrients is yours: the app starts you with the usual vitamins and minerals, and you can hide any of them,
 * change a name, unit or daily target, delete one you added, and add your own (vitamin B9 is there from the start, but a
 * "Omega 3" or "Creatine" works the same way).
 *
 * How much of a nutrient a food holds is kept per 100 g. A meal you log keeps its own copy of those numbers in
 * [MealEntity.extra], so changing a food or deleting a nutrient later never rewrites what you ate.
 */

object NutrientGroup {
    const val VITAMIN = "vitamin"
    const val MINERAL = "mineral"
    const val OTHER = "other"

    fun label(group: String): String = when (group) {
        VITAMIN -> "Vitamins"
        MINERAL -> "Minerals"
        else -> "Mine"
    }
}

/** One thing to count. [target] is the amount per day you aim for, in [unit], or 0 for none. */
@Entity(tableName = "nutrients")
data class NutrientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val group: String,
    val target: Double,
    /** Hidden nutrients are not shown or counted on the Food screen, but keep their values. */
    val hidden: Boolean,
    /** One of the starter nutrients (cannot be deleted, only hidden). */
    val builtIn: Boolean,
    val sortOrder: Int,
)

/** What 100 g of one food holds of one nutrient, typed by you. It wins over the number that came with the app. [foodRef] is a [FoodRef]. */
@Entity(tableName = "food_nutrients", primaryKeys = ["foodRef", "nutrientId"])
data class FoodNutrientEntity(
    val foodRef: String,
    val nutrientId: String,
    val amount100: Double,
)

/** The nutrients the app starts with, and the daily amounts printed on food labels (FDA Daily Values for adults). */
object DefaultNutrients {
    private fun v(id: String, name: String, unit: String, target: Double, order: Int) =
        NutrientEntity(id, name, unit, NutrientGroup.VITAMIN, target, hidden = false, builtIn = true, sortOrder = order)

    private fun m(id: String, name: String, unit: String, target: Double, order: Int) =
        NutrientEntity(id, name, unit, NutrientGroup.MINERAL, target, hidden = false, builtIn = true, sortOrder = order)

    val list: List<NutrientEntity> = listOf(
        v("vit-a", "Vitamin A", "µg", 900.0, 0),
        v("vit-c", "Vitamin C", "mg", 90.0, 1),
        v("vit-d", "Vitamin D", "µg", 20.0, 2),
        v("vit-e", "Vitamin E", "mg", 15.0, 3),
        v("vit-k", "Vitamin K", "µg", 120.0, 4),
        v("vit-b1", "Vitamin B1 (thiamin)", "mg", 1.2, 5),
        v("vit-b2", "Vitamin B2 (riboflavin)", "mg", 1.3, 6),
        v("vit-b3", "Vitamin B3 (niacin)", "mg", 16.0, 7),
        v("vit-b5", "Vitamin B5 (pantothenic acid)", "mg", 5.0, 8),
        v("vit-b6", "Vitamin B6", "mg", 1.7, 9),
        v("vit-b7", "Vitamin B7 (biotin)", "µg", 30.0, 10),
        v("vit-b9", "Vitamin B9 (folate)", "µg", 400.0, 11),
        v("vit-b12", "Vitamin B12", "µg", 2.4, 12),
        v("choline", "Choline", "mg", 550.0, 13),
        m("calcium", "Calcium", "mg", 1300.0, 14),
        m("iron", "Iron", "mg", 18.0, 15),
        m("magnesium", "Magnesium", "mg", 420.0, 16),
        m("phosphorus", "Phosphorus", "mg", 1250.0, 17),
        m("potassium", "Potassium", "mg", 4700.0, 18),
        m("zinc", "Zinc", "mg", 11.0, 19),
        m("copper", "Copper", "mg", 0.9, 20),
        m("manganese", "Manganese", "mg", 2.3, 21),
        m("selenium", "Selenium", "µg", 55.0, 22),
        m("iodine", "Iodine", "µg", 150.0, 23),
    )

    /** Fills the table with the starters. A row that is already there (one you changed) is left alone. */
    fun seed(db: SupportSQLiteDatabase) {
        for (n in list) {
            db.execSQL(
                "INSERT OR IGNORE INTO `nutrients` (`id`, `name`, `unit`, `group`, `target`, `hidden`, `builtIn`, `sortOrder`) " +
                    "VALUES (?, ?, ?, ?, ?, 0, 1, ?)",
                arrayOf<Any?>(n.id, n.name, n.unit, n.group, n.target, n.sortOrder),
            )
        }
    }
}

/** The text kept in [MealEntity.extra]: "vit-c=12.5;iron=0.4", the amount of each nutrient in 100 g. */
object NutrientValues {
    fun encode(values: Map<String, Double>): String =
        values.entries.filter { it.value != 0.0 && it.key.isNotEmpty() }.sortedBy { it.key }
            .joinToString(";") { it.key + "=" + trim(it.value) }

    fun decode(text: String): Map<String, Double> {
        if (text.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, Double>()
        for (part in text.split(';')) {
            val i = part.indexOf('=')
            if (i <= 0) continue
            val value = part.substring(i + 1).toDoubleOrNull() ?: continue
            out[part.substring(0, i)] = value
        }
        return out
    }

    private fun trim(v: Double): String = if (v % 1.0 == 0.0 && v < 1e9) v.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')

    /** A new id for a nutrient you add, safe to keep in [encode]'s text. */
    fun newId(): String = "n-" + java.util.UUID.randomUUID().toString().take(8)
}

/** Nutrient arithmetic, free of Android so it can be tested. */
object NutrientMath {
    /** What the meal gave you: the amounts per 100 g it remembers, scaled to the grams eaten. */
    fun forMeal(meal: MealEntity): Map<String, Double> {
        val per100 = NutrientValues.decode(meal.extra)
        if (per100.isEmpty()) return emptyMap()
        return per100.mapValues { it.value * meal.grams / 100 }
    }

    fun dayTotals(meals: List<MealEntity>): Map<String, Double> {
        val total = LinkedHashMap<String, Double>()
        for (meal in meals) for ((id, amount) in forMeal(meal)) total[id] = (total[id] ?: 0.0) + amount
        return total
    }

    /** How much of the daily [target] [amount] is, as a fraction (0.5 is half), or null when there is no target. */
    fun fraction(amount: Double, target: Double): Double? = if (target > 0) amount / target else null

    /** The numbers that came with the app, with the ones you typed on top. */
    fun merge(base: Map<String, Double>, yours: List<FoodNutrientEntity>): Map<String, Double> {
        if (yours.isEmpty()) return base
        val out = LinkedHashMap(base)
        for (n in yours) if (n.amount100 > 0) out[n.nutrientId] = n.amount100 else out.remove(n.nutrientId)
        return out
    }

    /** True when a food has no number for any of the nutrients that are shown, so the day total is not complete. */
    fun isMissing(meal: MealEntity): Boolean = meal.extra.isBlank()

    /** Hidden nutrients and nutrients with no number are not shown. */
    fun shown(list: List<NutrientEntity>, totals: Map<String, Double>): List<NutrientEntity> =
        list.filter { !it.hidden && (totals[it.id] ?: 0.0) > 0.0 }
}
