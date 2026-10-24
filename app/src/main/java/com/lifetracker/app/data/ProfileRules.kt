package com.lifetracker.app.data

import kotlin.math.min

/** What is true of the phone right now, as far as tracking profiles care. */
data class Situation(
    val charging: Boolean = false,
    val carMode: Boolean = false,
    /** Addresses of the Bluetooth devices connected now. */
    val bluetooth: Set<String> = emptySet(),
    val wifiConnected: Boolean = false,
    /** The name of the Wi-Fi network connected now, or null when none or not known. */
    val wifiName: String? = null,
    /** Average speed over the last few readings, or null when not known. */
    val speedKmh: Double? = null,
    val still: Boolean = false,
)

/** What the tracker remembers about one profile: since when its condition has held, since when it stopped, and whether it is on. */
data class ProfileMark(val holdingSinceMs: Long = 0L, val lostSinceMs: Long = 0L, val active: Boolean = false)

data class ProfileState(val marks: Map<String, ProfileMark> = emptyMap())

/** The profile in force now ([chosen] is null for none) and when a delay will next run out, so the engine can look again then. */
class ProfilePick(val state: ProfileState, val chosen: TrackingProfileEntity?, val nextCheckMs: Long?)

/** The settings recording actually uses now. */
data class Effective(val settings: TrackSettings, val profileId: String)

object ProfileRules {
    /** Android gives a Wi-Fi name in quotes; names are compared without them and without capital letters. */
    fun wifiKey(name: String): String = name.trim().removeSurrounding("\"").trim().lowercase()

    fun holds(p: TrackingProfileEntity, s: Situation): Boolean = when (p.condition) {
        ProfileCondition.CHARGING -> s.charging
        ProfileCondition.CAR_MODE -> s.carMode
        ProfileCondition.BLUETOOTH ->
            ProfileText.deviceAddress(p.conditionText).let { address -> address.isNotEmpty() && s.bluetooth.any { it.equals(address, ignoreCase = true) } }
        ProfileCondition.WIFI -> s.wifiConnected
        ProfileCondition.WIFI_NAMED -> p.conditionText.isNotBlank() && s.wifiName != null && wifiKey(s.wifiName) == wifiKey(p.conditionText)
        ProfileCondition.SPEED_ABOVE -> s.speedKmh != null && s.speedKmh > p.conditionNumber
        ProfileCondition.SPEED_BELOW -> s.speedKmh != null && s.speedKmh < p.conditionNumber
        ProfileCondition.STATIONARY -> s.still
        else -> false
    }

    /**
     * One look at the situation. A profile switches on after its condition has held for its switch-on delay, and off after it has
     * stopped holding for its switch-off delay. Of the profiles that are on, the highest priority wins (the first in [profiles]
     * when two have the same one, so pass them highest priority first). Hidden and switched-off profiles never apply.
     */
    fun step(state: ProfileState, profiles: List<TrackingProfileEntity>, s: Situation, nowMs: Long): ProfilePick {
        val marks = HashMap<String, ProfileMark>()
        var next: Long? = null
        fun later(t: Long) {
            if (t > nowMs) next = next?.let { min(it, t) } ?: t
        }
        for (p in profiles) {
            if (!p.enabled || p.deletedAt != 0L) continue
            val old = state.marks[p.id] ?: ProfileMark()
            val mark = if (holds(p, s)) {
                val since = if (old.holdingSinceMs == 0L) nowMs else old.holdingSinceMs
                val dueAt = since + p.activateDelaySec * 1000L
                val on = old.active || nowMs >= dueAt
                if (!on) later(dueAt)
                ProfileMark(since, 0L, on)
            } else {
                if (old.active) {
                    val lost = if (old.lostSinceMs == 0L) nowMs else old.lostSinceMs
                    val offAt = lost + p.deactivateDelaySec * 1000L
                    if (nowMs < offAt) {
                        later(offAt)
                        ProfileMark(0L, lost, true)
                    } else {
                        ProfileMark()
                    }
                } else {
                    ProfileMark()
                }
            }
            marks[p.id] = mark
        }
        val chosen = profiles.filter { marks[it.id]?.active == true }.maxWithOrNull(compareBy<TrackingProfileEntity> { it.priority })
        return ProfilePick(ProfileState(marks), chosen, next)
    }

    /** The recording settings to use: [base] (yours), changed by [profile] where one applies. A still profile always keeps every reading. */
    fun effective(base: TrackSettings, profile: TrackingProfileEntity?): Effective {
        if (profile == null) return Effective(base, "")
        val minMove = if (profile.condition == ProfileCondition.STATIONARY) 0 else profile.minMoveM
        return Effective(
            base.copy(
                intervalSec = profile.intervalSec,
                minMoveM = minMove,
                accuracyMode = profile.accuracyMode.ifEmpty { base.accuracyMode },
            ),
            profile.id,
        )
    }

    /** What is wrong with a profile as filled in, or null when it is fine. */
    fun problem(p: TrackingProfileEntity): String? = when {
        p.condition !in ProfileCondition.all -> "Pick what switches this profile on."
        p.condition == ProfileCondition.WIFI_NAMED && p.conditionText.isBlank() -> "Write the name of the Wi-Fi network."
        p.condition == ProfileCondition.BLUETOOTH && p.conditionText.isBlank() -> "Pick the Bluetooth device."
        (p.condition == ProfileCondition.SPEED_ABOVE || p.condition == ProfileCondition.SPEED_BELOW) && p.conditionNumber <= 0.0 -> "Give a speed above 0 km/h."
        p.intervalSec < 1 -> "Seconds between readings must be at least 1."
        p.minMoveM < 0 -> "The movement limit cannot be below 0."
        p.activateDelaySec < 0 || p.deactivateDelaySec < 0 -> "A delay cannot be below 0."
        p.accuracyMode.isNotEmpty() && p.accuracyMode !in AccuracyMode.all -> "Pick one of the accuracy levels."
        else -> null
    }
}
