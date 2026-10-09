package com.lifetracker.app.data

import kotlin.math.abs

/**
 * Charging arithmetic and wording. Kept free of Android so it can be tested.
 * The part that decides when a session starts and ends is [ChargeSessions].
 */
object ChargeStats {
    const val PLUG_AC = 1
    const val PLUG_USB = 2
    const val PLUG_WIRELESS = 4
    const val PLUG_DOCK = 8

    const val WALL = "wall"
    const val POWER_BANK = "powerbank"
    const val LAPTOP = "laptop"
    const val OTHER = "other"

    /** The choices on the banner and on the Charging screen, in order. */
    val SOURCES = listOf(WALL, POWER_BANK, LAPTOP, OTHER)

    fun sourceLabel(source: String): String = when (source) {
        WALL -> "Wall charger"
        POWER_BANK -> "Power bank"
        LAPTOP -> "Laptop"
        OTHER -> "Other"
        else -> ""
    }

    fun plugLabel(plugType: Int): String = when (plugType) {
        PLUG_AC -> "Charger"
        PLUG_USB -> "USB"
        PLUG_WIRELESS -> "Wireless"
        PLUG_DOCK -> "Dock"
        else -> "Charger"
    }

    /**
     * Android reports the charging current in microamps on most phones and in milliamps on a few.
     * Returns milliamps, or null if the phone gives no reading (0 or Long.MIN_VALUE).
     */
    fun toMilliamps(raw: Long): Int? {
        if (raw == 0L || raw == Long.MIN_VALUE) return null
        val a = abs(raw)
        return (if (a >= 20_000) a / 1000 else a).toInt()
    }

    fun durationMs(s: ChargeSessionEntity): Long = (s.endMs - s.startMs).coerceAtLeast(0)

    fun levelGain(s: ChargeSessionEntity): Int = s.endLevel - s.startLevel

    /** Average current in milliamps, or null if the phone never gave a reading. */
    fun avgMilliamps(s: ChargeSessionEntity): Int? =
        if (s.currentSamples <= 0) null else (s.sumMa / s.currentSamples).toInt()

    /**
     * Roughly how many watts went into the battery (average current times battery voltage).
     * This is lower than the charger's label, and lower still if you used the phone meanwhile.
     */
    fun avgWatts(s: ChargeSessionEntity): Double? {
        val ma = avgMilliamps(s) ?: return null
        if (s.samples <= 0 || s.sumMv <= 0) return null
        val volts = s.sumMv.toDouble() / s.samples / 1000.0
        return ma / 1000.0 * volts
    }

    fun wattsText(w: Double): String = "≈ " + (Math.round(w * 10) / 10.0).toString().removeSuffix(".0") + " W"

    /** How much of the charging time you were using the phone, from your phone usage sessions. */
    fun phoneUseMs(s: ChargeSessionEntity, usage: List<UsageSessionEntity>, nowMs: Long): Long {
        val end = if (s.ongoing) maxOf(s.endMs, nowMs) else s.endMs
        var total = 0L
        for (u in usage) {
            val from = maxOf(u.startMs, s.startMs)
            val to = minOf(u.endMs, end)
            if (to > from) total += to - from
        }
        return total
    }

    /**
     * What you probably charged with, learned from the sessions you tagged: the same kind of plug (and,
     * when known, about the same speed) that you tagged the same way most of the time. Null until there
     * is enough history to be fairly sure (at least 3 similar sessions, 70% the same answer).
     */
    fun guessSource(plugType: Int, watts: Double?, history: List<ChargeSessionEntity>): String? {
        val tagged = history.filter { it.source in SOURCES && it.plugType == plugType }
        val similar = if (watts == null) tagged else {
            tagged.filter {
                val w = avgWatts(it)
                w != null && abs(w - watts) <= maxOf(2.0, watts * 0.35)
            }
        }
        val pool = if (similar.size >= 3) similar else if (watts == null) tagged else emptyList()
        if (pool.size < 3) return null
        val (source, count) = pool.groupingBy { it.source }.eachCount().maxByOrNull { it.value } ?: return null
        return if (count.toDouble() / pool.size >= 0.7) source else null
    }

