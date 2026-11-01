package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One row that changed and has not been sent to the second phone yet. It holds only which row (table and key), never the
 * data: when it is time to send, the row is read as it is then, or sent as deleted if it is gone. So a row changed ten times
 * is sent once, and a change can never be sent out of date.
 *
 * Filled by database triggers (see [SyncCapture]). Changing it again moves it to the back of the line (a new [seq]).
 * IMPORTANT: if any column here changes, the database version must go up and a Migration must be added. See MIGRATION_28_29.
 */
@Entity(tableName = "sync_outbox", indices = [Index(value = ["tableName", "rowKey"], unique = true)])
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val tableName: String,
    val rowKey: String,
    val changedMs: Long,
)

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox ORDER BY seq LIMIT :limit")
    suspend fun next(limit: Int): List<SyncOutboxEntity>

    /** Takes out what was sent. A row changed again since it was read has a newer number, so it stays. */
    @Query("DELETE FROM sync_outbox WHERE seq IN (:seqs)")
    suspend fun remove(seqs: List<Long>)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun count(): Int

    @Query("DELETE FROM sync_outbox")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(row: SyncOutboxEntity)
}
