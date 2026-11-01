package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.PlaceCategoryEntity
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.Rule
import com.lifetracker.app.data.RuleCodec
import com.lifetracker.app.data.RuleLogEntity
import com.lifetracker.app.data.RuleRunner
import com.lifetracker.app.data.RulesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Rules page shows and does: your rules, the line of what ran, and the lists the editor picks from. */
class RulesViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.ruleDao()

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val rules: StateFlow<List<Rule>> = share(emptyList(), dao.observeRules().map { list -> list.map(RuleCodec::toRule) })
    val log: StateFlow<List<RuleLogEntity>> = share(emptyList(), dao.observeLog(100))
    val places: StateFlow<List<PlaceEntity>> = share(emptyList(), db.placeDao().observePlaces())
    val categories: StateFlow<List<PlaceCategoryEntity>> = share(emptyList(), db.placeCategoryDao().observeAll())
    val habits: StateFlow<List<HabitEntity>> = share(emptyList(), db.habitDao().observeHabits())
    val activities: StateFlow<List<ActivityTypeEntity>> = share(emptyList(), db.activityDao().observeAll())

    fun save(rule: Rule) {
        viewModelScope.launch(Dispatchers.IO) {
            val order = dao.get(rule.id)?.sortOrder ?: (dao.maxOrder() + 1)
            dao.upsert(RuleCodec.toEntity(rule, order))
            RulesWorker.refresh(app)
        }
    }

    fun setEnabled(id: String, on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setEnabled(id, on)
            RulesWorker.refresh(app)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(id)
            RulesWorker.refresh(app)
        }
    }

    fun runNow(id: String) {
        viewModelScope.launch(Dispatchers.IO) { RuleRunner.runNow(app, id) }
    }

    fun clearLog() {
        viewModelScope.launch(Dispatchers.IO) { dao.clearLog() }
    }
}
