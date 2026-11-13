package com.lifetracker.app.data

import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Where food photos and words are read. Each has its own key and its own two model names. */
enum class AiProvider(val key: String, val label: String, val company: String) {
    GEMINI("gemini", "Google Gemini", "Google"),
    OPENROUTER("openrouter", "OpenRouter", "OpenRouter");

    companion object {
        fun of(key: String?): AiProvider = entries.firstOrNull { it.key == key } ?: GEMINI
    }
}

/**
 * OpenRouter (openrouter.ai) is one key for hundreds of models from many makers. It speaks the same chat format as OpenAI:
 * the photo goes in as a data URL next to the prompt, and the answer is the text of the first choice.
 */
object OpenRouterAi {
    const val BASE = "https://openrouter.ai/api/v1"
    const val DEFAULT_MODEL = "google/gemini-2.5-flash"
    const val DEFAULT_STRONG_MODEL = "anthropic/claude-sonnet-4.5"

    /** Models that think first need room for it, or the answer is cut short. */
    private const val MAX_TOKENS = 4000

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** One request: [jpeg] is null for a meal that was typed or spoken. */
    fun requestBody(jpeg: ByteArray?, prompt: String, model: String, maxTokens: Int = MAX_TOKENS): JSONObject {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        if (jpeg != null) {
            val url = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)
            content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url)))
        }
        return JSONObject()
            .put("model", model.trim())
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("max_tokens", maxTokens)
            .put("temperature", 0.2)
    }

    fun request(path: String, key: String): Request.Builder = Request.Builder()
        .url("$BASE$path")
        .addHeader("Authorization", "Bearer " + key.trim())
        .addHeader("X-Title", "Kairos")

    /** The text of the answer, or an error that says what to do. */
    fun answerText(body: String): String {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: throw PhotoAiException("The answer was not readable. Try again.")
        root.optJSONObject("error")?.let { err ->
            val code = err.optInt("code", 0)
            throw failure(if (code in 400..599) code else 500, body, "")
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: throw PhotoAiException("OpenRouter gave no answer. Try again.")
        if (choice.optString("finish_reason") == "length") throw PhotoAiException("The answer was cut short. Try again, or use the stronger model.")
        val message = choice.optJSONObject("message")
        val raw = message?.opt("content")
        val text = when (raw) {
            is String -> raw
            is JSONArray -> (0 until raw.length()).joinToString("") { i ->
                val part = raw.opt(i)
                if (part is JSONObject) part.optString("text") else part?.toString().orEmpty()
            }
            else -> ""
        }
        if (text.isBlank()) throw PhotoAiException("The model gave an empty answer. Try again.")
        return text
    }

    private fun detail(body: String): String =
        runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty().trim()

    /** What went wrong with a request, said so that you know what to do. */
    fun failure(code: Int, body: String, model: String): PhotoAiException {
        val why = detail(body)
        val named = if (model.isNotBlank()) " (model $model)" else ""
        return when (code) {
            401 -> PhotoAiException("OpenRouter did not accept the key. Check it in the AI settings.")
            402 -> PhotoAiException("OpenRouter says there is no credit left. Add credit at openrouter.ai/credits, or choose a free model (its name ends in :free).")
            403 -> PhotoAiException("OpenRouter refused this request$named" + (if (why.isNotEmpty()) ": $why" else "") + ". Try another model.")
            404 -> PhotoAiException(
                if ("image" in why.lowercase()) "The model $model cannot read photos. Choose one that takes images in the AI settings."
                else "OpenRouter has no way to run the model $model. Check the name in the AI settings.",
            )
            408, 429 -> PhotoAiException("This model is busy, or its free allowance is used up for now. Try the other model, or wait a minute.")
            400 -> PhotoAiException("OpenRouter refused the request$named" + (if (why.isNotEmpty()) ": $why" else "") + ". Check the model name in the AI settings.")
            else -> PhotoAiException("OpenRouter had a problem (error $code). Try again in a minute.")
        }
    }

    /** One model on the list. Prices are dollars for a million words-ish "tokens"; null when the list does not say. */
    data class ModelInfo(
        val id: String,
        val name: String,
        val promptPerMillion: Double?,
        val completionPerMillion: Double?,
        val readsPhotos: Boolean,
        val contextLength: Int,
    ) {
        val free: Boolean get() = id.endsWith(":free") || (promptPerMillion == 0.0 && completionPerMillion == 0.0)

        /** "Free", "$0.30 in · $2.50 out per million", or "" when no price is known. */
        fun priceText(): String = when {
            free -> "Free"
            promptPerMillion == null -> ""
            else -> "$" + money(promptPerMillion) + " in" + (completionPerMillion?.let { " · $" + money(it) + " out" } ?: "") + " per million"
        }

        private fun money(v: Double): String = if (v >= 10) Math.round(v).toString() else String.format(java.util.Locale.US, "%.2f", v)
    }

    /** The answer of the models list. Models without an id are left out. */
    fun parseModels(body: String): List<ModelInfo> {
        val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val pricing = o.optJSONObject("pricing")
            fun perMillion(key: String): Double? = pricing?.optString(key)?.toDoubleOrNull()?.takeIf { it >= 0 }?.times(1_000_000)
            val inputs = o.optJSONObject("architecture")?.optJSONArray("input_modalities")
            val photos = inputs != null && (0 until inputs.length()).any { inputs.optString(it) == "image" }
            ModelInfo(id, o.optString("name").ifBlank { id }, perMillion("prompt"), perMillion("completion"), photos, o.optInt("context_length", 0))
        }.sortedBy { it.name.lowercase() }
    }

    /** The list narrowed by what was typed (any word may match the name or the id), photo readers only and free only when asked. */
    fun filter(models: List<ModelInfo>, query: String, photosOnly: Boolean, freeOnly: Boolean): List<ModelInfo> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return models.filter { m ->
            (!photosOnly || m.readsPhotos) && (!freeOnly || m.free) &&
                words.all { w -> w in m.id.lowercase() || w in m.name.lowercase() }
        }
    }

    /** The public list of models. It needs no key. */
    suspend fun fetchModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$BASE/models").get().build()
        val (code, body) = try {
            client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: java.io.IOException) {
            throw PhotoAiException("Could not reach OpenRouter. Check the internet and try again.", offline = true)
        }
        if (code !in 200..299) throw PhotoAiException("OpenRouter had a problem (error $code). Try again in a minute.")
        parseModels(body).also { if (it.isEmpty()) throw PhotoAiException("OpenRouter sent an empty list. Try again.") }
    }

    /** Asks each model one tiny question with the key as typed, so a wrong key or model name shows up now and not at the first photo. */
    suspend fun checkKey(key: String, models: List<Pair<String, String>>): FoodPhotoAi.KeyCheck = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext FoodPhotoAi.KeyCheck(false, "Paste the key first.")
        var allOk = true
        val lines = models.map { (label, model) ->
            val body = requestBody(null, "Reply with the word OK.", model, maxTokens = 32)
            val request = request("/chat/completions", key)
                .post(body.toString().toRequestBody(JSON))
                .build()
            val (code, text) = try {
                client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: java.io.IOException) {
                return@withContext FoodPhotoAi.KeyCheck(false, "Could not reach OpenRouter. Check the internet and test again.")
            }
            val inBody = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
            when {
                code in 200..299 && inBody == null -> "$label model ($model): works."
                code == 429 || inBody?.optInt("code") == 429 -> "$label model ($model): the key works, but this model is busy or its free allowance is used up."
                else -> { allOk = false; "$label model ($model): " + failure(if (code in 400..599) code else inBody?.optInt("code", 500) ?: 500, text, model).message }
            }
        }
        FoodPhotoAi.KeyCheck(allOk, lines.joinToString("\n"))
    }

    /** Whether a model name looks like OpenRouter's "maker/model" form. */
    fun looksLikeModelId(id: String): Boolean = Regex("""^[\w.\-]+/[\w.\-:~]+$""").matches(id.trim())
}
