package com.lifetracker.app.data

/** Time and goal arithmetic for activities. Kept free of Android so it can be tested. */
object ActivityStats {
    /** Minutes an entry lasts. A moment (no end time) is 0. */
    fun minutes(e: EntryEntity): Int = ((e.endMinute ?: e.startMinute) - e.startMinute).coerceAtLeast(0)

    fun minutesByActivity(entries: List<EntryEntity>): Map<String, Int> =
        entries.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { minutes(it) } }

    /** How one goal is going on a day. [ok] means reached (for a minimum) or still within the limit (for a maximum). */
    data class GoalProgress(val type: ActivityTypeEntity, val minutes: Int) {
        val goal: Int get() = type.goalMinutes
        val isLimit: Boolean get() = type.goalKind == ActivityTypeEntity.GOAL_AT_MOST
        val ok: Boolean get() = if (isLimit) minutes <= goal else minutes >= goal
        val fraction: Float get() = if (goal <= 0) 0f else (minutes.toFloat() / goal).coerceIn(0f, 1f)
    }

    /**
     * Progress for every active activity that has a goal, in the order the activities are listed.
     * [phoneMinutes] is time on apps you linked to an activity; it counts on top of what you logged.
     */
    fun goalProgress(
        types: List<ActivityTypeEntity>,
        entries: List<EntryEntity>,
        phoneMinutes: Map<String, Int> = emptyMap(),
    ): List<GoalProgress> {
        val byActivity = minutesByActivity(entries)
        return types
            .filter { !it.archived && it.goalMinutes > 0 }
            .map { GoalProgress(it, (byActivity[it.id] ?: 0) + (phoneMinutes[it.id] ?: 0)) }
    }

    /** What to show for an entry whose activity is no longer in the table (for example after restoring an old backup). */
    fun fallback(id: String): ActivityTypeEntity =
        ActivityTypeEntity(id, id, 0xFF738078L, 0, 0, true, Int.MAX_VALUE)

    fun find(types: List<ActivityTypeEntity>, id: String): ActivityTypeEntity =
        types.firstOrNull { it.id == id } ?: fallback(id)

    /** A fresh id for a custom activity. Ids of built-in activities never start with "c-". */
    fun newId(nowMillis: Long): String = "c-" + nowMillis.toString(36)
}
