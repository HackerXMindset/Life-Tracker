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
 * Tables added in database version 10: the places you named and the times you were at them.
 *
 * A place is a circle on the map: a centre ([lat], [lng]) and a radius in metres. [kind] is one
 * of the ids in PlaceKinds ("home", "study", "gym"...). A place is never deleted; [archived]
 * only stops the app watching it, so old visits keep their name.
 *
 * A visit is one stay. [placeId] is "" for a stop at a spot you have not named yet (those wait in
 * the review list). Times are milliseconds since 1970 (UTC). [source] is "geofence" (Android told
 * the app you arrived and left), "stop" (the phone stayed still for a while there) or "manual".
 * [ongoing] is true while you are still there. [ignored] hides an unnamed stop you said is not a place.
 * For an unnamed stop [lat] and [lng] are where the phone was; for a named place they are the place's centre.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_9_10 in Database.kt.
 */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val archived: Boolean,
    val createdMs: Long,
)

@Entity(tableName = "place_visits", indices = [Index("startMs")])
data class PlaceVisitEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val startMs: Long,
    val endMs: Long,
    val lat: Double,
    val lng: Double,
    val source: String,
    val ongoing: Boolean,
    val ignored: Boolean,
)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY archived, name COLLATE NOCASE")
    fun observePlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY createdMs")
    suspend fun allPlaces(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE archived = 0 ORDER BY createdMs")
    suspend fun activePlaces(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun place(id: String): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlace(place: PlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaces(places: List<PlaceEntity>)

    /** Visits that overlap [from, to), newest first. */
    @Query("SELECT * FROM place_visits WHERE (endMs >= :from OR ongoing = 1) AND startMs < :to ORDER BY startMs DESC")
    fun visitsBetween(from: Long, to: Long): Flow<List<PlaceVisitEntity>>

    /** Same as [visitsBetween] but read once. */
    @Query("SELECT * FROM place_visits WHERE (endMs >= :from OR ongoing = 1) AND startMs <= :to ORDER BY startMs")
    suspend fun visitsAround(from: Long, to: Long): List<PlaceVisitEntity>

    @Query("SELECT * FROM place_visits ORDER BY startMs")
    suspend fun allVisits(): List<PlaceVisitEntity>

    @Query("SELECT * FROM place_visits WHERE ongoing = 1 ORDER BY startMs DESC")
    fun observeOpen(): Flow<List<PlaceVisitEntity>>

    @Query("SELECT * FROM place_visits WHERE ongoing = 1 ORDER BY startMs DESC")
    suspend fun openVisits(): List<PlaceVisitEntity>

    /** Stops at spots you have not named, waiting for you to name them or say they are not places. */
    @Query("SELECT * FROM place_visits WHERE placeId = '' AND ignored = 0 AND ongoing = 0 ORDER BY startMs DESC")
    fun observeUnknown(): Flow<List<PlaceVisitEntity>>

    @Query("SELECT * FROM place_visits WHERE placeId = '' AND ignored = 0 AND ongoing = 0 ORDER BY startMs")
    suspend fun unknownOnce(): List<PlaceVisitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVisits(visits: List<PlaceVisitEntity>)

    /** Adds visits that are not there yet and leaves existing ones (and what you decided about them) alone. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingVisits(visits: List<PlaceVisitEntity>)

    @Query("UPDATE place_visits SET placeId = :placeId, lat = :lat, lng = :lng WHERE id IN (:ids)")
    suspend fun assign(ids: List<String>, placeId: String, lat: Double, lng: Double)

    @Query("UPDATE place_visits SET ignored = 1 WHERE id IN (:ids)")
    suspend fun ignore(ids: List<String>)
}
