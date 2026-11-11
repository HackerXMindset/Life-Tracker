package com.lifetracker.app.data

/** Turns "200 g rice, 150 g chicken" into ingredients: the ones with an amount are looked up in your foods first, the rest go to the AI. */
object IngredientText {
    /** One ingredient as written. [grams] is null when no weight or volume was given ("2 roti"). */
    class Part(val name: String, val grams: Double?, val original: String)

    private const val UNITS = "kg|kgs|g|gm|gms|gram|grams|ml|l|litre|litres|liter|liters"
    private const val NUMBER = "\\d+(?:[.,]\\d+)?"
    private val amountFirst = Regex("^($NUMBER)\\s*($UNITS)\\b\\.?\\s*(?:of\\s+)?(.+)$", RegexOption.IGNORE_CASE)
    private val amountLast = Regex("^(.+?)\\s*[:\\-]?\\s*($NUMBER)\\s*($UNITS)$", RegexOption.IGNORE_CASE)
    private val separators = Regex("\\s*(?:(?<!\\d),|,(?!\\d)|;|\\n|\\+|\\band\\b|\\bplus\\b)\\s*", RegexOption.IGNORE_CASE)

    private fun toGrams(amount: String, unit: String): Double? {
        val n = amount.replace(',', '.').toDoubleOrNull() ?: return null
        val u = unit.lowercase()
        val g = if (u.startsWith("k") || u == "l" || u.startsWith("lit")) n * 1000 else n
        return g.takeIf { it > 0 }
    }

    fun parse(text: String): List<Part> = text.split(separators).map { it.trim().trim('.') }.filter { it.isNotEmpty() }.map { piece ->
        amountFirst.matchEntire(piece)?.let { m ->
            toGrams(m.groupValues[1], m.groupValues[2])?.let { return@map Part(m.groupValues[3].trim(), it, piece) }
        }
        amountLast.matchEntire(piece)?.let { m ->
            toGrams(m.groupValues[2], m.groupValues[3])?.let { return@map Part(m.groupValues[1].trim(), it, piece) }
        }
        Part(piece, null, piece)
    }

    /** Whether the library food called [foodName] is what was asked for with [asked]: the same name, or it starts with it. */
    fun accepts(foodName: String, asked: String): Boolean {
        val food = FoodLibrary.fold(foodName).trim()
        val want = FoodLibrary.fold(asked).trim()
        return want.isNotEmpty() && (food == want || food.startsWith("$want ") || food.startsWith("$want,"))
    }

    /** The AI's answer as ingredients: its items with their grams, or the whole answer as one ingredient when it gave no items. */
    fun itemsFrom(food: PhotoFood): List<SavedMealItem> {
        fun item(name: String, grams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double): SavedMealItem? {
            if (grams <= 0) return null
            val k = 100.0 / grams
            return SavedMealItem("", name, grams, kcal * k, protein * k, carbs * k, fat * k)
        }
        val fromItems = food.items.mapNotNull { item(it.name, it.grams, it.kcal, it.protein, it.carbs, it.fat) }
        if (fromItems.isNotEmpty()) return fromItems
        return listOfNotNull(item(food.name, food.servingGrams, food.kcal, food.protein, food.carbs, food.fat))
    }
}
