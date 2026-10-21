package com.lifetracker.app.data

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's own diary, for working out what went wrong in the background. Every line is written to a text file for the
 * day on the phone (nothing is sent anywhere) and the latest ones are also kept in memory so the Live logs page can show
 * them as they arrive. Writing never throws and never slows the caller: it happens on one small background thread.
 * Days older than [LogFormat.KEEP_DAYS] are removed from the diary only.
 */
object AppLog {
    private const val MEMORY_LINES = 400
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "app-log").apply { isDaemon = true } }
    private val recent = ArrayDeque<LogLine>()

    @Volatile private var dir: File? = null

    private val revisionFlow = MutableStateFlow(0)

    /** Goes up by one with every line written, so a screen knows when to look again. */
    val revision: StateFlow<Int> = revisionFlow

    fun i(tag: String, text: String) = add(LogLevel.Info, tag, text)
    fun w(tag: String, text: String) = add(LogLevel.Warn, tag, text)
    fun e(tag: String, text: String, error: Throwable? = null) =
        add(LogLevel.Error, tag, if (error == null) text else "$text: ${error.javaClass.simpleName} ${error.message.orEmpty()}")

    /** Called once when the app process starts. */
    fun init(context: Context) {
        if (dir != null) return
        val app = context.applicationContext
        dir = File(app.filesDir, "logs").also { it.mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // The process is about to die, so this one is written on the spot.
            runCatching {
                val trace = error.stackTraceToString().lineSequence().take(14).joinToString(" ⏎ ")
                append(LogLine(System.currentTimeMillis(), LogLevel.Error, "Crash", "${thread.name}: $trace"))
            }
            previous?.uncaughtException(thread, error)
        }
        writer.execute { runCatching { trimOldDays() } }
        runCatching { describeStart(app) }
    }

    /** What the phone says about this start: why the last run ended, and the battery rules that may stop the app. */
    private fun describeStart(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        val exempt = KeepAlive.isExempt(context)
        val saver = power?.isPowerSaveMode == true
        val bucket = if (Build.VERSION.SDK_INT >= 28) {
            when (context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket) {
                10 -> "active"
                20 -> "working set"
                30 -> "frequent"
                40 -> "rare"
                45 -> "restricted"
                5 -> "exempted"
                50 -> "never"
                else -> "unknown"
            }
        } else {
            "n/a"
        }
        i(
            "App",
            "Started (Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}); " +
                "unrestricted battery: ${if (exempt) "yes" else "NO"}; battery saver: ${if (saver) "ON" else "off"}; standby group: $bucket",
        )
        if (Build.VERSION.SDK_INT >= 30) {
            val prefs = context.getSharedPreferences("applog", Context.MODE_PRIVATE)
            val seen = prefs.getLong("exit_seen_ms", 0L)
            val exits = context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(context.packageName, 0, 8).orEmpty()
                .filter { it.timestamp > seen }
                .sortedBy { it.timestamp }
            for (x in exits) {
                add(
                    exitLevel(x.reason),
                    "App",
                    "The last run ended: ${exitReason(x.reason)}${x.description?.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty()}",
                    x.timestamp,
                )
            }
            exits.lastOrNull()?.let { prefs.edit().putLong("exit_seen_ms", it.timestamp).apply() }
        }
    }

    /** Android's own numbers (ApplicationExitInfo.REASON_*), in words. */
    private fun exitReason(code: Int): String = when (code) {
        1 -> "the app closed itself"
        2 -> "ended by Android with a signal"
        3 -> "Android closed the idle app to free memory (normal for an app not on screen; Android's messages start it again)"
        4 -> "CRASH: a Java exception"
        5 -> "CRASH: native code"
        6 -> "the app stopped answering (ANR)"
        7 -> "the app failed while starting"
        8 -> "closed because a permission was changed"
        9 -> "closed for using too many resources"
        10 -> "closed by you from the app list (user request)"
        11 -> "force-stopped"
        12 -> "closed because something it depended on ended"
        13 -> "closed by the system or the phone's own cleaner (for example swiping the app away)"
        14 -> "frozen by Android"
        15 -> "closed because the app was changed (disabled or its data cleared)"
        16 -> "closed because the app was updated"
        else -> "other reason ($code)"
    }

    /** Only real trouble is a warning or an error; the ordinary ways Android ends an idle app are plain lines. */
    private fun exitLevel(code: Int): LogLevel = when (code) {
        4, 5, 6, 7 -> LogLevel.Error
        3, 10, 13, 16 -> LogLevel.Info
        else -> LogLevel.Warn
    }

    private fun add(level: LogLevel, tag: String, text: String, ms: Long = System.currentTimeMillis()) {
        val line = LogLine(ms, level, tag, text)
        Log.println(
            when (level) { LogLevel.Info -> Log.INFO; LogLevel.Warn -> Log.WARN; LogLevel.Error -> Log.ERROR },
            "LifeTracker/$tag",
            text,
        )
        synchronized(recent) {
            recent.addLast(line)
            while (recent.size > MEMORY_LINES) recent.removeFirst()
        }
        revisionFlow.value = revisionFlow.value + 1
        if (dir != null) writer.execute { runCatching { append(line) } }
    }

    @Synchronized
    private fun append(line: LogLine) {
        val folder = dir ?: return
        File(folder, LogFormat.fileName(LogFormat.dayOf(line.ms))).appendText(LogFormat.encode(line) + "\n")
    }

    /** The newest lines written since the app started, oldest first. */
    fun recentLines(): List<LogLine> = synchronized(recent) { recent.toList() }.sortedBy { it.ms }

    /** Days that have a diary file, newest first. */
    fun days(): List<LocalDate> =
        dir?.listFiles().orEmpty().mapNotNull { LogFormat.dayOfFile(it.name) }.sortedDescending()

    /** Every line of [day], oldest first. */
    @Synchronized
    fun read(day: LocalDate): List<LogLine> {
        val file = dir?.let { File(it, LogFormat.fileName(day)) } ?: return emptyList()
        if (!file.exists()) return emptyList()
        // Lines about earlier events (why the last run ended) are written late, so they are put back in time order.
        return runCatching { file.readLines().mapNotNull { LogFormat.decode(it) }.sortedBy { it.ms } }.getOrDefault(emptyList())
    }

    /** How much room the diary takes, in bytes. */
    fun sizeBytes(): Long = dir?.listFiles().orEmpty().sumOf { it.length() }

    /** Removes the whole diary (only when you ask). */
    @Synchronized
    fun clear() {
        dir?.listFiles().orEmpty().forEach { runCatching { it.delete() } }
        synchronized(recent) { recent.clear() }
        revisionFlow.value = revisionFlow.value + 1
    }

    @Synchronized
    private fun trimOldDays() {
        val folder = dir ?: return
        val files = folder.listFiles().orEmpty()
        val stale = LogFormat.staleDays(files.mapNotNull { LogFormat.dayOfFile(it.name) }, LocalDate.now()).toSet()
        files.filter { LogFormat.dayOfFile(it.name) in stale }.forEach { runCatching { it.delete() } }
    }
}
