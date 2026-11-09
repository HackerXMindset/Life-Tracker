package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLabelTextTest {
    private val biscuit = """
        {"status":1,"product":{"product_name":"Marie Gold","brands":"Britannia, Other","quantity":"250 g","serving_quantity":"25",
         "nutriments":{"energy-kcal_100g":440,"proteins_100g":7.5,"carbohydrates_100g":75,"fat_100g":11,"fiber_100g":2.1,
                       "sugars_100g":20,"sodium_100g":0.4,"saturated-fat_100g":5}}}
    """.trimIndent()

    @Test
    fun aScannedPackBecomesOneServingOfItsPrintedSize() {
        val food = OpenFoodFacts.parse(biscuit)!!
        assertEquals("Britannia Marie Gold", food.name)
        assertEquals(25.0, food.servingGrams, 1e-9)
        assertEquals(110.0, food.kcal, 1e-9)
        assertEquals(1.875, food.protein, 1e-9)
        assertEquals(100.0, food.sodiumMg, 1e-9) // 0.4 g of sodium per 100 g, a quarter of that per 25 g
        assertFalse(food.drink)
        assertFalse(food.weightGuessed)
        assertEquals(440.0, food.per100().kcal, 1e-9)
    }

    @Test
    fun aPackWithoutAServingSizeStartsAt100GramsAndSaysSo() {
        val food = OpenFoodFacts.parse("""{"status":1,"product":{"product_name":"Oats","nutriments":{"energy-kcal_100g":380}}}""")!!
        assertEquals(100.0, food.servingGrams, 1e-9)
        assertTrue(food.weightGuessed)
        assertEquals("Oats", food.name)
    }

    @Test
    fun energyInKilojoulesAndSaltAreConverted() {
        val food = OpenFoodFacts.parse("""{"status":1,"product":{"nutriments":{"energy_100g":1000,"salt_100g":1.0}}}""")!!
        assertEquals(239.0, food.kcal, 0.1)
        assertEquals(400.0, food.sodiumMg, 1e-9)
        assertEquals("Packaged food", food.name)
    }

    @Test
    fun aDrinkIsRecognisedFromItsQuantity() {
        fun drink(q: String) = OpenFoodFacts.parse("""{"status":1,"product":{"quantity":"$q","nutriments":{"energy-kcal_100g":42}}}""")!!.drink
        assertTrue(drink("1 l"))
        assertTrue(drink("330 ml"))
        assertFalse(drink("200 g"))
    }

    @Test
    fun unknownProductsAndProductsWithoutEnergyGiveNothing() {
        assertNull(OpenFoodFacts.parse("""{"status":0,"status_verbose":"product not found"}"""))
        assertNull(OpenFoodFacts.parse("""{"status":1,"product":{"product_name":"X"}}"""))
        assertNull(OpenFoodFacts.parse("""{"status":1,"product":{"nutriments":{"proteins_100g":5}}}"""))
        assertNull(OpenFoodFacts.parse("not json"))
    }

    @Test
    fun aTypedBarcodeKeepsOnlyItsDigits() {
        assertEquals("89012345678", OpenFoodFacts.cleanCode("890-1234 5678"))
    }

    @Test
    fun aLabelIsReadAsPer100GramsAndTurnedIntoOneServing() {
        val food = FoodPhotoAi.parseLabel("""{"name":"Oats","serving_size_grams":40,"calories":380,"protein":13.0,"carbs":66.0,"fat":7.0,"sodium":5}""")
        assertEquals(40.0, food.servingGrams, 1e-9)
        assertEquals(152.0, food.kcal, 1e-9)
        assertEquals(5.2, food.protein, 1e-9)
        assertFalse(food.weightGuessed)
        assertEquals(380.0, food.per100().kcal, 1e-9)
        assertEquals(5.0, food.per100().sodiumMg, 1e-9)
    }

    @Test
    fun aLabelWithoutAServingSizeStartsAt100Grams() {
        val food = FoodPhotoAi.parseLabel("""{"name":"Ghee","serving_size_grams":0,"calories":900,"fat":100}""")
        assertEquals(100.0, food.servingGrams, 1e-9)
        assertTrue(food.weightGuessed)
        assertEquals(900.0, food.kcal, 1e-9)
    }

    @Test
    fun theLabelPromptAsksForPer100GramsAndOnlyTheBoxesInTheForm() {
        val fields = FoodFields.defsFor(listOf("kcal", "protein"), emptyList())
        val prompt = FoodPhotoAi.labelPrompt("Amul brand", fields)
        assertTrue(prompt.contains("PER 100 g"))
        assertTrue(prompt.contains("\"protein\":0.0"))
        assertFalse(prompt.contains("\"carbs\""))
        assertFalse(prompt.contains("\"items\""))
        assertTrue(prompt.contains("Amul brand"))
    }

    @Test
    fun theTypedMealPromptHoldsTheWordsAndNeverTalksAboutAPhoto() {
        val prompt = FoodPhotoAi.textPrompt("2 rotis and 1 katori dal", "the roti were large")
        assertTrue(prompt.contains("2 rotis and 1 katori dal"))
        assertTrue(prompt.contains("the roti were large"))
        assertTrue(prompt.contains("\"items\""))
        assertFalse(prompt.contains("nutrition label is visible"))
        assertNotNull(FoodPhotoAi.requestBodyText(prompt, "gemini-3.5-flash-lite").getJSONArray("contents"))
    }
}
