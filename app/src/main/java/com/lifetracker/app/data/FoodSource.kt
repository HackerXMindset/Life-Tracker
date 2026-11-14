package com.lifetracker.app.data

/**
 * Where the numbers of a logged meal came from, kept in [MealEntity.source]. A photo or a few words is a guess by the AI, a label
 * or a barcode is read off a pack, and what you typed is yours. Meals logged before this was kept say "app" and are shown as typed.
 */
object FoodSource {
    const val APP = "app"
    const val PHOTO = "photo"
    const val LABEL = "label"
    const val TEXT = "text"
    const val BARCODE = "barcode"
    private const val EDITED = ":edited"

    /**
     * The tag to keep: [kind], the model that gave the numbers when you chose one from a comparison ("photo@google/gemini-2.5-flash"),
     * and a mark when you changed the numbers after they were read.
     */
    fun tag(kind: String, edited: Boolean, model: String? = null): String =
        if (kind == APP) kind else kind + (model?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" } ?: "") + (if (edited) EDITED else "")

    fun kind(source: String): String = source.substringBefore('@').substringBefore(':')

    /** The model chosen in a comparison, or null. */
    fun model(source: String): String? = if ('@' in source) source.substringAfter('@').removeSuffix(EDITED).ifEmpty { null } else null

    fun edited(source: String): Boolean = source.endsWith(EDITED)

    /** True for the numbers that are the AI's estimate and that you have not touched. */
    fun isGuess(source: String): Boolean = !edited(source) && (kind(source) == PHOTO || kind(source) == TEXT)

    /** A short line to show beside a meal. [foodId] tells a built-in food from your own when the meal was picked from the list. */
    fun label(source: String, foodId: String = ""): String {
        val changed = edited(source)
        val by = model(source)?.let { " ($it)" }.orEmpty()
        return when (kind(source)) {
            PHOTO -> (if (changed) "Photo guess, numbers changed by you" else "Photo guess by the AI") + by
            TEXT -> (if (changed) "AI guess from your words, changed by you" else "AI guess from your words") + by
            LABEL -> (if (changed) "Read from the pack label, changed by you" else "Read from the pack label") + by
            BARCODE -> if (changed) "Open Food Facts, changed by you" else "Open Food Facts"
            "gymmane" -> "Imported from GymMane"
            else -> when {
                foodId.startsWith("a:") -> "Built-in food table"
                foodId.startsWith("m:") -> "One of your own foods"
                else -> "Typed by you"
            }
        }
    }

    /** A copy of a meal is a new entry, so it counts as logged here and not as imported; a photo or label stays what it was. */
    fun forCopy(source: String): String = if (kind(source) in setOf(PHOTO, LABEL, TEXT, BARCODE)) source else APP

    /** How many of [meals] came from each kind of source, most common first. */
    fun counts(meals: List<MealEntity>): List<Pair<String, Int>> =
        meals.groupingBy { label(it.source, it.foodId) }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }
}
