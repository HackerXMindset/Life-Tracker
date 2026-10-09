package com.lifetracker.app.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.HealthConnectSync
import com.lifetracker.app.data.HealthSettings
import com.lifetracker.app.data.HealthSync
import com.lifetracker.app.data.SleepNightEntity
import com.lifetracker.app.data.SleepStats
import com.lifetracker.app.data.StepCounterSync
import com.lifetracker.app.data.StepDayEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How far Health Connect is set up. */
enum class HcState { Checking, Unavailable, NeedsUpdate, NeedsPermission, Connected }

class StepsSleepViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val settings = HealthSettings(app)

    private val rangeFlow = MutableStateFlow(RangeChoice.preset("7"))
    val range: StateFlow<RangeChoice> = rangeFlow

    private val hcFlow = MutableStateFlow(HcState.Checking)
    val hcState: StateFlow<HcState> = hcFlow

    private val counterOnFlow = MutableStateFlow(settings.useStepCounter)
    val useStepCounter: StateFlow<Boolean> = counterOnFlow

    private val counterPermissionFlow = MutableStateFlow(StepCounterSync.hasPermission(app))
    val counterPermission: StateFlow<Boolean> = counterPermissionFlow

    val hasCounterSensor: Boolean = StepCounterSync.hasSensor(app)

    private val estimateFlow = MutableStateFlow(settings.estimateSleep)
    val estimateSleep: StateFlow<Boolean> = estimateFlow

    private val timelineFlow = MutableStateFlow(settings.showOnTimeline)
    val showOnTimeline: StateFlow<Boolean> = timelineFlow

    private val syncingFlow = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = syncingFlow

    private val lastSyncFlow = MutableStateFlow(settings.lastSync)
    val lastSync: StateFlow<String> = lastSyncFlow

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    @OptIn(ExperimentalCoroutinesApi::class)
    val days: StateFlow<List<StepDayEntity>> = share(
        emptyList(),
        rangeFlow.flatMapLatest { r -> db.stepDao().daysBetween(r.span.from.toString(), r.span.to.toString()) },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val nights: StateFlow<List<SleepNightEntity>> = share(
        emptyList(),
        rangeFlow.flatMapLatest { r -> db.sleepDao().nightsBetween(r.span.from.toString(), r.span.to.toString()) },
    )

    fun setRange(r: RangeChoice) {
        rangeFlow.value = r
    }

    /** Looks again at what is set up (called when the screen comes back into view). */
    fun refresh() {
        counterPermissionFlow.value = StepCounterSync.hasPermission(app)
        viewModelScope.launch {
            hcFlow.value = when (HealthConnectSync.status(app)) {
                HealthConnectClient.SDK_AVAILABLE ->
                    if (HealthConnectSync.granted(app).containsAll(HealthConnectSync.PERMISSIONS)) HcState.Connected else HcState.NeedsPermission
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcState.NeedsUpdate
                else -> HcState.Unavailable
            }
        }
    }

    fun sync() {
        if (syncingFlow.value) return
        viewModelScope.launch {
            syncingFlow.value = true
            runCatching { HealthSync.run(app) }
            lastSyncFlow.value = settings.lastSync
            syncingFlow.value = false
            refresh()
        }
    }

    fun setUseStepCounter(on: Boolean) {
        settings.useStepCounter = on
        counterOnFlow.value = on
        if (on) {
            // Start counting from now, so steps taken before it was switched on are not added in one lump.
            settings.lastCounter = -1L
            sync()
        }
    }

    fun setEstimateSleep(on: Boolean) {
        settings.estimateSleep = on
        estimateFlow.value = on
        if (on) sync()
    }

    fun setShowOnTimeline(on: Boolean) {
        settings.showOnTimeline = on
        timelineFlow.value = on
    }

    /** Saves a night you set yourself. Returns false if the two times are the same. */
    fun saveNight(date: LocalDate, startMinute: Int, endMinute: Int): Boolean {
        val pair = SleepStats.build(date, startMinute, endMinute, ZoneId.systemDefault()) ?: return false
        viewModelScope.launch {
            db.sleepDao().upsert(SleepNightEntity(date.toString(), pair.first, pair.second, SleepStats.MANUAL))
        }
        return true
    }

    /** Marks a night as "not sleep" so it is not counted or estimated again. */
    fun skipNight(night: SleepNightEntity) {
        viewModelScope.launch { db.sleepDao().upsert(night.copy(source = SleepStats.SKIPPED)) }
    }
}
