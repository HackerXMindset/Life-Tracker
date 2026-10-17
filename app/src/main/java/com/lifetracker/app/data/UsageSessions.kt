package com.lifetracker.app.data

/** The few kinds of Android usage event the app cares about. */
enum class RawType {
    RESUME,
    PAUSE,

    /** An activity was stopped. It can arrive late, after the next screen has already opened, so it is only trusted for the screen on top. */
    STOP,

    /** The screen turned off or the phone was locked. */
    SCREEN_OFF,
    SHUTDOWN,

    /** The phone started again. If it died without saying so, whatever was open ended at the last event before this. */
    STARTUP,
}

/** One usage event: at [time] (ms), something happened to screen [cls] of app [pkg]. [cls] is empty when not known. */
data class RawEvent(val time: Long, val pkg: String, val type: RawType, val cls: String = "")

/**
 * Turns Android's raw app events into sessions: one stretch of one app on screen.
 * Kept free of Android so it can be tested.
 */
object UsageSessions {
    /** Sessions shorter than this are noise (an app flashing past). */
    const val MIN_SESSION_MS = 3_000L

    /** Two stretches of the same app closer than this are one (moving between screens inside an app). */
    const val JOIN_GAP_MS = 2_000L

    /**
     * [events] must be in time order. An app is on screen from its RESUME until it is paused,
     * another app comes up, the screen turns off, or the phone shuts down. An app still on
     * screen at [until] is closed there; the next sync, which re-reads from that session's
     * start, extends it (same id), so it is never counted twice.
     * Sessions of [skip] packages (launcher, system) are dropped after the fact, so they still
     * end whatever was open before them.
     */
    fun build(events: List<RawEvent>, until: Long, skip: Set<String> = emptySet()): List<UsageSessionEntity> {
        val raw = ArrayList<UsageSessionEntity>()
        var current: String? = null
        var currentCls = ""
        var start = 0L
        var lastTime = 0L

        fun close(at: Long) {
            val pkg = current ?: return
            if (at > start) raw += UsageSessionEntity(UsageSessionEntity.idFor(pkg, start), pkg, start, at)
            current = null
            currentCls = ""
        }

        // An event for another screen of the same app is about a screen that is no longer on top, so it must not end the session.
        fun isTopScreen(cls: String) = cls.isEmpty() || currentCls.isEmpty() || cls == currentCls

        for (e in events) {
            when (e.type) {
                RawType.RESUME -> if (current == e.pkg) {
                    // Moving to another screen of the same app: still the same stretch.
                    currentCls = e.cls
                } else {
                    close(e.time)
                    current = e.pkg
                    currentCls = e.cls
                    start = e.time
                }
                RawType.PAUSE -> if (current == e.pkg && isTopScreen(e.cls)) close(e.time)
                RawType.STOP -> if (current == e.pkg && e.cls.isNotEmpty() && e.cls == currentCls) close(e.time)
                RawType.SCREEN_OFF, RawType.SHUTDOWN -> close(e.time)
                RawType.STARTUP -> close(lastTime)
            }
            if (e.type != RawType.STARTUP) lastTime = e.time
        }
        close(until)

        // Join back-to-back stretches of the same app, then drop the tiny ones and the skipped apps.
        val joined = ArrayList<UsageSessionEntity>()
        for (s in raw) {
            val last = joined.lastOrNull()
            if (last != null && last.pkg == s.pkg && s.startMs - last.endMs <= JOIN_GAP_MS) {
                joined[joined.size - 1] = last.copy(endMs = s.endMs)
            } else {
                joined += s
            }
        }
        return joined.filter { it.pkg !in skip && it.endMs - it.startMs >= MIN_SESSION_MS }
    }
}

/**
 * Turns one day's sessions into what the Timeline and the Phone usage screen show.
 *
 * A **period of use** is a run of phone use. It ends after [IDLE_GAP_MS] without using any app, or when an app from a
 * different category starts (unless the two apps are part of a combination you named). Every app used inside a period
 * counts towards the period's activity, so ChatGPT used while studying counts as studying when you have said so.
 * Which activity a period gets:
 *  1. the activity of a named combination whose apps were used together,
 *  2. else the category the apps share (one app, or several apps with the same category),
 *  3. else, when several apps with no category were used together, Screen and leisure,
 *  4. else (one app with no category) none, and the Timeline shows just the app.
 * Nothing is stored: periods are worked out from the saved sessions each time they are drawn, so naming a combination
 * later also changes the past days, and nothing is ever lost.
 */
