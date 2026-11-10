package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodPartOrderTest {
    private val parts = listOf(FoodPart.EXTRAS, FoodPart.VITAMINS, FoodPart.WEEK)

    @Test
    fun withoutASavedOrderTheDefaultOrderStays() {
        assertEquals(parts, FoodPartOrder.sorted(emptyList(), parts))
    }

    @Test
    fun aSavedOrderIsApplied_andNewPartsGoLast() {
        val saved = listOf("week", "extras")
        assertEquals(listOf(FoodPart.WEEK, FoodPart.EXTRAS, FoodPart.VITAMINS), FoodPartOrder.sorted(saved, parts))
    }

    @Test
    fun movingSwapsWithTheNeighbour() {
        val up = FoodPartOrder.move(emptyList(), parts, FoodPart.WEEK, -1)
        assertEquals(listOf(FoodPart.EXTRAS, FoodPart.WEEK, FoodPart.VITAMINS), FoodPartOrder.sorted(up, parts))
        val down = FoodPartOrder.move(up, parts, FoodPart.EXTRAS, 1)
        assertEquals(listOf(FoodPart.WEEK, FoodPart.EXTRAS, FoodPart.VITAMINS), FoodPartOrder.sorted(down, parts))
    }

    @Test
    fun movingPastTheEndChangesNothing() {
        assertEquals(emptyList<String>(), FoodPartOrder.move(emptyList(), parts, FoodPart.EXTRAS, -1))
        assertEquals(emptyList<String>(), FoodPartOrder.move(emptyList(), parts, FoodPart.WEEK, 1))
    }

    @Test
    fun movingOnePileKeepsTheOther() {
        val top = listOf(FoodPart.WATER, FoodPart.SUPPLEMENTS)
        val first = FoodPartOrder.move(emptyList(), parts, FoodPart.WEEK, -1)
        val both = FoodPartOrder.move(first, top, FoodPart.SUPPLEMENTS, -1)
        assertEquals(listOf(FoodPart.EXTRAS, FoodPart.WEEK, FoodPart.VITAMINS), FoodPartOrder.sorted(both, parts))
        assertEquals(listOf(FoodPart.SUPPLEMENTS, FoodPart.WATER), FoodPartOrder.sorted(both, top))
    }
}
