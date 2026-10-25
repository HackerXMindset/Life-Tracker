package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Insert
import kotlinx.coroutines.flow.Flow

/*
 * Tables added in database version 28: the maps.
 *
 * map_styles are the map styles you added yourself ([address] is a style address or a tile address, possibly with {key} in it,
 * and [key] is your key for it). A style you hide is kept; it only leaves the list.
 *
 * map_areas are the areas of map kept on the phone. An area belongs to one style ([styleId] and the address [styleUrl] it was
 * downloaded from), covers a box ([west], [south], [east], [north]) from [minZoom] to [maxZoom], and says how it was added
 * ([source]: by hand, for a place, or where you went). [sig] is what a place's area was made from, so it is made again when the
 * place changes. [state] is queued, downloading, done, failed or removed. [tiles] and [bytes] are what is downloaded,
 * [totalTiles] what the whole area needs once known. [regionId] is the number the map library gave the downloaded region, -1 for
 * none. A removed area keeps its row; only its tiles are deleted.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a Migration must be added. See MIGRATION_27_28.
 */
@Entity(tableName = "map_styles")
data class MapStyleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val key: String,
    val hidden: Boolean,
    val createdMs: Long,
)

@Entity(tableName = "map_areas", indices = [Index("styleId")])
data class MapAreaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val styleId: String,
    val styleUrl: String,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val source: String,
    val placeId: String,
    val sig: String,
    val state: String,
    val createdMs: Long,
    val doneMs: Long,
    val tiles: Long,
    val bytes: Long,
    val totalTiles: Long,
    val regionId: Long,
    val note: String,
) {
    val bounds: Bounds get() = Bounds(west, south, east, north)

    fun view() = AreaView(id, styleId, bounds, state)
}

@Dao
interface MapDao {
    @Query("SELECT * FROM map_styles ORDER BY createdMs")
    fun observeStyles(): Flow<List<MapStyleEntity>>

    @Query("SELECT * FROM map_styles ORDER BY createdMs")
    suspend fun styles(): List<MapStyleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStyle(style: MapStyleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addStyleIfMissing(style: MapStyleEntity)

    @Query("SELECT * FROM map_areas ORDER BY createdMs DESC")
    fun observeAreas(): Flow<List<MapAreaEntity>>

    @Query("SELECT * FROM map_areas ORDER BY createdMs")
    suspend fun areas(): List<MapAreaEntity>

    @Query("SELECT * FROM map_areas WHERE id = :id")
    suspend fun area(id: String): MapAreaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArea(area: MapAreaEntity)

    /** The areas that still have to be downloaded, oldest first. */
    @Query("SELECT * FROM map_areas WHERE state = 'queued' OR state = 'downloading' ORDER BY createdMs")
    suspend fun pending(): List<MapAreaEntity>

    /** Progress of a download. Only changes an area that is still downloading, so a late update cannot undo the final one. */
    @Query("UPDATE map_areas SET tiles = :tiles, bytes = :bytes, totalTiles = :total WHERE id = :id AND state = 'downloading'")
    suspend fun setProgress(id: String, tiles: Long, bytes: Long, total: Long)

    @Query("SELECT COUNT(*) FROM map_areas WHERE state = 'queued' OR state = 'downloading'")
    suspend fun pendingCount(): Int
}
