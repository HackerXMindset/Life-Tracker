package com.lifetracker.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes backups to, and restores them from, the folder you chose.
 *
 * Ideas borrowed from how Streak does it: a file is written under a temporary
 * name and only renamed when complete (so a crash never leaves a broken
 * backup), only the newest few are kept, and a safety copy of your current
 * data is saved before any restore.
 */
object BackupManager {
    const val BACKUP_PREFIX = "life-tracker-backup_"
    const val SAFETY_PREFIX = "life-tracker-safety_"
    const val KEEP_BACKUPS = 5
    const val KEEP_SAFETY = 3

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    data class BackupFile(val name: String, val uri: Uri, val modified: Long) {
        val isSafety: Boolean get() = name.startsWith(SAFETY_PREFIX)
    }

    private fun tree(context: Context, uri: Uri): DocumentFile =
        DocumentFile.fromTreeUri(context, uri)?.takeIf { it.exists() }
            ?: throw IOException("That folder is not available. Choose it again.")

    /** Checks that files can really be created in the folder. */
    suspend fun probe(context: Context, folder: Uri) = withContext(Dispatchers.IO) {
        val dir = tree(context, folder)
        val file = dir.createFile("application/octet-stream", "life-tracker-write-test")
            ?: throw IOException("Android would not let the app write in that folder.")
        file.delete()
    }

    /** Backs up now into the chosen folder. Returns the new file's name. */
    suspend fun backupNow(context: Context, sources: DataSources): String = withContext(Dispatchers.IO) {
        val folder = sources.folder(DataSources.Slot.Backup)
            ?: throw IOException("Choose a backup folder first.")
        val name = write(context, folder, BACKUP_PREFIX, KEEP_BACKUPS)
        sources.lastBackupText = DISPLAY.format(LocalDateTime.now())
        name
    }

    /**
     * Makes one backup the first time the app is opened each day, if a backup
     * folder is set and there is something to save. Returns the file name, or
     * null if nothing was due.
     */
    suspend fun autoBackupIfDue(context: Context): String? = withContext(Dispatchers.IO) {
        val sources = DataSources(context)
        if (sources.folder(DataSources.Slot.Backup) == null) return@withContext null
        val today = LocalDate.now().toString()
        if (sources.lastAutoBackupDate == today) return@withContext null
        if (snapshot(AppDatabase.get(context)).isEmpty) return@withContext null
        val name = backupNow(context, sources)
        sources.lastAutoBackupDate = today
        name
    }

    /** Every backup and safety copy in the folder, newest first. */
    suspend fun list(context: Context, folder: Uri): List<BackupFile> = withContext(Dispatchers.IO) {
        tree(context, folder).listFiles()
            .mapNotNull { f ->
                val name = f.name ?: return@mapNotNull null
                if (f.isFile && name.startsWith("life-tracker-") && name.endsWith(".json")) {
                    BackupFile(name, f.uri, f.lastModified())
                } else {
                    null
                }
            }
            .sortedByDescending { it.name.substringAfter('_') }
    }

    /**
     * Replaces everything with the chosen backup. The file is read and checked
     * first, and the current data is saved as a safety copy before anything is
     * removed, so a bad file or a wrong tap changes nothing or can be undone.
     */
    suspend fun restore(context: Context, folder: Uri, file: BackupFile) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
            ?: throw IOException("Could not open that backup.")
        val data = BackupCodec.decode(bytes.toString(Charsets.UTF_8))

