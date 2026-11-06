package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutrientsTest {
    private fun meal(grams: Double, extra: String, id: String = "m") =
        MealEntity(id, "2026-11-06", 600, "lunch", "Dal", grams, 100.0, 10.0, 1.0, 5.0, "app", "", extra = extra)

    @Test
    fun starterListHasB9AndEveryIdIsSafeAndUnique() {
        val list = DefaultNutrients.list
        assertTrue(list.any { it.id == "vit-b9" && it.name.contains("B9") })
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertTrue(list.none { it.id.contains('=') || it.id.contains(';') })
        assertTrue(list.all { it.builtIn && !it.hidden && it.target > 0 })
        assertEquals(24, list.size)
    }

    @Test
    fun valuesWriteAndReadBack() {
        val text = NutrientValues.encode(mapOf("vit-c" to 12.5, "iron" to 0.4, "zero" to 0.0, "calcium" to 120.0))
        assertEquals("calcium=120;iron=0.4;vit-c=12.5", text)
        assertEquals(mapOf("calcium" to 120.0, "iron" to 0.4, "vit-c" to 12.5), NutrientValues.decode(text))
        assertTrue(NutrientValues.decode("").isEmpty())
        assertEquals(mapOf("a" to 1.0), NutrientValues.decode("junk;=3;a=1;b=x"))
    }

    @Test
    fun aMealGivesTheAmountsForTheGramsEaten() {
        val got = NutrientMath.forMeal(meal(250.0, "iron=2;vit-c=10"))
        assertEquals(5.0, got.getValue("iron"), 1e-9)
        assertEquals(25.0, got.getValue("vit-c"), 1e-9)
        assertTrue(NutrientMath.forMeal(meal(100.0, "")).isEmpty())
    }

    @Test
    fun aDayAddsTheMealsUp() {
        val total = NutrientMath.dayTotals(listOf(meal(100.0, "iron=2"), meal(50.0, "iron=2;zinc=1", "m2")))
        assertEquals(3.0, total.getValue("iron"), 1e-9)
        assertEquals(0.5, total.getValue("zinc"), 1e-9)
    }

    @Test
    fun theFractionOfATargetNeedsATarget() {
        assertEquals(0.5, NutrientMath.fraction(9.0, 18.0)!!, 1e-9)
        assertNull(NutrientMath.fraction(9.0, 0.0))
    }

    @Test
    fun whatYouTypedWinsAndZeroRemoves() {
        val base = mapOf("iron" to 1.0, "zinc" to 2.0)
        val merged = NutrientMath.merge(base, listOf(FoodNutrientEntity("a:x", "iron", 3.0), FoodNutrientEntity("a:x", "zinc", 0.0), FoodNutrientEntity("a:x", "vit-b9", 20.0)))
        assertEquals(mapOf("iron" to 3.0, "vit-b9" to 20.0), merged)
        assertEquals(base, NutrientMath.merge(base, emptyList()))
    }

    @Test
    fun hiddenAndEmptyNutrientsAreNotShown() {
        val list = listOf(
            NutrientEntity("a", "A", "mg", NutrientGroup.VITAMIN, 1.0, false, true, 0),
            NutrientEntity("b", "B", "mg", NutrientGroup.VITAMIN, 1.0, true, true, 1),
            NutrientEntity("c", "C", "mg", NutrientGroup.VITAMIN, 1.0, false, true, 2),
        )
        assertEquals(listOf("a"), NutrientMath.shown(list, mapOf("a" to 1.0, "b" to 1.0)).map { it.id })
    }

    @Test
    fun theFoodListCanCarryVitaminsPer100Grams() {
        val micro = FoodLibrary.parseMicro("id\tvit-c\tiron\nbanana\t8.7\t\norange\t53.2\t0.1\nsalt\t\t\n")
        assertEquals(mapOf("vit-c" to 8.7), micro["banana"])
        assertEquals(mapOf("vit-c" to 53.2, "iron" to 0.1), micro["orange"])
        assertNull(micro["salt"])
        val lib = FoodLibrary.parse("id\ten\tk\nbanana\tBanana\t89\n", "id\ten\titems\n", "id\tvit-c\nbanana\t8.7\n")
        assertEquals(mapOf("vit-c" to 8.7), lib.microOf("banana"))
        assertTrue(lib.microOf("nothing").isEmpty())
        assertTrue(FoodLibrary.parseMicro("").isEmpty())
    }
}
