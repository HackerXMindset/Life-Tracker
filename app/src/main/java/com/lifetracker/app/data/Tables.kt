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
 * Tables added in database version 2 (habits, to-dos, notes).
 *
 * Dates are stored as ISO text ("2026-10-04") so they sort and compare
 * correctly. Each table that comes from Streak keeps a `raw` copy of the
 * original record, so fields the app does not use yet can be used later
 * without asking you to import again.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_1_2 in Database.kt for the pattern.
 */

/** A habit. `kind`: 0 = yes/no, 1 = "relapse" (counts days without), 2 = counted amount. */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val category: String,
    val color: Long,
    val kind: Int,
    val quantKind: Int,
    val unitLabel: String,
    val target: Double,
    val step: Double,
    val anyAmount: Boolean,
    val startMinute: Int,
    val durationMinutes: Int,
    val startedOn: String,
    val archived: Boolean,
    val sortOrder: Int,
    val raw: String,
)

/** One day's record for one habit. For a "relapse" habit, a row means a relapse happened that day. */
@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "date"],
    indices = [Index("date")],
)
data class CompletionEntity(
    val habitId: String,
    val date: String,
    val count: Double,
    val minuteOfDay: Int?,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val sortOrder: Int,
)

@Entity(tableName = "todo_tags")
data class TodoTagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val kind: String,
    val sortOrder: Int,
)

/** A to-do. It appears on the Timeline only when it has a [date]; [minutes] is the time of day, [estimate] its length in minutes. */
@Entity(tableName = "todos", indices = [Index("date")])
data class TodoEntity(
    @PrimaryKey val id: String,
    val text: String,
    val done: Boolean,
    val date: String?,
    val minutes: Int?,
    val estimate: Int?,
    val priority: Int,
    val project: String,
    val createdAt: String,
    val doneAt: String?,
    val raw: String,
)

/** A note on a day. `type`: 0 = note, 1 = planned, 2 = completed. */
@Entity(tableName = "notes", indices = [Index("date")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: String,
    val type: Int,
    val text: String,
    val minutes: Int?,
    val createdAt: String,
)

/**
 * Remembers which outside items (such as Streak focus sessions) were already
 * turned into Timeline entries, so importing again never makes duplicates and
 * never brings back an entry you deleted.
 */
@Entity(tableName = "imported_items", primaryKeys = ["source", "sourceId"])
data class ImportedItemEntity(
    val source: String,
    val sourceId: String,
    val entryId: Long,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder, name")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_completions")
    fun observeCompletions(): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM habits")
    suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM habit_completions")
    suspend fun allCompletions(): List<CompletionEntity>

    @Query("SELECT * FROM categories")
    suspend fun allCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabits(habits: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: CompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletions(completions: List<CompletionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCompletion(habitId: String, date: String)

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun completion(habitId: String, date: String): CompletionEntity?

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Query("DELETE FROM habit_completions")
    suspend fun deleteAllCompletions()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM todos WHERE date = :date ORDER BY minutes IS NULL, minutes, createdAt")
    fun todosOn(date: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM notes WHERE date = :date ORDER BY minutes IS NULL, minutes, createdAt")
    fun notesOn(date: String): Flow<List<NoteEntity>>

    @Query("SELECT DISTINCT date FROM todos WHERE date IS NOT NULL")
    fun datesWithTodos(): Flow<List<String>>

    @Query("SELECT DISTINCT date FROM notes WHERE date != ''")
    fun datesWithNotes(): Flow<List<String>>

    @Query("SELECT * FROM todo_tags")
    fun observeTodoTags(): Flow<List<TodoTagEntity>>

    @Query("SELECT * FROM todos")
    suspend fun allTodos(): List<TodoEntity>

    @Query("SELECT * FROM todo_tags")
    suspend fun allTodoTags(): List<TodoTagEntity>

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTodos(todos: List<TodoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTodoTags(tags: List<TodoTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<NoteEntity>)

    @Query("UPDATE todos SET done = :done, doneAt = :doneAt WHERE id = :id")
    suspend fun setTodoDone(id: String, done: Boolean, doneAt: String?)

    @Query("DELETE FROM todos")
    suspend fun deleteAllTodos()

    @Query("DELETE FROM todo_tags")
    suspend fun deleteAllTodoTags()

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM imported_items")
    suspend fun all(): List<ImportedItemEntity>

    @Query("SELECT sourceId FROM imported_items WHERE source = :source")
    suspend fun sourceIds(source: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ImportedItemEntity>)

    @Query("DELETE FROM imported_items")
    suspend fun deleteAll()
}
