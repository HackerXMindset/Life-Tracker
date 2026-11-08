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
) {
    /** The numbers per 100 g (or 100 ml), which is how foods are kept. */
    fun per100(): PhotoFood {
        val k = 100.0 / servingGrams
        return copy(
            servingGrams = 100.0, kcal = kcal * k, protein = protein * k, carbs = carbs * k, fat = fat * k,
            fiber = fiber * k, sugar = sugar * k, sodiumMg = sodiumMg * k, sat = sat * k, mono = mono * k, poly = poly * k,
        )
    }
}

class PhotoAiException(message: String) : IOException(message)

/**
 * Asks Google's Gemini what is in a food photo. The method is the one the open-source calorie apps (Fud AI, CalorieMate) use:
 * a small JPEG goes inline with a prompt that asks for JSON only, with thinking turned down so the answer is quick,
 * and the result is shown to you to check before anything is saved.
 */
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

    fun prompt(note: String): String = buildString {
        append(
            "Look at this food photo. Identify the food (Indian dishes are common) and estimate what is on the plate or in the glass.\n" +
                "Respond ONLY with JSON in this shape:\n" +
                "{\"name\":\"...\",\"serving_size_grams\":0,\"calories\":0,\"protein\":0.0,\"carbs\":0.0,\"fat\":0.0,\"fiber\":0.0," +
                "\"sugar\":0.0,\"sodium\":0.0,\"saturated_fat\":0.0,\"monounsaturated_fat\":0.0,\"polyunsaturated_fat\":0.0," +
                "\"is_drink\":false,\"ingredients\":[{\"name\":\"...\",\"grams\":0.0}]}\n" +
                "All numbers are for the whole serving you can see (serving_size_grams, or millilitres for a drink), not per 100 g. " +
                "Calories are an integer. Protein, carbs, fat, fiber, sugar and the fat types are grams; sodium is milligrams. " +
                "If a nutrition label is visible, use its numbers. Otherwise estimate, including the oil and ghee used in cooking. " +
                "Put a visible count in the name, for example \"3 rotis with dal\". " +
                "When there are several foods, name the whole meal and list each food once in ingredients (at most 12). " +
                "Use null for a number you cannot estimate.",
        )
        if (note.isNotBlank()) append("\n\nThe user says about this food: ${note.trim()}\nUse this to improve the name, the portion size and the numbers.")
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
        val config = JSONObject().put("maxOutputTokens", 1500).put("responseMimeType", "application/json")
        thinkingLevel(model)?.let { config.put("thinkingConfig", JSONObject().put("thinkingLevel", it)) }
        val parts = JSONArray()
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", Base64.getEncoder().encodeToString(jpeg))))
            .put(JSONObject().put("text", prompt))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", config)
    }

    /** The JSON object in the model's text, even when it came wrapped in a code fence or with words around it. */
    fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else null
    }

    fun parse(text: String): PhotoFood {
        val json = extractJson(text)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: throw PhotoAiException("The answer was not readable. Try again, or type the food in.")
        val name = json.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" }
            ?: throw PhotoAiException("The photo was not recognised as food. Try a closer, brighter photo.")
        fun num(key: String): Double = if (json.isNull(key)) 0.0 else json.optDouble(key, 0.0).takeIf { it.isFinite() && it > 0 } ?: 0.0
        val grams = num("serving_size_grams")
        val kcal = num("calories")
        if (kcal <= 0) throw PhotoAiException("No calories came back. Try again, or type the food in.")
        val ingredients = json.optJSONArray("ingredients")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                val n = item.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
                val g = item.optDouble("grams", 0.0).takeIf { it.isFinite() && it > 0 }
                if (g != null) n + " " + Math.round(g) + " g" else n
            }
        }.orEmpty().take(12)
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
        )
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
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: throw PhotoAiException("Could not open the photo.")
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
        withContext(Dispatchers.IO) { send(context, jpeg, note, strong) }

    private suspend fun send(context: Context, jpeg: ByteArray, note: String, strong: Boolean): PhotoFood {
        val key = FoodAiSettings.key(context)
        if (key.isEmpty()) throw PhotoAiException("Add your Gemini key first.")
        val model = if (strong) FoodAiSettings.strongModel(context) else FoodAiSettings.model(context)
        val request = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .addHeader("x-goog-api-key", key)
            .post(requestBody(jpeg, prompt(note), model).toString().toRequestBody(JSON))
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
                code in 200..299 -> return parse(answerText(body))
                (code == 429 || code == 503 || code == 500) && attempt < delays.size -> {
                    delay(delays[attempt]); attempt++
                }
                code == 429 -> throw PhotoAiException(
                    "Gemini's free allowance is used up for now (it resets at 12:30 pm India time). Try the other model, or type the food in.",
                )
                code == 400 || code == 403 || code == 401 -> {
                    val detail = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
                    throw PhotoAiException(
                        if ("API key" in detail || code == 403 || code == 401) "Google did not accept the key. Check it in the AI settings."
                        else "Google refused the request" + (if (model.isNotBlank()) " (model $model)" else "") + ". Check the model name in the AI settings.",
                    )
                }
                code == 404 -> throw PhotoAiException("Google does not know the model $model. Change its name in the AI settings.")
                else -> throw PhotoAiException("Google had a problem (error $code). Try again in a minute.")
            }
        }
    }
}
