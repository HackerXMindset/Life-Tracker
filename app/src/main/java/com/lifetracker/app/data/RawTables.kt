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
 * Database version 27 (the Places and trips remake) added [bearing], [altitude], [battery], [steps], [source] and [profileId] and
 * copied the old trip readings in, so this is now the one store of readings. Rows from before keep "not known" in the new columns.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_11_12, MIGRATION_12_13 and MIGRATION_26_27 in Database.kt.
 */
@Entity(tableName = "raw_fixes")
data class RawFixEntity(
    @PrimaryKey val ms: Long,
    val lat: Double,
    val lng: Double,
    val acc: Float,
    @ColumnInfo(defaultValue = "-1") val speed: Float = -1f,
    /** Direction of travel in degrees (0 to 360), or -1 when the phone did not say. Added in database version 27. */
    val bearing: Float = -1f,
    /** Height above sea level in metres, or null when not known. Added in database version 27. */
    val altitude: Double? = null,
    /** Battery percent when the reading was taken, or -1 when not known. Added in database version 27. */
    val battery: Int = -1,
    /** The phone's step counter at this reading (steps since it last started), or -1 when not known. Added in database version 27. */
    val steps: Long = -1L,
    /** Where the reading came from, one of [FixSource], or an empty text for readings from before database version 27. */
    val source: String = "",
    /** The id of the tracking profile that was in force when it was taken, or an empty text for none. */
    val profileId: String = "",
)

/** The places a reading can come from. Plain words so a backup file stays readable. */
object FixSource {
    const val GPS = "gps"
    const val NETWORK = "network"
    /** The phone combined its sensors (what Google Play services normally reports). */
    const val FUSED = "fused"
    /** A synthetic reading put at the centre of a place when you leave it, so a trip starts cleanly. */
    const val ANCHOR = "anchor"
    /** A single reading taken now and then while recording is paused or the continuous readings are not running. */
    const val HEARTBEAT = "heartbeat"
    const val IMPORTED = "imported"
    /** Copied from the old trip readings when database version 27 was made. */
    const val OLD_TRIP = "old-trip"
}

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

    /** The newest reading, or null when there is none. */
    @Query("SELECT * FROM raw_fixes ORDER BY ms DESC LIMIT 1")
    suspend fun latest(): RawFixEntity?

    @Query("SELECT COUNT(*) FROM raw_fixes")
    suspend fun count(): Int

    @Query("SELECT MIN(ms) FROM raw_fixes")
    suspend fun firstMs(): Long?
}
