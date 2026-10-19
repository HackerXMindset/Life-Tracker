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
    /** A picture name or emoji (see IconCatalog); empty for none. Added in database version 19. */
    val icon: String = "",
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
        ActivityTypeEntity("Sleep", "Sleep", 0xFF5B5FC7L, 0, 0, false, 0, icon = "moon"),
        ActivityTypeEntity("Food", "Food", 0xFFC28410L, 0, 0, false, 1, icon = "utensils"),
        ActivityTypeEntity("Study", "Study", 0xFF17785AL, 8 * 60, ActivityTypeEntity.GOAL_AT_LEAST, false, 2, icon = "bookOpen"),
        ActivityTypeEntity("Health", "Health", 0xFF2878B4L, 0, 0, false, 3, icon = "heartPulse"),
        ActivityTypeEntity("Routine", "Routine", 0xFF738078L, 0, 0, false, 4, icon = "alarmClock"),
        ActivityTypeEntity("Exercise", "Exercise", 0xFFCC5F22L, 0, 0, false, 5, icon = "dumbbell"),
        ActivityTypeEntity("Screen", "Screen and leisure", 0xFF8A5FC2L, 0, 0, false, 6, icon = "smartphone"),
        ActivityTypeEntity("Event", "Life event", 0xFFB83A3AL, 0, 0, false, 7, icon = "partyPopper"),
    )

    /**
     * More activities offered from database version 20 (and on a new install). They are added only when no activity
     * with the same id or the same name (any capitals) exists, so nothing you made is ever doubled, renamed or removed.
     */
    val extra = listOf(
        ActivityTypeEntity("Work", "Work", 0xFF3F51B5L, 0, 0, false, 100, icon = "briefcase"),
        ActivityTypeEntity("Chores", "Chores", 0xFF8C5A3CL, 0, 0, false, 101, icon = "sprayCan"),
        ActivityTypeEntity("Cooking", "Cooking", 0xFFD9822BL, 0, 0, false, 102, icon = "cookingPot"),
        ActivityTypeEntity("Reading", "Reading", 0xFF2E9E6BL, 0, 0, false, 103, icon = "book"),
        ActivityTypeEntity("Meditation", "Meditation", 0xFF1A9AA8L, 0, 0, false, 104, icon = "flower2"),
        ActivityTypeEntity("Friends", "Friends", 0xFFD6457FL, 0, 0, false, 105, icon = "users"),
        ActivityTypeEntity("Family", "Family", 0xFFE0613CL, 0, 0, false, 106, icon = "heart"),
        ActivityTypeEntity("Shopping", "Shopping", 0xFF6BA32AL, 0, 0, false, 107, icon = "shoppingBag"),
        ActivityTypeEntity("Creative", "Creative", 0xFFB05FC2L, 0, 0, false, 108, icon = "paintbrush"),
        ActivityTypeEntity("Shows", "Movies and shows", 0xFF7A4FB5L, 0, 0, false, 109, icon = "clapperboard"),
        ActivityTypeEntity("Gaming", "Gaming", 0xFF5A6BD8L, 0, 0, false, 110, icon = "gamepad"),
        ActivityTypeEntity("Music", "Music", 0xFFCE4F8AL, 0, 0, false, 111, icon = "music"),
        ActivityTypeEntity("Outdoors", "Outdoors", 0xFF3E9B4FL, 0, 0, false, 112, icon = "treePine"),
        ActivityTypeEntity("SelfCare", "Self-care", 0xFF5BB5A6L, 0, 0, false, 113, icon = "bath"),
        ActivityTypeEntity("Errands", "Errands", 0xFF8E8E93L, 0, 0, false, 114, icon = "listChecks"),
        ActivityTypeEntity("Coding", "Coding", 0xFF2F6FDEL, 0, 0, false, 115, icon = "code"),
        ActivityTypeEntity("Writing", "Writing", 0xFF8B6914L, 0, 0, false, 116, icon = "penLine"),
        ActivityTypeEntity("Rest", "Rest", 0xFF6C7A89L, 0, 0, false, 117, icon = "armchair"),
        ActivityTypeEntity("Travel", "Travel", 0xFF00A5CFL, 0, 0, false, 118, icon = "mapPin"),
        ActivityTypeEntity("Money", "Money", 0xFF2E7D5BL, 0, 0, false, 119, icon = "coins"),
    )

    /** Fills an empty table (a new install). Never overwrites a row that is already there. */
    fun seed(db: SupportSQLiteDatabase) {
        for (t in list) {
            db.execSQL(
                "INSERT OR IGNORE INTO `activity_types` " +
                    "(`id`, `name`, `color`, `goalMinutes`, `goalKind`, `archived`, `sortOrder`, `icon`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(t.id, t.name, t.color, t.goalMinutes, t.goalKind, if (t.archived) 1 else 0, t.sortOrder, t.icon),
            )
        }
        addExtra(db)
    }

    /** Gives the eight built-in activities their icons (only where none was set) and adds the [extra] ones that are missing. */
    fun upgrade(db: SupportSQLiteDatabase) {
        for (t in list) {
            db.execSQL("UPDATE `activity_types` SET `icon` = ? WHERE `id` = ? AND `icon` = ''", arrayOf<Any?>(t.icon, t.id))
        }
        addExtra(db)
    }

    private fun addExtra(db: SupportSQLiteDatabase) {
        for (t in extra) {
            db.execSQL(
                "INSERT INTO `activity_types` (`id`, `name`, `color`, `goalMinutes`, `goalKind`, `archived`, `sortOrder`, `icon`) " +
                    "SELECT ?, ?, ?, ?, ?, ?, ?, ? WHERE NOT EXISTS " +
                    "(SELECT 1 FROM `activity_types` WHERE `id` = ? OR lower(`name`) = lower(?))",
                arrayOf<Any?>(t.id, t.name, t.color, t.goalMinutes, t.goalKind, 0, t.sortOrder, t.icon, t.id, t.name),
            )
        }
    }
}
