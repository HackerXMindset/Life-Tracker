package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MealCopyTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun meal(id: String, type: String, minute: Int) = MealEntity(
        id = id, date = "2026-11-10", minuteOfDay = minute, mealType = type, name = "Dal $id", grams = 200.0,
        kcal100 = 120.0, carbs100 = 15.0, fat100 = 4.0, protein100 = 6.0, source = "ont", raw = "x", foodId = "f1",
    )

    private var n = 0
    private fun id() = "new" + (n++)

    @Test
    fun withNoMealChosenEachKeepsItsOwnMealAndTime() {
        val out = MealCopy.copies(listOf(meal("a", "breakfast", 480), meal("b", "dinner", 1230)), null, today.plusDays(1), today, 900, ::id)
        assertEquals(listOf("breakfast", "dinner"), out.map { it.mealType })
        assertEquals(listOf(480, 1230), out.map { it.minuteOfDay })
        assertEquals(setOf("2026-11-13"), out.map { it.date }.toSet())
    }

    @Test
    fun breakfastCopiedToLunchTodayGetsTheTimeRightNow() {
        val out = MealCopy.copies(listOf(meal("a", "breakfast", 480)), "lunch", today, today, 790, ::id)
        assertEquals("lunch", out[0].mealType)
        assertEquals(790, out[0].minuteOfDay)
    }

    @Test
    fun anotherMealOnAnotherDayGetsItsUsualTime() {
        val out = MealCopy.copies(listOf(meal("a", "breakfast", 480)), "dinner", today.plusDays(2), today, 790, ::id)
        assertEquals(20 * 60, out[0].minuteOfDay)
    }

    @Test
    fun theSameMealKeepsItsTimeAndEverythingElseIsKept() {
        val src = meal("a", "lunch", 805)
        val out = MealCopy.copies(listOf(src), "lunch", today, today, 100, ::id)[0]
        assertEquals(805, out.minuteOfDay)
        assertNotEquals(src.id, out.id)
        assertEquals("app", out.source)
        assertEquals(src.grams, out.grams, 1e-9)
        assertEquals(src.kcal100, out.kcal100, 1e-9)
        assertEquals("f1", out.foodId)
        assertEquals("Dal a", out.name)
    }

    @Test
    fun everyCopyGetsItsOwnId() {
        val out = MealCopy.copies(listOf(meal("a", "lunch", 800), meal("b", "lunch", 810)), "snack", today, today, 600, ::id)
        assertEquals(2, out.map { it.id }.toSet().size)
    }
}
