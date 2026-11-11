package com.lifetracker.app.data

import java.text.Normalizer
import java.util.Locale

/** Finds a meal you logged before that is the same dish as a new photo reading, so its numbers can be reused. */
object RepeatMatch {
    private val FILLER = setOf("with", "and", "the", "of", "in", "plain", "fresh", "homemade", "home", "style", "some", "little")

    /** The words of a meal name that tell dishes apart: lower case, no accents, no counts or fillers, plurals trimmed ("3 Rotis" is "roti"). */
    fun words(name: String): Set<String> =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .split(Regex("[^a-z]+"))
            .filter { it.length > 1 && it !in FILLER }
            .map { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
            .toSet()

    /** How much two names share, from 0 (nothing) to 1 (the same words). */
    fun similarity(a: String, b: String): Double {
        val x = words(a)
        val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

    /**
     * The meal in [past] that is most like [name], at least [minSimilarity] alike; the most recent one wins a tie.
     * Meals with no calories (a drink of water, an unfinished entry) are never offered.
     */
    fun find(name: String, past: List<MealEntity>, minSimilarity: Double = 0.67): MealEntity? =
        past.filter { it.kcal100 > 0 }
            .map { it to similarity(name, it.name) }
            .filter { it.second >= minSimilarity }
            .maxWithOrNull(compareBy<Pair<MealEntity, Double>>({ it.second }, { it.first.date }, { it.first.minuteOfDay }))
            ?.first
}
