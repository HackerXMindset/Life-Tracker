package com.lifetracker.app.data.streak

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.AppLog
import com.lifetracker.app.data.CategoryEntity
import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.EntryEntity
import com.lifetracker.app.data.FocusSessionEntity
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.HabitSource
import com.lifetracker.app.data.HabitSyncState
import com.lifetracker.app.data.HabitTombstones
import com.lifetracker.app.data.HabitsSettings
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
    /** True when habits, their days, categories and notes were left alone because the app uses its own habit data. */
    val habitsSkipped: Boolean = false,
) {
    fun describe(): String = buildString {
        append("Imported from $fileName: ")
        if (habitsSkipped) append("habits left alone (using this app's own habit data)")
        else append("$habits habits, $completionsAdded new habit days")
        append(", $focusAdded focus sessions added to the Timeline")
        append(", $todos to-dos ($datedTodos with a date, shown on the Timeline)")
        if (!habitsSkipped) append(", $notes notes")
        append(".")
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

    /**
     * Imports if a newer backup is waiting. Streak writes one file per device it syncs with, so the newest file of
     * **each** device is read (oldest first, so the newest one wins where they disagree). Returns null when there
     * was nothing new to do.
     */
    suspend fun importIfNew(context: Context, force: Boolean = false): ImportResult? =
        withContext(Dispatchers.IO) {
            val sources = DataSources(context)
            val folder = sources.folder(DataSources.Slot.Streak)
                ?: throw IOException("Choose the Streak folder first.")

            val candidates = SourceScanner.streakCandidates(context, folder)
            if (candidates.isEmpty()) throw IOException("No Streak backup found in that folder.")

            val sync = HabitSyncState(context)
            val importHabits = HabitsSettings(context).source == HabitSource.Streak
            val db = AppDatabase.get(context)
            val seen = sources.streakSeenExportedAt

            // The newest readable file of each device (candidates are newest first).
            val perDevice = LinkedHashMap<String, Pair<String, StreakData>>()
            var lastError: Exception? = null
            for (file in candidates) {
                val device = StreakBackupWriter.deviceOf(file.name)
                if (device in perDevice) continue
                try {
                    perDevice[device] = file.name to StreakParser.parse(readBackupText(context, file.uri, file.name))
                } catch (e: Exception) {
                    lastError = e
                    AppLog.w("Streak", "Could not read ${file.name}: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            if (perDevice.isEmpty()) throw IOException(lastError?.message ?: "Could not read the Streak backup.")

            val results = ArrayList<ImportResult>()
            var newestExported = ""
            for ((device, pair) in perDevice.entries.sortedBy { it.value.first.substringBeforeLast('.') }.map { it.key to it.value }) {
                val (name, data) = pair
                val isNew = when {
                    force -> true
                    device.isNotEmpty() -> sync.lastFileOf(device) != name
                    else -> seen == null || data.exportedAt.isEmpty() || data.exportedAt > seen
                }
                if (!isNew) continue
                if (data.isEmpty) {
                    if (device.isNotEmpty()) sync.setLastFileOf(device, name)
                    continue
                }
                results += apply(db, sync, data, name, importHabits)
                if (device.isNotEmpty()) sync.setLastFileOf(device, name)
                if (data.exportedAt > newestExported) newestExported = data.exportedAt
                AppLog.i("Streak", "Read $name (device ${device.ifEmpty { "unknown" }})")
            }
            if (results.isEmpty()) return@withContext null
            if (newestExported.isNotEmpty()) sources.streakSeenExportedAt = newestExported
            sources.lastStreakImportText = LocalDateTime.now().toString()
            combine(results)
        }

    private fun combine(results: List<ImportResult>): ImportResult =
        if (results.size == 1) results.first() else ImportResult(
            fileName = "${results.size} files (" + results.joinToString { it.fileName } + ")",
            habits = results.maxOf { it.habits },
            completionsAdded = results.sumOf { it.completionsAdded },
            focusAdded = results.sumOf { it.focusAdded },
            todos = results.maxOf { it.todos },
            datedTodos = results.maxOf { it.datedTodos },
            notes = results.maxOf { it.notes },
            habitsSkipped = results.all { it.habitsSkipped },
        )

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

    private suspend fun apply(db: AppDatabase, sync: HabitSyncState, data: StreakData, fileName: String, importHabits: Boolean): ImportResult {
        val habitDao = db.habitDao()
        val planDao = db.planDao()
        val entryDao = db.entryDao()
        val importDao = db.importDao()

        var completionsAdded = 0
        var focusAdded = 0

        db.withTransaction {
            if (importHabits) {
                habitDao.upsertCategories(
                    data.categories.map { CategoryEntity(it.id, it.name, it.color, it.order) },
                )
            }

            // Habits and their days. Where both sides have a day, the higher count wins.
            val existing = habitDao.allCompletions().associateBy { it.habitId to it.date }
            // A habit hidden or cleared here keeps that when Streak's copy is read again.
            val localHabits = habitDao.allHabits().associateBy { it.id }
            val completions = ArrayList<CompletionEntity>()
            val habits = (if (importHabits) data.habits else emptyList()).map { h ->
                val first = h.completions.minOfOrNull { it.date }
                val startedOn = listOfNotNull(h.createdOn, first).minOrNull() ?: LocalDate.now()
                for (c in h.completions) {
                    val key = h.id to c.date.toString()
                    val old = existing[key]
                    val day = StreakMerge.day(old, h.id, c) ?: continue
                    if (old == null) completionsAdded++
                    completions += day
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
                    deletedAt = localHabits[h.id]?.deletedAt ?: 0L,
                    clearedAt = localHabits[h.id]?.clearedAt ?: 0L,
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

            if (importHabits) {
                val localNotes = planDao.allNotes().associateBy { it.id }
                planDao.upsertNotes(
                    data.notes.map {
                        NoteEntity(
                            it.id, it.habitId, it.date?.toString() ?: "", it.type, it.text, it.minutes, it.createdAt,
                            deletedAt = localNotes[it.id]?.deletedAt ?: 0L,
                        )
                    },
                )

                // What Streak says was deleted, brought back or cleared (Streak 2.4). Nothing is erased: items are hidden or shown.
                val plan = StreakMerge.plan(
                    deleted = data.deleted,
                    cleared = data.cleared,
                    changes = data.changes,
                    fileHabitIds = data.habits.map { it.id }.toSet(),
                    fileNoteIds = data.notes.map { it.id }.toSet(),
                    habits = habitDao.allHabits().map { StreakMerge.LocalHabit(it.id, it.deletedAt, it.clearedAt) },
                    notes = planDao.allNotes().map { StreakMerge.LocalNote(it.id, it.habitId, it.deletedAt) },
                    state = HabitTombstones.State(sync.changes, sync.deleted),
                )
                for ((id, at) in plan.hideHabits) habitDao.setHabitDeleted(id, at)
                for ((id, at) in plan.hideNotes) planDao.setNoteDeleted(id, at)
                for (id in plan.showHabits) habitDao.setHabitDeleted(id, 0L)
                for (id in plan.showNotes) planDao.setNoteDeleted(id, 0L)
                for ((id, at) in plan.clears) habitDao.setHabitCleared(id, at)
                for ((id, at) in plan.remember) sync.learnDeleted(id, at)
                for (id in plan.showHabits + plan.showNotes) sync.forgetDeleted(id)
                if (!plan.isEmpty) {
                    AppLog.i(
                        "Streak",
                        "$fileName: hid ${plan.hideHabits.size} habit(s) and ${plan.hideNotes.size} note(s), showed ${plan.showHabits.size} habit(s) " +
                            "and ${plan.showNotes.size} note(s), cleared progress on ${plan.clears.size} habit(s)",
                    )
                }
            }

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
            // Streak's focus sessions are also kept as sessions (once each), so they travel with this app's own.
            db.focusDao().insertMissing(
                data.focus.map {
                    FocusSessionEntity(
                        id = it.id,
                        habitId = it.habitId,
                        targetMinutes = it.targetMinutes,
                        seconds = it.seconds,
                        completed = it.completed,
                        startedAtMs = it.startedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        counted = it.counted,
                        label = it.label,
                    )
                },
            )
        }

        return ImportResult(
            fileName = fileName,
            habits = if (importHabits) data.habits.size else 0,
            completionsAdded = completionsAdded,
            focusAdded = focusAdded,
            todos = data.todos.size,
            datedTodos = data.todos.count { it.date != null },
            notes = if (importHabits) data.notes.size else 0,
            habitsSkipped = !importHabits,
        )
    }
}
