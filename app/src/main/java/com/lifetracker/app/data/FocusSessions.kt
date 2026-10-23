package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** One kept stretch of focus (Habits H13). Never deleted. [counted] is set once its minutes were added to a time habit. */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val targetMinutes: Int,
    val seconds: Int,
    val completed: Boolean,
    val startedAtMs: Long,
    val counted: Boolean = false,
    val label: String = "",
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMs DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMs")
    suspend fun all(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun get(id: String): FocusSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: FocusSessionEntity)

    /** Adds the sessions that are not here yet; one already here is left as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(sessions: List<FocusSessionEntity>)

    @Query("SELECT DISTINCT label FROM focus_sessions WHERE habitId = :habitId AND label != '' ORDER BY startedAtMs DESC")
    suspend fun labelsOf(habitId: String): List<String>
}

fun FocusSessionData.toEntity(counted: Boolean = false) =
    FocusSessionEntity(id, habitId, targetMinutes, seconds, completed, startedAtMillis, counted, label)

fun FocusSessionEntity.toData() = FocusSessionData(id, habitId, targetMinutes, seconds, completed, startedAtMs, label)

/** What a finished session does for its habit (Habits H13). Plain logic, so it can be tested. */
object FocusCredit {
    sealed class Credit {
        object None : Credit()
        /** Tick a yes/no habit once for today, with this amount (its goal for a day). */
        data class Tick(val amount: Double) : Credit()
        /** Add these minutes to a habit that counts minutes, on the day the session ended. */
        data class Minutes(val minutes: Double) : Credit()
    }

    const val QUANT_TIME = 3

    fun isTimeHabit(habit: HabitEntity): Boolean = habit.kind == HabitStats.KIND_COUNTED && habit.quantKind == QUANT_TIME

    /**
     * Nothing for no habit, a hidden or deleted one, an Avoid habit, a checklist or another counted habit. A habit that
     * counts minutes gets the session's minutes once ([alreadyCounted] false). A yes/no habit is ticked once, when the
     * session was completed and the day is not done already.
     */
    fun decide(habit: HabitEntity?, session: FocusSessionData, alreadyCounted: Boolean, doneToday: Boolean): Credit {
        if (habit == null || habit.deletedAt != 0L || habit.archived) return Credit.None
        if (habit.kind == HabitStats.KIND_RELAPSE) return Credit.None
        if (isTimeHabit(habit)) return if (alreadyCounted || session.seconds < 1) Credit.None else Credit.Minutes(session.seconds / 60.0)
        if (habit.kind != HabitStats.KIND_YES_NO || HabitSteps.isChecklist(habit)) return Credit.None
        if (!session.completed || doneToday) return Credit.None
        return Credit.Tick(maxOf(1.0, habit.target))
    }

    /** The day a stretch is counted on: the day it ended. */
    fun dayOf(session: FocusSessionData, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(session.startedAtMillis + maxOf(0, session.seconds - 1) * 1000L).atZone(zone).toLocalDate()

    /** A yes/no habit set to "Only a focus session can complete it" (Streak's `focusOnly`): not for checklists or Avoid habits. */
    fun needsFocusSession(habit: HabitEntity): Boolean =
        habit.kind == HabitStats.KIND_YES_NO && !HabitSteps.isChecklist(habit) &&
            runCatching { org.json.JSONObject(habit.raw).optBoolean("focusOnly", false) }.getOrDefault(false)

    /** The minutes a habit remembers for its focus set-up (Streak's `focusMinutes`, 25 when not set). */
    fun minutesOf(habit: HabitEntity): Int =
        runCatching { org.json.JSONObject(habit.raw).optInt("focusMinutes", 25) }.getOrDefault(25).coerceIn(0, 600)

    fun breakOf(habit: HabitEntity): Int =
        runCatching { org.json.JSONObject(habit.raw).optInt("focusBreakMinutes", 0) }.getOrDefault(0).coerceIn(0, 120)

    /** The habit's record with its remembered focus set-up changed. */
    fun withSetup(habit: HabitEntity, minutes: Int, breakMinutes: Int): HabitEntity {
        val o = runCatching { org.json.JSONObject(habit.raw) }.getOrDefault(org.json.JSONObject())
        o.put("focusMinutes", minutes)
        o.put("focusBreakMinutes", breakMinutes)
        return habit.copy(raw = o.toString())
    }

    fun withFocusOnly(raw: String, on: Boolean): String {
        val o = runCatching { org.json.JSONObject(raw) }.getOrDefault(org.json.JSONObject())
        o.put("focusOnly", on)
        return o.toString()
    }

    fun startedAt(session: FocusSessionEntity, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(session.startedAtMs), zone)
}
