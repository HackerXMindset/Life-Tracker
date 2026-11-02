package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One place goal (Goals, E9): a measure from your places and trips, over a period, to reach or to stay under. The words for
 * [measure], [period] and [direction] are in [GoalMeasures], [GoalPeriods] and [GoalDirections].
 * IMPORTANT: if any column here changes, the database version must go up and a Migration must be added. See MIGRATION_29_30.
 */
@Entity(tableName = "place_goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val measure: String,
    /** One place, or "" for a kind of place / any named place. */
    val placeId: String,
    /** A kind of place, or "" for one place / any named place. For "time out" it is the kind that counts as home. */
    val category: String,
    /** A way of travelling for the trip measures, or "" for any. */
    val mode: String,
    val period: String,
    /** The number of days for a "last N days" period. */
    val days: Int,
    val direction: String,
    /** Hours, kilometres or a count, depending on the measure. */
    val target: Double,
    val enabled: Boolean,
    val createdMs: Long,
    val sortOrder: Int,
)

@Dao
interface GoalDao {
    @Query("SELECT * FROM place_goals ORDER BY sortOrder, createdMs")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM place_goals ORDER BY sortOrder, createdMs")
    suspend fun all(): List<GoalEntity>

    @Query("SELECT * FROM place_goals WHERE id = :id")
    suspend fun get(id: String): GoalEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM place_goals")
    suspend fun maxOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    /** Restoring a backup only adds the goals that are missing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(goals: List<GoalEntity>)

    @Query("DELETE FROM place_goals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE place_goals SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
