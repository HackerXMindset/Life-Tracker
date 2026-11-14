package com.lifetracker.app.data

/** How a screen is locked. */
enum class LockMode(val key: String, val label: String) {
    OFF("off", "Off"),
    PHONE("phone", "Phone lock"),
    FACE("face", "Face scan");

    companion object {
        fun of(key: String?): LockMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** How close a face must be to yours to open a screen: the cosine similarity of the two face "fingerprints". Higher is stricter. */
enum class Strictness(val key: String, val label: String, val threshold: Float) {
    RELAXED("relaxed", "Relaxed", 0.60f),
    NORMAL("normal", "Normal", 0.70f),
    STRICT("strict", "Strict", 0.78f);

    companion object {
        fun of(key: String?): Strictness = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** The rules of the screen lock that need no phone: the timers, which mode really applies, and when the PIN takes over. */
object LockRules {
    /** "Unlocked right now": the screen is open and in use. */
    const val OPEN = Long.MAX_VALUE
    const val MAX_FACE_ATTEMPTS = 3

    /** Minutes a screen stays unlocked after you leave it, with the words for each. 0 locks it as soon as you leave. */
    val STAY_OPTIONS: List<Pair<Int, String>> = listOf(0 to "Every time", 1 to "1 minute", 5 to "5 minutes", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour")
    const val DEFAULT_STAY = 5

    fun stayLabel(minutes: Int): String = STAY_OPTIONS.firstOrNull { it.first == minutes }?.second ?: "$minutes minutes"

    /** Only the listed times are kept; anything else (an old or damaged setting) becomes the usual 5 minutes. */
    fun cleanStay(minutes: Int): Int = if (STAY_OPTIONS.any { it.first == minutes }) minutes else DEFAULT_STAY

    /** The moment a screen locks again: [stayMinutes] after you left it at [nowMs]. */
    fun lockedAgainAt(nowMs: Long, stayMinutes: Int): Long = nowMs + cleanStay(stayMinutes) * 60_000L

    fun isOpen(until: Long, nowMs: Long): Boolean = nowMs < until

    /** Coming back to a screen: still open if its time has not run out, otherwise locked (0 is long gone). */
    fun onReturn(until: Long, nowMs: Long): Long = if (isOpen(until, nowMs)) OPEN else 0L

    /** Face scan with no face saved cannot work, so the phone lock is used instead. */
    fun effectiveMode(mode: LockMode, faceSaved: Boolean): LockMode = if (mode == LockMode.FACE && !faceSaved) LockMode.PHONE else mode

    /** After this many scans that did not match, the phone PIN is asked for. */
    fun needsPin(failedScans: Int): Boolean = failedScans >= MAX_FACE_ATTEMPTS

    /** True when a lock is on for some screen other than Settings while Settings itself is open to anyone, so the locks could be switched off. */
    fun settingsLeftOpen(modes: Map<String, LockMode>, settingsKey: String): Boolean =
        modes.any { it.key != settingsKey && it.value != LockMode.OFF } && (modes[settingsKey] ?: LockMode.OFF) == LockMode.OFF
}
