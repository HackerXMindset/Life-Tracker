package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMathTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun profile(sex: Sex? = Sex.MALE, goal: GoalKind = GoalKind.MAINTAIN, activity: Activity = Activity.SEDENTARY, pace: Double = 0.5) =
        BodyProfile(sex, 1996, 175.0, activity, goal, pace)

    @Test
    fun bmiIsWeightOverHeightSquared() {
        assertEquals(22.857, BodyMath.bmi(70.0, 175.0)!!, 0.001)
        assertNull(BodyMath.bmi(0.0, 175.0))
        assertNull(BodyMath.bmi(70.0, 0.0))
    }

    @Test
    fun bmiHasPlainLabels() {
        assertEquals("Underweight", BodyMath.bmiLabel(17.0))
        assertEquals("Healthy range", BodyMath.bmiLabel(18.5))
        assertEquals("Healthy range", BodyMath.bmiLabel(24.9))
        assertEquals("Overweight", BodyMath.bmiLabel(25.0))
        assertEquals("Obese", BodyMath.bmiLabel(30.0))
    }

    @Test
    fun theHealthyRangeFollowsHeight() {
        val r = BodyMath.healthyKg(175.0)!!
        assertEquals(56.7, r.start, 0.1)
        assertEquals(76.3, r.endInclusive, 0.1)
        assertNull(BodyMath.healthyKg(0.0))
    }

    @Test
    fun ageComesFromTheBirthYear() {
        assertEquals(30, BodyMath.age(1996, today))
        assertNull(BodyMath.age(0, today))
        assertNull(BodyMath.age(2100, today))
    }

    @Test
    fun restingEnergyUsesMifflinStJeor() {
        // 10*70 + 6.25*175 - 5*30 + 5 = 1648.75 (man); the same minus 166 for a woman
        val man = BodyMath.plan(profile(), 70.0, today)!!
        assertEquals(1648.75, man.bmr, 1e-6)
        assertEquals(1648.75 * 1.2, man.maintenance, 1e-6)
        val woman = BodyMath.plan(profile(sex = Sex.FEMALE), 70.0, today)!!
        assertEquals(1482.75, woman.bmr, 1e-6)
    }

    @Test
    fun activityRaisesTheMaintenance() {
        val low = BodyMath.plan(profile(activity = Activity.SEDENTARY), 70.0, today)!!.maintenance
        val high = BodyMath.plan(profile(activity = Activity.VERY), 70.0, today)!!.maintenance
        assertTrue(high > low)
        assertEquals(1648.75 * 1.725, high, 1e-6)
    }

    @Test
    fun losingAHalfKiloAWeekTakesAboutFiveFiftyAway() {
        val plan = BodyMath.plan(profile(goal = GoalKind.LOSE, activity = Activity.ACTIVE), 70.0, today)!!
        assertEquals(-550.0, plan.adjustment, 1e-9)
        assertEquals(Math.round(1648.75 * 1.55 - 550).toDouble(), plan.target, 1e-9)
        assertFalse(plan.floored)
    }

    @Test
    fun gainingAddsCalories() {
        val plan = BodyMath.plan(profile(goal = GoalKind.GAIN, pace = 0.25), 70.0, today)!!
        assertEquals(275.0, plan.adjustment, 1e-9)
    }

    @Test
    fun theTargetNeverGoesBelowTheSafeMinimum() {
        val plan = BodyMath.plan(profile(sex = Sex.FEMALE, goal = GoalKind.LOSE, pace = 1.0), 45.0, today)!!
        assertTrue(plan.floored)
        assertEquals(1200.0, plan.target, 1e-9)
    }

    @Test
    fun noPlanWithoutWhatItNeeds() {
        assertNull(BodyMath.plan(profile(sex = null), 70.0, today))
        assertNull(BodyMath.plan(profile().copy(birthYear = 0), 70.0, today))
        assertNull(BodyMath.plan(profile().copy(heightCm = 0.0), 70.0, today))
        assertNull(BodyMath.plan(profile(), 0.0, today))
    }

    @Test
    fun macrosKeepYourProportionsWhenYouHaveAGoal() {
        val current = FoodGoalEntity("2026-01-01", 2000.0, carbs = 250.0, fat = 56.0, protein = 124.0) // 996+... = 1996+504 kcal
        val (protein, carbs, fat) = BodyMath.macros(1250.0, 70.0, current)
        val total = protein * 4 + carbs * 4 + fat * 9
        assertEquals(1250.0, total, 40.0)
        assertTrue(carbs > protein)
    }

    @Test
    fun macrosWithoutAGoalUseSimpleRules() {
        val (protein, carbs, fat) = BodyMath.macros(2000.0, 70.0, null)
        assertEquals(112.0, protein, 1e-9)
        assertEquals(56.0, fat, 1e-9)
        assertEquals(2000.0, protein * 4 + carbs * 4 + fat * 9, 20.0)
        // a small budget caps protein at 40 % of the calories
        assertEquals(100.0, BodyMath.macros(1000.0, 120.0, null).first, 1e-9)
    }

    @Test
    fun weeksToTargetOnlyWhenTheGoalPointsTheRightWay() {
        assertEquals(10.0, BodyMath.weeksToTarget(75.0, 70.0, GoalKind.LOSE, 0.5)!!, 1e-9)
        assertNull(BodyMath.weeksToTarget(65.0, 70.0, GoalKind.LOSE, 0.5))
        assertEquals(4.0, BodyMath.weeksToTarget(68.0, 70.0, GoalKind.GAIN, 0.5)!!, 1e-9)
        assertNull(BodyMath.weeksToTarget(75.0, 70.0, GoalKind.MAINTAIN, 0.5))
        assertNull(BodyMath.weeksToTarget(75.0, 0.0, GoalKind.LOSE, 0.5))
        assertEquals(today.plusDays(70), BodyMath.eta(today, 10.0))
    }

    @Test
    fun progressRunsFromTheStartToTheTarget() {
        assertEquals(0.5, BodyMath.progress(80.0, 77.5, 75.0), 1e-9)
        assertEquals(1.0, BodyMath.progress(80.0, 74.0, 75.0), 1e-9)
        assertEquals(0.0, BodyMath.progress(80.0, 82.0, 75.0), 1e-9)
        assertEquals(0.5, BodyMath.progress(70.0, 72.5, 75.0), 1e-9)
        assertEquals(1.0, BodyMath.progress(75.0, 75.0, 75.0), 1e-9)
    }

    @Test
    fun theStartWeightIsTheLastOneOnOrBeforeTheDayTheGoalBegan() {
        val w = listOf(WeightEntity("2026-09-01", 85.0), WeightEntity("2026-10-01", 82.0), WeightEntity("2026-11-01", 80.0))
        assertEquals(82.0, BodyMath.startWeight(w, "2026-10-15")!!, 1e-9)
        assertEquals(85.0, BodyMath.startWeight(w, "2026-08-01")!!, 1e-9) // before all: the first one
        assertEquals(85.0, BodyMath.startWeight(w, "")!!, 1e-9)
        assertNull(BodyMath.startWeight(emptyList(), "2026-10-15"))
    }
}
