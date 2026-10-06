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

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Delete
    suspend fun delete(entry: EntryEntity)
}

@Database(entities = [EntryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "life-tracker.db",
                ).build().also { instance = it }
            }
    }
}
