package com.lifetracker.app.data.streak

import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.HabitTicks
import com.lifetracker.app.data.HabitTombstones

/**
 * What a Streak backup file says about deleting, bringing back and clearing (Habits H6c), worked out from the file and
 * from what this app already has, free of Android so it can be tested. Nothing here erases anything: a deleted habit or
 * note is hidden with the time, and can be restored.
 */
object StreakMerge {
    /**
     * The day to write when Streak's copy of a day meets this app's ([old], null if there is none), or null to leave this
     * app's day as it is. The bigger amount wins, and the times come with it (this side's stay if Streak had none). With
     * the same amount, Streak's times are taken only when this side has none.
     */
    fun day(old: CompletionEntity?, habitId: String, c: StreakCompletion): CompletionEntity? {
        val key = c.date.toString()
        if (old == null) return CompletionEntity(habitId, key, c.count, c.minuteOfDay, HabitTicks.join(c.marks))
        if (c.count > old.count) {
            return CompletionEntity(
                habitId, key, c.count, c.minuteOfDay ?: old.minuteOfDay,
                if (c.marks.isNotEmpty()) HabitTicks.join(c.marks) else old.marks,
            )
        }
        if (c.count == old.count && old.marks.isEmpty() && c.marks.isNotEmpty()) {
            return CompletionEntity(habitId, key, old.count, c.minuteOfDay ?: old.minuteOfDay, HabitTicks.join(c.marks))
        }
        return null
    }

    class LocalHabit(val id: String, val deletedAt: Long, val clearedAt: Long)

    class LocalNote(val id: String, val habitId: String, val deletedAt: Long)

    class Plan(
        /** Habits to hide, id to time. Their notes are in [hideNotes] with the same time. */
        val hideHabits: Map<String, Long>,
        val hideNotes: Map<String, Long>,
        /** Habits and notes that Streak changed after they were deleted here: show them again. */
        val showHabits: Set<String>,
        val showNotes: Set<String>,
        /** New "cleared at" times, habit id to time. */
        val clears: Map<String, Long>,
        /** Deletions to remember (so an older file cannot bring the item back), id to time. */
        val remember: Map<String, Long>,
    ) {
        val isEmpty: Boolean
            get() = hideHabits.isEmpty() && hideNotes.isEmpty() && showHabits.isEmpty() && showNotes.isEmpty() && clears.isEmpty()
    }

    /**
     * [deleted], [cleared] and [changes] are the three lists in Streak's file, [fileHabitIds] and [fileNoteIds] the habits
     * and notes it holds. [state] is what this app remembers having changed and deleted.
     */
    fun plan(
        deleted: Map<String, Long>,
        cleared: Map<String, Long>,
        changes: Map<String, Long>,
        fileHabitIds: Set<String>,
        fileNoteIds: Set<String>,
        habits: List<LocalHabit>,
        notes: List<LocalNote>,
        state: HabitTombstones.State,
    ): Plan {
        val habitById = habits.associateBy { it.id }
        val noteById = notes.associateBy { it.id }
        val hideHabits = LinkedHashMap<String, Long>()
        val hideNotes = LinkedHashMap<String, Long>()
        val remember = LinkedHashMap<String, Long>()

        for ((id, at) in deleted) {
            if (!HabitTombstones.shouldApply(at, state.changes[id], state.deleted[id])) continue
            remember[id] = at
            val habit = habitById[id]
            val note = noteById[id]
            if (habit != null && habit.deletedAt == 0L) {
                hideHabits[id] = at
                for (n in notes) if (n.habitId == id && n.deletedAt == 0L) hideNotes[n.id] = at
            } else if (note != null && note.deletedAt == 0L) {
                hideNotes[id] = at
            }
        }

        // Something that is deleted here but was changed in Streak after the deletion (and not deleted again there) comes back.
        val showHabits = LinkedHashSet<String>()
        val showNotes = LinkedHashSet<String>()
        fun comes(id: String, since: Long): Boolean {
            val theirs = changes[id] ?: return false
            val theirDeletion = deleted[id]
            return theirs > since && (theirDeletion == null || theirs > theirDeletion)
        }
        for (habit in habits) {
            if (habit.deletedAt == 0L || habit.id !in fileHabitIds || habit.id in hideHabits) continue
            if (comes(habit.id, maxOf(habit.deletedAt, state.deleted[habit.id] ?: 0L))) showHabits += habit.id
        }
        for (note in notes) {
            if (note.deletedAt == 0L || note.id !in fileNoteIds || note.id in hideNotes) continue
            // A note deleted together with its habit comes back with the habit, not on its own.
            if (note.habitId in habitById && habitById.getValue(note.habitId).deletedAt != 0L && note.habitId !in showHabits) continue
            if (comes(note.id, maxOf(note.deletedAt, state.deleted[note.id] ?: 0L))) showNotes += note.id
        }
        for (habitId in showHabits) {
            for (n in notes) if (n.habitId == habitId && n.deletedAt == habitById.getValue(habitId).deletedAt) showNotes += n.id
        }

        val clears = LinkedHashMap<String, Long>()
        for ((id, at) in cleared) {
            val habit = habitById[id] ?: continue
            if (at > habit.clearedAt) clears[id] = at
        }
        return Plan(hideHabits, hideNotes, showHabits, showNotes, clears, remember)
    }
}
