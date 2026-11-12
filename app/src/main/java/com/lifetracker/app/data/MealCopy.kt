package com.lifetracker.app.data

import java.time.LocalDate

/** Copies of logged meals, for another day and/or another meal (breakfast eaten again as lunch). */
object MealCopy {
    /**
     * New meals with new ids on [toDate]. With [toType] null each keeps its own meal and time. With a meal chosen, a copy for the same meal
     * keeps its time and one for a different meal gets the time that meal gets when you add it: now for today, else its usual time.
     * Photos are not copied.
     */
    fun copies(
        source: List<MealEntity>, toType: String?, toDate: LocalDate, today: LocalDate, nowMinute: Int,
        newId: () -> String = FoodLogic::newMealId,
    ): List<MealEntity> = source.map { m ->
        val type = toType ?: m.mealType
        val minute = if (toType == null || toType == m.mealType) m.minuteOfDay else FoodLogic.minuteFor(type, toDate == today, nowMinute)
        m.copy(id = newId(), date = toDate.toString(), mealType = type, minuteOfDay = minute, source = "app")
    }
}
