package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The list of activities and the changes made to it. */
class ActivitiesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).activityDao()

    val types: StateFlow<List<ActivityTypeEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a new or changed activity. A new one (sortOrder -1) goes to the end of the list. */
    fun save(type: ActivityTypeEntity, onSaved: (ActivityTypeEntity) -> Unit = {}) {
        viewModelScope.launch {
            val placed = if (type.sortOrder < 0) type.copy(sortOrder = dao.maxSortOrder() + 1) else type
            dao.upsert(placed)
            onSaved(placed)
        }
    }

    fun setArchived(type: ActivityTypeEntity, archived: Boolean) {
        viewModelScope.launch { dao.upsert(type.copy(archived = archived)) }
    }
}
