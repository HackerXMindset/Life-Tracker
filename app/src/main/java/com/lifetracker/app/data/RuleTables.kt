package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One rule: when something happens, do something (Rules, E8). The trigger, conditions and actions are kept as text (see
 * RuleCodec) so new kinds can be added without changing the table.
 * IMPORTANT: if any column here changes, the database version must go up and a Migration must be added. See MIGRATION_28_29.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val triggerJson: String,
    val conditionsJson: String,
    val actionsJson: String,
    val cooldownSec: Int,
    val createdMs: Long,
    val lastRunMs: Long,
    val runs: Int,
    val sortOrder: Int,
)

/** One line of "what ran and why": a rule ran, or was held back and the reason. Only the latest few hundred are kept. */
@Entity(tableName = "rule_log", indices = [Index("atMs"), Index("ruleId")])
data class RuleLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: String,
    val ruleName: String,
    val atMs: Long,
    /** What happened, in words: "Arrived at Home". */
    val event: String,
    /** "ran", "skipped", "failed" or "off". */
    val result: String,
    /** Why, or what it did: "Ticked Study", "Only on weekdays". */
    val detail: String,
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY sortOrder, createdMs")
    fun observeRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY sortOrder, createdMs")
    suspend fun all(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY sortOrder, createdMs")
    suspend fun enabled(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun get(id: String): RuleEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM rules")
    suspend fun maxOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(rules: List<RuleEntity>)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE rules SET lastRunMs = :atMs, runs = runs + 1 WHERE id = :id")
    suspend fun noteRun(id: String, atMs: Long)

    @Insert
    suspend fun addLog(line: RuleLogEntity)

    @Query("SELECT * FROM rule_log ORDER BY atMs DESC, id DESC LIMIT :limit")
    fun observeLog(limit: Int): Flow<List<RuleLogEntity>>

    /** When [ruleId] last ran, newest first, since [sinceMs]: how often a rule runs is what stops a loop. */
    @Query("SELECT atMs FROM rule_log WHERE ruleId = :ruleId AND result = 'ran' AND atMs >= :sinceMs ORDER BY atMs DESC")
    suspend fun runsSince(ruleId: String, sinceMs: Long): List<Long>

    /** Keeps the newest [keep] lines. */
    @Query("DELETE FROM rule_log WHERE id NOT IN (SELECT id FROM rule_log ORDER BY atMs DESC, id DESC LIMIT :keep)")
    suspend fun trimLog(keep: Int)

    @Query("DELETE FROM rule_log")
    suspend fun clearLog()
}
