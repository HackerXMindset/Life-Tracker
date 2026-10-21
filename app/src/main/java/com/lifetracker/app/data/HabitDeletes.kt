package com.lifetracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the app remembers about deleting and restoring (Habits H6), free of Android so it can be tested.
 *
 * A deleted habit or note is hidden and kept. Streak sends and reads "deleted" as a list of id and time; a restore or any
 * later change removes the id from that list again, which Streak reads as "bring it back".
 */
object HabitTombstones {
    /** What changed here and what was deleted here, each as id to time in milliseconds. */
    data class State(val changes: Map<String, Long>, val deleted: Map<String, Long>)

    /** Streak forgets deletions after 120 days; so does this app. */
    const val KEEP_DAYS = 120L

    /** [ids] changed at [now]: the change time only moves forward, and a deleted id is no longer deleted. */
    fun changed(state: State, ids: Collection<String>, now: Long): State {
        val changes = LinkedHashMap(state.changes)
        val deleted = LinkedHashMap(state.deleted)
        for (id in ids) {
            if (id.isBlank()) continue
            changes[id] = maxOf(changes[id] ?: 0L, now)
            deleted.remove(id)
        }
        return State(changes, deleted)
    }

    /** [ids] deleted at [now]: they are no longer "changed" (Streak does the same), and are listed as deleted. */
    fun deleted(state: State, ids: Collection<String>, now: Long): State {
        val changes = LinkedHashMap(state.changes)
        val deleted = LinkedHashMap(state.deleted)
        for (id in ids) {
            if (id.isBlank()) continue
            deleted[id] = maxOf(deleted[id] ?: 0L, now)
            changes.remove(id)
        }
        return State(changes, deleted)
    }

    fun forgetOld(deleted: Map<String, Long>, now: Long): Map<String, Long> {
        val limit = now - KEEP_DAYS * 24 * 60 * 60 * 1000
        return deleted.filterValues { it >= limit }
    }

    /**
     * Whether a deletion Streak sent (at time [at]) should be applied here. Not if this app already knows about it
     * ([knownDeletedAt] is as late or later), and not if the item was changed here after the deletion ([editedAt]).
     */
    fun shouldApply(at: Long, editedAt: Long?, knownDeletedAt: Long?): Boolean =
        (knownDeletedAt == null || knownDeletedAt < at) && (editedAt == null || editedAt <= at)
}

/** "Clear all progress": only days after the day it was used count. The days stay stored. */
object HabitClears {
    /** The last day that no longer counts, or null when progress was never cleared. */
    fun lastClearedDay(clearedAt: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        if (clearedAt <= 0L) null else Instant.ofEpochMilli(clearedAt).atZone(zone).toLocalDate()

    fun counts(clearedAt: Long, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val last = lastClearedDay(clearedAt, zone) ?: return true
        return day.isAfter(last)
    }

    /** The days of a habit that count (a date text such as "2026-10-05" that cannot be read is kept). */
    fun visible(completions: List<CompletionEntity>, clearedAt: Long, zone: ZoneId = ZoneId.systemDefault()): List<CompletionEntity> {
        if (clearedAt <= 0L) return completions
        return completions.filter { c ->
            val day = runCatching { LocalDate.parse(c.date) }.getOrNull()
            day == null || counts(clearedAt, day, zone)
        }
    }
}
