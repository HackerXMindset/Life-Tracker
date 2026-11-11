package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekReportTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val week = (0..6).map { monday.plusDays(it.toLong()) }

    // 100 g at the given kcal per 100 g, 10 g of protein.
    private fun meal(id: String, name: String, date: String, kcal: Double, type: String = "lunch", protein100: Double = 10.0) =
        MealEntity(id, date, 700, type, name, 100.0, kcal, 10.0, 5.0, protein100, "app", "{}")

    private val goal = FoodGoalEntity("2026-11-01", 2000.0, 250.0, 60.0, 100.0)

    @Test
    fun emptyWeekHasNothing() {
        val r = WeekReport.build(week, emptyList(), { goal }, emptyList())
        assertEquals(7, r.days.size)
        assertEquals(0, r.loggedDays)
        assertNull(r.closest)
        assertNull(r.weightChangeKg)
        assertTrue(r.topFoods.isEmpty())
    }

    @Test
    fun closestAndFurthestDaysAreMeasuredAgainstTheGoal() {
        val meals = listOf(
            // Day one is on the calorie goal with 100 g of protein, so it counts as on target.
            meal("1", "Dal", "2026-11-02", 2000.0, protein100 = 100.0),
            meal("2", "Dal", "2026-11-03", 1000.0),
            meal("3", "Rice", "2026-11-04", 2600.0),
        )
        val r = WeekReport.build(week, meals, { goal }, emptyList())
        assertEquals(3, r.loggedDays)
        assertEquals(monday, r.closest!!.date)
        // 1000 is 50 % away and 2600 is 30 % away.
        assertEquals(monday.plusDays(1), r.furthest!!.date)
        assertEquals(1, r.daysOnTarget)
    }

    @Test
    fun oneLoggedDayHasNoSeparateFurthestDay() {
        val r = WeekReport.build(week, listOf(meal("1", "Dal", "2026-11-02", 2000.0)), { goal }, emptyList())
        assertEquals(monday, r.closest!!.date)
        assertNull(r.furthest)
    }

    @Test
    fun topFoodsAreCountedByNameMostFirst() {
        val meals = listOf(
            meal("1", "Dal", "2026-11-02", 100.0), meal("2", "dal ", "2026-11-03", 100.0), meal("3", "Roti", "2026-11-03", 100.0),
            meal("4", "Dal", "2026-11-04", 100.0), meal("5", "Roti", "2026-11-04", 100.0), meal("6", "Egg", "2026-11-04", 100.0),
        )
        assertEquals(listOf("Dal" to 3, "Roti" to 2, "Egg" to 1), WeekReport.build(week, meals, { null }, emptyList()).topFoods)
    }

    @Test
    fun proteinDaysUseNinetyPercentOfTheGoal() {
        // 100 g with 95 g protein per 100 g is 95 g (reached); 80 g is not.
        val meals = listOf(meal("1", "A", "2026-11-02", 100.0, protein100 = 95.0), meal("2", "B", "2026-11-03", 100.0, protein100 = 80.0))
        val r = WeekReport.build(week, meals, { goal }, emptyList())
        assertEquals(1, r.proteinDays)
        assertEquals(2, r.proteinGoalDays)
    }

    @Test
    fun weightChangeNeedsTwoWeighInsInTheWeek() {
        val one = listOf(WeightEntity("2026-11-03", 70.0), WeightEntity("2026-10-20", 72.0))
        assertNull(WeekReport.build(week, emptyList(), { null }, one).weightChangeKg)
        val two = one + WeightEntity("2026-11-08", 69.5)
        assertEquals(-0.5, WeekReport.build(week, emptyList(), { null }, two).weightChangeKg!!, 1e-9)
    }

    @Test
    fun summaryTextIsNothingForAnEmptyWeekAndShortOtherwise() {
        assertNull(WeekReport.summaryText(WeekReport.build(week, emptyList(), { goal }, emptyList())))
        val meals = listOf(meal("1", "Dal", "2026-11-02", 2000.0, protein100 = 100.0))
        val weights = listOf(WeightEntity("2026-11-02", 70.0), WeightEntity("2026-11-08", 69.5))
        val text = WeekReport.summaryText(WeekReport.build(week, meals, { goal }, weights))!!
        assertTrue(text, text.startsWith("Logged 1 of 7 days"))
        assertTrue(text, text.contains("Protein goal met on 1 of 1 days."))
        assertTrue(text, text.endsWith("Weight -0.5 kg."))
    }
}
