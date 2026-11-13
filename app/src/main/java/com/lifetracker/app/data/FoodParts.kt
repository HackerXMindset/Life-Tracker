package com.lifetracker.app.data

import java.util.Locale

/**
 * A meal is often logged as one line that holds several foods: "chicken curry and roti", "2 rotis, 1 katori dal". The stats and the
 * page of a food look at each food in it on its own, so the roti is counted whenever there was a roti, however it was written.
 * Nothing you logged is changed; this only reads the name. Free of the screen so it can be tested.
 */
object FoodParts {
    private val SEPARATOR = Regex("\\s*(?:,|;|\\+|&|/|\\band\\b|\\bwith\\b|\\bplus\\b)\\s*", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("\\([^)]*\\)|\\[[^]]*]")
    private val AMOUNT = Regex("^\\d+(?:[.,]\\d+)?(?:x|g|gm|gms|kg|ml|l|oz|pc|pcs)?$", RegexOption.IGNORE_CASE)
    private val WORDS = setOf(
        "a", "an", "one", "two", "three", "four", "five", "six", "half", "some", "x", "of",
        "g", "gm", "gms", "gram", "grams", "kg", "ml", "oz", "cup", "cups", "katori", "katoris", "bowl", "bowls", "plate", "plates",
        "piece", "pieces", "pcs", "slice", "slices", "tbsp", "tsp", "spoon", "spoons", "glass", "glasses", "serving", "servings",
    )

    /** The foods in [name], each without its amount ("2 rotis" is "rotis"), in the order written. Never empty for a non-blank name. */
    fun split(name: String): List<String> {
        val cleaned = name.replace(BRACKETS, " ")
        val parts = cleaned.split(SEPARATOR).map { strip(it) }.filter { it.isNotEmpty() }
        return parts.ifEmpty { listOf(name.trim()).filter { it.isNotEmpty() } }
    }

    private fun strip(part: String): String {
        val words = part.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var i = 0
        while (i < words.size - 1 && (AMOUNT.matches(words[i]) || words[i].lowercase() in WORDS)) i++
        return words.drop(i).joinToString(" ")
    }

    /** What two spellings of the same food share: lower case, letters only, the last word in the singular. */
    fun key(part: String): String {
        val words = part.lowercase(Locale.ROOT).replace(Regex("[^\\p{L} ]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val last = words.last()
        val singular = when {
            last.length > 4 && last.endsWith("ies") -> last.dropLast(3) + "y"
            last.length > 3 && last.endsWith("s") && !last.endsWith("ss") && !last.endsWith("us") -> last.dropLast(1)
            else -> last
        }
        return (words.dropLast(1) + singular).joinToString(" ")
    }

    /** The keys of every food in [name]. */
    fun keys(name: String): List<String> = split(name).map { key(it) }.filter { it.isNotEmpty() }.distinct()

    /** A part written for display: the first letter in capitals. */
    fun display(part: String): String = part.trim().replaceFirstChar { it.uppercase() }
}
