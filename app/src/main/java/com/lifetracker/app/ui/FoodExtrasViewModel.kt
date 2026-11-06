package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.BatchEntity
import com.lifetracker.app.data.BatchMath
import com.lifetracker.app.data.DiaryDay
import com.lifetracker.app.data.DiaryMath
import com.lifetracker.app.data.FastEntity
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodSettings
import com.lifetracker.app.data.FoodGoalEntity
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.FoodTrackerEntity
import com.lifetracker.app.data.TrackerEventEntity
import com.lifetracker.app.data.TrackerMath
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.MealPlanEntity
import com.lifetracker.app.data.PlanMath
import com.lifetracker.app.data.SupplementEntity
import com.lifetracker.app.data.SupplementTakenEntity
import com.lifetracker.app.data.WeightEntity
import java.time.LocalDate
import java.util.UUID
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

/** Supplements, weight, fasting, meal prep, plans and the week: what sits under the meals on the Food tab. */
class FoodExtrasViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app
    private val db = AppDatabase.get(app)
    private val dao = db.foodExtrasDao()
    private val foodDao = db.foodDao()

    private val day = MutableStateFlow(LocalDate.now())

    /** The day the Food tab shows. */
    fun setDay(date: LocalDate) {
        day.value = date
    }

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val supplements: StateFlow<List<SupplementEntity>> = share(emptyList(), dao.observeSupplements())

    @OptIn(ExperimentalCoroutinesApi::class)
    val taken: StateFlow<List<SupplementTakenEntity>> = share(emptyList(), day.flatMapLatest { dao.observeTakenOn(it.toString()) })

    val weights: StateFlow<List<WeightEntity>> = share(emptyList(), dao.observeWeights())
    val fasts: StateFlow<List<FastEntity>> = share(emptyList(), dao.observeFasts())
    val batches: StateFlow<List<BatchEntity>> = share(emptyList(), dao.observeBatches())
    val plans: StateFlow<List<MealPlanEntity>> = share(emptyList(), dao.observePlans())

    @OptIn(ExperimentalCoroutinesApi::class)
    val weekMeals: StateFlow<List<MealEntity>> = share(
        emptyList(),
        day.flatMapLatest { d ->
            val week = FoodLogic.weekOf(d)
            foodDao.observeMealsBetween(week.first().toString(), week.last().toString())
        },
    )

    val goals: StateFlow<List<FoodGoalEntity>> = share(emptyList(), foodDao.observeGoals())

    /** Goes up when a switch or a small setting changes. */
    val settingsTick: StateFlow<Int> = FoodSettings.changes

    // ---- supplements ----

    fun saveSupplement(s: SupplementEntity) {
        viewModelScope.launch { dao.upsertSupplement(s) }
    }

    fun deleteSupplement(s: SupplementEntity) {
        viewModelScope.launch {
            dao.deleteTakenOf(s.id)
            dao.deleteSupplement(s.id)
        }
    }

    fun setTaken(s: SupplementEntity, date: LocalDate, minuteOfDay: Int, on: Boolean) {
        viewModelScope.launch {
            if (on) dao.setTaken(SupplementTakenEntity(s.id, date.toString(), minuteOfDay)) else dao.clearTaken(s.id, date.toString())
        }
    }

    // ---- weight ----

    fun setWeight(date: LocalDate, kg: Double) {
        viewModelScope.launch { dao.setWeight(WeightEntity(date.toString(), kg)) }
    }

    fun deleteWeight(date: String) {
        viewModelScope.launch { dao.deleteWeight(date) }
    }

    // ---- fasting ----

    fun startFast(goalHours: Int, startMs: Long = System.currentTimeMillis()) {
        FoodSettings.setFast(context, startMs, goalHours)
    }

    fun endFast() {
        val start = FoodSettings.fastStartMs(context)
        if (start <= 0) return
        val goal = FoodSettings.fastGoalHours(context)
        val end = System.currentTimeMillis()
        viewModelScope.launch {
            if (end > start) dao.upsertFast(FastEntity("f:" + UUID.randomUUID().toString().take(12), start, end, goal))
            FoodSettings.setFast(context, 0L, goal)
        }
    }

    fun cancelFast() {
        FoodSettings.setFast(context, 0L, FoodSettings.fastGoalHours(context))
    }

    fun deleteFast(f: FastEntity) {
        viewModelScope.launch { dao.deleteFast(f.id) }
    }

    // ---- meal prep ----

    fun saveBatch(b: BatchEntity) {
        viewModelScope.launch { dao.upsertBatch(b) }
    }

    fun deleteBatch(b: BatchEntity) {
        viewModelScope.launch { dao.deleteBatch(b.id, System.currentTimeMillis()) }
    }

    /** Eats [grams] of the batch: logs them on the day and takes them off what is left. */
    fun eatFromBatch(b: BatchEntity, grams: Double, mealType: String, date: LocalDate, minuteOfDay: Int) {
        if (grams <= 0) return
        viewModelScope.launch {
            val eaten = grams.coerceAtMost(maxOf(b.leftGrams, 0.0).takeIf { it > 0 } ?: grams)
            foodDao.upsertMeal(FoodLogic.toMeal(BatchMath.asFood(b), eaten, date.toString(), minuteOfDay, mealType))
            dao.upsertBatch(b.copy(leftGrams = (b.leftGrams - eaten).coerceAtLeast(0.0)))
        }
    }

    // ---- plans ----

    fun savePlan(p: MealPlanEntity) {
        viewModelScope.launch { dao.upsertPlan(if (p.createdMs == 0L) p.copy(createdMs = System.currentTimeMillis()) else p) }
    }

    fun deletePlan(p: MealPlanEntity) {
        viewModelScope.launch { dao.deletePlan(p.id, System.currentTimeMillis()) }
    }

    /** Logs the meals of the plan that are still empty on [date]; says how many foods it added. */
    fun applyPlan(p: MealPlanEntity, date: LocalDate, nowMinute: Int, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val existing = foodDao.mealsOnceOn(date.toString())
            val today = date == LocalDate.now()
            val add = PlanMath.mealsToAdd(p, date.toString(), existing, { FoodLogic.minuteFor(it, today, nowMinute) }, FoodLogic::newMealId)
            if (add.isNotEmpty()) foodDao.upsertMeals(add)
            onDone(add.size)
        }
    }

    // ---- custom trackers ----

    val trackers: StateFlow<List<FoodTrackerEntity>> = share(emptyList(), dao.observeTrackers())

    /** Days that have at least one tap, for the dots on the week strip. */
    val eventDays: StateFlow<Set<String>> = share(emptySet(), dao.observeEventDates().map { it.toSet() })

    /** The taps of the week of the chosen day (Monday to Sunday). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weekEvents: StateFlow<List<TrackerEventEntity>> = share(
        emptyList(),
        day.flatMapLatest { d ->
            val week = FoodLogic.weekOf(d)
            dao.observeEventsBetween(week.first().toString(), week.last().toString())
        },
    )

    /** Taps and meals of the last [days] days up to the chosen day, for the Diary timeline. */
    private val diaryDays = MutableStateFlow(30)

    fun showEarlier() {
        diaryDays.value = diaryDays.value + 30
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val diary: StateFlow<List<DiaryDay>> = share(
        emptyList(),
        diaryDays.flatMapLatest { n ->
            val to = LocalDate.now()
            val from = to.minusDays(n.toLong())
            combine(foodDao.observeMealsBetween(from.toString(), to.toString()), dao.observeEventsBetween(from.toString(), to.toString())) { meals, events ->
                DiaryMath.days(meals, events)
            }
        },
    )

    val diarySpanDays: StateFlow<Int> = diaryDays

    fun saveTracker(t: FoodTrackerEntity) {
        viewModelScope.launch {
            val placed = if (t.sortOrder < 0) t.copy(sortOrder = dao.maxTrackerOrder() + 1) else t
            dao.upsertTracker(placed)
        }
    }

    fun deleteTracker(t: FoodTrackerEntity) {
        viewModelScope.launch {
            dao.deleteEventsOf(t.id)
            dao.deleteTracker(t.id)
        }
    }

    /** One tap: records [minuteOfDay] on [date]. */
    fun tap(t: FoodTrackerEntity, date: LocalDate, minuteOfDay: Int) {
        viewModelScope.launch { dao.upsertEvent(TrackerEventEntity(TrackerMath.newEventId(), t.id, date.toString(), minuteOfDay)) }
    }

    fun saveEvent(e: TrackerEventEntity) {
        viewModelScope.launch { dao.upsertEvent(e) }
    }

    fun deleteEvent(e: TrackerEventEntity) {
        viewModelScope.launch { dao.deleteEvent(e.id) }
    }

    /** The goal in force on [date], for the week summary. */
    fun goalOn(date: String): FoodGoalEntity? = FoodStats.goalFor(goals.value, LocalDate.parse(date))
}
