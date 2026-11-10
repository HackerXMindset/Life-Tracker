package com.lifetracker.app.data

/** Which number counts as the calories burned on a day. A number you typed always wins. */
object BurnedMath {
    /** Rough energy of walking above resting, per step per kilogram of body weight (10,000 steps at 70 kg is about 280 kcal). */
    const val KCAL_PER_STEP_PER_KG = 0.0004

    enum class Source { NONE, TYPED, HEALTH_CONNECT, STEPS }

    class Burned(val kcal: Int, val source: Source)

    fun fromSteps(steps: Int, kg: Double): Int =
        if (steps <= 0 || kg <= 0.0) 0 else Math.round(steps * kg * KCAL_PER_STEP_PER_KG).toInt()

    /** [typed] by you, [health] from Health Connect's active calories, otherwise an estimate from [steps]. Zero means not known. */
    fun pick(typed: Int, health: Int, steps: Int, kg: Double): Burned {
        if (typed > 0) return Burned(typed, Source.TYPED)
        if (health > 0) return Burned(health, Source.HEALTH_CONNECT)
        val estimate = fromSteps(steps, kg)
        return if (estimate > 0) Burned(estimate, Source.STEPS) else Burned(0, Source.NONE)
    }
}
