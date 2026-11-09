package com.lifetracker.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.edit
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** The key and models for "fill from a photo". Kept in a file that is left out of backups, so the key stays on this phone. */
object FoodAiSettings {
    const val FILE = "food_ai"
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_STRONG_MODEL = "gemini-3.8-flash"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun key(context: Context): String = prefs(context).getString("key", "")?.trim().orEmpty()
    fun model(context: Context): String = prefs(context).getString("model", DEFAULT_MODEL)?.trim().orEmpty().ifEmpty { DEFAULT_MODEL }
    fun strongModel(context: Context): String = prefs(context).getString("strong", DEFAULT_STRONG_MODEL)?.trim().orEmpty().ifEmpty { DEFAULT_STRONG_MODEL }
    fun noticeSeen(context: Context): Boolean = prefs(context).getBoolean("notice", false)

    fun save(context: Context, key: String, model: String, strong: String) {
        prefs(context).edit {
            putString("key", key.trim())
            putString("model", model.trim())
            putString("strong", strong.trim())
        }
    }

    fun markNoticeSeen(context: Context) = prefs(context).edit { putBoolean("notice", true) }
}

/** One food on the plate, as the photo reader saw it. */
data class PhotoItem(val name: String, val grams: Double, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double) {
    fun label(): String = if (grams > 0) name + " " + Math.round(grams) + " g" else name
}

/** What the model said about one photo. Every number is for the whole serving shown ([servingGrams]), not per 100 g. */
data class PhotoFood(
    val name: String,
    val servingGrams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val sodiumMg: Double,
    val sat: Double,
    val mono: Double,
    val poly: Double,
    val drink: Boolean,
    val ingredients: List<String>,
    /** True when the model gave no weight, so 100 g was assumed. */
    val weightGuessed: Boolean,
    /** Vitamins, minerals and your own nutrients for the whole serving, by nutrient id. Only the ones you asked for in your boxes. */
    val micro: Map<String, Double> = emptyMap(),
    /** The foods the photo reader saw on the plate, each with its own weight and main numbers (for the whole amount seen). */
    val items: List<PhotoItem> = emptyList(),
) {
    /** True when the plate holds several foods with calories of their own, so they can be ticked one by one. */
    val canSplit: Boolean get() = items.size >= 2 && items.sumOf { it.kcal } > 0

    /** The numbers per 100 g (or 100 ml), which is how foods are kept. */
    fun per100(): PhotoFood {
        val k = 100.0 / servingGrams
        return copy(
            servingGrams = 100.0, kcal = kcal * k, protein = protein * k, carbs = carbs * k, fat = fat * k,
            fiber = fiber * k, sugar = sugar * k, sodiumMg = sodiumMg * k, sat = sat * k, mono = mono * k, poly = poly * k,
            micro = micro.mapValues { it.value * k },
            items = emptyList(),
        )
    }
}

class PhotoAiException(message: String) : IOException(message)

/**
 * Asks Google's Gemini what is in a food photo. The method is the one the open-source calorie apps (Fud AI, CalorieMate) use:
 * a small JPEG goes inline with a prompt that asks for JSON only, with thinking turned down so the answer is quick,
 * and the result is shown to you to check before anything is saved.
 */
