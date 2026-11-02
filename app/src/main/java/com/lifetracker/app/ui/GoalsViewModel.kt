package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.GoalEntity
import com.lifetracker.app.data.GoalProgress
import com.lifetracker.app.data.GoalStats
import com.lifetracker.app.data.PlaceCategoryEntity
import com.lifetracker.app.data.PlaceEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/** A goal with where it stands now. */
class GoalItem(val goal: GoalEntity, val progress: GoalProgress)

/** What the Goals page and the rings show: your goals, their progress (looked at again every minute), and the lists the editor picks from. */
class GoalsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.goalDao()

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val goals: StateFlow<List<GoalEntity>> = share(emptyList(), dao.observeGoals())
    val places: StateFlow<List<PlaceEntity>> = share(emptyList(), db.placeDao().observePlaces())
    val categories: StateFlow<List<PlaceCategoryEntity>> = share(emptyList(), db.placeCategoryDao().observeAll())

    private val minute: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    val items: StateFlow<List<GoalItem>> = share(
        emptyList(),
        combine(goals, db.placeDao().observePlaces(), minute) { goals, places, now -> Triple(goals, places, now) }
            .transform { (goals, places, now) -> emit(compute(goals, places, now)) }
            .flowOn(Dispatchers.IO),
    )

    private suspend fun compute(goals: List<GoalEntity>, places: List<PlaceEntity>, now: Long): List<GoalItem> {
        if (goals.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = GoalStats.earliest(goals, today, zone) - 12 * 3_600_000L
        val visits = db.placeDao().visitsAround(from, now)
        val trips = db.tripDao().tripsIn(from, now + 1)
        return goals.map { g ->
            val value = GoalStats.value(g, visits, trips, places, now, today, zone)
            GoalItem(g, GoalStats.progress(g, value))
        }
    }

    fun save(goal: GoalEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val order = dao.get(goal.id)?.sortOrder ?: (dao.maxOrder() + 1)
            dao.upsert(goal.copy(sortOrder = order))
        }
    }

    fun setEnabled(id: String, on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { dao.setEnabled(id, on) }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) { dao.delete(id) }
    }
}
