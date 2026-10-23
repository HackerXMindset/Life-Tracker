package com.lifetracker.app.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject

/** One finished stretch of focus, as it is kept (Habits H13). [startedAtMillis] is when it began. */
data class FocusSessionData(
    val id: String,
    val habitId: String,
    val targetMinutes: Int,
    val seconds: Int,
    val completed: Boolean,
    val startedAtMillis: Long,
    val label: String = "",
) {
    val minutes: Int get() = seconds / 60
}

/**
 * The focus timer's state (Habits H13). It keeps moments, not a ticking count: when the running stretch began and how
 * much was done before it, so it is right after the app was closed or the phone slept.
 */
data class FocusState(
    val habitId: String = "",
    val label: String = "",
    /** Minutes of the current phase. 0 or less: a stopwatch (Streak's "flow"), which just counts up. */
    val targetMinutes: Int = 25,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 0,
    val longBreakMinutes: Int = 0,
    val isBreak: Boolean = false,
    val round: Int = 1,
    /** Seconds already done in this phase before the running stretch (all of it while paused). */
    val accumulatedSeconds: Int = 0,
    /** When the running stretch began, or null while paused. */
    val sinceMillis: Long? = null,
    /** A break is over and the timer waits for Continue. */
    val awaiting: Boolean = false,
    /** A countdown reached its target. It was saved at once. */
    val finished: Boolean = false,
    /** Stopping a Pomodoro round early drops that round. */
    val wholeRounds: Boolean = false,
)

/** What happened when a phase ended: the new state, a round to keep (if any), and what to tell the person. */
data class PhaseEnd(val state: FocusState, val saved: FocusSessionData?, val alert: Alert) {
    enum class Alert { None, CountdownDone, BreakStarts, BreakOver }
}

/** The rules of the focus timer, copied from Streak 2.4. No Android in it, so it can be tested. */
object FocusTimer {
    const val MIN_SESSION_SECONDS = 30
    const val LONG_BREAK_EVERY = 4

    fun isFlow(s: FocusState): Boolean = s.targetMinutes <= 0
    fun isPomodoro(s: FocusState): Boolean = s.breakMinutes > 0 && !isFlow(s)
    fun isRunning(s: FocusState): Boolean = s.sinceMillis != null
    fun isLongBreak(s: FocusState): Boolean = s.isBreak && s.longBreakMinutes > 0 && s.round % LONG_BREAK_EVERY == 0
    fun targetSeconds(s: FocusState): Int = s.targetMinutes * 60

    fun start(
        habitId: String,
        label: String,
        minutes: Int,
        breakMinutes: Int,
        longBreakMinutes: Int,
        wholeRounds: Boolean,
        now: Long,
    ): FocusState = FocusState(
        habitId = habitId,
        label = label.trim(),
        targetMinutes = minutes,
        focusMinutes = minutes,
        breakMinutes = if (minutes <= 0) 0 else breakMinutes.coerceAtLeast(0),
        longBreakMinutes = if (minutes <= 0) 0 else longBreakMinutes.coerceAtLeast(0),
        wholeRounds = wholeRounds,
        sinceMillis = now,
    )

    /** Seconds done in this phase at [at]. A countdown never shows more than its target. */
    fun elapsedSeconds(s: FocusState, at: Long): Int {
        val live = s.sinceMillis?.let { ((at - it) / 1000).toInt().coerceAtLeast(0) } ?: 0
        val total = s.accumulatedSeconds + live
        return if (isFlow(s)) total else total.coerceIn(0, targetSeconds(s))
    }

    fun reachedTarget(s: FocusState, at: Long): Boolean = !isFlow(s) && elapsedSeconds(s, at) >= targetSeconds(s)

    /** What the big clock shows: seconds left, or, for a stopwatch, seconds done. */
    fun displaySeconds(s: FocusState, at: Long): Int =
        if (isFlow(s)) elapsedSeconds(s, at) else targetSeconds(s) - elapsedSeconds(s, at)

