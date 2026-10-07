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
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun habitDao(): HabitDao
    abstract fun planDao(): PlanDao
    abstract fun importDao(): ImportDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "life-tracker.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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
