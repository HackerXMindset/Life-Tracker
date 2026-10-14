package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** What the add/edit habit form holds. [plan] is the schedule; its holiday and rest-day parts are not edited here. */
data class HabitForm(
    val name: String = "",
    val icon: String = "",
    val color: Long = HabitEditor.PALETTE.first(),
    val category: String = "",
    val kind: Int = HabitStats.KIND_YES_NO,
    /** The goal for one day. Only counted habits can set it; others keep their own. */
    val target: Double = 1.0,
    val unitLabel: String = "",
    val step: Double = 1.0,
    val anyAmount: Boolean = false,
    val plan: HabitPlan = HabitPlan(),
    val description: String = "",
)

/**
 * Makes and changes habits. A habit keeps Streak's own record (`HabitEntity.raw`), so a habit made here can be
 * handed to Streak later without converting anything, and fields this app does not edit are kept as they were.
 */
object HabitEditor {
    /** Colours to pick from, as 0xAARRGGBB. */
    val PALETTE: List<Long> = listOf(
        0xFF17785A, 0xFF4CC79B, 0xFF2E86DE, 0xFF5B6EE1, 0xFF8E5BD9, 0xFFC45BB5, 0xFFE0577A, 0xFFE0463A,
        0xFFE8812E, 0xFFE5B22B, 0xFF8FB339, 0xFF3AAFA9, 0xFF738078, 0xFF8D6E63, 0xFF546E7A, 0xFFB0BEC5,
    )

    /** Emoji to pick from quickly. Any other emoji can be typed. */
    val QUICK_ICONS = listOf("📚", "💪", "🏃", "🧘", "💧", "🛏️", "🥗", "🧠", "✍️", "🎧", "🧹", "💊")

    fun newId(): String = UUID.randomUUID().toString()

    fun formFrom(habit: HabitEntity): HabitForm {
        val o = parse(habit.raw)
        return HabitForm(
            name = habit.name,
            icon = habit.icon.takeIf { icon -> icon.any { it.code > 127 } }.orEmpty(),
            color = habit.color,
            category = habit.category,
            kind = habit.kind,
            target = habit.target,
            unitLabel = habit.unitLabel,
            step = habit.step,
            anyAmount = habit.anyAmount,
            plan = HabitPlan.fromRaw(habit.raw),
            description = o.optString("description"),
        )
    }

    /** What is wrong with the form, in words, or null if it can be saved. */
    fun validate(form: HabitForm): String? {
        if (form.name.isBlank()) return "Give it a name."
        if (form.kind == HabitStats.KIND_COUNTED) {
            if (!form.anyAmount && form.target <= 0) return "Set a goal above 0, or turn on \"any amount counts\"."
            if (form.step <= 0) return "The step must be above 0."
        }
        val plan = form.plan
        when (plan.interval) {
            HabitPlan.WEEKLY -> if (plan.timesPerPeriod !in 1..7) return "A week has 1 to 7 days."
            HabitPlan.MONTHLY -> if (plan.timesPerPeriod !in 1..31) return "A month has 1 to 31 days."
            HabitPlan.WEEKDAYS -> if (plan.weekdays.isEmpty()) return "Pick at least one day of the week."
            HabitPlan.EVERY_X_DAYS -> if (plan.every < 1) return "\"Every\" must be 1 or more."
        }
        return null
    }

    /**
     * The habit to store. [existing] is null for a new habit. Streak's own record is kept and the form's values are
     * written over it; a new habit gets Streak's defaults for everything the form does not ask about.
     */
    fun build(
        form: HabitForm,
        existing: HabitEntity?,
        id: String,
        sortOrder: Int,
        today: LocalDate,
        now: LocalDateTime,
    ): HabitEntity {
        val o = if (existing != null) parse(existing.raw) else JSONObject()
        if (existing == null) defaults(o, now)
        val plan = form.plan
        val counted = form.kind == HabitStats.KIND_COUNTED
        // A habit that is not counted keeps the goal it had (some yes/no habits want 2 a day), but a goal left over
        // from being a counted habit (20 pushups) must not become "20 times a day".
        val keepsOwn = existing != null && existing.kind != HabitStats.KIND_COUNTED
        val target = if (counted) form.target.coerceAtLeast(0.0) else (existing?.target?.takeIf { keepsOwn && it > 0 } ?: 1.0)
        val step = if (counted) form.step else (existing?.step?.takeIf { keepsOwn } ?: 1.0)

        o.put("id", id)
        o.put("name", form.name.trim())
        o.put("icon", form.icon.trim().ifEmpty { "target" })
        o.put("category", form.category.trim())
        o.put("description", form.description.trim())
        o.put("color", form.color)
        o.put("order", existing?.sortOrder ?: sortOrder)
        o.put("numberOfCompletionsPerDay", target)
        o.put("interval", plan.interval)
        o.put("targetFrequency", plan.timesPerPeriod)
        o.put("scheduleWeekdays", JSONArray(if (plan.interval == HabitPlan.WEEKDAYS) plan.weekdays.sorted() else emptyList<Int>()))
        o.put("scheduleEvery", plan.every)
        o.put("scheduleUnit", plan.unit)
        if (plan.interval == HabitPlan.EVERY_X_DAYS && plan.scheduleStart != null) {
            o.put("scheduleStart", dayKey(plan.scheduleStart))
        } else {
            o.remove("scheduleStart")
        }
        o.put("kind", form.kind)
        o.put("unitLabel", if (counted) form.unitLabel.trim() else "")
        o.put("incrementAmount", step)
        o.put("anyAmount", counted && form.anyAmount)

        return HabitEntity(
            id = id,
            name = form.name.trim(),
            icon = form.icon.trim().ifEmpty { "target" },
            category = form.category.trim(),
            color = form.color,
            kind = form.kind,
            quantKind = o.optInt("quantKind", 0),
            unitLabel = if (counted) form.unitLabel.trim() else "",
            target = target,
            step = step,
            anyAmount = counted && form.anyAmount,
            startMinute = o.optInt("startMinute", -1),
            durationMinutes = o.optInt("durationMinutes", 0),
            startedOn = existing?.startedOn ?: today.toString(),
            archived = existing?.archived ?: false,
            sortOrder = existing?.sortOrder ?: sortOrder,
            raw = o.toString(),
        )
    }