    /** When this phase ends if it keeps running, or null (paused, a stopwatch, waiting, or finished). */
    fun phaseEndMillis(s: FocusState): Long? {
        val since = s.sinceMillis ?: return null
        if (isFlow(s) || s.awaiting || s.finished) return null
        return since + (targetSeconds(s) - s.accumulatedSeconds) * 1000L
    }

    /** True when the running phase has reached its end and [onPhaseEnd] should be applied. */
    fun phaseDue(s: FocusState, at: Long): Boolean {
        val end = phaseEndMillis(s) ?: return false
        return at >= end
    }

    fun pause(s: FocusState, at: Long): FocusState {
        if (s.sinceMillis == null) return s
        return s.copy(accumulatedSeconds = elapsedSeconds(s, at), sinceMillis = null)
    }

    fun resume(s: FocusState, at: Long): FocusState {
        if (s.sinceMillis != null || s.awaiting || s.finished) return s
        return s.copy(sinceMillis = at)
    }

    /** One more minute on this phase. Not for a stopwatch, nor once a countdown is saved. */
    fun addMinute(s: FocusState, at: Long): FocusState {
        if (isFlow(s) || s.finished) return s
        val base = if (reachedTarget(s, at)) s.copy(accumulatedSeconds = targetSeconds(s), sinceMillis = s.sinceMillis?.let { at }) else s
        return base.copy(targetMinutes = base.targetMinutes + 1, awaiting = false)
    }

    /** Starts this phase again from zero (keeps running if it was running). */
    fun reset(s: FocusState, at: Long): FocusState =
        s.copy(accumulatedSeconds = 0, sinceMillis = if (s.sinceMillis != null) at else null, awaiting = false, finished = false)

    /**
     * The phase reached its end. A countdown is saved at once and finishes; a Pomodoro focus round is saved and the break
     * starts by itself; a finished break waits for Continue. A stopwatch never ends by itself.
     */
    fun onPhaseEnd(s: FocusState, now: Long, newId: () -> String): PhaseEnd {
        val end = phaseEndMillis(s) ?: return PhaseEnd(s, null, PhaseEnd.Alert.None)
        return when {
            isPomodoro(s) && !s.isBreak -> {
                val saved = roundOf(s, end, newId)
                PhaseEnd(nextPhase(s, now), saved, PhaseEnd.Alert.BreakStarts)
            }
            isPomodoro(s) && s.isBreak ->
                PhaseEnd(s.copy(accumulatedSeconds = targetSeconds(s), sinceMillis = null, awaiting = true), null, PhaseEnd.Alert.BreakOver)
            else -> {
                val saved = roundOf(s, end, newId)
                PhaseEnd(s.copy(accumulatedSeconds = targetSeconds(s), sinceMillis = null, finished = true), saved, PhaseEnd.Alert.CountdownDone)
            }
        }
    }

    /** Continue (after a break, or early) or Skip break: on to the next phase. Returns the round it saved, if it ended a focus round. */
    fun advance(s: FocusState, now: Long, newId: () -> String): PhaseEnd {
        if (!isPomodoro(s)) return PhaseEnd(s, null, PhaseEnd.Alert.None)
        val saved = if (!s.isBreak) roundOf(s, now, newId, endedEarly = true) else null
        return PhaseEnd(nextPhase(s, now), saved, PhaseEnd.Alert.None)
    }

    private fun nextPhase(s: FocusState, now: Long): FocusState {
        val toBreak = !s.isBreak
        val round = if (s.isBreak) s.round + 1 else s.round
        val after = s.copy(isBreak = toBreak, round = round)
        val minutes = when {
            !toBreak -> s.focusMinutes
            isLongBreak(after) -> s.longBreakMinutes
            else -> s.breakMinutes
        }
        return after.copy(targetMinutes = minutes, accumulatedSeconds = 0, sinceMillis = now, awaiting = false, finished = false)
    }

