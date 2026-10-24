package com.lifetracker.app.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * GPS all day, the part that touches the database. It holds what [TrackBrain] remembers between readings, asks the brain
 * what to do with each reading, saves the readings it says to keep, and hands them to today's live detector so visits and
 * trips keep appearing on the Timeline.
 */
object TrackEngine {
    private val lock = Mutex()
    private var state = BrainState()

    /** What the motion sensor last said: the phone has not moved for a while. */
    @Volatile
    var motionless: Boolean = false

    private var cachedAt = 0L
    private var profiles: List<TrackingProfileEntity> = emptyList()
    private var places: List<PlaceEntity> = emptyList()

    /** Profiles and places change rarely, so they are read again at most every 20 seconds. */
    private suspend fun lists(context: Context, now: Long) {
        if (now - cachedAt < 20_000L && cachedAt != 0L) return
        val db = AppDatabase.get(context)
        profiles = runCatching { db.trackingProfileDao().enabled() }.getOrDefault(emptyList())
        places = runCatching { db.placeDao().activePlaces() }.getOrDefault(emptyList())
        cachedAt = now
    }

    /** Forget the cached lists (a place or a profile just changed). */
    fun refresh() {
        cachedAt = 0L
    }

    /** Forget everything remembered between readings (GPS all day was switched on or off). */
    fun reset() {
        state = BrainState()
        motionless = false
        cachedAt = 0L
    }

    private fun context(settings: PlacesSettings, base: Situation, now: Long) =
        BrainContext(settings.trackSettings(), profiles, places, base, motionless, settings.manualPause, now)

    /** The result of a reading or a look: how to ask for readings next and whether a heartbeat reading is due. */
    class Outcome(val plan: Plan, val heartbeatNow: Boolean)

    /** One position reading arrived from the phone. */
    suspend fun onLocation(context: Context, location: Location): Outcome = withContext(Dispatchers.IO) {
        lock.withLock {
            val app = context.applicationContext
            val now = System.currentTimeMillis()
            val settings = PlacesSettings(app)
            lists(app, now)
            val raw = RawFixEntity(
                ms = location.time,
                lat = location.latitude,
                lng = location.longitude,
                acc = if (location.hasAccuracy()) location.accuracy else 0f,
                speed = if (location.hasSpeed()) location.speed else -1f,
                bearing = if (location.hasBearing()) location.bearing else -1f,
                altitude = if (location.hasAltitude()) location.altitude else null,
                battery = SystemSituation.batteryPercent(app),
                steps = StepCounterSync.counterNow(app) ?: -1L,
                source = when (location.provider) {
                    "gps" -> FixSource.GPS
                    "network" -> FixSource.NETWORK
                    else -> FixSource.FUSED
                },
            )
            settings.lastBeatMs = now
            settings.lastFixMs = now
            val step = TrackBrain.onFix(state, raw, context(settings, SystemSituation.read(app), now))
            state = step.state
            val rejected = step.rejected
            if (rejected != null) {
                settings.trackRefused = settings.trackRefused + 1
                AppLog.w("Fixes", "Reading at ${LogFormat.clock(raw.ms)} refused: ${rejected.word}")
            }
            keep(app, step.saves, settings)
            logPlan(settings, step.plan)
            Outcome(step.plan, step.heartbeatNow)
        }
    }

    /** Something around the phone changed, or a delay ran out: look at the plan again. */
    suspend fun reevaluate(context: Context): Outcome = withContext(Dispatchers.IO) {
        lock.withLock {
            val app = context.applicationContext
            val now = System.currentTimeMillis()
            val settings = PlacesSettings(app)
            lists(app, now)
            val step = TrackBrain.reevaluate(state, context(settings, SystemSituation.read(app), now))
            state = step.state
            logPlan(settings, step.plan)
            Outcome(step.plan, step.heartbeatNow)
        }
    }

    /** A heartbeat reading (one position taken while the continuous readings are paused). */
    suspend fun onHeartbeat(context: Context, fix: Fix) = withContext(Dispatchers.IO) {
        lock.withLock {
            val app = context.applicationContext
            val settings = PlacesSettings(app)
            val raw = RawFixEntity(
                ms = fix.timeMs, lat = fix.lat, lng = fix.lng, acc = fix.accuracyM, speed = fix.speedMs,
                battery = SystemSituation.batteryPercent(app), source = FixSource.HEARTBEAT,
            )
            if (raw.acc <= PlaceRules.MAX_ACCURACY_M) {
                AppLog.i("Heartbeat", "Heartbeat reading at ${LogFormat.clock(raw.ms)}: accuracy ${raw.acc.toInt()} m")
                keep(app, listOf(raw), settings)
            } else {
                AppLog.w("Heartbeat", "Heartbeat reading too inaccurate (${raw.acc.toInt()} m); not used")
            }
        }
    }

    /** Saves readings for good and gives them to the live detector. */
    private suspend fun keep(context: Context, saves: List<RawFixEntity>, settings: PlacesSettings) {
        if (saves.isEmpty()) return
        val db = AppDatabase.get(context)
        for (r in saves) {
            runCatching { db.rawDao().add(r) }.onFailure { AppLog.e("Fixes", "Could not save the reading at ${LogFormat.clock(r.ms)}", it) }
            settings.trackKept = settings.trackKept + 1
            runCatching { PlacesTracker.onFix(context, Fix(r.lat, r.lng, r.acc, r.ms, r.speed)) }
                .onFailure { AppLog.e("Fixes", "Could not process the reading at ${LogFormat.clock(r.ms)}", it) }
        }
    }

    private var lastLogged = ""

    private fun logPlan(settings: PlacesSettings, plan: Plan) {
        settings.trackStatus = plan.status
        val line = "${plan.status}; every ${plan.intervalSec} s, ${AccuracyMode.label(plan.accuracyMode)}" +
            (if (plan.gpsOff) "; readings off" else "") + (if (plan.profileId.isNotEmpty()) "; profile ${plan.profileId}" else "")
        if (line != lastLogged) {
            lastLogged = line
            AppLog.i("Track", line)
        }
    }
}
