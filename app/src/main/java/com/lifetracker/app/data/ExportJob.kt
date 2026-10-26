package com.lifetracker.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** What the automatic export is set to do, and what it last did. Kept on the phone. */
class ExportSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("export", Context.MODE_PRIVATE)

    var mode: String
        get() = prefs.getString("mode", ExportSchedule.OFF) ?: ExportSchedule.OFF
        set(value) = prefs.edit { putString("mode", value) }

    var format: ExportFormat
        get() = ExportFormat.of(prefs.getString("format", "gpx") ?: "gpx")
        set(value) = prefs.edit { putString("format", value.id) }

    /** Where the last automatic export ended; the next one starts here. */
    var lastEndMs: Long
        get() = prefs.getLong("last_end_ms", 0L)
        set(value) = prefs.edit { putLong("last_end_ms", value) }

    var lastNote: String
        get() = prefs.getString("last_note", "") ?: ""
        set(value) = prefs.edit { putString("last_note", value) }
}

/** Writes readings to a stream or a folder, in chunks so a long history does not fill the memory. */
object ExportJob {
    private const val CHUNK_MS = 7L * 24 * 60 * 60_000L

    /** Writes the readings from [fromMs] (included) to [toMs] (not included). Returns how many were written. */
    suspend fun write(db: AppDatabase, stream: OutputStream, format: ExportFormat, fromMs: Long, toMs: Long): Int {
        val raw = db.rawDao()
        var count = 0
        stream.bufferedWriter(Charsets.UTF_8).use { w ->
            val writer = ReadingsWriter(w, format, "Life Tracker readings")
            writer.begin()
            var from = fromMs
            while (from < toMs) {
                val fixes = raw.between(from, minOf(from + CHUNK_MS, toMs))
                count += fixes.size
                writer.add(fixes)
                from += CHUNK_MS
            }
            writer.end()
        }
        return count
    }

    /** Writes into a file of the chosen folder; the readings from [fromMs] to [toMs]. Returns a line saying what happened. */
    suspend fun writeToFolder(context: Context, db: AppDatabase, folder: Uri, format: ExportFormat, fromMs: Long, toMs: Long): Pair<Int, String> {
        val dir = DocumentFile.fromTreeUri(context, folder)
        if (dir == null || !dir.exists() || !dir.canWrite()) throw IOException("The export folder is not available. Choose it again.")
        val zone = ZoneId.systemDefault()
        val (first, last) = ExportRange.days(fromMs, toMs, zone)
        val name = ExportRange.fileName(format, first, last, LocalDate.now(zone))
        val file = dir.createFile("application/octet-stream", name) ?: throw IOException("Android would not create the file.")
        val stream = context.contentResolver.openOutputStream(file.uri, "wt") ?: throw IOException("Android would not open the file for writing.")
        val count = write(db, stream, format, fromMs, toMs)
        return count to (file.name ?: name)
    }
}

/** The automatic export: once a day or once a week it saves the readings since the last time into your folder. */
class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = ExportSettings(applicationContext)
        val folder = DataSources(applicationContext).folder(DataSources.Slot.Export)
        if (settings.mode == ExportSchedule.OFF || folder == null) return Result.success()
        val db = AppDatabase.get(applicationContext)
        val now = System.currentTimeMillis()
        val span = ExportSchedule.span(settings.lastEndMs, db.rawDao().firstMs(), now)
        if (span == null) {
            settings.lastNote = "Nothing new to export (" + LogFormat.clock(now, ZoneId.systemDefault()) + ")"
            return Result.success()
        }
        return try {
            val (count, name) = if (db.rawDao().between(span.first, span.second).isEmpty()) {
                0 to ""
            } else {
                ExportJob.writeToFolder(applicationContext, db, folder, settings.format, span.first, span.second)
            }
            settings.lastEndMs = span.second
            settings.lastNote = if (count == 0) "No new readings (" + LogFormat.clock(now, ZoneId.systemDefault()) + ")"
            else "Saved $count reading${if (count == 1) "" else "s"} to $name"
            AppLog.i("Export", settings.lastNote)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            settings.lastNote = "Export failed: ${e.message ?: e.javaClass.simpleName}"
            AppLog.w("Export", settings.lastNote)
            Result.success()
        }
    }
}

/** Starts, changes or stops the automatic export to match its settings. */
object ExportScheduler {
    private const val PERIODIC = "auto-export"
    private const val NOW = "auto-export-now"

    fun apply(context: Context) {
        val app = context.applicationContext
        val hours = ExportSchedule.hours(ExportSettings(app).mode)
        val wm = WorkManager.getInstance(app)
        if (hours == 0L) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<ExportWorker>(hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** One export now, of what is new since the last one. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ExportWorker>().build())
    }
}
