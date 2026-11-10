package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BurnedMathTest {
    @Test
    fun stepsBecomeCaloriesFromBodyWeight() {
        assertEquals(280, BurnedMath.fromSteps(10_000, 70.0))
        assertEquals(0, BurnedMath.fromSteps(0, 70.0))
        assertEquals(0, BurnedMath.fromSteps(8_000, 0.0))
    }

    @Test
    fun aTypedNumberWinsThenHealthConnectThenSteps() {
        assertEquals(BurnedMath.Source.TYPED, BurnedMath.pick(300, 450, 9_000, 70.0).source)
        assertEquals(300, BurnedMath.pick(300, 450, 9_000, 70.0).kcal)
        assertEquals(BurnedMath.Source.HEALTH_CONNECT, BurnedMath.pick(0, 450, 9_000, 70.0).source)
        val steps = BurnedMath.pick(0, 0, 10_000, 70.0)
        assertEquals(BurnedMath.Source.STEPS, steps.source)
        assertEquals(280, steps.kcal)
        assertEquals(BurnedMath.Source.NONE, BurnedMath.pick(0, 0, 0, 70.0).source)
    }
}
