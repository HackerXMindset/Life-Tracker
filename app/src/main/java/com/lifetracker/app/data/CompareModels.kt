package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** One model on the compare list, with the provider that runs it. */
data class CompareModel(val provider: AiProvider, val model: String)

/** What one model answered for a photo or some words: [food] when it worked, otherwise [error] says why not. [millis] is how long it took. */
data class CompareResult(val entry: CompareModel, val food: PhotoFood?, val error: String?, val millis: Long, val offline: Boolean = false)

/** The rules of the compare list, kept apart from the phone so they can be tested. */
object CompareModels {
    const val MIN = 2
    const val MAX = 4

    /** A model name without line breaks or stray spaces, or null when blank. */
    fun clean(provider: AiProvider, model: String): CompareModel? {
        val m = model.replace(Regex("""[\t\r\n|]"""), " ").trim()
        return if (m.isEmpty()) null else CompareModel(provider, m)
    }

    /** No blanks, no repeats of the same provider and model, at most [MAX], in the order chosen. */
    fun normalize(list: List<CompareModel>): List<CompareModel> =
        list.mapNotNull { clean(it.provider, it.model) }.distinctBy { it.provider to it.model.lowercase() }.take(MAX)

    fun encode(list: List<CompareModel>): String = normalize(list).joinToString("\n") { it.provider.key + "\t" + it.model }

    fun decode(text: String): List<CompareModel> = normalize(
        text.lines().mapNotNull { line ->
            val at = line.indexOf('\t')
            if (at <= 0) null else clean(AiProvider.of(line.substring(0, at)), line.substring(at + 1))
        },
    )

    /** Comparing needs the switch on and at least [MIN] models; with fewer, reading works as it always did. */
    fun active(on: Boolean, list: List<CompareModel>): Boolean = on && normalize(list).size >= MIN

    /** The lowest and highest calories among the models that answered, or null when fewer than two did. */
    fun kcalSpread(results: List<CompareResult>): Pair<Double, Double>? {
        val kcal = results.mapNotNull { it.food?.kcal }
        return if (kcal.size < 2) null else kcal.min() to kcal.max()
    }

    /** One line about how far apart the answers are, or "" when fewer than two models answered. */
    fun spreadText(results: List<CompareResult>): String {
        val (low, high) = kcalSpread(results) ?: return ""
        val apart = if (low > 0) Math.round((high - low) / low * 100) else 0L
        return "Calories run from ${Math.round(low)} to ${Math.round(high)} across the models" + if (apart >= 1) " ($apart% apart)." else "."
    }

    /** The calls one reading makes. */
    fun callsText(count: Int): String = if (count == 1) "1 call" else "$count calls"
}

/** The compare switch and list, kept with the AI keys on this phone (and out of backups). */
object CompareSettings {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FoodAiSettings.FILE, Context.MODE_PRIVATE)

    fun on(context: Context): Boolean = prefs(context).getBoolean("compare_on", false)

    fun models(context: Context): List<CompareModel> = CompareModels.decode(prefs(context).getString("compare_models", "").orEmpty())

    fun save(context: Context, on: Boolean, list: List<CompareModel>) {
        prefs(context).edit {
            putBoolean("compare_on", on)
            putString("compare_models", CompareModels.encode(list))
        }
    }

    fun active(context: Context): Boolean = CompareModels.active(on(context), models(context))

    /** Who will be sent the photo or words: the providers of the compare list, or the one provider in use. */
    fun providersUsed(context: Context): Set<AiProvider> =
        if (active(context)) models(context).map { it.provider }.toSet() else setOf(FoodAiSettings.provider(context))

    /** Whether there is a key to read with: any provider on the compare list, or the one in use. */
    fun keyReady(context: Context): Boolean =
        if (active(context)) models(context).any { FoodAiSettings.key(context, it.provider).isNotEmpty() }
        else FoodAiSettings.key(context).isNotEmpty()

    fun noticeSeen(context: Context): Boolean = providersUsed(context).all { FoodAiSettings.noticeSeen(context, it) }

    fun markNoticeSeen(context: Context) = providersUsed(context).forEach { FoodAiSettings.markNoticeSeen(context, it) }
}
