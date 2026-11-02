package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A moment that changed your life (Step 14): moving city, starting exam prep. [date] is "yyyy-MM-dd". [mark] is an emoji or short
 * sign shown beside the title, or "".
 * IMPORTANT: if any column here changes, the database version must go up and a Migration must be added. See MIGRATION_30_31.
 */
@Entity(tableName = "life_events")
data class LifeEventEntity(
    @PrimaryKey val id: String,
    val date: String,
    val title: String,
    val note: String,
    val mark: String,
    val createdMs: Long,
)

@Dao
interface LifeEventDao {
    @Query("SELECT * FROM life_events ORDER BY date DESC, createdMs DESC")
    fun observeEvents(): Flow<List<LifeEventEntity>>

    @Query("SELECT * FROM life_events WHERE date = :date ORDER BY createdMs")
    fun observeOn(date: String): Flow<List<LifeEventEntity>>

    @Query("SELECT * FROM life_events ORDER BY date, createdMs")
    suspend fun all(): List<LifeEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: LifeEventEntity)

    /** Restoring a backup only adds the events that are missing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(events: List<LifeEventEntity>)

    @Query("DELETE FROM life_events WHERE id = :id")
    suspend fun delete(id: String)
}
