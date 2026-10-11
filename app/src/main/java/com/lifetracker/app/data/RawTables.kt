package com.lifetracker.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/*
 * Table added in database version 12: every position reading the app takes.
 *
 * Visits and trips are worked out from these readings, live and again later (see PlaceCleanup), so the
 * readings are kept for good: if a rule improves, the past can be worked out again. [ms] is the time of the
 * reading in milliseconds since 1970 (UTC), [acc] the accuracy radius in metres. Only readings accurate to
 * 150 m or better are kept.
 *
 * Database version 13 added [speed]: how fast the phone itself said it was going, in metres per second, or -1 if it
 * did not say. It lets a position jump the phone did not feel be told apart from real movement.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_11_12 and MIGRATION_12_13 in Database.kt.
 */
@Entity(tableName = "raw_fixes")
data class RawFixEntity(
    @PrimaryKey val ms: Long,
    val lat: Double,
    val lng: Double,
    val acc: Float,
    @ColumnInfo(defaultValue = "-1") val speed: Float = -1f,
)

@Dao
interface RawFixDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(fix: RawFixEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(fixes: List<RawFixEntity>)

    /** Readings from [from] up to (not including) [to], oldest first. */
    @Query("SELECT * FROM raw_fixes WHERE ms >= :from AND ms < :to ORDER BY ms")
    suspend fun between(from: Long, to: Long): List<RawFixEntity>

    @Query("SELECT * FROM raw_fixes ORDER BY ms")
    suspend fun all(): List<RawFixEntity>

    @Query("SELECT COUNT(*) FROM raw_fixes")
    suspend fun count(): Int

    @Query("SELECT MIN(ms) FROM raw_fixes")
    suspend fun firstMs(): Long?
}
