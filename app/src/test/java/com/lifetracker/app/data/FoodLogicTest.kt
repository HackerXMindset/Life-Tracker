package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodLogicTest {
    private val banana = Loggable(
        "a:banana", "Banana", Per100(kcal = 89.0, protein = 1.1, carbs = 22.8, fat = 0.3, fiber = 2.6), mapOf("vit-c" to 8.7),
        liquid = false, portion = PortionKind.Unit, portionGrams = 120.0, cat = FoodCat.Fruit, cookedYield = 0.0, photoAsset = "food/photos/banana.webp",
    )

    @Test
    fun aFoodBecomesAMealWithItsNumbersAndVitamins() {
        val meal = FoodLogic.toMeal(banana, 240.0, "2026-11-06", 480, "breakfast", "id1")
        assertEquals(213.6, meal.kcal, 0.001)
        assertEquals("a:banana", meal.foodId)
        assertEquals(6.24, meal.fiber, 0.001)
        assertEquals(mapOf("vit-c" to 8.7), NutrientValues.decode(meal.extra))
        assertEquals(20.88, NutrientMath.forMeal(meal).getValue("vit-c"), 0.001)
    }

    @Test
    fun portionsAreCountedFromThePortionSize() {
        assertEquals(1.5, banana.portionsOf(180.0)!!, 1e-9)
        assertEquals("1.5 pieces", FoodLogic.portionText(banana, 180.0))
        assertEquals("1 piece", FoodLogic.portionText(banana, 120.0))
        assertEquals(null, FoodLogic.portionText(banana.copy(portion = null), 120.0))
        assertEquals(120.0, banana.startAmount, 1e-9)
        assertEquals(100.0, banana.copy(portionGrams = 0.0).startAmount, 1e-9)
    }

    @Test
    fun cookedRiceSpreadsTheNumbersOverTheHeavierWeight() {
        val rice = banana.copy(name = "Rice", per100 = Per100(kcal = 360.0, carbs = 80.0), micro = mapOf("iron" to 2.7), cookedYield = 2.7, portionGrams = 50.0)
        val cooked = rice.cooked()
        assertEquals("Rice (cooked)", cooked.name)
        assertEquals(133.33, cooked.per100.kcal, 0.01)
        assertEquals(1.0, cooked.micro.getValue("iron"), 1e-9)
        assertEquals(135.0, cooked.portionGrams, 1e-9)
        assertTrue(!cooked.canCook)
        assertEquals(banana, banana.cooked())
    }

    @Test
    fun aQuickAddThatIsSavedKeepsItsNumbersPer100Grams() {
        val f = FoodLogic.foodFromQuickAdd("Samosa", 80.0, 260.0, 4.0, 30.0, 14.0, nowMs = 5L)
        assertTrue(f.id.startsWith("m:"))
        assertEquals(325.0, f.kcal100, 1e-9)
        assertEquals(17.5, f.fat100, 1e-9)
        assertEquals("serving", f.portionKind)
        assertEquals(80.0, f.portionGrams, 1e-9)
        val back = FoodLogic.toMeal(FoodLogic.of(f, emptyMap()), f.portionGrams, "2026-11-06", 600, "snack")
        assertEquals(260.0, back.kcal, 1e-9)
        assertEquals("1 serving", FoodLogic.portionText(FoodLogic.of(f, emptyMap()), 80.0))
    }

    @Test
    fun aQuickAddWithoutGramsCountsAsOneHundred() {
        val f = FoodLogic.foodFromQuickAdd(" Chai ", 0.0, 40.0, 1.0, 6.0, 1.0)
        assertEquals("Chai", f.name)
        assertEquals(40.0, f.kcal100, 1e-9)
        assertEquals(100.0, f.portionGrams, 1e-9)
    }

    @Test
    fun searchOwnNeedsEveryWordAndPutsPrefixFirst() {
        val foods = listOf(
            FoodEntity("m:1", "Maa's dal", 1.0, 1.0, 1.0, 1.0), FoodEntity("m:2", "Green dal soup", 1.0, 1.0, 1.0, 1.0),
            FoodEntity("m:3", "Dal makhani", 1.0, 1.0, 1.0, 1.0),
        )
        assertEquals(listOf("m:3", "m:1", "m:2"), FoodLogic.searchOwn(foods, "dal").map { it.id })
        assertEquals(listOf("m:2"), FoodLogic.searchOwn(foods, "green dal").map { it.id })
        assertEquals(3, FoodLogic.searchOwn(foods, "").size)
    }

    @Test
    fun theMealIsGuessedFromTheTimeOfDay() {
        assertEquals("breakfast", FoodLogic.mealTypeFor(8 * 60))
        assertEquals("lunch", FoodLogic.mealTypeFor(13 * 60))
        assertEquals("snack", FoodLogic.mealTypeFor(17 * 60))
        assertEquals("dinner", FoodLogic.mealTypeFor(21 * 60))
    }

    @Test
    fun eatAgainKeepsTheNumbersAndFindsThePhoto() {
        val lib = FoodLibrary.parse("id\ten\tk\tkind\tg\nbanana\tBanana\t89\tunit\t120\n", "id\ten\titems\n")
        val meal = FoodLogic.toMeal(banana, 120.0, "2026-11-06", 480, "breakfast")
        val again = FoodLogic.ofMeal(meal, lib)
        assertEquals("food/photos/banana.webp", again.photoAsset)
        assertEquals(89.0, again.per100.kcal, 1e-9)
        assertEquals(120.0, again.portionGrams, 1e-9)
        val typed = FoodLogic.ofMeal(meal.copy(foodId = "", grams = 150.0), lib)
        assertEquals(null, typed.photoAsset)
        assertEquals(150.0, typed.portionGrams, 1e-9)
        assertEquals(PortionKind.Serving, typed.portion)
    }

    @Test
    fun theWeekStartsOnMonday() {
        val week = FoodLogic.weekOf(LocalDate.of(2026, 11, 6))
        assertEquals(LocalDate.of(2026, 11, 2), week.first())
        assertEquals(LocalDate.of(2026, 11, 8), week.last())
        assertEquals(7, week.size)
    }

    @Test
    fun macroSharesAddToOne() {
        val (p, c, f) = FoodLogic.macroShares(Macros(kcal = 0.0, carbs = 100.0, fat = 50.0, protein = 100.0))
        assertEquals(1.0, p + c + f, 1e-9)
        assertEquals(Triple(0.0, 0.0, 0.0), FoodLogic.macroShares(Macros(0.0, 0.0, 0.0, 0.0)))
    }

    @Test
    fun aNewMealGetsTheRightTime() {
        assertEquals(615, FoodLogic.minuteFor("lunch", true, 615))
        assertEquals(8 * 60, FoodLogic.minuteFor("breakfast", false, 615))
        assertEquals(13 * 60, FoodLogic.minuteFor("lunch", false, 615))
        assertEquals(20 * 60, FoodLogic.minuteFor("dinner", false, 615))
        assertEquals(17 * 60, FoodLogic.minuteFor("snack", false, 615))
    }

    @Test
    fun aMealBecomesAFoodOfYourOwnWithOneServing() {
        val meal = FoodLogic.toMeal(banana, 150.0, "2026-11-06", 480, "breakfast", "id1")
        val food = FoodLogic.foodFromMeal(meal, 5L)
        assertTrue(FoodRef.isMine(food.id))
        assertEquals("Banana", food.name)
        assertEquals(89.0, food.kcal100, 1e-9)
        assertEquals("serving", food.portionKind)
        assertEquals(150.0, food.portionGrams, 1e-9)
        assertEquals(5L, food.createdMs)
    }

    @Test
    fun aReadyDishListsItsFoodsWithCookedOnesTurnedIntoCookedNumbers() {
        val foods = "id\ten\tk\tp\tc\tf\tfi\tsu\tna\tml\tkind\tg\tan\tsa\tmu\tpo\taa\tcat\tax\tcy\n" +
            "rice\tRice\t360\t7\t80\t1\t\t\t\t\t\t\t\t\t\t\t\tgrain\t\t2.7\n" +
            "dal\tDal\t100\t7\t15\t1\t\t\t\t\t\t\t\t\t\t\t\tlegume\t\t\n"
        val dishes = "id\ten\titems\nmeal\tDal rice\trice:200c dal:150\n"
        val library = FoodLibrary.parse(foods, dishes)
        val parts = FoodLogic.dishParts(library.dishes.single(), library)
        assertEquals(2, parts.size)
        assertEquals("Rice (cooked)", parts[0].first.name)
        assertEquals(200.0, parts[0].second, 1e-9)
        assertEquals(133.33, parts[0].first.per100.kcal, 0.01)
        assertEquals("Dal", parts[1].first.name)
    }
}
