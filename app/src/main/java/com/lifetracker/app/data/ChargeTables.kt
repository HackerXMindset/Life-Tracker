package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Table added in database version 8: the times your phone was on a charger.
 *
 * A session starts when the phone is plugged in and ends when it is unplugged. Times are
 * milliseconds since 1970 (UTC). [plugType] is Android's own number (1 wall charger, 2 USB,
 * 4 wireless, 8 dock). [source] is what YOU said you charged with ("wall", "powerbank",
 * "laptop", "other") or "" if you never said. The sums let the screen work out the average
 * charging speed without storing every reading. [ongoing] is true while the phone is still plugged in.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_7_8 in Database.kt.
 */
@Entity(tableName = "charge_sessions", indices = [Index("startMs")])
data class ChargeSessionEntity(
    @PrimaryKey val id: String,
    val startMs: Long,
    val endMs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val plugType: Int,
    val source: String,
    val samples: Int,
    val currentSamples: Int,
    val sumMa: Long,
    val sumMv: Long,
    val ongoing: Boolean,
)

@Dao
interface ChargeDao {
    /** Sessions that overlap [from, to), newest first. */
    @Query("SELECT * FROM charge_sessions WHERE endMs >= :from AND startMs < :to ORDER BY startMs DESC")
    fun sessionsBetween(from: Long, to: Long): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions ORDER BY startMs DESC")
    fun observeAll(): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions ORDER BY startMs")
    suspend fun allSessions(): List<ChargeSessionEntity>

    /** The session that has not been closed yet, if the phone was plugged in the last time we looked. */
    @Query("SELECT * FROM charge_sessions WHERE ongoing = 1 ORDER BY startMs DESC LIMIT 1")
    suspend fun openSession(): ChargeSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<ChargeSessionEntity>)

    @Query("UPDATE charge_sessions SET source = :source WHERE id = :id")
    suspend fun setSource(id: String, source: String)
}