    private fun roundOf(s: FocusState, endedAt: Long, newId: () -> String, endedEarly: Boolean = false): FocusSessionData? {
        val seconds = elapsedSeconds(s, endedAt)
        if (seconds < MIN_SESSION_SECONDS) return null
        return FocusSessionData(
            id = newId(),
            habitId = s.habitId,
            targetMinutes = s.focusMinutes,
            seconds = seconds,
            completed = !endedEarly || seconds >= targetSeconds(s),
            startedAtMillis = endedAt - seconds * 1000L,
            label = s.label,
        )
    }

    /**
     * The session to keep when the person ends the timer, or null when there is nothing to keep (under 30 seconds, a
     * break, or a dropped Pomodoro round). [completed] is whether the goal counts as met.
     */
    fun stop(s: FocusState, now: Long, completed: Boolean, newId: () -> String): FocusSessionData? {
        if (s.finished) return null // already saved when it finished
        val dropped = isPomodoro(s) && !completed && s.wholeRounds
        if (s.isBreak || dropped) return null
        val seconds = elapsedSeconds(s, now)
        if (seconds < MIN_SESSION_SECONDS) return null
        return FocusSessionData(newId(), s.habitId, s.focusMinutes, seconds, completed, now - seconds * 1000L, s.label)
    }

    /** Whether ending now counts as meeting the goal: a stopwatch always does, a countdown once its target is reached. */
    fun metGoal(s: FocusState, now: Long): Boolean = isFlow(s) || reachedTarget(s, now) || s.finished

    /**
     * A session split at midnight, because a Timeline block belongs to one day. Under two pieces, or any piece under a
     * minute, it stays whole. The first piece keeps the id, later ones get "-2", "-3"...; only the last is "completed".
     */
    fun split(session: FocusSessionData, zone: ZoneId = ZoneId.systemDefault()): List<FocusSessionData> {
        val pieces = ArrayList<FocusSessionData>()
        var start = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.startedAtMillis), zone)
        var left = session.seconds
        while (left > 0) {
            val border = start.toLocalDate().plusDays(1).atStartOfDay()
            val gap = java.time.Duration.between(start, border).seconds.toInt().coerceAtLeast(1)
            val last = gap >= left
            val span = if (last) left else gap
            pieces += session.copy(
                id = if (pieces.isEmpty()) session.id else "${session.id}-${pieces.size + 1}",
                seconds = span,
                completed = session.completed && last,
                startedAtMillis = start.atZone(zone).toInstant().toEpochMilli(),
            )
            if (last) break
            start = border
            left -= span
        }
        return if (pieces.size < 2 || pieces.any { it.seconds < 60 }) listOf(session) else pieces
    }

    fun toJson(s: FocusState): String = JSONObject()
        .put("habitId", s.habitId).put("label", s.label)
        .put("target", s.targetMinutes).put("focus", s.focusMinutes)
        .put("break", s.breakMinutes).put("longBreak", s.longBreakMinutes)
        .put("isBreak", s.isBreak).put("round", s.round)
        .put("acc", s.accumulatedSeconds).put("since", s.sinceMillis ?: JSONObject.NULL)
        .put("awaiting", s.awaiting).put("finished", s.finished).put("whole", s.wholeRounds)
        .toString()

    /** The saved state, or null when there is none or it cannot be read. */
    fun fromJson(text: String?): FocusState? {
        if (text.isNullOrBlank()) return null
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return FocusState(
            habitId = o.optString("habitId"),
            label = o.optString("label"),
            targetMinutes = o.optInt("target", 25),
            focusMinutes = o.optInt("focus", 25),
            breakMinutes = o.optInt("break", 0),
            longBreakMinutes = o.optInt("longBreak", 0),
            isBreak = o.optBoolean("isBreak"),
            round = o.optInt("round", 1).coerceAtLeast(1),
            accumulatedSeconds = o.optInt("acc", 0).coerceAtLeast(0),
            sinceMillis = if (o.isNull("since") || !o.has("since")) null else o.optLong("since"),
            awaiting = o.optBoolean("awaiting"),
            finished = o.optBoolean("finished"),
            wholeRounds = o.optBoolean("whole"),
        )
    }

    /** "25:00", or "1:05:00" from an hour up. */
    fun clock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
