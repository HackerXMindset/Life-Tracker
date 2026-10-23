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
    /** Tracking only: no streak, no missed days; the card shows the last check and this week's and month's totals. Not for Avoid habits. */
    val tracking: Boolean = false,
    /** Checklist habit (Yes/no only): the steps, whether any [stepsNeeded] of them is enough, and how many. Blank titles are dropped. */
    val steps: List<HabitStep> = emptyList(),
    val anySteps: Boolean = false,
    val stepsNeeded: Int = 1,
    /** 0 = not set, 1 = easy, 2 = medium, 3 = hard. Only weighs the Today ring, and only if that is switched on. */
    val difficulty: Int = 0,
    /** The habit's reminders (Habits H12). Null means "leave the habit's own as they are"; a list, even an empty one, replaces them. */
    val reminders: List<HabitReminders.Reminder>? = null,
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
            // "target" is only what is stored when no icon was picked.
            icon = habit.icon.takeIf { it != "target" }.orEmpty(),
            color = habit.color,
            category = habit.category,
            kind = habit.kind,
            target = habit.target,
            unitLabel = habit.unitLabel,
            step = habit.step,
            anyAmount = habit.anyAmount,
            plan = HabitPlan.fromRaw(habit.raw),
            description = o.optString("description"),
            tracking = HabitRules.isTracking(habit),
            steps = HabitSteps.read(habit.raw),
            anySteps = HabitSteps.anySteps(habit.raw),
            stepsNeeded = HabitSteps.stepsNeeded(habit.raw).coerceAtLeast(1),
            difficulty = difficultyOf(habit.raw),
            reminders = HabitReminders.read(habit.raw),
        )
    }

    /** The difficulty in a habit's record: 0 = not set, 1 = easy, 2 = medium, 3 = hard. */
    fun difficultyOf(raw: String): Int = runCatching { JSONObject(raw).optInt("difficulty", 0) }.getOrDefault(0).coerceIn(0, 3)

    /** What is wrong with the form, in words, or null if it can be saved. */
    fun validate(form: HabitForm): String? {
        if (form.name.isBlank()) return "Give it a name."
        if (form.kind == HabitStats.KIND_COUNTED) {
            // A goal of 0 means no goal: any amount counts the day.
            if (form.target < 0) return "The goal cannot be below 0. Use 0 for no goal."
            if (form.step <= 0) return "The step must be above 0."
        }
        val steps = form.steps.count { it.title.isNotBlank() }
        if (form.kind == HabitStats.KIND_YES_NO && steps > 0) {
            if (form.tracking) return "A checklist cannot also be \"just tracking\"."
            if (form.anySteps && form.stepsNeeded !in 1..steps) return "Steps needed must be between 1 and $steps."
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
        // "Count from the last time done" and "same weekday each month" only mean something for "every X"; Streak ignores them otherwise.
        val everyX = plan.interval == HabitPlan.EVERY_X_DAYS
        val fromLastDone = everyX && plan.fromLastDone
        o.put("fromLastDone", fromLastDone)
        o.put("monthWeekday", everyX && plan.unit == HabitPlan.UNIT_MONTHS && !fromLastDone && plan.monthWeekday)
        o.put("difficulty", form.difficulty.coerceIn(0, 3))
        form.reminders?.let { HabitReminders.put(o, it) }
        if (plan.interval == HabitPlan.EVERY_X_DAYS && plan.scheduleStart != null) {
            o.put("scheduleStart", dayKey(plan.scheduleStart))
        } else {
            o.remove("scheduleStart")
        }
        o.put("kind", form.kind)
        o.put("unitLabel", if (counted) form.unitLabel.trim() else "")
        o.put("incrementAmount", step)
        o.put("anyAmount", counted && form.anyAmount)
        o.put("tracking", form.tracking && form.kind != HabitStats.KIND_RELAPSE)
        if (form.kind == HabitStats.KIND_YES_NO) {
            // Only a Yes/no habit has its steps edited here; any other kind keeps what its record already holds.
            val kept = form.steps.filter { it.title.isNotBlank() }
            o.put("substeps", HabitSteps.toJson(kept))
            o.put("anySteps", form.anySteps && kept.isNotEmpty())
            o.put("stepsNeeded", form.stepsNeeded.coerceIn(1, maxOf(1, kept.size)))
        }

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
            val start = plan.scheduleStart
            val months = if (plan.every == 1) "month" else "${plan.every} months"
            when {
                plan.fromLastDone -> (if (plan.every == 1) "Every $unit" else "Every ${plan.every} ${unit}s") + ", counted from the last time done"
                plan.unit == HabitPlan.UNIT_MONTHS && start != null && plan.monthWeekday ->
                    "On the ${weekdaySlot(start)} of every $months"
                plan.unit == HabitPlan.UNIT_MONTHS && start != null -> "On the ${ordinal(start.dayOfMonth)} of every $months"
                plan.every == 1 -> "Every $unit"
                else -> "Every ${plan.every} ${unit}s"
            }
        }
        else -> "Every day"
    }

    /** 1 = "1st", 2 = "2nd", 11 = "11th", 23 = "23rd". */
    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    /** "second Wednesday" or "last Friday": the same weekday in the same week of the month as [day] (the 5th week counts as last). */
    fun weekdaySlot(day: LocalDate): String {
        val nth = (day.dayOfMonth - 1) / 7
        val name = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        val word = when (nth) { 0 -> "first"; 1 -> "second"; 2 -> "third"; else -> "last" }
        return "$word $name"
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
