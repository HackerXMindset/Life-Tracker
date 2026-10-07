package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.FoodGoalEntity
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.WaterEntity
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoodViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).foodDao()

    private val selected = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = selected

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    @OptIn(ExperimentalCoroutinesApi::class)
    val meals: StateFlow<List<MealEntity>> =
        share(emptyList(), selected.flatMapLatest { dao.mealsOn(it.toString()) })

    val goal: StateFlow<FoodGoalEntity?> =
        share(null, combine(selected, dao.observeGoals()) { day, goals -> FoodStats.goalFor(goals, day) })

    @OptIn(ExperimentalCoroutinesApi::class)
    val waterMl: StateFlow<Int> =
        share(0, selected.flatMapLatest { dao.waterOn(it.toString()) }.map { it?.ml ?: 0 })

    /** Meals eaten before, one per name, for adding again in one tap. */
    val recent: StateFlow<List<MealEntity>> =
        share(emptyList(), dao.observeRecentMeals().map { FoodStats.recentDistinct(it, 8) })

    fun shift(days: Long) {
        selected.value = selected.value.plusDays(days)
    }

    fun goToday() {
        selected.value = LocalDate.now()
    }

    fun addMeal(meal: MealEntity) {
        viewModelScope.launch { dao.upsertMeal(meal) }
    }

    fun deleteMeal(meal: MealEntity) {
        viewModelScope.launch { dao.deleteMeal(meal) }
    }

    fun addWater(deltaMl: Int) {
        viewModelScope.launch {
            val day = selected.value.toString()
            val current = dao.water(day)?.ml ?: 0
            dao.setWater(WaterEntity(day, (current + deltaMl).coerceAtLeast(0)))
        }
    }
}
