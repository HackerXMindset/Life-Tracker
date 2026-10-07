package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Added in database version 4: the kinds of activity you log on the Timeline,
 * and the daily goal (if any) for each.
 *
 * An entry stores its activity's `id` in its `category` column, so the entries
 * table never had to change. Built-in activities keep their old ids ("Study",
 * "Sleep" and so on), which is why entries logged before this step still match.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_3_4 in Database.kt.
 */

/**
 * One kind of activity. [color] is an ARGB value. [goalMinutes] is 0 for no goal;
 * [goalKind] says whether the goal is a minimum to reach or a limit to stay under.
 * Archived activities no longer appear when logging, but old entries keep their look.
 */
@Entity(tableName = "activity_types")
data class ActivityTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val goalMinutes: Int,
    val goalKind: Int,
    val archived: Boolean,
    val sortOrder: Int,
) {
    companion object {
        const val GOAL_AT_LEAST = 0
        const val GOAL_AT_MOST = 1
    }
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_types ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ActivityTypeEntity>>

    @Query("SELECT * FROM activity_types ORDER BY sortOrder, name")
    suspend fun all(): List<ActivityTypeEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM activity_types")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(type: ActivityTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(types: List<ActivityTypeEntity>)

    @Query("DELETE FROM activity_types")
    suspend fun deleteAll()
}

/** The eight activities the app has had since Step 2, with the old 8 hour study goal kept as a goal on Study. */
object DefaultActivities {
    val list: List<ActivityTypeEntity> = listOf(
        ActivityTypeEntity("Sleep", "Sleep", 0xFF5B5FC7L, 0, 0, false, 0),
        ActivityTypeEntity("Food", "Food", 0xFFC28410L, 0, 0, false, 1),
        ActivityTypeEntity("Study", "Study", 0xFF17785AL, 8 * 60, ActivityTypeEntity.GOAL_AT_LEAST, false, 2),
        ActivityTypeEntity("Health", "Health", 0xFF2878B4L, 0, 0, false, 3),
        ActivityTypeEntity("Routine", "Routine", 0xFF738078L, 0, 0, false, 4),
        ActivityTypeEntity("Exercise", "Exercise", 0xFFCC5F22L, 0, 0, false, 5),
        ActivityTypeEntity("Screen", "Screen and leisure", 0xFF8A5FC2L, 0, 0, false, 6),
        ActivityTypeEntity("Event", "Life event", 0xFFB83A3AL, 0, 0, false, 7),
    )

    /** Fills an empty table. Never overwrites a row that is already there. */
    fun seed(db: SupportSQLiteDatabase) {
        for (t in list) {
            db.execSQL(
                "INSERT OR IGNORE INTO `activity_types` " +
                    "(`id`, `name`, `color`, `goalMinutes`, `goalKind`, `archived`, `sortOrder`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(t.id, t.name, t.color, t.goalMinutes, t.goalKind, if (t.archived) 1 else 0, t.sortOrder),
            )
        }
    }
}
