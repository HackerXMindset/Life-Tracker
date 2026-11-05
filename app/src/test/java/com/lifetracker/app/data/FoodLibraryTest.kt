package com.lifetracker.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodLibraryTest {
    private val assets = File("src/main/assets/food")
    private val lib: FoodLibraryData by lazy { FoodLibrary.parse(File(assets, "foods.tsv").readText(), File(assets, "dishes.tsv").readText()) }

    @Test
    fun theBundledListHasEveryFoodAndEachHasAPhoto() {
        assertEquals(561, lib.foods.size)
        assertEquals(561, lib.foods.map { it.id }.toSet().size)
        for (f in lib.foods) assertTrue("no photo for ${f.id}", File(assets, f.photoAsset.removePrefix("food/")).isFile)
    }

    @Test
    fun everyDishUsesFoodsThatExist() {
        assertEquals(27, lib.dishes.size)
        for (d in lib.dishes) for (i in d.items) {
            val food = lib.food(i.foodId)
            assertNotNull("${d.id} uses ${i.foodId}", food)
            if (i.cooked) assertTrue("${d.id}: ${i.foodId} cannot be cooked", food!!.canCook)
        }
    }

    @Test
    fun numbersAreReadPer100Grams() {
        val banana = lib.food("banana")!!
        assertEquals("Banana", banana.name)
        assertEquals(89.0, banana.per100.kcal, 0.001)
        assertEquals(2.6, banana.per100.fiber, 0.001)
        assertEquals(120.0, banana.portionGrams, 0.001)
        assertEquals(PortionKind.Unit, banana.portion)
        assertEquals(FoodCat.Fruit, banana.cat)
        assertEquals(9, banana.per100.amino.size)
        assertEquals("a:banana", banana.ref)
        assertEquals(banana, lib.byRef("a:banana"))
        assertEquals(null, lib.byRef("m:banana"))
    }

    @Test
    fun saltIsSodiumInMilligrams() {
        assertEquals(38758.0, lib.food("salt")!!.per100.sodiumMg, 0.001)
        assertTrue(lib.food("water")!!.liquid)
    }

    @Test
    fun searchFindsExactNameFirstThenStartsWith() {
        val hits = lib.search("banana").map { it.id }
        assertEquals("banana", hits.first())
        assertTrue(lib.search("BANANA ").isNotEmpty())
        assertTrue(lib.search("zzzzqq").isEmpty())
        assertTrue(lib.search("", FoodCat.Fruit).all { it.cat == FoodCat.Fruit })
        assertTrue(lib.search("", null).isEmpty())
    }

    @Test
    fun searchIgnoresAccentsAndNeedsEveryWord() {
        assertEquals("cafe", FoodLibrary.fold("Café"))
        val both = lib.search("greek yogurt")
        assertTrue(both.isNotEmpty() && both.all { it.name.lowercase().contains("greek") && it.name.lowercase().contains("yogurt") })
    }

    @Test
    fun aCookedDishLineIsMarked() {
        val line = FoodLibrary.parse("id\ten\tk\nrice\tRice\t130\n", "id\ten\titems\nbowl\tBowl\trice:200c rice:50\n").dishes.single().items
        assertEquals(listOf(DishItem("rice", 200.0, true), DishItem("rice", 50.0, false)), line)
    }

    @Test
    fun aDamagedLineIsSkippedNotFatal() {
        val data = FoodLibrary.parse("id\ten\tk\tp\n\tNoId\t1\t1\nok\tFine\t10\t2\n", "id\ten\titems\nbad\tBad\tnothing\n")
        assertEquals(listOf("ok"), data.foods.map { it.id })
        assertTrue(data.dishes.isEmpty())
    }
}
