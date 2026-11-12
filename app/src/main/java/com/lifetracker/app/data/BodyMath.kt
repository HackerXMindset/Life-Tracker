package com.lifetracker.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Sex(val key: String, val label: String) {
    FEMALE("female", "Female"),
    MALE("male", "Male");

    companion object {
        fun fromKey(key: String?): Sex? = entries.firstOrNull { it.key == key }
    }
}

/** How active you are in a usual week, with the factor that turns resting energy into daily energy. */
enum class Activity(val label: String, val hint: String, val factor: Double) {
    SEDENTARY("Sedentary", "A desk or sitting most of the day, little exercise", 1.2),
    LOW("Low active", "Light exercise or a walk 1 to 3 days a week", 1.375),
    ACTIVE("Active", "Exercise 3 to 5 days a week, or on your feet a lot", 1.55),
    VERY("Very active", "Hard exercise most days, or heavy physical work", 1.725),
}

enum class GoalKind(val key: String, val label: String) {
    LOSE("lose", "Lose weight"),
    MAINTAIN("maintain", "Maintain"),
    GAIN("gain", "Gain weight");

    companion object {
        fun fromKey(key: String?): GoalKind = entries.firstOrNull { it.key == key } ?: MAINTAIN
    }
}

/** What you told the app about your body. The weight is not here: it comes from your weigh-ins. */
data class BodyProfile(
    val sex: Sex? = null,
    val birthYear: Int = 0,
    val heightCm: Double = 0.0,
    val activity: Activity = Activity.LOW,
    val goal: GoalKind = GoalKind.MAINTAIN,
    /** How fast to lose or gain, in kg a week. */
    val paceKg: Double = 0.5,
    /** The day this goal was started, "" when not known. */
    val since: String = "",
)

/**
 * BMI, resting energy (Mifflin-St Jeor), the calories to keep your weight for your activity, and a daily target for losing or
 * gaining at a pace. These are estimates for an adult, not medical advice. No Android in it, so it can be tested.
 */
object BodyMath {
    /** About how much energy a kilogram of body weight holds. */
    const val KCAL_PER_KG = 7700.0
    val PACES = listOf(0.25, 0.5, 0.75, 1.0)

    fun bmi(kg: Double, cm: Double): Double? = if (kg > 0 && cm > 0) kg / ((cm / 100) * (cm / 100)) else null

    fun bmiLabel(bmi: Double): String = when {
        bmi < 18.5 -> "Underweight"
        bmi < 25.0 -> "Healthy range"
        bmi < 30.0 -> "Overweight"
        else -> "Obese"
    }

    /** The weights for [cm] that give a BMI from 18.5 to 24.9. */
    fun healthyKg(cm: Double): ClosedFloatingPointRange<Double>? =
        if (cm > 0) (18.5 * (cm / 100) * (cm / 100))..(24.9 * (cm / 100) * (cm / 100)) else null

    fun age(birthYear: Int, today: LocalDate): Int? = if (birthYear in 1900..today.year) today.year - birthYear else null

    fun minimumKcal(sex: Sex): Double = if (sex == Sex.MALE) 1500.0 else 1200.0

    class Plan(
        /** Calories you burn at rest. */
        val bmr: Double,
        /** Calories to keep your weight at your activity. */
        val maintenance: Double,
        /** What is added (gain) or taken away (lose) each day. */
        val adjustment: Double,
        val target: Double,
        /** True when the target was raised to the safe minimum. */
        val floored: Boolean,
    )

    /** The plan for [kg] now, or null while the sex, age, height or weight is missing. */
    fun plan(profile: BodyProfile, kg: Double, today: LocalDate): Plan? {
        val sex = profile.sex ?: return null
        val age = age(profile.birthYear, today) ?: return null
        if (kg <= 0 || profile.heightCm <= 0) return null
        val bmr = 10 * kg + 6.25 * profile.heightCm - 5 * age + if (sex == Sex.MALE) 5 else -161
        val maintenance = bmr * profile.activity.factor
        val adjustment = when (profile.goal) {
            GoalKind.MAINTAIN -> 0.0
            GoalKind.LOSE -> -profile.paceKg * KCAL_PER_KG / 7
            GoalKind.GAIN -> profile.paceKg * KCAL_PER_KG / 7
        }
        val raw = maintenance + adjustment
        val floor = minimumKcal(sex)
        val floored = raw < floor
        return Plan(bmr, maintenance, adjustment, Math.round(if (floored) floor else raw).toDouble(), floored)
    }

    /**
     * Protein, carbs and fat in grams for [kcal]. Your current goal's macros are kept in the same proportions; with none, protein
     * is 1.6 g a kilo (at most 40 % of the calories), fat a quarter of the calories, and carbs the rest.
     */
    fun macros(kcal: Double, kg: Double, current: FoodGoalEntity?): Triple<Double, Double, Double> {
        val currentKcal = current?.let { it.protein * 4 + it.carbs * 4 + it.fat * 9 } ?: 0.0
        if (current != null && currentKcal > 0) {
            val k = kcal / currentKcal
            return Triple(Math.round(current.protein * k).toDouble(), Math.round(current.carbs * k).toDouble(), Math.round(current.fat * k).toDouble())
        }
        val protein = minOf(1.6 * kg, kcal * 0.4 / 4)
        val fat = kcal * 0.25 / 9
        val carbs = ((kcal - protein * 4 - fat * 9) / 4).coerceAtLeast(0.0)
        return Triple(Math.round(protein).toDouble(), Math.round(carbs).toDouble(), Math.round(fat).toDouble())
    }

    /** Weeks to go from [current] to [target] at [paceKg] a week; null when already there or the goal points the other way. */
    fun weeksToTarget(current: Double, target: Double, goal: GoalKind, paceKg: Double): Double? {
        if (target <= 0 || paceKg <= 0 || current <= 0) return null
        return when (goal) {
            GoalKind.LOSE -> if (current > target) (current - target) / paceKg else null
            GoalKind.GAIN -> if (current < target) (target - current) / paceKg else null
            GoalKind.MAINTAIN -> null
        }
    }

    fun eta(today: LocalDate, weeks: Double): LocalDate = today.plusDays(Math.ceil(weeks * 7).toLong())

    /** How far you are from the weight you started at to the target, 0 to 1. */
    fun progress(start: Double, current: Double, target: Double): Double {
        if (start <= 0 || target <= 0 || current <= 0) return 0.0
        if (Math.abs(start - target) < 1e-9) return 1.0
        return ((start - current) / (start - target)).coerceIn(0.0, 1.0)
    }

    /** The weight to count progress from: the last weigh-in on or before [since], else your first one. */
    fun startWeight(weights: List<WeightEntity>, since: String): Double? {
        val sorted = weights.sortedBy { it.date }
        if (sorted.isEmpty()) return null
        return (if (since.isBlank()) null else sorted.lastOrNull { it.date <= since })?.kg ?: sorted.first().kg
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()
}
