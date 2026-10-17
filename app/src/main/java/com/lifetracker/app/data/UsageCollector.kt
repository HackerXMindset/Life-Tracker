package com.lifetracker.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads Android's app usage history (the same source Digital Wellbeing uses) and saves it
 * as sessions. Needs "Usage access", which you switch on once in Android's settings.
 */
object UsageCollector {
    // Android's event numbers, written out so this builds on every Android version the app supports.
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val ACTIVITY_STOPPED = 23
    private const val DEVICE_SHUTDOWN = 26
    private const val DEVICE_STARTUP = 27
    private const val KEYGUARD_SHOWN = 17

    /**
     * Raise this when the rules that turn events into sessions change. The next sync then builds the days Android still
     * remembers again, so they are counted the new way. Days Android no longer remembers are left exactly as they are.
     */
    private const val REBUILD_VERSION = 2

    private const val FIRST_LOOKBACK_MS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_LOOKBACK_MS = 30L * 24 * 60 * 60 * 1000

    /** Apps that are the phone itself, not something you used. Home screens are added at run time. */
    private val SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.miui.aod",
        "com.google.android.apps.nexuslauncher",
    )

    @Suppress("DEPRECATION")
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens Android's "Usage access" settings, on this app's entry if the phone allows it. */
    fun openAccessSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Copies new app history into the database. Returns how many sessions were read, or null without access. */
    suspend fun sync(context: Context, now: Long = System.currentTimeMillis()): Int? = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext null
        val db = AppDatabase.get(context)
        val dao = db.usageDao()

        // Start just before the newest saved session, so it is read again and extended if it was still going.
        val settings = UsageSettings(context)
        val rebuild = settings.rebuiltVersion < REBUILD_VERSION
        val latest = dao.latestStart()
        val from = when {
            rebuild || latest == null -> now - FIRST_LOOKBACK_MS
            else -> maxOf(latest - 5_000L, now - MAX_LOOKBACK_MS)
        }

        val events = readEvents(context, from, now)
        val sessions = UsageSessions.build(events, now, skipPackages(context))
        val known = dao.allApps().associateBy { it.pkg }
        val pm = context.packageManager
        val apps = sessions.map { it.pkg }.distinct().mapNotNull { pkg ->
            val label = labelOf(pm, pkg)
            val old = known[pkg]
            when {
                old == null -> UsageAppEntity(pkg, label, "", false)
                old.label != label -> old.copy(label = label)
                else -> null
            }
        }
        db.withTransaction {
            dao.upsertApps(apps)
            // Building again from the first event Android still has: what was saved from that moment on was built with the
            // old rules, so it is replaced. Anything before the first event is not touched.
            if (rebuild && events.isNotEmpty()) dao.deleteSessionsFrom(events.first().time)
            dao.upsertSessions(sessions)
        }
        if (rebuild && events.isNotEmpty()) settings.rebuiltVersion = REBUILD_VERSION
        sessions.size
    }

    /**
     * Android's own total of time in front for [startMs, endMs), summed over every app except the phone itself. It is only a
     * rough guide: it reads a little higher than Digital Wellbeing, which counts the events more carefully (as this app does).
     * Null without usage access.
     */
    suspend fun androidTotalMs(context: Context, startMs: Long, endMs: Long): Long? = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val skip = skipPackages(context)
        manager.queryAndAggregateUsageStats(startMs, endMs).filterKeys { it !in skip }.values.sumOf { it.totalTimeInForeground }
    }

    private fun readEvents(context: Context, from: Long, to: Long): List<RawEvent> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(from, to) ?: return emptyList()
        val out = ArrayList<RawEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: ""
            val type = when (e.eventType) {
                ACTIVITY_RESUMED -> RawType.RESUME
                ACTIVITY_PAUSED -> RawType.PAUSE
                ACTIVITY_STOPPED -> RawType.STOP
                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN -> RawType.SCREEN_OFF
                DEVICE_SHUTDOWN -> RawType.SHUTDOWN
                DEVICE_STARTUP -> RawType.STARTUP
                else -> null
            } ?: continue
            out += RawEvent(e.timeStamp, pkg, type, e.className ?: "")
        }
        return out.sortedBy { it.time }
    }

    @Suppress("DEPRECATION")
    private fun skipPackages(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        return SYSTEM_PACKAGES + launchers
    }

    @Suppress("DEPRECATION")
    private fun labelOf(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
