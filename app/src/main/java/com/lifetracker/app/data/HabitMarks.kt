package com.lifetracker.app.data

/**
 * The checks of habits, seen as one line each ("events") so the Food tab can show them next to meals. A check is never stored
 * twice: the Habits tab owns it (a [CompletionEntity] with its times) and this only reads it. No Android in it, so it can be tested.
 */
object HabitMarks {
    /** The minute of a check that has no time (a day ticked without a clock, or an old one). */
    const val NO_TIME = -1

    /** Where an event came from: its habit, its day, its minute and its place among the checks of that minute. */
    data class Ref(val habitId: String, val date: String, val minute: Int)

    fun eventId(habitId: String, date: String, minute: Int, index: Int): String = "$habitId|$date|$minute|$index"

    /** Reads an id made by [eventId]; null for anything else. The habit id may itself contain no "|". */
    fun parseId(id: String): Ref? {
        val parts = id.split('|')
        if (parts.size != 4) return null
        val minute = parts[2].toIntOrNull() ?: return null
        if (parts[1].length != 10) return null
        return Ref(parts[0], parts[1], minute)
    }

    /**
     * One event for each check of the [watched] habits. A day with times gives one per time; a day with an amount but no time
     * gives one at [NO_TIME]. Habits that are not watched are left out.
     */
    fun events(completions: List<CompletionEntity>, watched: Set<String>): List<TrackerEventEntity> {
        val out = ArrayList<TrackerEventEntity>()
        for (c in completions) {
            if (c.habitId !in watched || c.count <= 0.0) continue
            val times = HabitTicks.times(c)
            if (times.isEmpty()) {
                out.add(TrackerEventEntity(eventId(c.habitId, c.date, NO_TIME, 0), c.habitId, c.date, NO_TIME))
            } else {
                val seen = HashMap<Int, Int>()
                for (m in times) {
                    val n = (seen[m] ?: 0).also { seen[m] = it + 1 }
                    out.add(TrackerEventEntity(eventId(c.habitId, c.date, m, n), c.habitId, c.date, m))
                }
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.minuteOfDay }, { it.id }))
    }

    /**
     * The day with one check taken away: the one at [minute] ([NO_TIME] takes the last check). Returns null when nothing is left,
     * so the day is cleared. [amount] is what one check is worth.
     */
    fun without(old: CompletionEntity, minute: Int, amount: Double): CompletionEntity? {
        val times = HabitTicks.times(old)
        val at = if (minute == NO_TIME) -1 else times.indexOf(minute)
        val left = HabitTicks.round(old.count - amount)
        if (left <= 1e-9) return null
        val rest = if (at >= 0) times.toMutableList().also { it.removeAt(at) } else times.dropLast(1)
        return old.copy(count = left, minuteOfDay = rest.lastOrNull(), marks = HabitTicks.join(rest))
    }
}
