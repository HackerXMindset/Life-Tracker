package com.lifetracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.BackupManager
import com.lifetracker.app.data.BackupManager.BackupFile
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.DataSources.Slot
import com.lifetracker.app.data.FoundFile
import com.lifetracker.app.data.SourceScanner
import com.lifetracker.app.data.streak.StreakImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the Data sources screen shows. A null folder name means "not chosen yet". */
data class DataSourcesState(
    val backupFolder: String? = null,
    val lastBackup: String? = null,
    val backups: List<BackupFile> = emptyList(),
    val streakFolder: String? = null,
    val streakNewest: FoundFile? = null,
    val lastStreakImport: String? = null,
    val nutriFolder: String? = null,
    val nutriNewest: FoundFile? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

class DataSourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app.applicationContext
    private val sources = DataSources(context)

    private val _state = MutableStateFlow(DataSourcesState())
    val state: StateFlow<DataSourcesState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val fresh = snapshot()
            _state.update { fresh.copy(message = it.message, busy = it.busy) }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun setFolder(slot: Slot, uri: Uri) {
        launchAction { 
            withContext(Dispatchers.IO) { sources.setFolder(slot, uri) }
            if (slot == Slot.Backup) BackupManager.probe(context, uri)
            "Folder saved."
        }
    }

    fun backupNow() {
        launchAction { "Backed up as ${BackupManager.backupNow(context, sources)}" }
    }

    fun importStreak() {
        launchAction {
            StreakImporter.importIfNew(context, force = true)?.describe()
                ?: "The Streak backup has nothing in it to import."
        }
    }

    fun restore(file: BackupFile) {
        launchAction {
            val folder = sources.folder(Slot.Backup) ?: error("Choose a backup folder first.")
            BackupManager.restore(context, folder, file)
            "Restored from ${file.name}. Your previous data was saved as a safety copy first."
        }
    }

    /** Runs a slow action, shows its result or a readable error, then refreshes the screen. */
    private fun launchAction(action: suspend () -> String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                action()
            } catch (e: Exception) {
                e.message ?: "Something went wrong."
            }
            val fresh = snapshot()
            _state.value = fresh.copy(message = message, busy = false)
        }
    }

    private suspend fun snapshot(): DataSourcesState = withContext(Dispatchers.IO) {
        val backup = sources.folder(Slot.Backup)
        val streak = sources.folder(Slot.Streak)
        val nutri = sources.folder(Slot.OpenNutriTracker)
        DataSourcesState(
            backupFolder = backup?.let(sources::folderName),
            lastBackup = sources.lastBackupText,
            backups = backup?.let { runCatching { BackupManager.list(context, it) }.getOrNull() } ?: emptyList(),
            streakFolder = streak?.let(sources::folderName),
            streakNewest = streak?.let { SourceScanner.newestStreak(context, it) },
            lastStreakImport = sources.lastStreakImportText?.let {
                runCatching { formatDateTime(java.time.LocalDateTime.parse(it).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) }.getOrNull()
            },
            nutriFolder = nutri?.let(sources::folderName),
            nutriNewest = nutri?.let { SourceScanner.newestOpenNutriTracker(context, it) },
        )
    }
}