private fun JSONObject.positive(key: String): Double = if (isNull(key)) 0.0 else optDouble(key, 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0

object FoodPhotoAi {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private const val MAX_SIDE = 1600
    private const val JPEG_QUALITY = 80
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** The JSON the model is asked to answer with: only the boxes in the form, then (for a plate) the foods on it. */
    private fun shape(fields: List<FieldDef>, withItems: Boolean): String {
        val fixed = fields.filter { it.fixed }.ifEmpty { listOf(FoodFields.FIXED.first()) }
        val nutrients = fields.filter { !it.fixed }
        val shape = StringBuilder("{\"name\":\"...\",\"serving_size_grams\":0")
        for (f in fixed) shape.append(",\"").append(f.aiKey).append("\":").append(if (f.id == FoodFields.KCAL) "0" else "0.0")
        if (nutrients.isNotEmpty()) {
            shape.append(",\"nutrients\":{")
            shape.append(nutrients.joinToString(",") { "\"" + it.aiKey + "\":0.0" })
            shape.append("}")
        }
        shape.append(",\"is_drink\":false")
        if (withItems) shape.append(",\"items\":[{\"name\":\"...\",\"grams\":0.0,\"calories\":0,\"protein\":0.0,\"carbs\":0.0,\"fat\":0.0}]")
        shape.append("}")
        return shape.toString()
    }

    private fun nutrientUnits(fields: List<FieldDef>): String {
        val nutrients = fields.filter { !it.fixed }
        return if (nutrients.isEmpty()) "" else "In nutrients, give each amount in the unit shown: " +
            nutrients.joinToString("; ") { it.aiKey + " = " + it.label + " in " + it.unit } + ". "
    }

    private const val UNITS = "Calories are an integer. Protein, carbs, fat, fiber, sugar and the fat types are grams; sodium is milligrams. "

    private fun mealPrompt(intro: String, fields: List<FieldDef>, hint: String, hintLead: String, seesPhoto: Boolean): String = buildString {
        append(intro + "\nRespond ONLY with JSON in this shape:\n" + shape(fields, withItems = true) + "\n")
        append("All numbers are for the whole serving (serving_size_grams, or millilitres for a drink), not per 100 g. " + UNITS)
        append(nutrientUnits(fields))
        append("Give only the numbers listed in the shape above. ")
        append(
            if (seesPhoto) "If a nutrition label is visible, use its numbers. Otherwise estimate, including the oil and ghee used in cooking. "
            else "Estimate, including the oil and ghee used in cooking. ",
        )
        append(
            (if (seesPhoto) "Put a visible count in the name, for example \"3 rotis with dal\". " else "Put the counts in the name, for example \"3 rotis with dal\". ") +
                "When there are several foods, name the whole meal and list each food once in items (at most 12), each with its own weight, calories, protein, carbs and fat; the items should add up to the totals. For a single food give one item. " +
                "Use null for a number you cannot estimate.",
        )
        if (hint.isNotBlank()) append("\n\n$hintLead ${hint.trim()}\nUse this to improve the name, the portion size and the numbers.")
    }

    fun prompt(note: String, fields: List<FieldDef> = FoodFields.FIXED): String = mealPrompt(
        "Look at this food photo. Identify the food (Indian dishes are common) and estimate what is on the plate or in the glass.",
        fields, note, "The user says about this food:", seesPhoto = true,
    )

    /** For a meal that was typed or spoken instead of photographed. */
    fun textPrompt(text: String, note: String, fields: List<FieldDef> = FoodFields.FIXED): String = mealPrompt(
        "The user describes what they ate or drank: \"" + text.trim() + "\". Work out the foods (Indian dishes are common) and estimate the amounts " +
            "from the counts and sizes they gave; use usual home portions where they gave none (a roti about 40 g, a katori of dal about 150 g, a glass about 250 ml).",
        fields, note, "The user adds:", seesPhoto = false,
    )

    /** For a photo of the nutrition table on a pack: read it, do not estimate. */
    fun labelPrompt(note: String, fields: List<FieldDef> = FoodFields.FIXED): String = buildString {
        append("This is a photo of the nutrition facts table on a food package. Read the table; do not guess what is not printed.\nRespond ONLY with JSON in this shape:\n")
        append(shape(fields, withItems = false) + "\n")
        append(
            "name is the product name. The numbers are PER 100 g (or per 100 ml for a drink). If the table has only a per-serving column, convert it " +
                "to per 100 g using the serving size. serving_size_grams is the weight (or millilitres) of ONE serving as printed on the pack, or 0 if none is printed. " + UNITS +
                "If only salt is given, sodium is the salt in grams times 400. ",
        )
        append(nutrientUnits(fields))
        append("Give only the numbers listed in the shape above. Use null for a number that is not on the label.")
        if (note.isNotBlank()) append("\n\nThe user says about this product: ${note.trim()}")
    }

    /** "minimal" for the small models, "low" for newer big ones, nothing for models that do not take the setting. */
    fun thinkingLevel(model: String): String? {
        val id = model.trim().lowercase().substringAfterLast('/')
        val m = Regex("""^gemini-(\d+)(?:\.(\d+))?-(.+)$""").find(id) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: 0
        val variant = m.groupValues[3]
        if (major < 3) return null
        return when {
            variant.startsWith("flash-lite") -> "minimal"
            variant.startsWith("flash") && (major > 3 || minor >= 7) -> "low"
            variant.startsWith("flash") -> "minimal"
            else -> "low"
        }
    }

    fun requestBody(jpeg: ByteArray, prompt: String, model: String): JSONObject {
        val config = JSONObject().put("maxOutputTokens", 2500).put("responseMimeType", "application/json")
        thinkingLevel(model)?.let { config.put("thinkingConfig", JSONObject().put("thinkingLevel", it)) }
        val parts = JSONArray()
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", Base64.getEncoder().encodeToString(jpeg))))
            .put(JSONObject().put("text", prompt))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", config)
    }

    /** The same request without a photo, for a meal that was typed or spoken. */
    fun requestBodyText(prompt: String, model: String): JSONObject {
        val config = JSONObject().put("maxOutputTokens", 2500).put("responseMimeType", "application/json")
        thinkingLevel(model)?.let { config.put("thinkingConfig", JSONObject().put("thinkingLevel", it)) }
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", config)
    }

    /** The JSON object in the model's text, even when it came wrapped in a code fence or with words around it. */
    fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else null
    }

    fun parse(text: String, fields: List<FieldDef> = FoodFields.FIXED): PhotoFood {
        val json = extractJson(text)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: throw PhotoAiException("The answer was not readable. Try again, or type the food in.")
        val name = json.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" }
            ?: throw PhotoAiException("The photo was not recognised as food. Try a closer, brighter photo.")
        fun num(key: String): Double = if (json.isNull(key)) 0.0 else json.optDouble(key, 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0
        val grams = num("serving_size_grams")
        val kcal = num("calories")
        if (kcal <= 0) throw PhotoAiException("No calories came back. Try again, or type the food in.")
        val items = json.optJSONArray("items")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val n = o.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
                PhotoItem(n, o.positive("grams"), o.positive("calories"), o.positive("protein"), o.positive("carbs"), o.positive("fat"))
            }
        }.orEmpty().take(12)
        val ingredients = if (items.isNotEmpty()) items.map { it.label() } else json.optJSONArray("ingredients")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val n = item.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
                val g = item.optDouble("grams", 0.0).takeIf { it.isFinite() && it > 0 }
                if (g != null) n + " " + Math.round(g) + " g" else n
            }
        }.orEmpty().take(12)
        val nutrientJson = json.optJSONObject("nutrients")
        val micro = LinkedHashMap<String, Double>()
        if (nutrientJson != null) {
            for (f in fields.filter { !it.fixed }) {
                val v = if (nutrientJson.isNull(f.aiKey)) 0.0 else nutrientJson.optDouble(f.aiKey, 0.0)
                if (v.isFinite() && v > 0) micro[f.id] = v
            }
        }
        return PhotoFood(
            name = name,
            servingGrams = if (grams > 0) grams else 100.0,
            kcal = kcal,
            protein = num("protein"), carbs = num("carbs"), fat = num("fat"),
            fiber = num("fiber"), sugar = num("sugar"), sodiumMg = num("sodium"),
            sat = num("saturated_fat"), mono = num("monounsaturated_fat"), poly = num("polyunsaturated_fat"),
            drink = json.optBoolean("is_drink", false),
            ingredients = ingredients,
            weightGuessed = grams <= 0,
            micro = micro,
            items = items,
        )
    }

    /**
     * A label answer: numbers are per 100 g and [serving_size_grams] is the printed serving. The result is one serving of the product
     * (or 100 g, marked as guessed, when the pack prints no serving).
     */
    fun parseLabel(text: String, fields: List<FieldDef> = FoodFields.FIXED): PhotoFood {
        val json = extractJson(text)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: throw PhotoAiException("The answer was not readable. Try again with a closer, brighter photo of the table.")
        val serving = json.positive("serving_size_grams")
        json.put("serving_size_grams", 100.0)
        val per100 = parse(json.toString(), fields).copy(weightGuessed = false)
        return if (serving > 0) per100.atGrams(serving) else per100.copy(weightGuessed = true)
    }

    /** The text of the answer, or an error that says what to do. */
    fun answerText(body: String): String {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: throw PhotoAiException("The answer was not readable.")
        root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }
            ?: return candidateText(root)
        throw PhotoAiException("Google did not accept this photo. Try another one.")
    }

    private fun candidateText(root: JSONObject): String {
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw PhotoAiException("Google gave no answer. Try again.")
        if (candidate.optString("finishReason") == "MAX_TOKENS") throw PhotoAiException("The answer was cut short. Try again, or use the stronger model.")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = (0 until (parts?.length() ?: 0)).joinToString("") { parts!!.optJSONObject(it)?.optString("text").orEmpty() }
        if (text.isBlank()) throw PhotoAiException("Google gave an empty answer. Try again.")
        return text
    }

    /** A small, upright JPEG from a photo the camera or the gallery gave. */
    suspend fun prepare(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decoder returns no bitmap, only the size in [bounds], so a null result is normal here.
        val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
        if (!opened) throw PhotoAiException("Could not open the photo.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoAiException("That file is not a photo Kairos can read.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw PhotoAiException("Could not read the photo.")
        val turn = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (turn != 0f || scale < 1f) {
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (turn != 0f) postRotate(turn)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    /** Sends the photo and returns what the model saw. [strong] uses the bigger model. */
    suspend fun analyze(context: Context, jpeg: ByteArray, note: String, strong: Boolean): PhotoFood =
        withContext(Dispatchers.IO) { send(context, jpeg, strong, { prompt(note, it) }, { text, fields -> parse(text, fields) }) }

    /** Sends a photo of a nutrition table and returns one serving of the product. */
    suspend fun analyzeLabel(context: Context, jpeg: ByteArray, note: String, strong: Boolean): PhotoFood =
        withContext(Dispatchers.IO) { send(context, jpeg, strong, { labelPrompt(note, it) }, { text, fields -> parseLabel(text, fields) }) }

    /** Sends a typed or spoken meal and returns what the model made of it. */
    suspend fun analyzeText(context: Context, text: String, note: String, strong: Boolean): PhotoFood =
        withContext(Dispatchers.IO) { send(context, null, strong, { textPrompt(text, note, it) }, { answer, fields -> parse(answer, fields) }) }

    private suspend fun send(
        context: Context,
        jpeg: ByteArray?,
        strong: Boolean,
        promptFor: (List<FieldDef>) -> String,
        parser: (String, List<FieldDef>) -> PhotoFood,
    ): PhotoFood {
        val key = FoodAiSettings.key(context)
        if (key.isEmpty()) throw PhotoAiException("Add your Gemini key first.")
        val model = if (strong) FoodAiSettings.strongModel(context) else FoodAiSettings.model(context)
        val fields = FoodFields.defs(context)
        val prompt = promptFor(fields)
        val payload = if (jpeg != null) requestBody(jpeg, prompt, model) else requestBodyText(prompt, model)
        val request = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .addHeader("x-goog-api-key", key)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val delays = longArrayOf(2_000, 5_000)
        var attempt = 0
        while (true) {
            val (code, body) = try {
                client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw PhotoAiException("Could not reach Google. Check the internet and try again.")
            }
            when {
                code in 200..299 -> return parser(answerText(body), fields)
                (code == 429 || code == 503 || code == 500) && attempt < delays.size -> {
                    delay(delays[attempt]); attempt++
                }
                else -> throw failure(code, body, model)
            }
        }
    }

    /** What went wrong with a request, said so that you know what to do. */
    fun failure(code: Int, body: String, model: String): PhotoAiException = when (code) {
        429 -> PhotoAiException(
            "Gemini's free allowance is used up for now (it resets at 12:30 pm India time). Try the other model, or type the food in.",
        )
        400, 401, 403 -> {
            val detail = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
            PhotoAiException(
                if ("API key" in detail || code == 403 || code == 401) "Google did not accept the key. Check it in the AI settings."
                else "Google refused the request" + (if (model.isNotBlank()) " (model $model)" else "") + ". Check the model name in the AI settings.",
            )
        }
        404 -> PhotoAiException("Google does not know the model $model. Change its name in the AI settings.")
        else -> PhotoAiException("Google had a problem (error $code). Try again in a minute.")
    }

    /** The answer of [checkKey]: [ok] when both models answered, and one line for each model. */
    data class KeyCheck(val ok: Boolean, val message: String)

    /** Asks each model one tiny question with the key as typed (before saving it), so a wrong key or model name shows up now and not at the first photo. */
    suspend fun checkKey(key: String, models: List<Pair<String, String>>): KeyCheck = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext KeyCheck(false, "Paste the key first.")
        var allOk = true
        val lines = models.map { (label, model) ->
            val config = JSONObject().put("maxOutputTokens", 64)
            thinkingLevel(model)?.let { config.put("thinkingConfig", JSONObject().put("thinkingLevel", it)) }
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Reply with the word OK.")))))
                .put("generationConfig", config)
            val request = Request.Builder()
                .url("$BASE/models/${model.trim()}:generateContent")
                .addHeader("x-goog-api-key", key.trim())
                .post(body.toString().toRequestBody(JSON))
                .build()
            val result = try {
                client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                return@withContext KeyCheck(false, "Could not reach Google. Check the internet and test again.")
            }
            val (code, text) = result
            when {
                code in 200..299 -> "$label model ($model): works."
                code == 429 -> "$label model ($model): the key works, but today's free allowance is used up."
                else -> { allOk = false; "$label model ($model): " + failure(code, text, model).message }
            }
        }
        KeyCheck(allOk, lines.joinToString("\n"))
    }
}
