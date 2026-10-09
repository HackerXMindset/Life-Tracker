package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Tables added in database version 9: your steps and your sleep.
 *
 * Steps are stored as one number per day ([date] is "2026-10-09"). [source] says where
 * the number came from: "healthconnect" or "sensor" (the phone's own step counter).
 *
 * Sleep is one row per night, filed under the date you WOKE UP. Times are milliseconds
 * since 1970 (UTC). [source] is "estimate" (worked out from when you stopped using the phone),
 * "healthconnect", "manual" (you edited it), or "skipped" (you said it was not sleep).
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_8_9 in Database.kt.
 */
@Entity(tableName = "step_days")
data class StepDayEntity(
    @PrimaryKey val date: String,
    val steps: Int,
    val source: String,
)

@Entity(tableName = "sleep_nights")
data class SleepNightEntity(
    @PrimaryKey val date: String,
    val startMs: Long,
    val endMs: Long,
    val source: String,
)

@Dao
interface StepDao {
    /** Days from [from] to [to], both included, as ISO dates. Newest first. */
    @Query("SELECT * FROM step_days WHERE date >= :from AND date <= :to ORDER BY date DESC")
    fun daysBetween(from: String, to: String): Flow<List<StepDayEntity>>

    @Query("SELECT * FROM step_days WHERE date = :date")
    fun observeDay(date: String): Flow<StepDayEntity?>

    @Query("SELECT * FROM step_days WHERE date = :date")
    suspend fun day(date: String): StepDayEntity?

    @Query("SELECT * FROM step_days ORDER BY date")
    suspend fun allDays(): List<StepDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<StepDayEntity>)
}

@Dao
interface SleepDao {
    /** Nights whose wake-up date is from [from] to [to], both included. Newest first. */
    @Query("SELECT * FROM sleep_nights WHERE date >= :from AND date <= :to ORDER BY date DESC")
    fun nightsBetween(from: String, to: String): Flow<List<SleepNightEntity>>

    @Query("SELECT * FROM sleep_nights WHERE date = :date")
    fun observeNight(date: String): Flow<SleepNightEntity?>

    @Query("SELECT * FROM sleep_nights WHERE date = :date")
    suspend fun night(date: String): SleepNightEntity?

    @Query("SELECT * FROM sleep_nights ORDER BY date")
    suspend fun allNights(): List<SleepNightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(night: SleepNightEntity)

    /** Adds nights that are not there yet and leaves existing ones (and your edits) alone. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(nights: List<SleepNightEntity>)
}
