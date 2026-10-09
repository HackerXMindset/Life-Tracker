package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.CallEntity
import com.lifetracker.app.data.CallsCollector
import com.lifetracker.app.data.CallsSettings
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).callDao()
    private val settings = CallsSettings(app)

    private val rangeFlow = MutableStateFlow(RangeChoice.preset("30"))
    val range: StateFlow<RangeChoice> = rangeFlow

    private val accessFlow = MutableStateFlow(CallsCollector.hasAccess(app))
    val hasAccess: StateFlow<Boolean> = accessFlow

    private val syncingFlow = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = syncingFlow

    private val lastSyncFlow = MutableStateFlow(settings.lastSync)
    val lastSync: StateFlow<String> = lastSyncFlow

    private val showFlow = MutableStateFlow(settings.showOnTimeline)
    val showOnTimeline: StateFlow<Boolean> = showFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val calls: StateFlow<List<CallEntity>> = rangeFlow.flatMapLatest { r ->
        val zone = ZoneId.systemDefault()
        dao.callsBetween(r.span.startMs(zone), r.span.endMs(zone))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRange(r: RangeChoice) {
        rangeFlow.value = r
    }

    /** Looks again at the permission (called when the screen comes back into view); copies the log if it was just allowed. */
    fun refreshAccess() {
        val now = CallsCollector.hasAccess(app)
        val gained = now && !accessFlow.value
        accessFlow.value = now
        if (gained) sync()
    }

    fun sync() {
        if (syncingFlow.value) return
        viewModelScope.launch {
            syncingFlow.value = true
            val count = runCatching { CallsCollector.sync(app) }.getOrNull()
            if (count != null) {
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
                settings.lastSync = stamp
                lastSyncFlow.value = stamp
            }
            syncingFlow.value = false
        }
    }

    private val copyingOlderFlow = MutableStateFlow(false)
    val copyingOlder: StateFlow<Boolean> = copyingOlderFlow

    /** Result of the last "Copy older calls", as text for the screen. Empty before the first. */
    private val olderResultFlow = MutableStateFlow("")
    val olderResult: StateFlow<String> = olderResultFlow

    /** Reads the whole call log from the beginning, for days before the first copy. */
    fun copyOlder() {
        if (copyingOlderFlow.value) return
        viewModelScope.launch {
            copyingOlderFlow.value = true
            val count = runCatching { CallsCollector.copyAll(app) }.getOrNull()
            olderResultFlow.value = if (count == null) "Could not read the call log." else "Read $count calls from your phone's call log."
            copyingOlderFlow.value = false
        }
    }

    fun setShowOnTimeline(show: Boolean) {
        settings.showOnTimeline = show
        showFlow.value = show
    }
}
