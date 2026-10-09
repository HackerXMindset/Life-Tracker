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
 * Table added in database version 7: your phone calls, copied from the phone's call log.
 *
 * Times are milliseconds since 1970 (UTC). [type] is Android's own call type number
 * (1 incoming, 2 outgoing, 3 missed, 4 voicemail, 5 declined, 6 blocked, 7 answered elsewhere).
 * The id is "start|number", so copying the call log twice never doubles a call.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_6_7 in Database.kt.
 */
@Entity(tableName = "calls", indices = [Index("startMs")])
data class CallEntity(
    @PrimaryKey val id: String,
    val number: String,
    val name: String,
    val type: Int,
    val startMs: Long,
    val durationSec: Int,
)

@Dao
interface CallDao {
    /** Calls that started in [from, to). */
    @Query("SELECT * FROM calls WHERE startMs >= :from AND startMs < :to ORDER BY startMs")
    fun callsBetween(from: Long, to: Long): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls ORDER BY startMs")
    suspend fun allCalls(): List<CallEntity>

    @Query("SELECT MAX(startMs) FROM calls")
    suspend fun latestStart(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalls(calls: List<CallEntity>)

    @Query("DELETE FROM calls")
    suspend fun deleteAllCalls()
}
