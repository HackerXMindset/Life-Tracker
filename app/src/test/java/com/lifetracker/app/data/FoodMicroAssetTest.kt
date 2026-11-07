package com.lifetracker.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vitamin and mineral table shipped with the app (USDA SR Legacy, per 100 g). */
class FoodMicroAssetTest {
    private val assets = File("src/main/assets/food")
    private val lib = FoodLibrary.parse(
        File(assets, "foods.tsv").readText(),
        File(assets, "dishes.tsv").readText(),
        File(assets, "micro.tsv").readText(),
    )
    private val known = DefaultNutrients.list.map { it.id }.toSet()

    @Test
    fun everyRowIsAFoodInTheLibrary() {
        val ids = lib.foods.map { it.id }.toSet()
        assertTrue(lib.micro.size > 300)
        assertTrue(lib.micro.keys.all { it in ids })
    }

    @Test
    fun everyColumnIsANutrientTheAppKnows() {
        assertTrue(lib.micro.values.all { row -> row.keys.all { it in known } })
    }

    @Test
    fun valuesAreSane() {
        for ((id, row) in lib.micro) for ((n, v) in row) {
            assertTrue("$id $n=$v", v > 0 && v < 20000)
        }
        // Per 100 g, values taken from the USDA table.
        assertEquals(8.7, lib.microOf("banana")["vit-c"]!!, 1e-9)
        assertEquals(5.0, lib.microOf("banana")["calcium"]!!, 1e-9)
    }
}
