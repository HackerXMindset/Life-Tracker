package com.lifetracker.app.data

import kotlin.math.min

/** Everything the brain needs to know besides the reading itself. */
class BrainContext(
    val settings: TrackSettings,
    val profiles: List<TrackingProfileEntity>,
    val places: List<PlaceEntity>,
    /** Charging, car mode, Bluetooth and Wi-Fi as they are now; the speed and "still" are worked out by the brain. */
    val base: Situation,
    /** What the motion sensor says: the phone has not moved for a while. */
    val motionless: Boolean,
    /** You pressed Pause. */
    val manualPause: Boolean,
    val nowMs: Long,
)

/** What the brain remembers between readings. */
data class BrainState(
    val filter: FilterState = FilterState(),
    val pause: PauseState = PauseState(),
    val profile: ProfileState = ProfileState(),
    /** The last readings that passed the first checks, oldest first (for speed and "still"). */
    val recent: List<RawFixEntity> = emptyList(),
)

/** How the position readings should be asked for now. */
data class Plan(
    val intervalSec: Int,
    val accuracyMode: String,
    /** True when the readings can stop altogether (paused by Wi-Fi, no movement, or by you). */
    val gpsOff: Boolean,
    /** Take one heartbeat reading in this many milliseconds, or null for none. */
    val heartbeatInMs: Long?,
    /** When a profile's switch-on or switch-off delay runs out, so the plan should be looked at again, or null. */
    val nextCheckMs: Long?,
    val profileId: String,
    /** One line for the notification and the diary. */
    val status: String,
)

/** What to do after a reading or a look at the surroundings. */
class BrainStep(
    val state: BrainState,
    /** Readings to keep, oldest first (a reading at a place's centre comes before the reading itself). */
    val saves: List<RawFixEntity>,
    val plan: Plan,
    /** Why the reading was not kept, or null. */
    val rejected: Reject?,
    /** Take a heartbeat reading now. */
    val heartbeatNow: Boolean,
)

/** Joins the filter, the profiles and the pause zones into one decision per reading. No Android in here, so it can be tested. */
object TrackBrain {
    /** How many recent readings are remembered. */
    const val RECENT = 12

    private fun situation(state: BrainState, ctx: BrainContext) =
        ctx.base.copy(speedKmh = RollingSpeed.avgKmh(state.recent), still = StillDetector.isStill(state.recent))

    private fun planOf(
        eff: Effective,
        gpsOff: Boolean,
        heartbeatInMs: Long?,
        nextCheckMs: Long?,
        status: String,
        profileName: String?,
        recent: List<RawFixEntity> = emptyList(),
        nowMs: Long = 0L,
    ) = Plan(
        // Fast movement records densely by itself, whatever the profile says (it never slows anything down).
        intervalSec = FastPace.cap(eff.settings.intervalSec, FastPace.lastFastMs(recent), nowMs),
        accuracyMode = eff.settings.accuracyMode,
        gpsOff = gpsOff,
        heartbeatInMs = heartbeatInMs,
        nextCheckMs = nextCheckMs,
        profileId = eff.profileId,
        status = status + (if (profileName.isNullOrBlank()) "" else " · $profileName"),
    )

    private fun heartbeatIn(pause: PauseState, places: List<PlaceEntity>, nowMs: Long): Long? {
        if (!pause.paused) return null
        val zone = places.firstOrNull { it.id == pause.zoneId } ?: return null
        if (zone.heartbeatMin <= 0) return null
        val baseline = if (pause.lastHeartbeatMs > 0L) pause.lastHeartbeatMs else pause.sinceMs
        return (baseline + zone.heartbeatMin * 60_000L - nowMs).coerceAtLeast(0L)
    }

