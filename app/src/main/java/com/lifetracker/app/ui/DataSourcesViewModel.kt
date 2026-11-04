package com.lifetracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.BackupManager
import com.lifetracker.app.data.BackupSummary
import com.lifetracker.app.data.BackupManager.BackupFile
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.DataSources.Slot
import com.lifetracker.app.data.FoundFile
import com.lifetracker.app.data.SourceScanner
import com.lifetracker.app.data.ont.OntImporter
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
    val lastOntImport: String? = null,
    val nutriFolder: String? = null,
    val nutriNewest: FoundFile? = null,
    val message: String? = null,
    val busy: Boolean = false,
    /** A backup that was opened to be looked at, waiting for "Replace everything". */
    val pending: PendingRestore? = null,
    /** Set after a restore that put settings back: the app must restart to use them. */
    val restartNeeded: Boolean = false,
)

/** A backup read and checked, shown to the person (what it holds) before it replaces anything. */
class PendingRestore(val name: String, val uri: Uri, val isSafety: Boolean, val summary: String, val hasSettings: Boolean, val exportedAt: String)

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
            _state.update { fresh.copy(message = it.message, busy = it.busy, pending = it.pending, restartNeeded = it.restartNeeded) }
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

    fun importOpenNutriTracker() {
        launchAction {
            OntImporter.importIfNew(context, force = true)?.describe()
                ?: "The OpenNutriTracker export has nothing in it to import."
        }
    }

    /** Opens a backup from the list and shows what it holds. Nothing is changed until [confirmRestore]. */
    fun preview(file: BackupFile) = preview(file.uri, file.name, file.isSafety)

    /** Opens a backup file picked anywhere on the phone and shows what it holds. */
    fun previewFile(uri: Uri) = preview(uri, uri.lastPathSegment ?: "that file", false)

    private fun preview(uri: Uri, name: String, safety: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = runCatching { BackupManager.peek(context, uri) }
            _state.update {
                result.fold(
                    onSuccess = { data ->
                        it.copy(
                            busy = false,
                            pending = PendingRestore(name, uri, safety, BackupSummary.text(data), BackupSummary.hasSettings(data), data.exportedAt),
                        )
                    },
                    onFailure = { e -> it.copy(busy = false, message = e.message ?: "That file could not be read.") },
                )
            }
        }
    }

    fun cancelRestore() {
        _state.update { it.copy(pending = null) }
    }

    fun confirmRestore() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null, busy = true, message = null) }
        viewModelScope.launch {
            val outcome = runCatching { BackupManager.restoreFromFile(context, pending.uri) }
            val fresh = snapshot()
            _state.value = outcome.fold(
                onSuccess = { r ->
                    fresh.copy(
                        busy = false,
                        restartNeeded = r.settings > 0,
                        message = "Restored from ${pending.name}. Your previous data was saved as a safety copy first.",
                    )
                },
                onFailure = { e -> fresh.copy(busy = false, message = e.message ?: "The restore did not work.") },
            )
        }
    }

    /** Saves the one backup file to a place the person picked. */
    fun saveCopy(uri: Uri) {
        launchAction {
            val data = BackupManager.exportTo(context, uri)
            "Saved one backup file with everything: " + BackupSummary.lines(data).size + " kinds of data and " + BackupSummary.settingCount(data) + " settings."
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
            backups = runCatching { BackupManager.list(context, backup) }.getOrNull() ?: emptyList(),
            streakFolder = streak?.let(sources::folderName),
            streakNewest = streak?.let { SourceScanner.newestStreak(context, it) },
            lastStreakImport = sources.lastStreakImportText?.let {
                runCatching { formatDateTime(java.time.LocalDateTime.parse(it).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) }.getOrNull()
            },
            nutriFolder = nutri?.let(sources::folderName),
            nutriNewest = nutri?.let { SourceScanner.newestOpenNutriTracker(context, it) },
            lastOntImport = sources.lastOntImportText?.let {
                runCatching { formatDateTime(java.time.LocalDateTime.parse(it).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) }.getOrNull()
            },
        )
    }
}
