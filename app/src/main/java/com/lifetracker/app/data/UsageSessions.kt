package com.lifetracker.app.data

/** The few kinds of Android usage event the app cares about. */
enum class RawType { RESUME, PAUSE, SCREEN_OFF, SHUTDOWN }

/** One usage event: at [time] (ms), something happened to app [pkg]. */
data class RawEvent(val time: Long, val pkg: String, val type: RawType)

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
        var start = 0L

        fun close(at: Long) {
            val pkg = current ?: return
            if (at > start) raw += UsageSessionEntity(UsageSessionEntity.idFor(pkg, start), pkg, start, at)
            current = null
        }

        for (e in events) {
            when (e.type) {
                RawType.RESUME -> if (current != e.pkg) {
                    close(e.time)
                    current = e.pkg
                    start = e.time
                }
                RawType.PAUSE -> if (current == e.pkg) close(e.time)
                RawType.SCREEN_OFF, RawType.SHUTDOWN -> close(e.time)
            }
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

/** Turns one day's sessions into what the Timeline and the Phone usage screen show. */
object UsageDays {
    /** Stretches of the same app closer than this are shown as one block. */
    const val BLOCK_GAP_MS = 2 * 60_000L

    /** A run of one app, with [activeMs] actually on screen inside it. */
    data class Block(
        val pkg: String,
        val label: String,
        val activityId: String,
        val startMs: Long,
        val endMs: Long,
        val activeMs: Long,
    )

    data class Day(
        val blocks: List<Block>,
        val totalMs: Long,
        /** Time per app, biggest first (ignored apps left out). */
        val perApp: List<Pair<String, Long>>,
        /** Minutes per linked activity, to count towards goals. */
        val minutesByActivity: Map<String, Int>,
    )

    fun build(
        sessions: List<UsageSessionEntity>,
        apps: Map<String, UsageAppEntity>,
        dayStart: Long,
        dayEnd: Long,
        blockGapMs: Long = BLOCK_GAP_MS,
    ): Day {
        // Cut sessions at midnight, so a late-night session counts towards the right days.
        val clipped = sessions.mapNotNull { s ->
            val a = maxOf(s.startMs, dayStart)
            val b = minOf(s.endMs, dayEnd)
            if (b > a && apps[s.pkg]?.ignored != true) s.copy(startMs = a, endMs = b) else null
        }.sortedBy { it.startMs }

        val blocks = ArrayList<Block>()
        for (s in clipped) {
            val info = apps[s.pkg]
            val last = blocks.lastOrNull()
            if (last != null && last.pkg == s.pkg && s.startMs - last.endMs <= blockGapMs) {
                blocks[blocks.size - 1] = last.copy(endMs = maxOf(last.endMs, s.endMs), activeMs = last.activeMs + (s.endMs - s.startMs))
            } else {
                blocks += Block(s.pkg, info?.label ?: s.pkg, info?.activityId.orEmpty(), s.startMs, s.endMs, s.endMs - s.startMs)
            }
        }

        val perApp = clipped.groupBy { it.pkg }
            .map { (pkg, list) -> pkg to list.sumOf { it.endMs - it.startMs } }
            .sortedByDescending { it.second }
        val byActivity = HashMap<String, Long>()
        for ((pkg, ms) in perApp) {
            val activity = apps[pkg]?.activityId.orEmpty()
            if (activity.isNotEmpty()) byActivity[activity] = (byActivity[activity] ?: 0L) + ms
        }
        return Day(
            blocks = blocks,
            totalMs = perApp.sumOf { it.second },
            perApp = perApp,
            minutesByActivity = byActivity.mapValues { (it.value / 60_000L).toInt() },
        )
    }
}
