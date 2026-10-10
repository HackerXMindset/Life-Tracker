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
 * Tables added in database version 6: how you used your phone.
 *
 * Android only keeps a week or so of detailed app history, so the app copies it into
 * these tables regularly. A session is one stretch of one app being on screen.
 * Times are milliseconds since 1970 (UTC), so they stay correct if the time zone changes.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_5_6 in Database.kt.
 */

/** One stretch of one app on screen. The id is "package|start", so syncing twice never doubles a session. */
@Entity(tableName = "usage_sessions", indices = [Index("startMs")])
data class UsageSessionEntity(
    @PrimaryKey val id: String,
    val pkg: String,
    val startMs: Long,
    val endMs: Long,
) {
    companion object {
        fun idFor(pkg: String, startMs: Long) = "$pkg|$startMs"
    }
}

/**
 * What the app knows about each app it has seen. [activityId] links the app to one of your
 * activities ("" for none), so its time counts towards that activity's goal. [ignored] hides it.
 */
@Entity(tableName = "usage_apps")
data class UsageAppEntity(
    @PrimaryKey val pkg: String,
    val label: String,
    val activityId: String,
    val ignored: Boolean,
)

@Dao
interface UsageDao {
    /** Sessions that overlap [from, to). */
    @Query("SELECT * FROM usage_sessions WHERE endMs > :from AND startMs < :to ORDER BY startMs")
    fun sessionsBetween(from: Long, to: Long): Flow<List<UsageSessionEntity>>

    /** The same as [sessionsBetween], read once. */
    @Query("SELECT * FROM usage_sessions WHERE endMs > :from AND startMs < :to ORDER BY startMs")
    suspend fun sessionsOnce(from: Long, to: Long): List<UsageSessionEntity>

    @Query("SELECT * FROM usage_sessions ORDER BY startMs")
    suspend fun allSessions(): List<UsageSessionEntity>

    @Query("SELECT MAX(startMs) FROM usage_sessions")
    suspend fun latestStart(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<UsageSessionEntity>)

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM usage_apps ORDER BY label COLLATE NOCASE")
    fun observeApps(): Flow<List<UsageAppEntity>>

    @Query("SELECT * FROM usage_apps")
    suspend fun allApps(): List<UsageAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: UsageAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<UsageAppEntity>)

    @Query("DELETE FROM usage_apps")
    suspend fun deleteAllApps()
}
