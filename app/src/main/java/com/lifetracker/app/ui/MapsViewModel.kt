package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.MapSettings
import com.lifetracker.app.data.MapStyleEntity
import com.lifetracker.app.data.MapStyles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Maps settings page: which style the maps use, and the styles you added. */
class MapsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val settings = MapSettings(app)

    val styles: StateFlow<List<MapStyleEntity>> =
        db.mapDao().observeStyles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedFlow = MutableStateFlow(settings.styleId)

    /** The id of the style in use. */
    val selected: StateFlow<String> = selectedFlow

    fun select(id: String) {
        settings.styleId = id
        selectedFlow.value = id
    }

    /** Adds a style of your own and starts using it. Returns what is wrong, or null when it was added. */
    fun add(name: String, address: String, key: String): String? {
        val problem = MapStyles.problem(name, address, key)
        if (problem != null) return problem
        val now = System.currentTimeMillis()
        val style = MapStyleEntity(MapStyles.newId(now), name.trim(), address.trim(), key.trim(), false, now)
        viewModelScope.launch {
            db.mapDao().upsertStyle(style)
            select(style.id)
        }
        return null
    }

    /** Hiding keeps the style; it only leaves the list. The map goes back to the default if it was in use. */
    fun setHidden(style: MapStyleEntity, hidden: Boolean) {
        viewModelScope.launch {
            db.mapDao().upsertStyle(style.copy(hidden = hidden))
            if (hidden && selectedFlow.value == style.id) select(MapStyles.DEFAULT_ID)
        }
    }
}