    data class Totals(val sessions: Int, val chargingMs: Long, val gainPoints: Int)

    fun totals(list: List<ChargeSessionEntity>) = Totals(
        sessions = list.size,
        chargingMs = list.sumOf { durationMs(it) },
        gainPoints = list.sumOf { levelGain(it).coerceAtLeast(0) },
    )

    /** "Wall charger", or what you plugged it into as far as we can tell: "Power bank (guess)", or just "USB". */
    fun title(s: ChargeSessionEntity, guess: String? = null): String = when {
        s.source in SOURCES -> sourceLabel(s.source)
        guess != null -> sourceLabel(guess) + " (guess)"
        else -> plugLabel(s.plugType)
    }

    fun durationText(ms: Long): String = CallStats.durationText((ms / 1000).toInt())

    /** One line: "34% → 88% · 2h 10m · ≈ 18 W". */
    fun detail(s: ChargeSessionEntity): String = buildList {
        add("${s.startLevel}% → ${s.endLevel}%")
        add(if (s.ongoing) "still charging" else durationText(durationMs(s)))
        avgWatts(s)?.let { add(wattsText(it)) }
    }.joinToString(" · ")
}

/**
 * Decides, from one look at the battery, whether a charging session began, carried on or ended.
 * Android only lets us look now and then (when plugged in and every 15 minutes while charging, or
 * instantly if the app happens to be running), so the end of a session is the last time we saw
 * it charging unless we saw the unplug itself.
 */
object ChargeSessions {
    /** If we have not seen a charging session for this long it must have been unplugged in between. */
    const val STALE_MS = 45L * 60 * 1000

    /** A drop of this many points while "charging" means it was unplugged and used in between. */
    private const val DROP_POINTS = 3

    class Sample(
        val nowMs: Long,
        val charging: Boolean,
        val plugType: Int,
        val level: Int,
        val milliamps: Int?,
        val millivolts: Int?,
    )

    /** [changed] must be saved; [started] is the new session when one just began (the banner is for that one). */
    class Result(val changed: List<ChargeSessionEntity>, val started: ChargeSessionEntity?)

    fun apply(open: ChargeSessionEntity?, s: Sample, sawUnplug: Boolean = false): Result {
        if (open == null) {
            if (!s.charging) return Result(emptyList(), null)
            val first = begin(s)
            return Result(listOf(first), first)
        }
        if (!s.charging) {
            val closed = if (sawUnplug) open.copy(ongoing = false, endMs = s.nowMs, endLevel = s.level) else open.copy(ongoing = false)
            return Result(listOf(closed), null)
        }
        val gap = s.nowMs - open.endMs
        val unpluggedBetween = gap > STALE_MS || s.level <= open.endLevel - DROP_POINTS || s.plugType != open.plugType
        if (unpluggedBetween) {
            val first = begin(s)
            return Result(listOf(open.copy(ongoing = false), first), first)
        }
        val more = open.copy(
            endMs = s.nowMs,
            endLevel = s.level,
            plugType = open.plugType,
            samples = open.samples + 1,
            currentSamples = open.currentSamples + if (s.milliamps != null) 1 else 0,
            sumMa = open.sumMa + (s.milliamps ?: 0),
            sumMv = open.sumMv + (s.millivolts ?: 0),
        )
        return Result(listOf(more), null)
    }

    private fun begin(s: Sample) = ChargeSessionEntity(
        id = s.nowMs.toString(),
        startMs = s.nowMs,
        endMs = s.nowMs,
        startLevel = s.level,
        endLevel = s.level,
        plugType = s.plugType,
        source = "",
        samples = 1,
        currentSamples = if (s.milliamps != null) 1 else 0,
        sumMa = (s.milliamps ?: 0).toLong(),
        sumMv = (s.millivolts ?: 0).toLong(),
        ongoing = true,
    )
}
