package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * One box of the new-food form, and one number the photo reader is asked for.
 * [id] is one of the fixed ids in [FoodFields.FIXED] or the id of a nutrient (a vitamin, a mineral or one of your own).
 * [aiKey] is the name used in the photo reader's JSON answer; for a nutrient it is the id inside the "nutrients" object.
 */
data class FieldDef(
    val id: String,
    val label: String,
    val short: String,
    val unit: String,
    val fixed: Boolean,
    val aiKey: String,
    /** "basic" for the fixed boxes, otherwise the nutrient's [NutrientGroup]. */
    val group: String,
) {
    /** The text above the box: "Calories (kcal)", "Protein g", "Vitamin B9 (folate) µg". */
    val caption: String get() = if (unit == "kcal") "$short ($unit)" else "$short $unit"
}

/**
 * Which boxes the new-food form shows, in order. The photo reader, the label reader and the widget are asked for exactly these
 * numbers and no others. Calories always stay. A box that is taken out is only hidden: foods that already hold a number for it keep it.
 */
object FoodFields {
    const val FILE = "food_fields"
    const val KCAL = "kcal"
    const val BASIC = "basic"

    val FIXED: List<FieldDef> = listOf(
        FieldDef(KCAL, "Calories", "Calories", "kcal", true, "calories", BASIC),
        FieldDef("protein", "Protein", "Protein", "g", true, "protein", BASIC),
        FieldDef("carbs", "Carbs", "Carbs", "g", true, "carbs", BASIC),
        FieldDef("fat", "Fat", "Fat", "g", true, "fat", BASIC),
        FieldDef("fiber", "Fibre", "Fibre", "g", true, "fiber", BASIC),
        FieldDef("sugar", "Sugar", "Sugar", "g", true, "sugar", BASIC),
        FieldDef("sodium", "Sodium", "Sodium", "mg", true, "sodium", BASIC),
        FieldDef("sat", "Saturated fat", "Saturated", "g", true, "saturated_fat", BASIC),
        FieldDef("mono", "Monounsaturated fat", "Mono", "g", true, "monounsaturated_fat", BASIC),
        FieldDef("poly", "Polyunsaturated fat", "Poly", "g", true, "polyunsaturated_fat", BASIC),
    )

    val DEFAULT_IDS: List<String> = FIXED.map { it.id }

    fun nutrientField(n: NutrientEntity) = FieldDef(n.id, n.name, n.name, n.unit, false, n.id, n.group)

    /** The saved text ("kcal,protein,vit-b9") as ids. Nothing saved means the usual boxes. */
    fun decode(raw: String?): List<String> {
        if (raw == null) return DEFAULT_IDS
        return sanitize(raw.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun encode(ids: List<String>): String = sanitize(ids).joinToString(",")

    /** Calories first and only once, no repeats. */
    fun sanitize(ids: List<String>): List<String> = (listOf(KCAL) + ids.filter { it != KCAL && it.isNotBlank() }).distinct()

    /** The boxes for [ids]: a nutrient that was deleted or is hidden right now is left out (its id stays saved, so it comes back when it is shown again). */
    fun defsFor(ids: List<String>, nutrients: List<NutrientEntity>): List<FieldDef> =
        sanitize(ids).mapNotNull { id ->
            FIXED.firstOrNull { it.id == id }
                ?: nutrients.firstOrNull { it.id == id && !it.hidden }?.let { nutrientField(it) }
        }

    /** Everything that could still be added: the fixed boxes that are out, then your nutrients that are showing and not yet in. */
    fun addable(ids: List<String>, nutrients: List<NutrientEntity>): List<FieldDef> {
        val have = sanitize(ids).toSet()
        val fixedIds = FIXED.map { it.id }.toSet()
        val more = nutrients.filter { !it.hidden && it.id !in have && it.id !in fixedIds }.sortedBy { it.sortOrder }.map { nutrientField(it) }
        return FIXED.filter { it.id !in have } + more
    }

    fun load(context: Context): List<String> =
        decode(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("ids", null))

    fun save(context: Context, ids: List<String>) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString("ids", encode(ids)) }
    }

    /** The boxes as they are now, for the form and for the photo reader. */
    suspend fun defs(context: Context): List<FieldDef> =
        defsFor(load(context), AppDatabase.get(context).foodDao().allNutrients())
}
