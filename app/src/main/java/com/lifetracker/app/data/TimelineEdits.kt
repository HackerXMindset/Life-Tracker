package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * What you changed about something on the Timeline: its times, its category, or that you hid it.
 *
 * The phone's own records (visits, trips, calls, charging, app use, sleep) are never changed: your change is kept here,
 * next to the record, and the Timeline shows your version on top. Removing the change brings the original back.
 * A value of -1 (or "" for the category) means "not changed".
 */
@Entity(tableName = "timeline_edits", primaryKeys = ["kind", "refId"])
data class TimelineEditEntity(
    val kind: String,
    val refId: String,
    val startMs: Long,
    val endMs: Long,
    val activityId: String,
    val hidden: Boolean,
    val updatedMs: Long,
    /** What the item was, so a hidden one can be listed and brought back. */
    val label: String = "",
) {
    val key: String get() = TimelineEdits.key(kind, refId)
}

@Dao
interface TimelineEditDao {
    @Query("SELECT * FROM timeline_edits")
    fun observeAll(): Flow<List<TimelineEditEntity>>

    @Query("SELECT * FROM timeline_edits ORDER BY kind, refId")
    suspend fun all(): List<TimelineEditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(edit: TimelineEditEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(edits: List<TimelineEditEntity>)

    /** Undoing an edit removes only your change, never the record it sat on. */
    @Query("DELETE FROM timeline_edits WHERE kind = :kind AND refId = :refId")
    suspend fun remove(kind: String, refId: String)
}

/** The arithmetic of edits, free of Android so it can be tested. */
object TimelineEdits {
    const val VISIT = "visit"
    const val TRIP = "trip"
    const val CALL = "call"
    const val CHARGE = "charge"
    const val SLEEP = "sleep"
    const val MEAL = "meal"
    const val APP = "app"

    private const val DAY_MS = 24 * 60 * 60_000L

    fun key(kind: String, refId: String) = "$kind|$refId"

    /** An app block has no id of its own: its app and the time it began. */
    fun appRef(pkg: String, startMs: Long) = "$pkg@$startMs"

    fun edit(kind: String, refId: String, startMs: Long = -1, endMs: Long = -1, activityId: String = "", hidden: Boolean = false, now: Long, label: String = "") =
        TimelineEditEntity(kind, refId, startMs, endMs, activityId, hidden, now, label)

    /** The start and end to show: yours if you changed them, otherwise the originals. */
    fun times(origStartMs: Long, origEndMs: Long, edit: TimelineEditEntity?): Pair<Long, Long> {
        val start = edit?.startMs?.takeIf { it >= 0 } ?: origStartMs
        val end = edit?.endMs?.takeIf { it >= 0 } ?: origEndMs
        return start to maxOf(end, start)
    }

    fun isHidden(edit: TimelineEditEntity?): Boolean = edit?.hidden == true

    /** The category you chose, or null to keep the item's own. */
    fun category(edit: TimelineEditEntity?): String? = edit?.activityId?.takeIf { it.isNotEmpty() }

    /**
     * Turns two clock times picked on the day starting at [dayStartMs] into a start and end. An end at or before the
     * start means the next morning (a night's sleep, a late call).
     */
    fun pickTimes(dayStartMs: Long, startMinute: Int, endMinute: Int): Pair<Long, Long> {
        val start = dayStartMs + startMinute * 60_000L
        var end = dayStartMs + endMinute * 60_000L
        if (end <= start) end += DAY_MS
        return start to end
    }

    /** The minute of the day (0 to 1439) that [ms] falls on, for a day starting at [dayStartMs]. */
    fun minuteOf(ms: Long, dayStartMs: Long): Int = (((ms - dayStartMs) / 60_000L) % 1440).toInt().let { if (it < 0) it + 1440 else it }
}
