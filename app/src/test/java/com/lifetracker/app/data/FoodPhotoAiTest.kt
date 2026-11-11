package com.lifetracker.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FoodPhotoAiTest {
    private val answer = """
        ```json
        {"name":"2 rotis with dal","serving_size_grams":300,"calories":450,"protein":18.0,"carbs":70.0,"fat":9.0,
         "fiber":null,"sugar":3.0,"sodium":600,"saturated_fat":2.0,"monounsaturated_fat":null,"polyunsaturated_fat":1.5,
         "is_drink":false,"ingredients":[{"name":"Roti","grams":100},{"name":"Dal","grams":200.0},{"name":"","grams":5}]}
        ```
    """.trimIndent()

    @Test
    fun readsTheJsonEvenInsideACodeFence() {
        val food = FoodPhotoAi.parse(answer)
        assertEquals("2 rotis with dal", food.name)
        assertEquals(300.0, food.servingGrams, 1e-9)
        assertEquals(450.0, food.kcal, 1e-9)
        assertEquals(0.0, food.fiber, 1e-9) // null means unknown, not a number
        assertEquals(listOf("Roti 100 g", "Dal 200 g"), food.ingredients)
        assertFalse(food.weightGuessed)
    }

    @Test
    fun numbersForTheWholeServingBecomeNumbersPer100Grams() {
        val per100 = FoodPhotoAi.parse(answer).per100()
        assertEquals(150.0, per100.kcal, 1e-9)
        assertEquals(6.0, per100.protein, 1e-9)
        assertEquals(200.0, per100.sodiumMg, 1e-9)
        assertEquals(100.0, per100.servingGrams, 1e-9)
    }

    @Test
    fun aMissingWeightAssumes100AndSaysSo() {
        val food = FoodPhotoAi.parse("""{"name":"Apple","calories":52,"protein":0.3}""")
        assertTrue(food.weightGuessed)
        assertEquals(100.0, food.servingGrams, 1e-9)
        assertEquals(52.0, food.per100().kcal, 1e-9)
    }

    @Test
    fun answersThatAreNotFoodAreRefusedWithAnExplanation() {
        for (bad in listOf("sorry, I cannot see food", """{"name":null,"calories":0}""", """{"name":"Tea","calories":0}""")) {
            try {
                FoodPhotoAi.parse(bad)
                fail("should have refused: $bad")
            } catch (e: PhotoAiException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun thinkingIsTurnedDownForTheFlashModels() {
        assertEquals("minimal", FoodPhotoAi.thinkingLevel("gemini-3.5-flash-lite"))
        assertEquals("minimal", FoodPhotoAi.thinkingLevel("gemini-3.6-flash"))
        assertEquals("low", FoodPhotoAi.thinkingLevel("gemini-3.8-flash"))
        assertEquals("minimal", FoodPhotoAi.thinkingLevel("models/gemini-3.1-flash-lite"))
        assertNull(FoodPhotoAi.thinkingLevel("gemini-2.5-flash"))
        assertNull(FoodPhotoAi.thinkingLevel("something-else"))
    }

    @Test
    fun theRequestHoldsThePhotoThePromptAndAskForJson() {
        val body = FoodPhotoAi.requestBody(byteArrayOf(1, 2, 3), FoodPhotoAi.prompt("2 rotis"), "gemini-3.5-flash-lite")
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals("image/jpeg", parts.getJSONObject(0).getJSONObject("inlineData").getString("mimeType"))
        assertEquals("AQID", parts.getJSONObject(0).getJSONObject("inlineData").getString("data"))
        assertTrue(parts.getJSONObject(1).getString("text").contains("2 rotis"))
        val config = body.getJSONObject("generationConfig")
        assertEquals("application/json", config.getString("responseMimeType"))
        assertEquals("minimal", config.getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertFalse(FoodPhotoAi.prompt("").contains("The user says"))
    }

    @Test
    fun theAnswerTextIsTakenFromTheFirstCandidateOrExplained() {
        val ok = JSONObject("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\"a\":1}"}]}}]}""").toString()
        assertEquals("{\"a\":1}", FoodPhotoAi.answerText(ok))
        for (bad in listOf(
            """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"{"}]}}]}""",
            """{"promptFeedback":{"blockReason":"SAFETY"}}""",
            """{"candidates":[]}""",
        )) {
            try {
                FoodPhotoAi.answerText(bad)
                fail("should have failed: $bad")
            } catch (e: PhotoAiException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun theKeyIsNotPutInBackups() {
        assertFalse(SettingsBackup.included(FoodAiSettings.FILE))
    }

    @Test
    fun theCookingFatIsReadAndCannotBeMoreThanTheFat() {
        val some = FoodPhotoAi.parse("""{"name":"Paneer butter masala","serving_size_grams":250,"calories":520,"fat":38,"cooking_fat_grams":22}""")
        assertEquals(22.0, some.cookingFatG, 1e-9)
        val capped = FoodPhotoAi.parse("""{"name":"Fruit","serving_size_grams":100,"calories":60,"fat":1,"cooking_fat_grams":12}""")
        assertEquals(1.0, capped.cookingFatG, 1e-9)
        assertEquals(0.0, FoodPhotoAi.parse("""{"name":"Tea","serving_size_grams":150,"calories":40,"fat":1}""").cookingFatG, 1e-9)
    }

    @Test
    fun onlyAPlateIsAskedForTheCookingFat() {
        assertTrue(FoodPhotoAi.prompt("").contains("\"cooking_fat_grams\":0.0"))
        assertFalse(FoodPhotoAi.labelPrompt("").contains("cooking_fat_grams"))
    }
}
