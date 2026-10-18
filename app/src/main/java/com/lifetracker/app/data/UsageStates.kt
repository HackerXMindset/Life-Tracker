package com.lifetracker.app.data

/**
 * Works out when the phone was not in use, so the Timeline can say why nothing was recorded: the screen stayed off
 * ("Offline"), or the phone was switched off ("Phone off"). Kept free of Android so it can be tested.
 */
object UsageStates {
    /** Off periods shorter than this are not worth keeping. */
    const val MIN_MS = 30_000L

    /**
     * [events] must be in time order. The screen is off from "screen off" until the screen turns on (or an app comes up).
     * A shut down ends it and starts a phone-off period that lasts until the phone starts again; a phone that died
     * without saying so was off from its last event. A period still going at [until] ends there, and the next sync
     * extends it (same id), so it is never counted twice. Apps in [skip] (the lock screen, the always-on display) coming up
     * do not count as use.
     */
    fun build(events: List<RawEvent>, until: Long, skip: Set<String> = emptySet()): List<UsageStateEntity> {
        val out = ArrayList<UsageStateEntity>()
        var offStart: Long? = null
        var downStart: Long? = null
        var lastTime = 0L

        fun add(kind: String, start: Long, end: Long) {
            if (end - start >= MIN_MS) out += UsageStateEntity(UsageStateEntity.idFor(kind, start), kind, start, end)
        }
        fun endOff(at: Long) {
            offStart?.let { add(UsageStateEntity.OFF, it, at) }
            offStart = null
        }

        for (e in events) {
            when (e.type) {
                RawType.SCREEN_OFF -> if (offStart == null && downStart == null) offStart = e.time
                RawType.SCREEN_ON -> endOff(e.time)
                RawType.RESUME -> if (e.pkg !in skip) endOff(e.time)
                RawType.SHUTDOWN -> {
                    endOff(e.time)
                    if (downStart == null) downStart = e.time
                }
                RawType.STARTUP -> {
                    val from = downStart ?: lastTime.takeIf { it > 0L }
                    if (from != null) {
                        // A phone that died: whatever screen-off period was going ended at its last event.
                        if (downStart == null) offStart?.let { s -> add(UsageStateEntity.OFF, s, from) }
                        offStart = null
                        add(UsageStateEntity.SHUTDOWN, from, e.time)
                    }
                    downStart = null
                }
                RawType.PAUSE, RawType.STOP, RawType.LOCK -> Unit
            }
            if (e.type != RawType.STARTUP) lastTime = e.time
        }
        offStart?.let { add(UsageStateEntity.OFF, it, until) }
        downStart?.let { add(UsageStateEntity.SHUTDOWN, it, until) }
        return out
    }
}
