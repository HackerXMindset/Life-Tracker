package com.lifetracker.app.data

/** What the place editor edits about a pause zone. The same five fields every place carries. */
data class PauseSettings(
    val pauseHere: Boolean = false,
    /** One Wi-Fi name per line. */
    val wifi: String = "",
    val motionless: Boolean = false,
    /** Minutes between heartbeat readings while paused; 0 for none. */
    val heartbeatMin: Int = 0,
    /** Seconds to keep recording after arriving; -1 for automatic. */
    val entryDelaySec: Int = -1,
) {
    companion object {
        fun of(place: PlaceEntity) =
            PauseSettings(place.pauseHere, place.pauseWifi, place.pauseMotionless, place.heartbeatMin, place.entryDelaySec)
    }
}

/** Reading a pause zone back as words, cleaning its Wi-Fi list and checking its numbers. */
object PauseText {
    const val MAX_HEARTBEAT_MIN = 1440
    const val MAX_ENTRY_SEC = 3600

    /** Names one per line, trimmed, blanks dropped, the same name (capital letters ignored) kept once as first typed. */
    fun cleanWifi(text: String): String {
        val seen = HashSet<String>()
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() && seen.add(ProfileRules.wifiKey(it)) }.joinToString("\n")
    }

    /** Adds one name to a list (no change if it is already there). */
    fun addWifi(text: String, name: String): String = cleanWifi(text + "\n" + name)

    fun problem(s: PauseSettings): String? = when {
        s.heartbeatMin < 0 || s.heartbeatMin > MAX_HEARTBEAT_MIN -> "Heartbeat is 0 (none) to $MAX_HEARTBEAT_MIN minutes."
        s.entryDelaySec < -1 || s.entryDelaySec > MAX_ENTRY_SEC -> "Entry delay is automatic or 0 to $MAX_ENTRY_SEC seconds."
        else -> null
    }

    /** The settings with the Wi-Fi list cleaned and, when pausing is off, nothing else lost (they are kept for later). */
    fun clean(s: PauseSettings) = s.copy(wifi = cleanWifi(s.wifi))

    fun describe(s: PauseSettings): String {
        if (!s.pauseHere) return "Not a pause zone. Readings are recorded as usual here."
        val names = cleanWifi(s.wifi).lines().filter { it.isNotEmpty() }
        val sb = StringBuilder("Recording pauses ")
        sb.append(
            when {
                s.entryDelaySec < 0 -> "after about 3½ readings"
                s.entryDelaySec == 0 -> "as soon as you arrive"
                else -> ProfileText.seconds(s.entryDelaySec) + " after you arrive"
            },
        )
        sb.append(".")
        val stops = ArrayList<String>()
        if (names.isNotEmpty()) stops += "on the Wi-Fi " + names.joinToString(" or ")
        if (s.motionless) stops += "when you are still"
        if (stops.isNotEmpty()) sb.append(" The GPS also switches off ").append(stops.joinToString(" and ")).append(".")
        if (s.heartbeatMin > 0) sb.append(" A reading every ${s.heartbeatMin} min shows you are still here.")
        sb.append(" A reading at the centre is added when you leave. The stay still shows as a visit.")
        return sb.toString()
    }
}
