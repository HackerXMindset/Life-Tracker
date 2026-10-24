package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Tables added in database version 27 (the Places and trips remake).
 *
 * Nothing here ever changes a position reading. Edits to trips and notes on readings are kept beside the readings,
 * so the original is always there and any edit can be undone.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a Migration must be added.
 * See MIGRATION_26_27 in Database.kt; its SQL must match these entities exactly.
 */

/** What a [TripEditEntity] did. */
object TripEditKind {
    /** Two trips made one: [TripEditEntity.atMs] is the end of the earlier trip, [TripEditEntity.otherMs] the start of the later one. */
    const val MERGE = "merge"
    /** One trip made two: [TripEditEntity.atMs] is the time of the reading where it was cut. */
    const val SPLIT = "split"
}

/**
 * A merge or a split of trips, stored by boundary times so it still holds if the trips are worked out again.
 * [undoneMs] is 0 while the edit is in force and the time you undid it otherwise; the row is never removed.
 */
@Entity(tableName = "trip_edits")
data class TripEditEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val atMs: Long,
    val otherMs: Long,
    val createdMs: Long,
    val undoneMs: Long = 0L,
)

/** Your note on one position reading, found by its time. A cleared note stays as an empty text. */
@Entity(tableName = "point_notes")
data class PointNoteEntity(
    @PrimaryKey val ms: Long,
    val note: String,
    val updatedMs: Long,
)

/** What switches a tracking profile on. */
object ProfileCondition {
    const val CHARGING = "charging"
    const val CAR_MODE = "car-mode"
    /** [TrackingProfileEntity.conditionText] is the device's address. */
    const val BLUETOOTH = "bluetooth"
    const val WIFI = "wifi"
    /** [TrackingProfileEntity.conditionText] is the network name. */
    const val WIFI_NAMED = "wifi-named"
    /** [TrackingProfileEntity.conditionNumber] is the speed in km/h. */
    const val SPEED_ABOVE = "speed-above"
    const val SPEED_BELOW = "speed-below"
    const val STATIONARY = "stationary"

    val all = listOf(CHARGING, CAR_MODE, BLUETOOTH, WIFI, WIFI_NAMED, SPEED_ABOVE, SPEED_BELOW, STATIONARY)
}

/**
 * A tracking rule: while its condition holds, recording uses this profile's interval, movement limit and accuracy instead of the
 * normal settings. The highest [priority] that holds wins. [minMoveM] 0 means any movement; [accuracyMode] empty means the normal one.
 * A hidden profile ([deletedAt] not 0) is kept and can be shown again.
 */
@Entity(tableName = "tracking_profiles")
data class TrackingProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val condition: String,
    val conditionText: String = "",
    val conditionNumber: Double = 0.0,
    val intervalSec: Int,
    val minMoveM: Int = 0,
    val accuracyMode: String = "",
    val priority: Int = 0,
    val activateDelaySec: Int = 0,
    val deactivateDelaySec: Int = 0,
    val enabled: Boolean = true,
    val deletedAt: Long = 0L,
    val createdMs: Long,
)

@Dao
interface TripEditDao {
    @Query("SELECT * FROM trip_edits ORDER BY createdMs")
    suspend fun all(): List<TripEditEntity>

    /** The edits in force, oldest first (the order they were made in matters when they are applied). */
    @Query("SELECT * FROM trip_edits WHERE undoneMs = 0 ORDER BY createdMs, id")
    suspend fun active(): List<TripEditEntity>

    @Query("SELECT * FROM trip_edits WHERE undoneMs = 0 ORDER BY createdMs, id")
    fun observeActive(): Flow<List<TripEditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(edit: TripEditEntity)

    /** Adds edits that are not there yet and leaves existing ones (including one you undid since) alone. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(edits: List<TripEditEntity>)

    @Query("UPDATE trip_edits SET undoneMs = :now WHERE id = :id")
    suspend fun undo(id: String, now: Long)
}

@Dao
interface PointNoteDao {
    @Query("SELECT * FROM point_notes ORDER BY ms")
    suspend fun all(): List<PointNoteEntity>

    @Query("SELECT * FROM point_notes WHERE ms >= :from AND ms < :to AND note <> '' ORDER BY ms")
    suspend fun between(from: Long, to: Long): List<PointNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: PointNoteEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(notes: List<PointNoteEntity>)
}

@Dao
interface TrackingProfileDao {
    /** Profiles that are not hidden, highest priority first. */
    @Query("SELECT * FROM tracking_profiles WHERE deletedAt = 0 ORDER BY priority DESC, name COLLATE NOCASE")
    fun observe(): Flow<List<TrackingProfileEntity>>

    /** Every profile, hidden ones last, for the profiles page. */
    @Query("SELECT * FROM tracking_profiles ORDER BY (deletedAt <> 0), priority DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<TrackingProfileEntity>>

    @Query("SELECT * FROM tracking_profiles WHERE deletedAt = 0 AND enabled = 1 ORDER BY priority DESC, name COLLATE NOCASE")
    suspend fun enabled(): List<TrackingProfileEntity>

    @Query("SELECT * FROM tracking_profiles ORDER BY createdMs")
    suspend fun all(): List<TrackingProfileEntity>

    @Query("SELECT * FROM tracking_profiles WHERE id = :id")
    suspend fun profile(id: String): TrackingProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: TrackingProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(profiles: List<TrackingProfileEntity>)
}
