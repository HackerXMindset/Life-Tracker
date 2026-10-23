package com.lifetracker.app.data

import org.json.JSONObject

/**
 * Housekeeping on the Habits tab (Habits H11): moving a habit up or down, and the Today filter. No Android in it, so it
 * can be tested. Nothing here deletes or changes what a habit recorded.
 */
object HabitOrder {
    /**
     * [ids] with [id] moved one place up ([delta] negative) or down (positive), swapping with the nearest habit that is
     * not in [skip] (hidden habits keep their place). Unchanged when [id] is not there or is already at that end.
     */
    fun move(ids: List<String>, id: String, delta: Int, skip: Set<String> = emptySet()): List<String> {
        val from = ids.indexOf(id)
        if (from < 0 || delta == 0) return ids
        val step = if (delta < 0) -1 else 1
        var to = from + step
        while (to in ids.indices && ids[to] in skip) to += step
        if (to !in ids.indices) return ids
        val out = ids.toMutableList()
        out[from] = ids[to]
        out[to] = id
        return out
    }

    /** The habit at place [index]: its sort order and Streak's own `order` field in its record, so the order travels. */
    fun withOrder(habit: HabitEntity, index: Int): HabitEntity {
        val o = runCatching { JSONObject(habit.raw) }.getOrDefault(JSONObject())
        o.put("order", index)
        return habit.copy(sortOrder = index, raw = o.toString())
    }

    /** The Today filter. Every switch is off by default, which shows everything. */
    data class Filter(
        /** Only habits due today (not off-schedule, not paused). */
        val dueToday: Boolean = false,
        val hideCompleted: Boolean = false,
        val hideTracking: Boolean = false,
        /** Completed habits go to the bottom (instead of being hidden). */
        val completedLast: Boolean = false,
    ) {
        /** Does any switch hide something? (Completed last only moves things.) */
        val hides: Boolean get() = dueToday || hideCompleted || hideTracking
        val active: Boolean get() = hides || completedLast
    }

    /** What the filter needs to know about one habit. */
    data class Facts(val dueToday: Boolean, val completed: Boolean, val tracking: Boolean)

    /** [items] after the filter: some hidden, and, with "completed last", completed ones moved down (order otherwise kept). */
    fun <T> apply(items: List<T>, filter: Filter, facts: (T) -> Facts): List<T> {
        val shown = items.filter { item ->
            val f = facts(item)
            !(filter.dueToday && !f.dueToday) && !(filter.hideCompleted && f.completed) && !(filter.hideTracking && f.tracking)
        }
        if (!filter.completedLast) return shown
        val (done, open) = shown.partition { facts(it).completed }
        return open + done
    }
}
