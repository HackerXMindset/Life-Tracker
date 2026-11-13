package com.lifetracker.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OpenRouterAiTest {
    @Test
    fun aPhotoGoesInAsADataUrlNextToThePrompt() {
        val body = OpenRouterAi.requestBody(byteArrayOf(1, 2, 3), "What is this?", " google/gemini-2.5-flash ")
        assertEquals("google/gemini-2.5-flash", body.getString("model"))
        assertEquals(4000, body.getInt("max_tokens"))
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(2, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("What is this?", content.getJSONObject(0).getString("text"))
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
        assertTrue(content.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun wordsAloneHaveNoImagePart() {
        val content = OpenRouterAi.requestBody(null, "2 roti", "m/x").getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(1, content.length())
    }

    @Test
    fun theAnswerIsTheTextOfTheFirstChoice() {
        val body = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"{\"name\":\"Dal\"}"}}]}"""
        assertEquals("""{"name":"Dal"}""", OpenRouterAi.answerText(body))
    }

    @Test
    fun anAnswerInPartsIsJoined() {
        val body = """{"choices":[{"message":{"content":[{"type":"text","text":"{\"a\":"},{"type":"text","text":"1}"}]}}]}"""
        assertEquals("""{"a":1}""", OpenRouterAi.answerText(body))
    }

    private fun message(body: String): String = try {
        OpenRouterAi.answerText(body); fail("should have failed"); ""
    } catch (e: PhotoAiException) {
        e.message.orEmpty()
    }

    @Test
    fun badAnswersSayWhatToDo() {
        assertTrue("cut short" in message("""{"choices":[{"finish_reason":"length","message":{"content":"{"}}]}"""))
        assertTrue("empty" in message("""{"choices":[{"message":{"content":"  "}}]}"""))
        assertTrue("no answer" in message("""{"choices":[]}"""))
        assertTrue("not readable" in message("<html>"))
        assertTrue("credit" in message("""{"error":{"code":402,"message":"Insufficient credits"}}"""))
    }

    @Test
    fun failuresNameTheFix() {
        assertTrue("key" in OpenRouterAi.failure(401, "", "m").message!!)
        assertTrue("credit" in OpenRouterAi.failure(402, "", "m").message!!)
        assertTrue("busy" in OpenRouterAi.failure(429, "", "m").message!!)
        assertTrue("500" in OpenRouterAi.failure(500, "", "m").message!!)
        val noImage = OpenRouterAi.failure(404, """{"error":{"message":"No endpoints found that support image input"}}""", "meta/llama")
        assertTrue("cannot read photos" in noImage.message!! && "meta/llama" in noImage.message!!)
        assertTrue("meta/zzz" in OpenRouterAi.failure(404, "", "meta/zzz").message!!)
        val bad = OpenRouterAi.failure(400, """{"error":{"message":"bad field"}}""", "a/b")
        assertTrue("bad field" in bad.message!! && "a/b" in bad.message!!)
    }

    private val list = """
        {"data":[
         {"id":"google/gemini-2.5-flash","name":"Google: Gemini 2.5 Flash","context_length":1000000,
          "pricing":{"prompt":"0.0000003","completion":"0.0000025"},
          "architecture":{"input_modalities":["text","image"]}},
         {"id":"meta/llama-text:free","name":"Meta: Llama Text (free)","context_length":8000,
          "pricing":{"prompt":"0","completion":"0"},"architecture":{"input_modalities":["text"]}},
         {"id":"anthropic/claude-sonnet-4.5","name":"Anthropic: Claude Sonnet 4.5","context_length":200000,
          "pricing":{"prompt":"0.000003","completion":"0.000015"},"architecture":{"input_modalities":["text","image","file"]}},
         {"id":"","name":"no id"},
         {"id":"x/nopricing"}
        ]}
    """.trimIndent()

    @Test
    fun theModelListIsReadAndSortedByName() {
        val models = OpenRouterAi.parseModels(list)
        assertEquals(listOf("Anthropic: Claude Sonnet 4.5", "Google: Gemini 2.5 Flash", "Meta: Llama Text (free)", "x/nopricing"), models.map { it.name })
        val gemini = models.first { it.id == "google/gemini-2.5-flash" }
        assertTrue(gemini.readsPhotos)
        assertFalse(gemini.free)
        assertEquals("$0.30 in · $2.50 out per million", gemini.priceText())
        assertEquals("$3.00 in · $15 out per million", models.first { it.id.startsWith("anthropic") }.priceText())
        val free = models.first { it.id.endsWith(":free") }
        assertTrue(free.free)
        assertFalse(free.readsPhotos)
        assertEquals("Free", free.priceText())
        assertEquals("", models.first { it.id == "x/nopricing" }.priceText())
    }

    @Test
    fun anUnreadableListIsEmpty() {
        assertTrue(OpenRouterAi.parseModels("nope").isEmpty())
        assertTrue(OpenRouterAi.parseModels("""{"x":1}""").isEmpty())
    }

    @Test
    fun theListIsNarrowedByWordsPhotosAndPrice() {
        val models = OpenRouterAi.parseModels(list)
        assertEquals(2, OpenRouterAi.filter(models, "", photosOnly = true, freeOnly = false).size)
        assertEquals(listOf("google/gemini-2.5-flash"), OpenRouterAi.filter(models, "gemini flash", true, false).map { it.id })
        assertEquals(listOf("meta/llama-text:free"), OpenRouterAi.filter(models, "", photosOnly = false, freeOnly = true).map { it.id })
        assertTrue(OpenRouterAi.filter(models, "", photosOnly = true, freeOnly = true).isEmpty())
        assertEquals(listOf("anthropic/claude-sonnet-4.5"), OpenRouterAi.filter(models, "CLAUDE", true, false).map { it.id })
    }

    @Test
    fun modelNamesAreRecognised() {
        assertTrue(OpenRouterAi.looksLikeModelId("google/gemini-2.5-flash"))
        assertTrue(OpenRouterAi.looksLikeModelId(" meta-llama/llama-3.3-70b-instruct:free "))
        assertFalse(OpenRouterAi.looksLikeModelId("gemini-3.5-flash-lite"))
        assertFalse(OpenRouterAi.looksLikeModelId(""))
    }

    @Test
    fun providersAreFoundByKey() {
        assertEquals(AiProvider.GEMINI, AiProvider.of(null))
        assertEquals(AiProvider.GEMINI, AiProvider.of("nonsense"))
        assertEquals(AiProvider.OPENROUTER, AiProvider.of("openrouter"))
        assertEquals(AiProvider.OPENROUTER, AiProvider.of(AiProvider.OPENROUTER.key))
    }

    @Test
    fun theProviderPicksTheRightFailureText() {
        assertTrue("OpenRouter" in FoodPhotoAi.failure(AiProvider.OPENROUTER, 401, "", "m").message!!)
        assertTrue("Google" in FoodPhotoAi.failure(AiProvider.GEMINI, 403, "", "m").message!!)
        assertEquals("""{"a":1}""", FoodPhotoAi.answerText(AiProvider.OPENROUTER, """{"choices":[{"message":{"content":"{\"a\":1}"}}]}"""))
        assertEquals("hi", FoodPhotoAi.answerText(AiProvider.GEMINI, """{"candidates":[{"content":{"parts":[{"text":"hi"}]}}]}"""))
        assertEquals("x", JSONObject("""{"a":"x"}""").getString("a"))
    }
}