        val db = AppDatabase.get(context)
        if (!snapshot(db).isEmpty) {
            write(context, folder, SAFETY_PREFIX, KEEP_SAFETY)
        }
        replaceAll(db, data)
        // Let the next open import the newest Streak backup again, to catch up on anything newer.
        DataSources(context).apply {
            streakSeenExportedAt = null
            ontSeenKey = null
        }
    }

    /** All of the app's data as it is right now. */
    suspend fun snapshot(db: AppDatabase): BackupData = BackupData(
        version = BackupCodec.FORMAT_VERSION,
        exportedAt = LocalDateTime.now().toString(),
        entries = db.entryDao().all(),
        habits = db.habitDao().allHabits(),
        completions = db.habitDao().allCompletions(),
        categories = db.habitDao().allCategories(),
        todoTags = db.planDao().allTodoTags(),
        todos = db.planDao().allTodos(),
        notes = db.planDao().allNotes(),
        imported = db.importDao().all(),
        meals = db.foodDao().allMeals(),
        foodGoals = db.foodDao().allGoals(),
        water = db.foodDao().allWater(),
        activityTypes = db.activityDao().all(),
        moneyCategories = db.moneyDao().allCategories(),
        moneyItems = db.moneyDao().allItems(),
        moneyEntries = db.moneyDao().allEntries(),
        usageSessions = db.usageDao().allSessions(),
        usageApps = db.usageDao().allApps(),
        calls = db.callDao().allCalls(),
        chargeSessions = db.chargeDao().allSessions(),
        stepDays = db.stepDao().allDays(),
        sleepNights = db.sleepDao().allNights(),
    )

    /**
     * A version 9 backup replaces everything except the phone usage, call, charging, step and sleep history, which are only ever added to. Older backups never held some of
     * the data (version 1: only Timeline entries; version 2: no food or water),
     * so they replace only what they hold and leave the rest alone; the import record is cleared so the next Streak import can
     * bring back any focus sessions the old backup did not have.
     */
    private suspend fun replaceAll(db: AppDatabase, data: BackupData) {
        db.withTransaction {
            db.entryDao().deleteAll()
            db.entryDao().insertAll(data.entries)
            db.importDao().deleteAll()
            if (data.version >= 2) {
                db.habitDao().deleteAllHabits()
                db.habitDao().deleteAllCompletions()
                db.habitDao().deleteAllCategories()
                db.planDao().deleteAllTodos()
                db.planDao().deleteAllTodoTags()
                db.planDao().deleteAllNotes()
                db.habitDao().upsertHabits(data.habits)
                db.habitDao().upsertCompletions(data.completions)
                db.habitDao().upsertCategories(data.categories)
                db.planDao().upsertTodoTags(data.todoTags)
                db.planDao().upsertTodos(data.todos)
                db.planDao().upsertNotes(data.notes)
                db.importDao().insertAll(data.imported)
            }
            if (data.version >= 3) {
                db.foodDao().deleteAllMeals()
                db.foodDao().deleteAllGoals()
                db.foodDao().deleteAllWater()
                db.foodDao().upsertMeals(data.meals)
                db.foodDao().upsertGoals(data.foodGoals)
                db.foodDao().upsertWater(data.water)
            }
            if (data.version >= 4) {
                db.activityDao().deleteAll()
                db.activityDao().upsertAll(data.activityTypes)
            }
            if (data.version >= 5) {
                db.moneyDao().deleteAllEntries()
                db.moneyDao().deleteAllItems()
                db.moneyDao().deleteAllCategories()
                db.moneyDao().upsertCategories(data.moneyCategories)
                db.moneyDao().upsertItems(data.moneyItems)
                db.moneyDao().upsertEntries(data.moneyEntries)
            }
            if (data.version >= 6) {
                // Phone usage is a history that only grows: restoring adds to it and never removes
                // sessions copied after the backup was made.
                db.usageDao().upsertApps(data.usageApps)
                db.usageDao().upsertSessions(data.usageSessions)
            }
            if (data.version >= 7) {
                // Same for calls: add the backup's calls to the ones already here.
                db.callDao().upsertCalls(data.calls)
            }
            if (data.version >= 8) {
                // Charging history also only grows: restoring adds to it.
                db.chargeDao().upsertAll(data.chargeSessions)
            }
            if (data.version >= 9) {
                // Steps and sleep only grow too: a day keeps its larger step count, and nights you edited are kept.
                val steps = db.stepDao()
                steps.upsertAll(data.stepDays.map { StepStats.merge(steps.day(it.date), it.date, it.steps, it.source) })
                db.sleepDao().insertMissing(data.sleepNights)
            }
        }
    }

    private suspend fun write(context: Context, folder: Uri, prefix: String, keep: Int): String {
        val dir = tree(context, folder)
        val text = BackupCodec.encode(snapshot(AppDatabase.get(context)))
        val base = prefix + STAMP.format(LocalDateTime.now())

        val part = dir.createFile("application/octet-stream", "$base.json.part")
            ?: throw IOException("Android would not let the app write in that folder.")
        try {
            val out = context.contentResolver.openOutputStream(part.uri, "wt")
                ?: throw IOException("Could not open the backup file for writing.")
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            if (!part.renameTo("$base.json")) throw IOException("Could not finish the backup file.")
        } catch (e: Exception) {
            runCatching { part.delete() }
            throw e
        }
        prune(dir, prefix, keep)
        return "$base.json"
    }

    private fun prune(dir: DocumentFile, prefix: String, keep: Int) {
        val mine = dir.listFiles().filter { it.isFile && it.name?.startsWith(prefix) == true }
        mine.filter { it.name?.endsWith(".part") == true }.forEach { runCatching { it.delete() } }
        mine.filter { it.name?.endsWith(".json") == true }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { runCatching { it.delete() } }
    }

    private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
}
