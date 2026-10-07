package com.lifetracker.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FoundFile(val name: String, val modified: Long, val uri: Uri)

/**
 * Finds the newest export from another app inside the folder you chose.
 * This step only looks; the actual imports come in the Habits and Food steps.
 * Nothing in these folders is ever changed.
 */
object SourceScanner {
    /**
     * Streak saves `streak_backup_<date>_<time>.json` and `.zip`. Every one found,
     * newest first (the name carries the time). Where a .json and a .zip share a
     * time, the .json comes first.
     */
    suspend fun streakCandidates(context: Context, folder: Uri): List<FoundFile> = withContext(Dispatchers.IO) {
        runCatching {
            files(context, folder)
                .filter {
                    it.name.startsWith("streak_backup_", ignoreCase = true) &&
                        (it.name.endsWith(".json") || it.name.endsWith(".zip"))
                }
                .sortedWith(
                    compareByDescending<FoundFile> { it.name.substringBeforeLast('.') }
                        .thenByDescending { if (it.name.endsWith(".json")) 1 else 0 },
                )
        }.getOrDefault(emptyList())
    }

    suspend fun newestStreak(context: Context, folder: Uri): FoundFile? =
        streakCandidates(context, folder).firstOrNull()

    /** OpenNutriTracker exports are named `opennutritracker-export*.zip` with no date, so the newest by modified time wins. */
    suspend fun newestOpenNutriTracker(context: Context, folder: Uri): FoundFile? = withContext(Dispatchers.IO) {
        runCatching {
            files(context, folder)
                .filter { it.name.startsWith("opennutritracker", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true) }
                .maxByOrNull { it.modified }
        }.getOrNull()
    }

    private fun files(context: Context, folder: Uri): List<FoundFile> {
        val dir = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.exists() } ?: return emptyList()
        return dir.listFiles().mapNotNull { f ->
            val name = f.name
            if (f.isFile && name != null) FoundFile(name, f.lastModified(), f.uri) else null
        }
    }
}
