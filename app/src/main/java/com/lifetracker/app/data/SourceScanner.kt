package com.lifetracker.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FoundFile(val name: String, val modified: Long)

/**
 * Finds the newest export from another app inside the folder you chose.
 * This step only looks; the actual imports come in the Habits and Food steps.
 * Nothing in these folders is ever changed.
 */
object SourceScanner {
    /** Streak saves `streak_backup_<date>_<time>.json` and `.zip`; the newest name wins. */
    suspend fun newestStreak(context: Context, folder: Uri): FoundFile? = withContext(Dispatchers.IO) {
        runCatching {
            files(context, folder)
                .filter {
                    it.name.startsWith("streak_backup_", ignoreCase = true) &&
                        (it.name.endsWith(".json") || it.name.endsWith(".zip"))
                }
                // Same timestamp for the .json and .zip: prefer the .json.
                .sortedWith(
                    compareBy<FoundFile>({ it.name.substringBeforeLast('.') }, { if (it.name.endsWith(".json")) 1 else 0 }),
                )
                .lastOrNull()
        }.getOrNull()
    }

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
            if (f.isFile && name != null) FoundFile(name, f.lastModified()) else null
        }
    }
}
