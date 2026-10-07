package com.lifetracker.app.data.ont

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class OntIntake(
    val id: String,
    /** breakfast, lunch, dinner or snack. */
    val type: String,
    /** Always counted as grams, exactly as OpenNutriTracker does, whatever its unit label says. */
    val amount: Double,
    val at: LocalDateTime,
    val name: String,
    val kcal100: Double,
    val carbs100: Double,
    val fat100: Double,
    val protein100: Double,
    val raw: String,
)

data class OntGoal(val date: LocalDate, val kcal: Double, val carbs: Double, val fat: Double, val protein: Double)

data class OntActivity(
    val id: String,
    val at: LocalDateTime,
    val durationMinutes: Double,
    val name: String,
    val isCustom: Boolean,
    val description: String,
    val burnedKcal: Double,
)

data class OntData(
    val intakes: List<OntIntake>,
    val goals: List<OntGoal>,
    val activities: List<OntActivity>,
) {
    val isEmpty: Boolean get() = intakes.isEmpty() && goals.isEmpty() && activities.isEmpty()
}

/**
 * Reads the three files of an OpenNutriTracker export that this app uses:
 * `user_intake.json` (meals), `user_tracked_day.json` (daily goals) and
 * `user_activity.json` (activities). Any of them may be missing or empty.
 * One odd record is skipped instead of failing the import.
 */
object OntParser {
    const val INTAKE_FILE = "user_intake.json"
    const val TRACKED_DAY_FILE = "user_tracked_day.json"
    const val ACTIVITY_FILE = "user_activity.json"

    fun parse(intakeJson: String?, trackedDayJson: String?, activityJson: String?): OntData {
        require(!(intakeJson == null && trackedDayJson == null && activityJson == null)) {
            "This is not an OpenNutriTracker export."
        }
        return OntData(
            intakes = records(intakeJson).mapNotNull { guarded { intake(it) } },
            goals = records(trackedDayJson).mapNotNull { guarded { goal(it) } },
            activities = records(activityJson).mapNotNull { guarded { activity(it) } },
        )
    }

    /** Dates in the export are local time without a zone; some carry a "Z" but are still just a day. */
    fun moment(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value)
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun <T> guarded(block: () -> T?): T? = try {
        block()
    } catch (e: JSONException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private fun records(text: String?): List<JSONObject> {
        if (text.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("This is not an OpenNutriTracker export.")
        }
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.text(key: String, fallback: String = ""): String =
        if (isNull(key)) fallback else getString(key)

    private fun JSONObject.number(key: String, fallback: Double = 0.0): Double =
        if (isNull(key)) fallback else getDouble(key)

    private fun intake(o: JSONObject): OntIntake {
        val meal = o.getJSONObject("meal")
        val n = meal.optJSONObject("nutriments") ?: JSONObject()
        val type = o.text("type").lowercase()
        return OntIntake(
            id = o.getString("id"),
            type = if (type in setOf("breakfast", "lunch", "dinner", "snack")) type else "snack",
            amount = o.number("amount"),
            at = moment(o.text("dateTime")) ?: throw IllegalArgumentException("no time"),
            name = meal.text("name").trim().ifBlank { "Meal" },
            kcal100 = n.number("energyKcal100"),
            carbs100 = n.number("carbohydrates100"),
            fat100 = n.number("fat100"),
            protein100 = n.number("proteins100"),
            raw = o.toString(),
        )
    }

    private fun goal(o: JSONObject): OntGoal {
        // Only the day matters; the time and "Z" on these values are noise.
        val day = LocalDate.parse(o.getString("day").substring(0, 10))
        return OntGoal(
            date = day,
            kcal = o.number("calorieGoal"),
            carbs = o.number("carbsGoal"),
            fat = o.number("fatGoal"),
            protein = o.number("proteinGoal"),
        )
    }

    private fun activity(o: JSONObject): OntActivity {
        val a = o.getJSONObject("physicalActivityDBO")
        val code = a.text("code")
        val specific = a.text("specificActivity")
        val isCustom = code == "99999"
        val kcal = if (o.isNull("userKcal")) o.number("burnedKcal") else o.number("userKcal")
        val name = when {
            isCustom && specific != "custom" && specific.isNotBlank() -> specific
            isCustom -> "Custom activity"
            else -> specific
        }
        return OntActivity(
            id = o.getString("id"),
            at = moment(o.text("date")) ?: throw IllegalArgumentException("no time"),
            durationMinutes = o.number("duration"),
            name = name,
            isCustom = isCustom,
            description = a.text("description"),
            burnedKcal = kcal,
        )
    }
}
