package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyGoalSyncTest {
    private val today = LocalDate.of(2026, 11, 12)
    private val profile = BodyProfile(Sex.MALE, 1996, 175.0, Activity.SEDENTARY, GoalKind.LOSE, 0.5)
    private val weights = listOf(WeightEntity("2026-11-10", 82.0))

    @Test
    fun aMissingSetupOrWeighInChangesNothing() {
        assertNull(BodyGoalSync.due(profile.copy(sex = null), weights, emptyList(), today))
        assertNull(BodyGoalSync.due(profile, emptyList(), emptyList(), today))
        assertNull(BodyGoalSync.due(profile, listOf(WeightEntity("2026-11-20", 82.0)), emptyList(), today))
    }

    @Test
    fun theGoalStartsTodayAtThePlannedCalories() {
        val plan = BodyMath.plan(profile, 82.0, today)!!
        val due = BodyGoalSync.due(profile, weights, emptyList(), today)!!
        assertEquals("2026-11-12", due.date)
        assertEquals(plan.target, due.kcal, 1e-9)
        assertEquals(true, due.protein > 0 && due.carbs > 0 && due.fat > 0)
    }

    @Test
    fun nothingIsWrittenWhenTheGoalAlreadyMatches() {
        val plan = BodyMath.plan(profile, 82.0, today)!!
        val goals = listOf(FoodGoalEntity("2026-11-01", plan.target, 200.0, 50.0, 100.0))
        assertNull(BodyGoalSync.due(profile, weights, goals, today))
    }

    @Test
    fun aNewWeightMovesTheGoalAndKeepsTheMacroProportions() {
        val goals = listOf(FoodGoalEntity("2026-11-01", 2371.0, 392.0, 73.0, 98.0))
        val due = BodyGoalSync.due(profile, listOf(WeightEntity("2026-11-12", 79.0)), goals, today)!!
        val plan = BodyMath.plan(profile, 79.0, today)!!
        assertEquals(plan.target, due.kcal, 1e-9)
        val k = plan.target / (98.0 * 4 + 392.0 * 4 + 73.0 * 9)
        assertEquals(Math.round(98.0 * k).toDouble(), due.protein, 1e-9)
    }
}
