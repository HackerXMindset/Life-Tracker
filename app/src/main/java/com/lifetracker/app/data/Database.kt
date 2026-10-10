package com.lifetracker.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One logged activity. [endMinute] is null for a single moment (a test result,
 * a milestone). Minutes are counted from midnight, 0..1439.
 */
@Entity(tableName = "entries", indices = [Index("date")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startMinute: Int,
    val endMinute: Int?,
    val category: String,
    val title: String,
    val note: String,
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE date = :date ORDER BY startMinute, id")
    fun forDate(date: String): Flow<List<EntryEntity>>

    @Query("SELECT DISTINCT date FROM entries")
    fun datesWithEntries(): Flow<List<String>>

    @Query("SELECT * FROM entries ORDER BY date, startMinute, id")
    suspend fun all(): List<EntryEntity>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(entry: EntryEntity)
}

@Database(
    entities = [
        EntryEntity::class,
        HabitEntity::class,
        CompletionEntity::class,
        CategoryEntity::class,
        TodoTagEntity::class,
        TodoEntity::class,
        NoteEntity::class,
        ImportedItemEntity::class,
        MealEntity::class,
        FoodGoalEntity::class,
        WaterEntity::class,
        ActivityTypeEntity::class,
        MoneyCategoryEntity::class,
        MoneyItemEntity::class,
        MoneyEntryEntity::class,
        UsageSessionEntity::class,
        UsageAppEntity::class,
        CallEntity::class,
        ChargeSessionEntity::class,
        StepDayEntity::class,
        SleepNightEntity::class,
        PlaceEntity::class,
        PlaceVisitEntity::class,
        TripEntity::class,
        TripPointEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun habitDao(): HabitDao
    abstract fun planDao(): PlanDao
    abstract fun importDao(): ImportDao
    abstract fun foodDao(): FoodDao
    abstract fun activityDao(): ActivityDao
    abstract fun moneyDao(): MoneyDao
    abstract fun usageDao(): UsageDao
    abstract fun callDao(): CallDao
    abstract fun chargeDao(): ChargeDao
    abstract fun stepDao(): StepDao
    abstract fun sleepDao(): SleepDao
    abstract fun placeDao(): PlaceDao
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "life-tracker.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .addCallback(object : RoomDatabase.Callback() {
                        // A brand new database starts with the built-in activities and money categories.
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            DefaultActivities.seed(db)
                            DefaultMoneyCategories.seed(db)
                        }
                    })
                    .build()
                    .also { instance = it }
            }
    }
}

