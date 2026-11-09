package com.lifetracker.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Looks a barcode up on Open Food Facts, the free open database of packaged food. No key and no account; the numbers are typed in by
 * people all over the world, so many Indian products are missing and some entries have mistakes, which is why the check page comes next.
 */
object OpenFoodFacts {
    private const val BASE = "https://world.openfoodfacts.org/api/v2/product/"
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    /** Only the digits of a barcode, so spaces and dashes in a typed number do not matter. */
    fun cleanCode(raw: String): String = raw.filter { it.isDigit() }

    /** The packaged food for [code], or null when Open Food Facts does not know it or has no numbers for it. */
    suspend fun lookup(code: String): PhotoFood? = withContext(Dispatchers.IO) {
        val digits = cleanCode(code)
        if (digits.length < 6) throw PhotoAiException("That does not look like a barcode number.")
        val request = Request.Builder()
            .url("$BASE$digits.json?fields=product_name,brands,nutriments,serving_quantity,quantity")
            .header("User-Agent", "Kairos/1.0 (Android; personal use)")
            .build()
        val (status, body) = try {
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw PhotoAiException("Could not reach Open Food Facts. Check the internet and try again.")
        }
        if (status == 404) return@withContext null
        if (status !in 200..299) throw PhotoAiException("Open Food Facts had a problem (error $status). Try again in a minute.")
        parse(body)
    }

    /** The answer of the product API as one serving of food, or null when there is no product or no calories in it. */
    fun parse(body: String): PhotoFood? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.optInt("status", 0) != 1) return null
        val product = root.optJSONObject("product") ?: return null
        val n = product.optJSONObject("nutriments") ?: return null

        fun num(key: String): Double? {
            if (!n.has(key) || n.isNull(key)) return null
            val v = n.optDouble(key, Double.NaN)
            return if (v.isFinite() && v >= 0) v else null
        }

        val kcal = num("energy-kcal_100g") ?: num("energy_100g")?.let { it / 4.184 } ?: return null
        val sodiumMg = num("sodium_100g")?.let { it * 1000 } ?: num("salt_100g")?.let { it * 400 } ?: 0.0

        val brand = product.optString("brands").split(',').firstOrNull()?.trim().orEmpty()
        val title = product.optString("product_name").trim()
        val name = when {
            title.isEmpty() && brand.isEmpty() -> "Packaged food"
            title.isEmpty() -> brand
            brand.isEmpty() || title.contains(brand, ignoreCase = true) -> title
            else -> "$brand $title"
        }
        val drink = Regex("""\d\s*(ml|cl|dl|l)\b""", RegexOption.IGNORE_CASE).containsMatchIn(product.optString("quantity"))
        val serving = product.optString("serving_quantity").toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

        val per100 = PhotoFood(
            name = name, servingGrams = 100.0, kcal = kcal,
            protein = num("proteins_100g") ?: 0.0, carbs = num("carbohydrates_100g") ?: 0.0, fat = num("fat_100g") ?: 0.0,
            fiber = num("fiber_100g") ?: 0.0, sugar = num("sugars_100g") ?: 0.0, sodiumMg = sodiumMg,
            sat = num("saturated-fat_100g") ?: 0.0, mono = num("monounsaturated-fat_100g") ?: 0.0, poly = num("polyunsaturated-fat_100g") ?: 0.0,
            drink = drink, ingredients = emptyList(), weightGuessed = false,
        )
        return if (serving != null) per100.atGrams(serving) else per100.copy(weightGuessed = true)
    }
}
