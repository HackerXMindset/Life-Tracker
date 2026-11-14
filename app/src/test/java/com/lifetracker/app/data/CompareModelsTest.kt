package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareModelsTest {
    private val gem = CompareModel(AiProvider.GEMINI, "gemini-3.5-flash-lite")
    private val or1 = CompareModel(AiProvider.OPENROUTER, "google/gemini-2.5-flash")
    private val or2 = CompareModel(AiProvider.OPENROUTER, "anthropic/claude-sonnet-4.5")

    private fun food(kcal: Double) =
        FoodPhotoAi.parse("""{"name":"Dal","serving_size_grams":300,"calories":$kcal,"protein":10,"carbs":40,"fat":8}""")

    private fun ok(m: CompareModel, kcal: Double) = CompareResult(m, food(kcal), null, 1200)
    private fun failed(m: CompareModel) = CompareResult(m, null, "busy", 300)

    @Test
    fun providersCanBeMixedAndKeepTheirOrder() {
        assertEquals(listOf(or1, gem, or2), CompareModels.normalize(listOf(or1, gem, or2)))
    }

    @Test
    fun blanksAndRepeatsAreDroppedAndAtMostFourKept() {
        val list = listOf(or1, CompareModel(AiProvider.OPENROUTER, "  GOOGLE/gemini-2.5-flash "), CompareModel(AiProvider.GEMINI, " "), gem, or2,
            CompareModel(AiProvider.OPENROUTER, "a/b"), CompareModel(AiProvider.OPENROUTER, "c/d"))
        val out = CompareModels.normalize(list)
        assertEquals(listOf(or1, gem, or2, CompareModel(AiProvider.OPENROUTER, "a/b")), out)
        // the same name under another provider is a different model
        assertEquals(2, CompareModels.normalize(listOf(CompareModel(AiProvider.GEMINI, "x"), CompareModel(AiProvider.OPENROUTER, "x"))).size)
    }

    @Test
    fun theListSurvivesBeingSavedAndRead() {
        val list = listOf(or1, gem, or2)
        assertEquals(list, CompareModels.decode(CompareModels.encode(list)))
        assertTrue(CompareModels.decode("").isEmpty())
        assertEquals(listOf(gem), CompareModels.decode("junk\n\ngemini\tgemini-3.5-flash-lite"))
    }

    @Test
    fun comparingNeedsTheSwitchAndTwoModels() {
        assertFalse(CompareModels.active(false, listOf(or1, or2)))
        assertFalse(CompareModels.active(true, listOf(or1)))
        assertFalse(CompareModels.active(true, listOf(or1, or1)))
        assertTrue(CompareModels.active(true, listOf(or1, gem)))
    }

    @Test
    fun theSpreadIsOnlyAboutModelsThatAnswered() {
        assertNull(CompareModels.kcalSpread(listOf(ok(or1, 400.0), failed(or2))))
        assertEquals("", CompareModels.spreadText(listOf(ok(or1, 400.0), failed(or2))))
        val results = listOf(ok(or1, 400.0), failed(gem), ok(or2, 500.0))
        assertEquals(400.0 to 500.0, CompareModels.kcalSpread(results))
        assertEquals("Calories run from 400 to 500 across the models (25% apart).", CompareModels.spreadText(results))
        assertEquals("Calories run from 400 to 400 across the models.", CompareModels.spreadText(listOf(ok(or1, 400.0), ok(or2, 400.0))))
    }

    @Test
    fun callsAreCountedInWords() {
        assertEquals("2 calls", CompareModels.callsText(2))
        assertEquals("1 call", CompareModels.callsText(1))
    }
}
