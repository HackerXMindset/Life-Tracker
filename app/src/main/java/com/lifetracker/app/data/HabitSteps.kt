package com.lifetracker.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One step of a checklist habit. The id stays when the title is changed. */
data class HabitStep(val id: String, val title: String)

/**
 * Checklist habits (Habits H8): a habit made of steps, as Streak's `substeps`. The steps live in the habit's own record
 * (`HabitEntity.raw`) so they travel with it; each day keeps which steps were ticked. No Android in it, so it can be tested.
 */
object HabitSteps {
    fun read(raw: String): List<HabitStep> = try {
        val array = JSONObject(raw).optJSONArray("substeps") ?: JSONArray()
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty()) null else HabitStep(id, o.optString("title"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** "Any of the steps is enough" (and how many), as Streak stores it. */
    fun anySteps(raw: String): Boolean = runCatching { JSONObject(raw).optBoolean("anySteps", false) }.getOrDefault(false)

    fun stepsNeeded(raw: String): Int = runCatching { JSONObject(raw).optInt("stepsNeeded", 1) }.getOrDefault(1)

    fun isChecklist(habit: HabitEntity): Boolean = habit.kind != HabitStats.KIND_RELAPSE && read(habit.raw).isNotEmpty()

    /** How many steps make the day done: all of them, or the chosen number (at least 1, at most all). 0 when there are no steps. */
    fun needed(raw: String): Int {
        val total = read(raw).size
        if (total == 0) return 0
        return if (anySteps(raw)) stepsNeeded(raw).coerceIn(1, total) else total
    }

    /** "a,b,c" to [a, b, c]. */
    fun parse(ids: String): List<String> = ids.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun join(ids: Collection<String>): String = ids.joinToString(",")

    /** The steps ticked on a day that are still steps of the habit. */
    fun ticked(day: CompletionEntity?, steps: List<HabitStep>): Set<String> {
        if (day == null) return emptySet()
        val valid = steps.map { it.id }.toSet()
        return parse(day.steps).filter { it in valid }.toSet()
    }

    /**
     * The amount of a checklist day: the steps ticked that still exist. A day with no step list (from before steps were kept)
     * keeps its stored amount.
     */
    fun dayCount(day: CompletionEntity, steps: List<HabitStep>): Double =
        if (day.steps.isBlank()) day.count else ticked(day, steps).size.toDouble()

    /** Ticks or unticks one step. Null when no step is left ticked, so the day is cleared. [nowMinute] is noted when ticking, if known. */
    fun toggle(old: CompletionEntity?, habitId: String, date: String, stepId: String, steps: List<HabitStep>, nowMinute: Int?): CompletionEntity? {
        val now = ticked(old, steps).toMutableSet()
        val ticking = now.add(stepId)
        if (!ticking) now.remove(stepId)
        if (now.isEmpty()) return null
        val marks = HabitTicks.times(old) + (if (ticking) listOfNotNull(nowMinute) else emptyList())
        return CompletionEntity(habitId, date, now.size.toDouble(), marks.lastOrNull(), HabitTicks.join(marks), join(steps.map { it.id }.filter { it in now }))
    }

    /** Every step ticked, or, when the day is already done, null (the day is cleared), like Streak's toggle. */
    fun toggleAll(old: CompletionEntity?, habitId: String, date: String, habit: HabitEntity, steps: List<HabitStep>, nowMinute: Int?): CompletionEntity? {
        if (ticked(old, steps).size >= needed(habit.raw)) return null
        val marks = HabitTicks.times(old) + listOfNotNull(nowMinute)
        return CompletionEntity(habitId, date, steps.size.toDouble(), marks.lastOrNull(), HabitTicks.join(marks), join(steps.map { it.id }))
    }

    /** The steps as the JSON the habit record keeps. Blank titles are dropped. */
    fun toJson(steps: List<HabitStep>): JSONArray {
        val out = JSONArray()
        steps.filter { it.title.isNotBlank() }.forEach { out.put(JSONObject().put("id", it.id).put("title", it.title.trim())) }
        return out
    }
}