object UsageDays {
    /** No app used for this long ends a period. */
    const val IDLE_GAP_MS = 5 * 60_000L

    /** What several apps used together count as when nothing says otherwise. */
    const val MIXED_ACTIVITY_ID = "Screen"

    /** One app inside a period: how long it was really on screen and how many separate times it was opened. */
    data class AppUse(
        val pkg: String,
        val label: String,
        val activityId: String,
        val activeMs: Long,
        val opens: Int,
    )

    /** A combination of apps you named: when these are used together the period counts as [activityId]. */
    data class Combo(val id: String, val pkgs: Set<String>, val activityId: String)

    /** A period of use, with [activeMs] actually on screen inside it. For one app [label] is its name; for several, the names joined. */
    data class Block(
        val pkg: String,
        val label: String,
        val activityId: String,
        val startMs: Long,
        val endMs: Long,
        val activeMs: Long,
        val opens: Int = 1,
        /** Where the period began as the phone recorded it; stays the same when you move the block, so your change can be found again. */
        val refStartMs: Long = startMs,
        /** The stretches of real use inside this block (start, end), so totals and the pie count only time on screen. */
        val spans: List<Pair<Long, Long>> = emptyList(),
        /** Every app used in the period, longest first. */
        val apps: List<AppUse> = emptyList(),
        /** The named combination that decided the activity, or empty. */
        val comboId: String = "",
    )

    data class Day(
        val blocks: List<Block>,
        val totalMs: Long,
        /** Time per app, biggest first (ignored apps left out). */
        val perApp: List<Pair<String, Long>>,
        /** Minutes per activity, to count towards goals. Each period counts for its own activity. */
        val minutesByActivity: Map<String, Int>,
        /** The name of every app seen, to label [perApp]. */
        val appLabels: Map<String, String> = emptyMap(),
    )

    /** Whether app [pkg] belongs to the period that already holds [inPeriod]. */
    internal fun joins(inPeriod: Set<String>, pkg: String, category: (String) -> String, combos: List<Combo>): Boolean {
        if (pkg in inPeriod) return true
        if (combos.any { pkg in it.pkgs && inPeriod.any { a -> a in it.pkgs } }) return true
        val first = category(inPeriod.first())
        return inPeriod.all { category(it) == first } && category(pkg) == first
    }

    /** Share of a period's time that the apps of a combination must make up, so one app used on the side does not decide it. */
    const val COMBO_MIN_SHARE = 0.8

    /**
     * The combination that decides a period: it needs at least two of its apps (or all of them if it has fewer), those apps must make
     * up at least 80% of the period's time (so an app of another kind used for less than 20% does not break it), and the one
     * covering the most time wins.
     */
    internal fun bestCombo(use: Map<String, Long>, combos: List<Combo>): Combo? {
        val total = use.values.sum()
        return combos
            .filter { c -> c.pkgs.count { it in use } >= minOf(2, c.pkgs.size) }
            .filter { c -> c.pkgs.sumOf { use[it] ?: 0L } >= total * COMBO_MIN_SHARE }
            .maxWithOrNull(compareBy<Combo>({ c -> c.pkgs.sumOf { use[it] ?: 0L } }, { it.id }))
    }

    private fun activeByApp(run: List<UsageSessionEntity>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (s in run) out[s.pkg] = (out[s.pkg] ?: 0L) + (s.endMs - s.startMs)
        return out
    }

    /**
     * A short stretch of something else between two stretches of a named combination (you studied, glanced at a game for two
     * minutes, and went back) does not end the combination: the three become one period when together they still match it.
     */
    private fun absorbInterruptions(runs: MutableList<MutableList<UsageSessionEntity>>, combos: List<Combo>, idleGapMs: Long) {
        if (combos.isEmpty()) return
        var i = 1
        while (i < runs.size - 1) {
            val before = runs[i - 1]
            val mid = runs[i]
            val after = runs[i + 1]
            val closeBefore = mid.first().startMs - before.maxOf { it.endMs } < idleGapMs
            val closeAfter = after.first().startMs - mid.maxOf { it.endMs } < idleGapMs
            if (closeBefore && closeAfter && bestCombo(activeByApp(before + mid + after), combos) != null) {
                runs[i - 1] = (before + mid + after).toMutableList()
                runs.removeAt(i + 1)
                runs.removeAt(i)
                i = maxOf(1, i - 1)
            } else {
                i++
            }
        }
    }