/**
 * Version 1 -> 2: adds the habit, to-do, note and import tables. The existing
 * `entries` table is not touched, so everything you have logged stays as it is.
 * The SQL must match the entities in Tables.kt exactly or Room refuses to open
 * the database.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`icon` TEXT NOT NULL, `category` TEXT NOT NULL, `color` INTEGER NOT NULL, " +
                "`kind` INTEGER NOT NULL, `quantKind` INTEGER NOT NULL, `unitLabel` TEXT NOT NULL, " +
                "`target` REAL NOT NULL, `step` REAL NOT NULL, `anyAmount` INTEGER NOT NULL, " +
                "`startMinute` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                "`startedOn` TEXT NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "`raw` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_completions` (`habitId` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, `count` REAL NOT NULL, `minuteOfDay` INTEGER, " +
                "PRIMARY KEY(`habitId`, `date`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_completions_date` ON `habit_completions` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`color` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todo_tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`color` INTEGER NOT NULL, `kind` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todos` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`done` INTEGER NOT NULL, `date` TEXT, `minutes` INTEGER, `estimate` INTEGER, " +
                "`priority` INTEGER NOT NULL, `project` TEXT NOT NULL, `createdAt` TEXT NOT NULL, " +
                "`doneAt` TEXT, `raw` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_date` ON `todos` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `habitId` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, `type` INTEGER NOT NULL, `text` TEXT NOT NULL, `minutes` INTEGER, " +
                "`createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_date` ON `notes` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `imported_items` (`source` TEXT NOT NULL, " +
                "`sourceId` TEXT NOT NULL, `entryId` INTEGER NOT NULL, PRIMARY KEY(`source`, `sourceId`))",
        )
    }
}

/**
 * Version 2 -> 3: adds the meal, food goal and water tables. Nothing existing is
 * touched. The SQL must match the entities in FoodTables.kt exactly.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `meals` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`minuteOfDay` INTEGER NOT NULL, `mealType` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`grams` REAL NOT NULL, `kcal100` REAL NOT NULL, `carbs100` REAL NOT NULL, " +
                "`fat100` REAL NOT NULL, `protein100` REAL NOT NULL, `source` TEXT NOT NULL, " +
                "`raw` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_date` ON `meals` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_goals` (`date` TEXT NOT NULL, `kcal` REAL NOT NULL, " +
                "`carbs` REAL NOT NULL, `fat` REAL NOT NULL, `protein` REAL NOT NULL, PRIMARY KEY(`date`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `water_log` (`date` TEXT NOT NULL, `ml` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`))",
        )
    }
}

/**
 * Version 3 -> 4: adds the activity types table (names, colours and goals) and fills it
 * with the eight built-in activities, so entries logged earlier keep their colours.
 * The `entries` table is not touched. The SQL must match ActivityTypeEntity exactly.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activity_types` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`color` INTEGER NOT NULL, `goalMinutes` INTEGER NOT NULL, `goalKind` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        DefaultActivities.seed(db)
    }
}

/**
 * Version 4 -> 5: adds the money tables (categories, saved items, entries) and the starter
 * categories. Nothing existing is touched. The SQL must match MoneyTables.kt exactly.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `money_categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`color` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `money_items` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`kind` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                "`type` INTEGER NOT NULL, `chargeDay` INTEGER NOT NULL, `startDate` TEXT NOT NULL, " +
                "`endDate` TEXT NOT NULL, `lastPosted` TEXT NOT NULL, `archived` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `money_entries` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`kind` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, `note` TEXT NOT NULL, `itemId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_money_entries_date` ON `money_entries` (`date`)")
        DefaultMoneyCategories.seed(db)
    }
}

/**
 * Version 5 -> 6: adds the phone usage tables (app sessions and what you told the app about
 * each app). Nothing existing is touched. The SQL must match UsageTables.kt exactly.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `usage_sessions` (`id` TEXT NOT NULL, `pkg` TEXT NOT NULL, " +
                "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_startMs` ON `usage_sessions` (`startMs`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `usage_apps` (`pkg` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                "`activityId` TEXT NOT NULL, `ignored` INTEGER NOT NULL, PRIMARY KEY(`pkg`))",
        )
    }
}

/**
 * Version 6 -> 7: adds the calls table. Nothing existing is touched. The SQL must match
 * CallTables.kt exactly.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `calls` (`id` TEXT NOT NULL, `number` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, " +
                "`durationSec` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_startMs` ON `calls` (`startMs`)")
    }
}

/**
 * Version 7 -> 8: adds the charge_sessions table. Nothing existing is touched. The SQL must match
 * ChargeTables.kt exactly.
 */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `charge_sessions` (`id` TEXT NOT NULL, `startMs` INTEGER NOT NULL, " +
                "`endMs` INTEGER NOT NULL, `startLevel` INTEGER NOT NULL, `endLevel` INTEGER NOT NULL, " +
                "`plugType` INTEGER NOT NULL, `source` TEXT NOT NULL, `samples` INTEGER NOT NULL, " +
                "`currentSamples` INTEGER NOT NULL, `sumMa` INTEGER NOT NULL, `sumMv` INTEGER NOT NULL, " +
                "`ongoing` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_charge_sessions_startMs` ON `charge_sessions` (`startMs`)")
    }
}

/**
 * Version 8 -> 9: adds the step_days and sleep_nights tables. Nothing existing is touched. The SQL
 * must match StepSleepTables.kt exactly.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `step_days` (`date` TEXT NOT NULL, `steps` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, PRIMARY KEY(`date`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sleep_nights` (`date` TEXT NOT NULL, `startMs` INTEGER NOT NULL, " +
                "`endMs` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`date`))",
        )
    }
}

/**
 * Version 9 -> 10: adds the places and place_visits tables. Nothing existing is touched. The SQL
 * must match PlaceTables.kt exactly.
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `places` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`lat` REAL NOT NULL, `lng` REAL NOT NULL, `radiusM` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                "`createdMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `place_visits` (`id` TEXT NOT NULL, `placeId` TEXT NOT NULL, " +
                "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, " +
                "`source` TEXT NOT NULL, `ongoing` INTEGER NOT NULL, `ignored` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_place_visits_startMs` ON `place_visits` (`startMs`)")
    }
}

/**
 * Version 10 -> 11: adds the trips and trip_points tables. Nothing existing is touched. The SQL
 * must match TripTables.kt exactly.
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `startMs` INTEGER NOT NULL, " +
                "`endMs` INTEGER NOT NULL, `fromPlaceId` TEXT NOT NULL, `toPlaceId` TEXT NOT NULL, " +
                "`distanceM` INTEGER NOT NULL, `medianKmh` INTEGER NOT NULL, `topKmh` INTEGER NOT NULL, " +
                "`activity` TEXT NOT NULL, `mode` TEXT NOT NULL, `modeSource` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_startMs` ON `trips` (`startMs`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_points` (`tripId` TEXT NOT NULL, `ms` INTEGER NOT NULL, " +
                "`lat` REAL NOT NULL, `lng` REAL NOT NULL, `acc` REAL NOT NULL, PRIMARY KEY(`tripId`, `ms`))",
        )
    }
}
