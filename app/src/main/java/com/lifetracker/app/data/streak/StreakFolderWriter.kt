package com.lifetracker.app.data.streak

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.HabitSyncState
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What happened when the app tried to send its changes to Streak's folder. */
data class SendResult(val message: String, val wrote: Boolean)

/**
 * Writes this app's changes into Streak's sync folder as a Streak backup file (Habits H5c), so Streak takes them in
 * the next time it checks the folder. Only files this app wrote itself are ever replaced; Streak's own files are
 * never touched.
 */
object StreakFolderWriter {
    /** How many of this app's own files stay in the folder. */
    const val KEEP = 3

    /** True when Android lets this app write to the chosen Streak folder (it must be chosen again once to allow it). */
    fun canWrite(context: Context): Boolean {
        val folder = DataSources(context).folder(DataSources.Slot.Streak) ?: return false
        return context.contentResolver.persistedUriPermissions.any { it.uri == folder && it.isWritePermission }
    }

    suspend fun send(context: Context, now: LocalDateTime = LocalDateTime.now()): SendResult =
        withContext(Dispatchers.IO) {
            val sources = DataSources(context)
            val folder = sources.folder(DataSources.Slot.Streak)
                ?: return@withContext SendResult("Choose the Streak folder first.", false)
            if (!canWrite(context)) {
                return@withContext SendResult(
                    "This app can only read the Streak folder. Choose the folder again to allow writing.",
                    false,
                )
            }
            val dir = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.exists() && it.canWrite() }
                ?: return@withContext SendResult("The Streak folder is missing or cannot be written to.", false)

            val db = AppDatabase.get(context)
            val sync = HabitSyncState(context)
            val device = sync.deviceId
            val changes = sync.changes
            val habits = db.habitDao().allHabits()
            val completions = db.habitDao().allCompletions()
            val notes = db.planDao().allNotes()
            val categories = db.habitDao().allCategories()

            fun make(at: LocalDateTime) = StreakBackupWriter.build(device, at, habits, completions, notes, categories, changes)

            val fingerprint = make(LocalDateTime.of(2000, 1, 1, 0, 0))
                ?: return@withContext SendResult("Nothing to send yet: nothing has been changed here.", false)
            val digest = sha256(fingerprint)
            if (digest == sync.lastSentDigest) {
                return@withContext SendResult("Already sent. Nothing has changed since.", false)
            }

            val text = make(now) ?: return@withContext SendResult("Nothing to send yet: nothing has been changed here.", false)
            val bytes = text.toByteArray(Charsets.UTF_8)
            val name = StreakBackupWriter.fileName(now, device)

            // Written whole under a name Streak ignores, then renamed, so Streak never reads half a file.
            val part = dir.createFile("application/octet-stream", "$name.part")
                ?: throw IOException("Could not create a file in the Streak folder.")
            try {
                context.contentResolver.openOutputStream(part.uri, "wt")?.use { it.write(bytes) }
                    ?: throw IOException("Could not write to the Streak folder.")
                if (!part.renameTo(name)) throw IOException("Could not finish the file in the Streak folder.")
            } catch (e: Exception) {
                runCatching { part.delete() }
                throw e
            }
            val written = dir.findFile(name)
            if (written == null || written.length() != bytes.size.toLong()) {
                throw IOException("The file in the Streak folder does not look complete.")
            }

            prune(dir, device)
            sync.lastSentDigest = digest
            sync.lastSentText = now.toString()
            val root = JSONObject(text)
            SendResult(
                "Sent ${root.getJSONArray("habits").length()} habits and ${root.getJSONArray("notes").length()} notes as $name. " +
                    "Streak takes them in the next time it checks the folder.",
                true,
            )
        }

    /** Keeps this app's newest [KEEP] files and removes its older ones. Files of other devices are left alone. */
    private fun prune(dir: DocumentFile, device: String) {
        val ours = dir.listFiles()
            .filter { f -> f.isFile && f.name?.let { StreakBackupWriter.isOurs(it, device) } == true }
            .sortedByDescending { it.name }
        for (old in ours.drop(KEEP)) runCatching { old.delete() }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
