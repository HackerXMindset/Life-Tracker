package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodFieldsTest {
    private fun nutrient(id: String, name: String, group: String = NutrientGroup.VITAMIN, hidden: Boolean = false, order: Int = 0) =
        NutrientEntity(id = id, name = name, unit = "µg", group = group, target = 0.0, hidden = hidden, builtIn = true, sortOrder = order)

    private val folate = nutrient("vit-b9", "Vitamin B9 (folate)", order = 9)
    private val iron = nutrient("iron", "Iron", NutrientGroup.MINERAL, order = 12)
    private val hiddenOne = nutrient("zinc", "Zinc", NutrientGroup.MINERAL, hidden = true, order = 19)
    private val all = listOf(iron, folate, hiddenOne)

    @Test
    fun nothingSavedMeansTheUsualBoxesAndCaloriesAlwaysStayFirst() {
        assertEquals(FoodFields.DEFAULT_IDS, FoodFields.decode(null))
        assertEquals(listOf("kcal"), FoodFields.decode(""))
        assertEquals(listOf("kcal", "protein", "vit-b9"), FoodFields.decode("protein, kcal,protein,,vit-b9"))
        assertEquals("kcal,carbs", FoodFields.encode(listOf("carbs")))
        assertEquals(FoodFields.DEFAULT_IDS, FoodFields.decode(FoodFields.encode(FoodFields.DEFAULT_IDS)))
    }

    @Test
    fun aTakenOutFatAndAnAddedFolateGiveTheBoxesYouAskedFor() {
        val ids = FoodFields.DEFAULT_IDS.filter { it != "fat" } + "vit-b9"
        val defs = FoodFields.defsFor(ids, all)
        assertFalse(defs.any { it.id == "fat" })
        assertEquals("vit-b9", defs.last().id)
        assertFalse(defs.last().fixed)
        assertEquals("Vitamin B9 (folate) µg", defs.last().caption)
        assertEquals("Calories (kcal)", defs.first().caption)
        assertEquals("Sodium mg", defs.first { it.id == "sodium" }.caption)
    }

    @Test
    fun aHiddenOrDeletedNutrientIsLeftOutButItsIdStaysSaved() {
        val ids = listOf("kcal", "zinc", "gone", "iron")
        assertEquals(listOf("kcal", "iron"), FoodFields.defsFor(ids, all).map { it.id })
        assertEquals("kcal,zinc,gone,iron", FoodFields.encode(ids))
    }

    @Test
    fun whatCanStillBeAddedIsTheTakenOutBoxesThenTheShowingNutrients() {
        val ids = listOf("kcal", "protein", "iron")
        val addable = FoodFields.addable(ids, all).map { it.id }
        assertEquals(listOf("carbs", "fat", "fiber", "sugar", "sodium", "sat", "mono", "poly", "vit-b9"), addable)
    }

    @Test
    fun thePromptAsksOnlyForTheBoxesInTheForm() {
        val fields = FoodFields.defsFor(listOf("kcal", "protein", "vit-b9"), all)
        val prompt = FoodPhotoAi.prompt("", fields)
        assertTrue(prompt.contains("\"calories\":0"))
        assertTrue(prompt.contains("\"protein\":0.0"))
        assertTrue(prompt.contains("\"nutrients\":{\"vit-b9\":0.0}"))
        assertTrue(prompt.contains("vit-b9 = Vitamin B9 (folate) in µg"))
        // The totals part of the answer shape (before the per-food items) holds only the boxes in the form.
        val totals = prompt.substringBefore("\"items\"")
        assertFalse(totals.contains("\"fat\":0.0"))
        assertFalse(totals.contains("\"sodium\":0.0"))
        assertFalse(totals.contains("\"carbs\""))
        assertTrue(prompt.substringAfter("\"items\"").contains("\"fat\":0.0")) // each food on the plate still gets the main numbers
        val usual = FoodFoodsPrompt.usual()
        assertTrue(usual.contains("\"saturated_fat\":0.0"))
        assertFalse(usual.contains("\"nutrients\""))
    }

    @Test
    fun theAnswerFillsTheVitaminBoxAndIgnoresOnesThatWereNotAsked() {
        val fields = FoodFields.defsFor(listOf("kcal", "vit-b9"), all)
        val answer = """{"name":"Palak dal","serving_size_grams":200,"calories":220,"nutrients":{"vit-b9":150.0,"iron":3.0}}"""
        val food = FoodPhotoAi.parse(answer, fields)
        assertEquals(mapOf("vit-b9" to 150.0), food.micro)
        val per100 = food.per100()
        assertEquals(75.0, per100.micro.getValue("vit-b9"), 1e-9)
        assertEquals(300.0, food.atGrams(400.0).micro.getValue("vit-b9"), 1e-9)
        val meal = food.atGrams(100.0).toMeal("Palak dal", "lunch", "2026-11-09", 780)
        assertEquals(75.0, NutrientValues.decode(meal.extra).getValue("vit-b9"), 1e-9)
    }

    @Test
    fun anAnswerWithoutTheNutrientsObjectStillWorks() {
        val food = FoodPhotoAi.parse("""{"name":"Apple","calories":52}""", FoodFields.defsFor(listOf("kcal", "vit-b9"), all))
        assertTrue(food.micro.isEmpty())
    }

    @Test
    fun theBoxListIsKeptInBackupsButTheKeyIsNot() {
        assertTrue(SettingsBackup.included(FoodFields.FILE))
        assertFalse(SettingsBackup.included(FoodAiSettings.FILE))
    }

    @Test
    fun problemsWithTheKeyOrModelAreExplained() {
        assertTrue(FoodPhotoAi.failure(429, "", "m").message!!.contains("12:30"))
        assertTrue(FoodPhotoAi.failure(404, "", "gemini-x").message!!.contains("gemini-x"))
        assertTrue(FoodPhotoAi.failure(403, "", "m").message!!.contains("key"))
        assertTrue(FoodPhotoAi.failure(400, """{"error":{"message":"API key not valid"}}""", "m").message!!.contains("key"))
        assertTrue(FoodPhotoAi.failure(400, """{"error":{"message":"bad field"}}""", "gemini-y").message!!.contains("gemini-y"))
        assertTrue(FoodPhotoAi.failure(500, "", "m").message!!.contains("500"))
    }

    private object FoodFoodsPrompt {
        fun usual(): String = FoodPhotoAi.prompt("")
    }
}
