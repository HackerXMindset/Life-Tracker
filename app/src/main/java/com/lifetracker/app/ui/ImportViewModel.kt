package com.lifetracker.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.AppLog
import com.lifetracker.app.data.ImportFormats
import com.lifetracker.app.data.ImportParse
import com.lifetracker.app.data.ImportPlanner
import com.lifetracker.app.data.ImportPreview
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlacesTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.ZoneId

/** The Import card: read a file, show what it holds and what would be added, and add it only when you say so. */
class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Application = app

    /** What the card shows: [preview] once a file is read, [message] for what happened, [busy] while working. */
    class State(val busy: Boolean = false, val preview: ImportPreview? = null, val message: String = "", val canImport: Boolean = false)

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow

    private var pending: Pair<ImportParse, ImportPlanner.Plan>? = null
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Reads the file and works out the preview. Nothing is added yet. */
    fun choose(uri: Uri) {
        pending = null
        stateFlow.value = State(busy = true, message = "Reading the file...")
        viewModelScope.launch {
            stateFlow.value = runCatching {
                withContext(Dispatchers.IO) {
                    val head = context.contentResolver.openInputStream(uri)?.use { s ->
                        val bytes = ByteArray(4_000)
                        val n = s.read(bytes)
                        if (n > 0) String(bytes, 0, n, Charsets.UTF_8) else ""
                    } ?: throw IOException("Android would not open that file.")
                    val kind = ImportFormats.detect(head)
                    val parse = context.contentResolver.openInputStream(uri)?.use { ImportFormats.parse(kind, it, zone) }
                        ?: throw IOException("Android would not open that file.")
                    val existing = existingSeconds(parse)
                    val plan = ImportPlanner.plan(parse, existing, PlaceRules.MAX_ACCURACY_M.toFloat(), zone)
                    pending = parse to plan
                    State(preview = plan.preview, canImport = plan.indexes.isNotEmpty())
                }
            }.getOrElse { State(message = "The file could not be read: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    private suspend fun existingSeconds(parse: ImportParse): Set<Long> {
        val buf = parse.fixes
        if (buf.size == 0) return emptySet()
        var first = Long.MAX_VALUE
        var last = Long.MIN_VALUE
        for (i in 0 until buf.size) {
            first = minOf(first, buf.ms(i))
            last = maxOf(last, buf.ms(i))
        }
        val raw = AppDatabase.get(context).rawDao()
        val out = HashSet<Long>()
        val chunk = 7L * 24 * 60 * 60_000L
        var from = first
        while (from <= last) {
            raw.between(from, minOf(from + chunk, last + 1)).forEach { out += it.ms / 1000L }
            from += chunk
        }
        return out
    }

    /** Adds the new readings (only adds; nothing is replaced), then works the visits out again. */
    fun confirm() {
        val (parse, plan) = pending ?: return
        stateFlow.value = State(busy = true, preview = plan.preview, message = "Adding the readings...")
        viewModelScope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val raw = AppDatabase.get(context).rawDao()
                    val before = raw.count()
                    plan.indexes.toList().chunked(2_000).forEach { part -> raw.insertMissing(part.map { parse.fixes.fix(it) }) }
                    val added = raw.count() - before
                    if (added > 0) runCatching { PlacesTracker.cleanupAll(context) }
                    AppLog.i("Import", "Imported $added reading(s) from a ${plan.preview.kind.label} file")
                    "Added $added reading${if (added == 1) "" else "s"}." + if (added > 0) " Visits and trips were worked out again." else ""
                }
            }.getOrElse { "Import failed: ${it.message ?: it.javaClass.simpleName}" }
            pending = null
            stateFlow.value = State(message = message)
        }
    }

    fun cancel() {
        pending = null
        stateFlow.value = State()
    }

    fun previewText(p: ImportPreview): String = p.text(zone)
}
