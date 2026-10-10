package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Tables added in database version 11: your trips.
 *
 * A trip is the way between two stays: it starts when you leave a place or stop and ends when you
 * arrive at the next one. Times are milliseconds since 1970 (UTC). [fromPlaceId] and [toPlaceId] are
 * the named places at each end, or "" for a spot you have not named. [distanceM] is the length of the
 * route worked out from the position readings in trip_points, [medianKmh] the usual speed and [topKmh]
 * the top speed (ignoring glitches). [activity] is how long Android said you were walking, cycling or
 * in a vehicle, as "WALKING=60000,IN_VEHICLE=540000" (milliseconds). [mode] is how you travelled
 * ("walk", "cycle", "bike"...), [modeSource] is "auto" (the app's guess) or "you" (you set it).
 *
 * trip_points are the position readings of a trip, one row each. They are never deleted.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_10_11 in Database.kt.
 */
@Entity(tableName = "trips", indices = [Index("startMs")])
data class TripEntity(
    @PrimaryKey val id: String,
    val startMs: Long,
    val endMs: Long,
    val fromPlaceId: String,
    val toPlaceId: String,
    val distanceM: Int,
    val medianKmh: Int,
    val topKmh: Int,
    val activity: String,
    val mode: String,
    val modeSource: String,
)

@Entity(tableName = "trip_points", primaryKeys = ["tripId", "ms"])
data class TripPointEntity(
    val tripId: String,
    val ms: Long,
    val lat: Double,
    val lng: Double,
    val acc: Float,
)

@Dao
interface TripDao {
    /** Trips that overlap [from, to), newest first. */
    @Query("SELECT * FROM trips WHERE endMs >= :from AND startMs < :to ORDER BY startMs DESC")
    fun tripsBetween(from: Long, to: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY startMs")
    suspend fun allTrips(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE modeSource = 'you' ORDER BY startMs DESC LIMIT 500")
    suspend fun corrected(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE modeSource <> 'you' ORDER BY startMs DESC LIMIT 300")
    suspend fun guessed(): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingTrips(trips: List<TripEntity>)

    @Query("UPDATE trips SET mode = :mode, modeSource = :source WHERE id = :id")
    suspend fun setMode(id: String, mode: String, source: String)

    @Query("SELECT * FROM trip_points ORDER BY tripId, ms")
    suspend fun allPoints(): List<TripPointEntity>

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY ms")
    suspend fun points(tripId: String): List<TripPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPoints(points: List<TripPointEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingPoints(points: List<TripPointEntity>)

    /** Only for readings of a trip that turned out not to be one (a few jittery readings at the same place). */
    @Query("DELETE FROM trip_points WHERE tripId = :tripId")
    suspend fun dropPoints(tripId: String)
}