    /** A new position reading arrived. */
    fun onFix(state: BrainState, fix: RawFixEntity, ctx: BrainContext): BrainStep {
        val pick = ProfileRules.step(state.profile, ctx.profiles, situation(state, ctx), ctx.nowMs)
        val eff = ProfileRules.effective(ctx.settings, pick.chosen)
        val name = pick.chosen?.name
        if (ctx.manualPause) {
            val plan = planOf(eff, true, null, pick.nextCheckMs, "Paused by you", name)
            return BrainStep(state.copy(profile = pick.state), emptyList(), plan, null, false)
        }
        val tagged = fix.copy(profileId = eff.profileId)
        val verdict = LiveFilter.check(state.filter, tagged, eff.settings.accuracyLimitM, eff.settings.minMoveM)
        val reject = verdict.reject
        if (reject != null && reject != Reject.TOO_CLOSE) {
            // Out of order, too inaccurate or a jump: it counts for nothing, not even for working out whether you are still.
            val plan = planOf(eff, false, heartbeatIn(state.pause, ctx.places, ctx.nowMs), pick.nextCheckMs, "Recording", name, state.recent, ctx.nowMs)
            return BrainStep(state.copy(filter = verdict.state, profile = pick.state), emptyList(), plan, reject, false)
        }
        val pause = PauseZoneRules.step(
            state.pause, ctx.places, fix.lat, fix.lng, ctx.base.wifiName, ctx.motionless, ctx.nowMs, eff.settings.intervalSec,
        )
        val saves = ArrayList<RawFixEntity>()
        pause.anchor?.let {
            saves.add(RawFixEntity(fix.ms - 1, it.lat, it.lng, 5f, source = FixSource.ANCHOR, profileId = eff.profileId))
        }
        if (reject == null && pause.save) saves.add(tagged)
        val recent = (state.recent + tagged).takeLast(RECENT)
        val next = BrainState(verdict.state, pause.state, pick.state, recent)
        val plan = planOf(
            eff, pause.gpsOff, heartbeatIn(pause.state, ctx.places, ctx.nowMs), pick.nextCheckMs, pause.status, name, recent, ctx.nowMs,
        )
        return BrainStep(next, saves, plan, reject, pause.heartbeatNow)
    }

    /**
     * Something changed around the phone (the Wi-Fi, the charger, the motion sensor, a button) or a delay ran out, with no new
     * reading: look at the plan again from where you were last seen.
     */
    fun reevaluate(state: BrainState, ctx: BrainContext): BrainStep {
        val pick = ProfileRules.step(state.profile, ctx.profiles, situation(state, ctx), ctx.nowMs)
        val eff = ProfileRules.effective(ctx.settings, pick.chosen)
        val name = pick.chosen?.name
        if (ctx.manualPause) {
            return BrainStep(state.copy(profile = pick.state), emptyList(), planOf(eff, true, null, pick.nextCheckMs, "Paused by you", name), null, false)
        }
        val last = state.recent.lastOrNull()
        if (last == null) {
            return BrainStep(state.copy(profile = pick.state), emptyList(), planOf(eff, false, null, pick.nextCheckMs, "Recording", name, state.recent, ctx.nowMs), null, false)
        }
        val pause = PauseZoneRules.step(
            state.pause, ctx.places, last.lat, last.lng, ctx.base.wifiName, ctx.motionless, ctx.nowMs, eff.settings.intervalSec,
        )
        val next = state.copy(pause = pause.state, profile = pick.state)
        val plan = planOf(eff, pause.gpsOff, heartbeatIn(pause.state, ctx.places, ctx.nowMs), pick.nextCheckMs, pause.status, name, state.recent, ctx.nowMs)
        return BrainStep(next, emptyList(), plan, null, pause.heartbeatNow)
    }

    /** How long to wait before looking at the plan again: the nearest of a heartbeat and a profile delay, or null for neither. */
    fun waitMs(plan: Plan, nowMs: Long): Long? {
        val delay = plan.nextCheckMs?.let { it - nowMs }
        val wait = when {
            plan.heartbeatInMs != null && delay != null -> min(plan.heartbeatInMs, delay)
            plan.heartbeatInMs != null -> plan.heartbeatInMs
            else -> delay
        }
        return wait?.coerceAtLeast(1_000L)
    }
}
