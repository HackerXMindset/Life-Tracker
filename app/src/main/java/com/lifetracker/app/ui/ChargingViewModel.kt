package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.ChargeNotifier
import com.lifetracker.app.data.ChargeSessionEntity
import com.lifetracker.app.data.ChargeSettings
import com.lifetracker.app.data.ChargeStats
import com.lifetracker.app.data.ChargeTracker
import com.lifetracker.app.data.ChargeWorker
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One charging session as the screen shows it. [guess] is set only when you never said what you charged with. */
class ChargeRow(
    val session: ChargeSessionEntity,
    val guess: String?,
    val phoneUseMs: Long,
)

class ChargingViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.chargeDao()
    private val settings = ChargeSettings(app)

    private val rangeFlow = MutableStateFlow(RangeChoice.preset("7"))
    val range: StateFlow<RangeChoice> = rangeFlow

    private val trackFlow = MutableStateFlow(settings.track)
    val track: StateFlow<Boolean> = trackFlow

    private val bannerFlow = MutableStateFlow(settings.showBanner)
    val showBanner: StateFlow<Boolean> = bannerFlow

    private val timelineFlow = MutableStateFlow(settings.showOnTimeline)
    val showOnTimeline: StateFlow<Boolean> = timelineFlow

    private val notificationsFlow = MutableStateFlow(ChargeNotifier.canNotify(app))
    val notificationsOn: StateFlow<Boolean> = notificationsFlow

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<ChargeRow>> = share(
        emptyList(),
        rangeFlow.flatMapLatest { r ->
            val zone = ZoneId.systemDefault()
            val from = r.span.startMs(zone)
            val to = r.span.endMs(zone)
            combine(dao.sessionsBetween(from, to), dao.observeAll(), db.usageDao().sessionsBetween(from, to)) { list, history, usage ->
                val now = System.currentTimeMillis()
                list.map { s ->
                    val guess = if (s.source.isEmpty()) ChargeStats.guessSource(s.plugType, ChargeStats.avgWatts(s), history) else null
                    ChargeRow(s, guess, ChargeStats.phoneUseMs(s, usage, now))
                }
            }
        },
    )

    fun setRange(r: RangeChoice) {
        rangeFlow.value = r
    }

    /** Looks again at whether the app may show notifications (called when the screen comes back into view). */
    fun refreshNotifications() {
        notificationsFlow.value = ChargeNotifier.canNotify(app)
        // Closes a session that ended while the app was not running, or notes that the phone is charging now.
        viewModelScope.launch { runCatching { ChargeTracker.sample(app, banner = false) } }
    }

    fun setTrack(on: Boolean) {
        settings.track = on
        trackFlow.value = on
        if (on) ChargeWorker.schedule(app) else ChargeWorker.cancel(app)
    }

    fun setShowBanner(on: Boolean) {
        settings.showBanner = on
        bannerFlow.value = on
    }

    fun setShowOnTimeline(on: Boolean) {
        settings.showOnTimeline = on
        timelineFlow.value = on
    }

    fun setSource(id: String, source: String) {
        viewModelScope.launch { dao.setSource(id, source) }
    }
}
