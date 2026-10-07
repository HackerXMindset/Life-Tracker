package com.lifetracker.app.data.ont

import com.lifetracker.app.data.MealEntity

/** One Timeline entry made from an OpenNutriTracker activity. */
data class ActivitySpec(
    val sourceId: String,
    val date: java.time.LocalDate,
    val startMinute: Int,
    /** Null for something with no length, such as a "stool" custom activity logged as a moment. */
    val endMinute: Int?,
    val category: String,
    val title: String,
    val note: String,
)

object OntConverter {
    private const val LAST_MINUTE = 1439

    fun meal(i: OntIntake): MealEntity = MealEntity(
        id = i.id,
        date = i.at.toLocalDate().toString(),
        minuteOfDay = i.at.hour * 60 + i.at.minute,
        mealType = i.type,
        name = i.name,
        grams = i.amount,
        kcal100 = i.kcal100,
        carbs100 = i.carbs100,
        fat100 = i.fat100,
        protein100 = i.protein100,
        source = "ont",
        raw = i.raw,
    )

    /**
     * An activity with a length becomes a block; one without (duration 0) becomes a
     * moment. Custom activities (the ones you name yourself) go under Health; the
     * rest under Exercise. A block that would run past midnight stops at the end of the day.
     */
    fun activity(a: OntActivity): ActivitySpec {
        val start = a.at.hour * 60 + a.at.minute
        val minutes = Math.round(a.durationMinutes).toInt()
        val end = if (minutes > 0) (start + minutes).coerceAtMost(LAST_MINUTE).takeIf { it > start } else null
        val title = a.name.replaceFirstChar { it.uppercase() }
        val parts = listOfNotNull(
            "Imported from OpenNutriTracker",
            a.description.takeIf { it.isNotBlank() && !a.isCustom },
            if (a.burnedKcal > 0) "${Math.round(a.burnedKcal)} kcal" else null,
        )
        return ActivitySpec(
            sourceId = a.id,
            date = a.at.toLocalDate(),
            startMinute = start,
            endMinute = end,
            category = if (a.isCustom) "Health" else "Exercise",
            title = title,
            note = parts.joinToString(" · "),
        )
    }
}
