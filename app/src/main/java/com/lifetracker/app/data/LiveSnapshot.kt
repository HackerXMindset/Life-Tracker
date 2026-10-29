package com.lifetracker.app.data

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the live map and the "On the move" row need, taken at one moment. */
class LiveSnapshot(
    val live: LiveRules.Live,
    /** The newest reading, or null when there is none yet. */
    val last: RawFixEntity?,
    /** Today's path so far (thinned for drawing), empty when it was not asked for. */
    val path: List<Pair<Double, Double>>,
    val nowMs: Long,
    /** True when GPS all day is the recording mode (so Pause and Resume mean something). */
    val allDay: Boolean,
    val paused: Boolean,
    val intervalSec: Int,
    /** The line the notification shows. */
    val status: String,
    /** The tracking profile in force, or an empty text for none. */
    val profileName: String,
)

/** Reads the latest readings and the recording settings. It only reads. */
object LiveSource {
    private const val LOOK_BACK_MS = 3 * 60 * 60_000L

    suspend fun load(context: Context, withPath: Boolean): LiveSnapshot = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val settings = PlacesSettings(context)
        val now = System.currentTimeMillis()
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val from = if (withPath) dayStart else maxOf(dayStart, now - LOOK_BACK_MS)
        val today = db.rawDao().between(from, now + 1)
        val recent = if (withPath) today.filter { it.ms >= maxOf(dayStart, now - LOOK_BACK_MS) } else today
        val tracking = settings.enabled && settings.allDay
        val interval = settings.trackIntervalSec
        val live = LiveRules.read(recent, now, tracking, settings.manualPause, interval)
        val path = if (withPath) TripsRange.thin(FixFilter.clean(today).map { it.lat to it.lng }, 10.0) else emptyList()
        val profile = settings.trackProfileId.takeIf { it.isNotEmpty() }?.let { db.trackingProfileDao().profile(it)?.name }.orEmpty()
        LiveSnapshot(
            live = live,
            last = recent.maxByOrNull { it.ms } ?: db.rawDao().latest(),
            path = path,
            nowMs = now,
            allDay = settings.allDay,
            paused = settings.manualPause,
            intervalSec = interval,
            status = settings.trackStatus,
            profileName = profile,
        )
    }
}
