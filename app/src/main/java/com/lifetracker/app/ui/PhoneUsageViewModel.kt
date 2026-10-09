package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.UsageAppEntity
import com.lifetracker.app.data.UsageCollector
import com.lifetracker.app.data.UsageDays
import com.lifetracker.app.data.UsageSettings
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhoneUsageViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.usageDao()
    private val settings = UsageSettings(app)

    private val rangeFlow = MutableStateFlow(RangeChoice.preset("today"))
    val range: StateFlow<RangeChoice> = rangeFlow

    private val accessFlow = MutableStateFlow(UsageCollector.hasAccess(app))
    val hasAccess: StateFlow<Boolean> = accessFlow

    private val syncingFlow = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = syncingFlow

    private val lastSyncFlow = MutableStateFlow(settings.lastSync)
    val lastSync: StateFlow<String> = lastSyncFlow

    private val showFlow = MutableStateFlow(settings.showOnTimeline)
    val showOnTimeline: StateFlow<Boolean> = showFlow

    private val minFlow = MutableStateFlow(settings.minBlockMinutes)
    val minBlockMinutes: StateFlow<Int> = minFlow

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val apps: StateFlow<List<UsageAppEntity>> = share(emptyList(), dao.observeApps())

    val activityTypes: StateFlow<List<ActivityTypeEntity>> = share(emptyList(), db.activityDao().observeAll())

    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<UsageDays.Day> = share(
        UsageDays.Day(emptyList(), 0L, emptyList(), emptyMap()),
        rangeFlow.flatMapLatest { r ->
            val zone = ZoneId.systemDefault()
            val start = r.span.startMs(zone)
            val end = r.span.endMs(zone)
            combine(dao.sessionsBetween(start, end), dao.observeApps()) { sessions, list ->
                UsageDays.build(sessions, list.associateBy { it.pkg }, start, end)
            }
        },
    )

    fun setRange(r: RangeChoice) {
        rangeFlow.value = r
    }

    /** Looks again at whether usage access has been switched on (called when the screen comes back into view). */
    fun refreshAccess() {
        val now = UsageCollector.hasAccess(app)
        val gained = now && !accessFlow.value
        accessFlow.value = now
        if (gained) sync()
    }

    fun sync() {
        if (syncingFlow.value) return
        viewModelScope.launch {
            syncingFlow.value = true
            val count = runCatching { UsageCollector.sync(app) }.getOrNull()
            if (count != null) {
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
                settings.lastSync = stamp
                lastSyncFlow.value = stamp
            }
            syncingFlow.value = false
        }
    }

    fun setShowOnTimeline(show: Boolean) {
        settings.showOnTimeline = show
        showFlow.value = show
    }

    fun setMinBlockMinutes(minutes: Int) {
        settings.minBlockMinutes = minutes
        minFlow.value = minutes
    }

    fun link(app: UsageAppEntity, activityId: String) {
        viewModelScope.launch { dao.upsertApp(app.copy(activityId = activityId)) }
    }

    fun setIgnored(app: UsageAppEntity, ignored: Boolean) {
        viewModelScope.launch { dao.upsertApp(app.copy(ignored = ignored)) }
    }
}
