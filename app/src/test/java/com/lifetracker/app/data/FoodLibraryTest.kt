package com.lifetracker.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodLibraryTest {
    private val assets = File("src/main/assets/food")
    private val lib: FoodLibraryData by lazy { FoodLibrary.parse(FoodLibrary.join(File(assets, "foods.tsv").readText(), File(assets, "foods-india.tsv").readText(), File(assets, "foods-ifct.tsv").readText()), File(assets, "dishes.tsv").readText()) }

    @Test
    fun theBundledListHasEveryFoodAndEachHasAPhoto() {
        assertEquals(561 + extraRows(), lib.foods.size)
        assertEquals(561 + extraRows(), lib.foods.map { it.id }.toSet().size)
        val mine = File(assets, "foods-india.tsv").readLines().drop(1).filter { it.isNotBlank() }.map { it.substringBefore('\t') }.toSet()
        // The Indian foods have no photos; the list from GymMane has one each (the IFCT foods only where it is the same plain food).
        for (f in lib.foods.filter { it.id !in mine && !it.id.startsWith("ifct-") }) assertTrue("no photo for ${f.id}", File(assets, f.photoAsset.removePrefix("food/")).isFile)
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

    private fun small(aliases: String = "") = FoodLibrary.parse(
        "id\ten\tk\nrava\tRava upma mix\t350\nroti\tRoti\t120\nchapati\tChapati\t110\nrice\tRice\t130\ndaltadka\tDal tadka\t100\ndalmakhani\tDal makhani\t120\n",
        "id\ten\titems\n", "", aliases,
    )

    @Test
    fun anotherNameForAFoodFindsItAfterTheExactMatches() {
        val lib = small("sooji rava semolina\nroti chapati phulka\n")
        assertEquals(listOf("rava"), lib.search("sooji").map { it.id })
        assertEquals(listOf("roti", "chapati"), lib.search("phulka").map { it.id }.sortedBy { if (it == "roti") 0 else 1 })
        // The food that really has the word comes before the one found through another name.
        assertEquals(listOf("chapati", "roti"), lib.search("chapati").map { it.id })
        // Without the list nothing changes.
        assertTrue(small().search("sooji").isEmpty())
    }

    @Test
    fun yourOwnFoodsComeFirstAmongEqualMatches() {
        val lib = small()
        assertEquals(listOf("daltadka", "dalmakhani"), lib.search("dal").map { it.id })
        assertEquals(listOf("dalmakhani", "daltadka"), lib.search("dal", boost = setOf("a:dalmakhani")).map { it.id })
    }

    @Test
    fun aliasLinesJoinTheirWords() {
        val map = FoodLibrary.parseAliases("a b\nb c\nsolo\n")
        assertEquals(setOf("a", "b", "c"), map["b"]!! + map["a"]!! + map["c"]!!)
        assertEquals(setOf("a", "b"), map["a"])
        assertTrue("solo" !in map)
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

    @Test
    fun indianFoodsAreByThePieceAndMarkedApproximate() {
        val jamun = lib.foods.first { it.id == "gulab-jamun" }
        assertEquals(PortionKind.Unit, jamun.portion)
        assertEquals(FoodCat.Sweet, jamun.cat)
        assertTrue(jamun.approx)
        // kcal matches the macros it is built from: 4 protein, 4 carbs, 9 fat.
        assertEquals(4 * 4.0 + 4 * 52 + 9 * 12, jamun.per100.kcal, 1.0)
        val samosa = lib.foods.first { it.id == "samosa" }
        assertEquals(80.0, samosa.portionGrams, 1e-9)
        assertEquals(FoodCat.Snack, samosa.cat)
        assertTrue(lib.foods.filter { it.cat in listOf(FoodCat.Sweet, FoodCat.Snack, FoodCat.Indian) }.size > 100)
    }

    @Test
    fun everyIndianRowAddsUpAndHasAPortion() {
        val mine = File(assets, "foods-india.tsv").readLines().drop(1).filter { it.isNotBlank() }.map { it.substringBefore('\t') }.toSet()
        assertTrue(mine.size > 100)
        for (f in lib.foods.filter { it.id in mine }) {
            assertTrue(f.id, f.approx)
            val fromMacros = 4 * f.per100.protein + 4 * f.per100.carbs + 9 * f.per100.fat
            assertEquals(f.id, fromMacros, f.per100.kcal, 1.5)
            assertTrue(f.id, f.portion != null && f.portionGrams > 0)
        }
    }

    private fun extraRows() = listOf("foods-india.tsv", "foods-ifct.tsv").sumOf { n -> File(assets, n).readLines().drop(1).count { it.isNotBlank() } }

    @Test
    fun ifctFoodsCarryTheirMeasuredNumbers() {
        val atta = lib.foods.first { it.id == "ifct-A019" }
        assertEquals(320.0, atta.per100.kcal, 1.0)
        assertEquals(10.57, atta.per100.protein, 1e-9)
        assertFalse(atta.approx)
        assertTrue(lib.foods.count { it.id.startsWith("ifct-") } > 500)
    }
}
