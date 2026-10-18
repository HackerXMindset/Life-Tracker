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

/**
 * A combination of apps you named (added in database version 16). When these apps are used together, the whole period
 * counts as [activityId]. [pkgs] holds the package names joined by commas. Nothing in the saved sessions changes: the
 * rule is applied each time a day is drawn, so naming one also changes the past days.
 */
@Entity(tableName = "usage_combos")
data class UsageComboEntity(
    @PrimaryKey val id: String,
    val name: String,
    val pkgs: String,
    val activityId: String,
    val updatedMs: Long,
) {
    val pkgSet: Set<String> get() = pkgs.split(',').filter { it.isNotEmpty() }.toSet()

    fun toCombo() = UsageDays.Combo(id, pkgSet, activityId)

    companion object {
        fun joinPkgs(set: Collection<String>) = set.toSortedSet().joinToString(",")
    }
}

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

    /** Used only when the sessions of the days Android still remembers are built again with the corrected rules. */
    @Query("DELETE FROM usage_sessions WHERE startMs >= :from")
    suspend fun deleteSessionsFrom(from: Long)

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

    @Query("SELECT * FROM usage_combos ORDER BY name COLLATE NOCASE")
    fun observeCombos(): Flow<List<UsageComboEntity>>

    @Query("SELECT * FROM usage_combos ORDER BY id")
    suspend fun allCombos(): List<UsageComboEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCombo(combo: UsageComboEntity)

    /** Restoring a backup only adds combinations; one already on this phone is kept as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingCombos(combos: List<UsageComboEntity>)

    /** Removes only the rule. Every saved session stays, and the days are simply drawn without it. */
    @Query("DELETE FROM usage_combos WHERE id = :id")
    suspend fun deleteCombo(id: String)
}
