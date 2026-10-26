package com.lifetracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.ExportFormat
import com.lifetracker.app.data.ExportJob
import com.lifetracker.app.data.ExportPlan
import com.lifetracker.app.data.ExportScheduler
import com.lifetracker.app.data.ExportSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** The Export card: saving readings by hand, and the automatic export. */
class ExportViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Application = app
    private val settings = ExportSettings(app)
    private val sources = DataSources(app)

    /** What the automatic export is set to, shown on the card. */
    class Auto(val mode: String, val format: ExportFormat, val folderName: String?, val note: String)

    private fun readAuto(): Auto = Auto(
        settings.mode, settings.format,
        sources.folder(DataSources.Slot.Export)?.let { sources.folderName(it) },
        settings.lastNote,
    )

    private val autoFlow = MutableStateFlow(readAuto())
    val auto: StateFlow<Auto> = autoFlow

    private val messageFlow = MutableStateFlow("")

    /** What the last manual export did (or failed to do). */
    val message: StateFlow<String> = messageFlow

    fun refresh() {
        autoFlow.value = readAuto()
    }

    /** Saves the readings of [plan] into the file you chose. */
    fun export(uri: Uri, plan: ExportPlan, format: ExportFormat) {
        viewModelScope.launch {
            messageFlow.value = "Exporting..."
            messageFlow.value = runCatching {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Android would not open that file for writing.")
                    val count = ExportJob.write(AppDatabase.get(context), stream, format, plan.fromMs, plan.toMs)
                    if (count == 0) "No readings in that time, so the file is empty."
                    else "Exported $count reading${if (count == 1) "" else "s"} as ${format.label}."
                }
            }.getOrElse { "Export failed: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    fun setMode(mode: String) {
        settings.mode = mode
        ExportScheduler.apply(context)
        refresh()
    }

    fun setAutoFormat(format: ExportFormat) {
        settings.format = format
        refresh()
    }

    fun setFolder(uri: Uri) {
        runCatching { sources.setFolder(DataSources.Slot.Export, uri) }
            .onFailure { settings.lastNote = "Android would not keep access to that folder: ${it.message ?: ""}" }
        refresh()
    }

    fun exportNow() {
        ExportScheduler.runNow(context)
        settings.lastNote = "Exporting..."
        refresh()
    }
}
