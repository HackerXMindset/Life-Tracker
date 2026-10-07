package com.lifetracker.app.data.streak

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.CategoryEntity
import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.EntryEntity
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.ImportedItemEntity
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.data.SourceScanner
import com.lifetracker.app.data.TodoEntity
import com.lifetracker.app.data.TodoTagEntity
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What one import did, in words the Data sources screen can show. */
data class ImportResult(
    val fileName: String,
    val habits: Int,
    val completionsAdded: Int,
    val focusAdded: Int,
    val todos: Int,
    val datedTodos: Int,
    val notes: Int,
) {
    fun describe(): String = buildString {
        append("Imported from $fileName: $habits habits")
        append(", $completionsAdded new habit days")
        append(", $focusAdded focus sessions added to the Timeline")
        append(", $todos to-dos ($datedTodos with a date, shown on the Timeline)")
        append(", $notes notes.")
    }
}

/**
 * Brings the newest Streak backup into the app.
 *
 * Rules, borrowed from how Streak merges its own backups:
 * - The newest readable file wins; an unreadable one is skipped.
 * - A backup is only imported if it was exported after the last one imported
 *   (unless you tap Import now).
 * - Nothing is erased. For each habit day the higher count is kept; to-dos
 *   that are done stay done.
 * - A focus session becomes a Timeline entry once. If you delete that entry
 *   later, importing again does not bring it back.
 */
object StreakImporter {
    private const val SOURCE_FOCUS = "streak-focus"

    /** Imports if a newer backup is waiting. Returns null when there was nothing new to do. */
    suspend fun importIfNew(context: Context, force: Boolean = false): ImportResult? =
        withContext(Dispatchers.IO) {
            val sources = DataSources(context)
            val folder = sources.folder(DataSources.Slot.Streak)
                ?: throw IOException("Choose the Streak folder first.")

            val candidates = SourceScanner.streakCandidates(context, folder)
            if (candidates.isEmpty()) throw IOException("No Streak backup found in that folder.")

            var data: StreakData? = null
            var used: String? = null
            var lastError: Exception? = null
            for (file in candidates) {
                try {
                    data = StreakParser.parse(readBackupText(context, file.uri, file.name))
                    used = file.name
                    break
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (data == null || used == null) {
                throw IOException(lastError?.message ?: "Could not read the Streak backup.")
            }

            val seen = sources.streakSeenExportedAt
            if (!force && seen != null && data.exportedAt.isNotEmpty() && data.exportedAt <= seen) {
                return@withContext null
            }
            if (data.isEmpty) return@withContext null

            val result = apply(AppDatabase.get(context), data, used)
            sources.streakSeenExportedAt = data.exportedAt
            sources.lastStreakImportText = LocalDateTime.now().toString()
            result
        }

    private fun readBackupText(context: Context, uri: Uri, name: String): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Could not open $name.")
        if (!name.endsWith(".zip", ignoreCase = true)) return bytes.toString(Charsets.UTF_8)
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == "backup.json") {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        throw IOException("$name does not contain backup.json.")
    }

    private suspend fun apply(db: AppDatabase, data: StreakData, fileName: String): ImportResult {
        val habitDao = db.habitDao()
        val planDao = db.planDao()
        val entryDao = db.entryDao()
        val importDao = db.importDao()

        var completionsAdded = 0
        var focusAdded = 0

        db.withTransaction {
            habitDao.upsertCategories(
                data.categories.map { CategoryEntity(it.id, it.name, it.color, it.order) },
            )

            // Habits and their days. Where both sides have a day, the higher count wins.
            val existing = habitDao.allCompletions().associateBy { it.habitId to it.date }
            val completions = ArrayList<CompletionEntity>()
            val habits = data.habits.map { h ->
                val first = h.completions.minOfOrNull { it.date }
                val startedOn = listOfNotNull(h.createdOn, first).minOrNull() ?: LocalDate.now()
                for (c in h.completions) {
                    val key = h.id to c.date.toString()
                    val old = existing[key]
                    if (old == null) {
                        completionsAdded++
                        completions += CompletionEntity(h.id, c.date.toString(), c.count, c.minuteOfDay)
                    } else if (c.count > old.count) {
                        completions += CompletionEntity(h.id, c.date.toString(), c.count, c.minuteOfDay ?: old.minuteOfDay)
                    }
                }
                HabitEntity(
                    id = h.id,
                    name = h.name,
                    icon = h.icon,
                    category = h.category,
                    color = h.color,
                    kind = h.kind,
                    quantKind = h.quantKind,
                    unitLabel = h.unitLabel,
                    target = h.target,
                    step = h.step,
                    anyAmount = h.anyAmount,
                    startMinute = h.startMinute,
                    durationMinutes = h.durationMinutes,
                    startedOn = startedOn.toString(),
                    archived = h.archived,
                    sortOrder = h.order,
                    raw = h.raw,
                )
            }
            habitDao.upsertHabits(habits)
            habitDao.upsertCompletions(completions)

            // To-dos: Streak's details win, but a to-do you ticked here stays ticked.
            planDao.upsertTodoTags(data.todoTags.map { TodoTagEntity(it.id, it.name, it.color, it.kind, it.order) })
            val oldTodos = planDao.allTodos().associateBy { it.id }
            planDao.upsertTodos(
                data.todos.map { t ->
                    val old = oldTodos[t.id]
                    TodoEntity(
                        id = t.id,
                        text = t.text,
                        done = t.done || old?.done == true,
                        date = t.date?.toString(),
                        minutes = t.minutes,
                        estimate = t.estimate,
                        priority = t.priority,
                        project = t.project,
                        createdAt = t.createdAt,
                        doneAt = t.doneAt ?: old?.doneAt,
                        raw = t.raw,
                    )
                },
            )

            planDao.upsertNotes(
                data.notes.map {
                    NoteEntity(it.id, it.habitId, it.date?.toString() ?: "", it.type, it.text, it.minutes, it.createdAt)
                },
            )

            // Focus sessions become Timeline entries, once each.
            val habitById = data.habits.associateBy { it.id }
            val alreadyImported = importDao.sourceIds(SOURCE_FOCUS).toHashSet()
            val marks = ArrayList<ImportedItemEntity>()
            for (session in data.focus) {
                val habit = habitById[session.habitId]
                val title = session.label.ifBlank { habit?.name ?: "Focus session" }
                val category = if (habit == null) "Study" else FocusConverter.timelineCategory(habit.category)
                for (piece in FocusConverter.pieces(session)) {
                    if (piece.sourceId in alreadyImported) continue
                    val id = entryDao.insert(
                        EntryEntity(
                            date = piece.date.toString(),
                            startMinute = piece.startMinute,
                            endMinute = piece.endMinute,
                            category = category,
                            title = title,
                            note = "Focus session imported from Streak",
                        ),
                    )
                    marks += ImportedItemEntity(SOURCE_FOCUS, piece.sourceId, id)
                    focusAdded++
                }
            }
            importDao.insertAll(marks)
        }

        return ImportResult(
            fileName = fileName,
            habits = data.habits.size,
            completionsAdded = completionsAdded,
            focusAdded = focusAdded,
            todos = data.todos.size,
            datedTodos = data.todos.count { it.date != null },
            notes = data.notes.size,
        )
    }
}
