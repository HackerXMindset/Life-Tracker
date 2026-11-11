package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMealTest {
    private val food = PhotoFood(
        name = "Dal rice", servingGrams = 300.0, kcal = 450.0, protein = 15.0, carbs = 75.0, fat = 9.0, fiber = 6.0, sugar = 3.0,
        sodiumMg = 600.0, sat = 2.0, mono = 3.0, poly = 1.5, drink = false, ingredients = listOf("Rice 150 g", "Dal 150 g"), weightGuessed = false,
    )

    @Test
    fun aLoggedMealAddsUpToWhatThePhotoReaderSaid() {
        val meal = food.toMeal("  Dal rice ", "lunch", "2026-11-09", 780, id = "x")
        assertEquals("Dal rice", meal.name)
        assertEquals(300.0, meal.grams, 1e-9)
        assertEquals(450.0, meal.kcal, 1e-9)
        assertEquals(15.0, meal.protein, 1e-9)
        assertEquals(75.0, meal.carbs, 1e-9)
        assertEquals(9.0, meal.fat, 1e-9)
        assertEquals(6.0, meal.fiber, 1e-9)
        assertEquals(600.0, meal.sodiumMg, 1e-9)
        assertEquals("lunch", meal.mealType)
        assertEquals(780, meal.minuteOfDay)
    }

    @Test
    fun aSmallerPortionScalesEveryNumber() {
        val half = food.atGrams(150.0)
        assertEquals(150.0, half.servingGrams, 1e-9)
        assertEquals(225.0, half.kcal, 1e-9)
        assertEquals(7.5, half.protein, 1e-9)
        assertEquals(300.0, half.sodiumMg, 1e-9)
        val meal = half.toMeal("Dal rice", "dinner", "2026-11-09", 1200)
        assertEquals(225.0, meal.kcal, 1e-9)
        assertEquals(150.0, meal.kcal100, 1e-9) // per 100 g does not change with the portion
    }

    @Test
    fun anEmptyNameBecomesFoodAndAZeroPortionChangesNothing() {
        assertEquals("Food", food.toMeal("   ", "snack", "2026-11-09", 600).name)
        assertEquals(food, food.atGrams(0.0))
    }

    private val plate = FoodPhotoAi.parse(
        """{"name":"Roti and dal","serving_size_grams":300,"calories":500,"protein":20,"carbs":80,"fat":10,"fiber":8,
            "items":[{"name":"Roti","grams":100,"calories":300,"protein":9,"carbs":55,"fat":4},
                     {"name":"Dal","grams":200,"calories":200,"protein":11,"carbs":25,"fat":6}]}""",
    )

    @Test
    fun theFoodsOnThePlateAreReadWithTheirOwnNumbers() {
        assertEquals(2, plate.items.size)
        assertTrue(plate.canSplit)
        assertEquals(listOf("Roti 100 g", "Dal 200 g"), plate.ingredients)
        assertEquals(200.0, plate.items[1].kcal, 1e-9)
    }

    @Test
    fun aSinglefoodOrItemsWithoutCaloriesCannotBeTickedOneByOne() {
        assertFalse(FoodPhotoAi.parse("""{"name":"Apple","calories":52,"items":[{"name":"Apple","grams":100,"calories":52}]}""").canSplit)
        assertFalse(FoodPhotoAi.parse("""{"name":"Tea","calories":40,"items":[{"name":"Tea","grams":100},{"name":"Milk","grams":50}]}""").canSplit)
        assertEquals(listOf(food), food.separateItems(setOf(0)))
    }

    @Test
    fun untickingAFoodRemovesItsShareOfEveryNumber() {
        val onlyRoti = plate.onlyTicked(setOf(0))
        assertEquals(300.0, onlyRoti.kcal, 1e-9)
        assertEquals(180.0, onlyRoti.servingGrams, 1e-9)
        assertEquals(4.8, onlyRoti.fiber, 1e-9)
        assertEquals(1, onlyRoti.items.size)
        assertEquals(plate, plate.onlyTicked(setOf(0, 1)))
    }

    @Test
    fun eachTickedFoodCanBeLoggedOnItsOwn() {
        val parts = plate.separateItems(setOf(0, 1))
        assertEquals(listOf("Roti", "Dal"), parts.map { it.name })
        assertEquals(listOf(300.0, 200.0), parts.map { it.kcal })
        assertEquals(listOf(100.0, 200.0), parts.map { it.servingGrams })
        assertEquals(9.0, parts[0].protein, 1e-9)
        assertEquals(4.8, parts[0].fiber, 1e-9) // 60% of the plate's calories
        assertEquals(3.2, parts[1].fiber, 1e-9)
        assertEquals(listOf("Dal"), plate.separateItems(setOf(1)).map { it.name })
        val meal = parts[1].toMeal("Dal", "dinner", "2026-11-09", 1200)
        assertEquals(200.0, meal.kcal, 1e-9)
        assertEquals(200.0, meal.grams, 1e-9)
    }

    @Test
    fun theItemsFollowThePortionSlider() {
        val half = plate.atGrams(150.0)
        assertEquals(150.0, half.items[0].kcal, 1e-9)
        assertEquals(50.0, half.items[0].grams, 1e-9)
        assertTrue(plate.per100().items.isEmpty())
    }

    @Test
    fun aMealPhotoFileNameCannotEscapeItsFolder() {
        assertEquals("ab", MealPhotos.safe("a/../b"))
        assertEquals("m-12_x", MealPhotos.safe("m-12_x"))
        assertEquals("x", MealPhotos.safe("///"))
    }

    @Test
    fun katoriAndBowlAreKnownPortionsWithUniqueKeys() {
        assertEquals(PortionKind.Katori, PortionKind.of("katori"))
        assertEquals(PortionKind.Bowl, PortionKind.of("bowl"))
        assertEquals(PortionKind.entries.size, PortionKind.entries.map { it.key }.toSet().size)
    }

    @Test
    fun cookingFatIsNineKcalAGramAndNeverGoesBelowZero() {
        val oily = food.copy(cookingFatG = 5.0)
        val more = oily.withCookingFat(15.0)
        assertEquals(15.0, more.cookingFatG, 1e-9)
        assertEquals(540.0, more.kcal, 1e-9)
        assertEquals(19.0, more.fat, 1e-9)
        val none = oily.withCookingFat(0.0)
        assertEquals(405.0, none.kcal, 1e-9)
        assertEquals(4.0, none.fat, 1e-9)
        // A guess bigger than the whole meal cannot make the numbers negative.
        assertEquals(0.0, food.copy(cookingFatG = 100.0, kcal = 100.0, fat = 20.0).withCookingFat(0.0).kcal, 1e-9)
        assertEquals(0.0, oily.withCookingFat(-3.0).cookingFatG, 1e-9)
    }

    @Test
    fun theCookingFatScalesWithTheAmountEaten() {
        assertEquals(2.5, food.copy(cookingFatG = 5.0).atGrams(150.0).cookingFatG, 1e-9)
    }

    @Test
    fun yourOwnNumbersKeepTodaysWeightAndMoveThePlateWithThem() {
        val before = food.toMeal("Dal rice", "lunch", "2026-11-01", 780, id = "old").copy(kcal100 = 200.0, protein100 = 8.0, carbs100 = 30.0, fat100 = 4.0)
        val plate = food.copy(
            cookingFatG = 10.0,
            items = listOf(PhotoItem("Rice", 150.0, 200.0, 4.0, 45.0, 1.0), PhotoItem("Dal", 150.0, 250.0, 11.0, 30.0, 8.0)),
        )
        val mine = plate.withNumbersOf(before)
        assertEquals(300.0, mine.servingGrams, 1e-9)
        assertEquals(600.0, mine.kcal, 1e-9)
        assertEquals(24.0, mine.protein, 1e-9)
        assertEquals(12.0, mine.fat, 1e-9)
        assertEquals(0.0, mine.cookingFatG, 1e-9)
        // The two foods on the plate still add up to the new total, in the same proportion as before.
        assertEquals(600.0, mine.items.sumOf { it.kcal }, 1e-6)
        assertEquals(200.0 / 450.0, mine.items[0].kcal / mine.kcal, 1e-9)
    }
}