    fun build(
        sessions: List<UsageSessionEntity>,
        apps: Map<String, UsageAppEntity>,
        dayStart: Long,
        dayEnd: Long,
        idleGapMs: Long = IDLE_GAP_MS,
        combos: List<Combo> = emptyList(),
    ): Day {
        // Cut sessions at midnight, so a late-night session counts towards the right days.
        val clipped = sessions.mapNotNull { s ->
            val a = maxOf(s.startMs, dayStart)
            val b = minOf(s.endMs, dayEnd)
            if (b > a && apps[s.pkg]?.ignored != true) s.copy(startMs = a, endMs = b) else null
        }.sortedWith(compareBy({ it.startMs }, { it.endMs }))

        fun category(pkg: String) = apps[pkg]?.activityId.orEmpty()
        fun label(pkg: String) = apps[pkg]?.label ?: pkg

        // Group the sessions into periods.
        val runs = ArrayList<MutableList<UsageSessionEntity>>()
        var current: MutableList<UsageSessionEntity>? = null
        val inCurrent = LinkedHashSet<String>()
        var lastEnd = 0L
        for (s in clipped) {
            val run = current
            if (run != null && s.startMs - lastEnd < idleGapMs && joins(inCurrent, s.pkg, ::category, combos)) {
                run += s
            } else {
                val fresh = mutableListOf(s)
                runs += fresh
                current = fresh
                inCurrent.clear()
            }
            inCurrent += s.pkg
            lastEnd = maxOf(lastEnd, s.endMs)
        }

        absorbInterruptions(runs, combos, idleGapMs)

        val blocks = runs.map { run ->
            val active = activeByApp(run)
            val opens = HashMap<String, Int>()
            for (s in run) opens[s.pkg] = (opens[s.pkg] ?: 0) + 1
            val uses = active.map { (pkg, ms) -> AppUse(pkg, label(pkg), category(pkg), ms, opens[pkg] ?: 1) }
                .sortedWith(compareByDescending<AppUse> { it.activeMs }.thenBy { it.pkg })
            val combo = bestCombo(active, combos)
            val categories = uses.map { it.activityId }.distinct()
            val activity = when {
                combo != null -> combo.activityId
                uses.size == 1 -> categories.single()
                categories.size == 1 -> categories.single().ifEmpty { MIXED_ACTIVITY_ID }
                else -> MIXED_ACTIVITY_ID
            }
            val title = if (uses.size == 1) {
                uses.single().label
            } else {
                uses.take(3).joinToString(" + ") { it.label } + if (uses.size > 3) " + ${uses.size - 3} more" else ""
            }
            val first = run.first()
            Block(
                pkg = first.pkg,
                label = title,
                activityId = activity,
                startMs = first.startMs,
                endMs = run.maxOf { it.endMs },
                activeMs = uses.sumOf { it.activeMs },
                opens = run.size,
                spans = run.map { it.startMs to it.endMs },
                apps = uses,
                comboId = combo?.id.orEmpty(),
            )
        }

        val perApp = clipped.groupBy { it.pkg }
            .map { (pkg, list) -> pkg to list.sumOf { it.endMs - it.startMs } }
            .sortedByDescending { it.second }
        val byActivity = HashMap<String, Long>()
        for (b in blocks) if (b.activityId.isNotEmpty()) byActivity[b.activityId] = (byActivity[b.activityId] ?: 0L) + b.activeMs
        return Day(
            blocks = blocks,
            totalMs = perApp.sumOf { it.second },
            perApp = perApp,
            minutesByActivity = byActivity.mapValues { (it.value / 60_000L).toInt() },
            appLabels = perApp.associate { (pkg, _) -> pkg to label(pkg) },
        )
    }
}