    /** Hides a habit, or shows it again. Nothing it recorded is touched. */
    fun withArchived(habit: HabitEntity, archived: Boolean, now: LocalDateTime): HabitEntity {
        val o = parse(habit.raw)
        if (archived) o.put("archivedAt", now.toString()) else o.remove("archivedAt")
        return habit.copy(archived = archived, raw = o.toString())
    }

    /** Reasons offered for a missed day, as in Streak. You can also write your own. */
    val MISS_PRESETS = listOf("Overslept", "Busy", "Traveling", "Sick", "Tired", "Forgot")

    /** The reasons you gave for missed days, by day. */
    fun missReasons(raw: String): Map<LocalDate, String> {
        val reasons = parse(raw).optJSONObject("missReasons") ?: return emptyMap()
        val out = HashMap<LocalDate, String>()
        for (key in reasons.keys()) {
            val day = HabitPlan.dayKey(key) ?: continue
            val text = reasons.optString(key).trim()
            if (text.isNotEmpty()) out[day] = text
        }
        return out
    }

    /** The habit with a reason for a day (an empty or null reason removes it). Everything else is kept. */
    fun withMissReason(habit: HabitEntity, day: LocalDate, reason: String?): HabitEntity {
        val o = parse(habit.raw)
        val reasons = o.optJSONObject("missReasons") ?: JSONObject()
        val text = reason?.trim().orEmpty()
        if (text.isEmpty()) reasons.remove(dayKey(day)) else reasons.put(dayKey(day), text)
        if (reasons.length() == 0) o.remove("missReasons") else o.put("missReasons", reasons)
        return habit.copy(raw = o.toString())
    }

    /** The presets first, then any other reason you have used before. */
    fun reasonOptions(habit: HabitEntity): List<String> =
        (MISS_PRESETS + missReasons(habit.raw).values).distinct()

    /** "Every day", "Mon, Wed, Fri", "3 times a week" ... for the habit page. */
    fun describe(plan: HabitPlan): String = when (plan.interval) {
        HabitPlan.WEEKLY -> "${plan.timesPerPeriod} ${if (plan.timesPerPeriod == 1) "day" else "days"} a week"
        HabitPlan.MONTHLY -> "${plan.timesPerPeriod} ${if (plan.timesPerPeriod == 1) "day" else "days"} a month"
        HabitPlan.WEEKDAYS -> plan.weekdays.sorted().joinToString(", ") { DAY_NAMES[(it - 1).coerceIn(0, 6)] }
        HabitPlan.EVERY_X_DAYS -> {
            val unit = when (plan.unit) {
                HabitPlan.UNIT_WEEKS -> "week"
                HabitPlan.UNIT_MONTHS -> "month"
                else -> "day"
            }
            if (plan.every == 1) "Every $unit" else "Every ${plan.every} ${unit}s"
        }
        else -> "Every day"
    }

    val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun defaults(o: JSONObject, now: LocalDateTime) {
        o.put("createdAt", now.toString())
        o.put("completions", JSONObject())
        o.put("reminders", JSONArray())
        o.put("coverPath", "")
        o.put("coverClarity", 100)
        o.put("dailyCost", 0)
        o.put("quantKind", 0)
        o.put("bookCoverPath", "")
        o.put("focusMinutes", 25)
        o.put("focusBreakMinutes", 0)
        o.put("focusOnly", false)
        o.put("tracking", false)
        o.put("anySteps", false)
        o.put("stepsNeeded", 1)
        o.put("difficulty", 0)
        o.put("startMinute", -1)
        o.put("durationMinutes", 0)
        o.put("substeps", JSONArray())
        o.put("vacations", JSONArray())
        o.put("restDays", JSONArray())
    }

    private fun parse(raw: String): JSONObject = try {
        JSONObject(raw)
    } catch (e: Exception) {
        JSONObject()
    }

    private fun dayKey(day: LocalDate): String =
        "%02d-%02d-%04d".format(day.dayOfMonth, day.monthValue, day.year)
}
