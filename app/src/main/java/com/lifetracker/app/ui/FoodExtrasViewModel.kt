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
import com.lifetracker.app.data.BodyGoalSync
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.HabitLink
import com.lifetracker.app.data.HabitMarks
import com.lifetracker.app.data.MonthMath
import com.lifetracker.app.data.StatSpan
import com.lifetracker.app.data.TrackerEventEntity
import com.lifetracker.app.data.TrackerMigration
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.MealPlanEntity
import com.lifetracker.app.data.PlanMath
import com.lifetracker.app.data.SupplementEntity
import com.lifetracker.app.data.SupplementTakenEntity
import com.lifetracker.app.data.WeightEntity
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // ---- trackers are habits ----
    //
    // A "tracker" is a habit of the Habits tab that you picked to show in Food. Its checks are the habit's own, so nothing is
    // kept twice: a tap here is a check there, and a check there shows up here.

    private val habitDao = db.habitDao()

    init {
        viewModelScope.launch { runCatching { TrackerMigration.run(context) } }
        // The daily goal follows the Body setup: a new weigh-in or a changed setup updates it, unless you set a goal by hand.
        viewModelScope.launch {
            combine(dao.observeWeights(), foodDao.observeGoals(), FoodSettings.changes) { w, g, _ -> w to g }.collect { (w, g) ->
                if (!FoodSettings.autoGoal(context)) return@collect
                val due = BodyGoalSync.due(FoodSettings.bodyProfile(context), w, g, LocalDate.now()) ?: return@collect
                foodDao.upsertGoals(listOf(due))
            }
        }
    }

    private val watchedIds = FoodSettings.changes.map { FoodSettings.watchedHabits(context) }.distinctUntilChanged()

    /** Every habit you can pick to show here. */
    val habits: StateFlow<List<HabitEntity>> = share(emptyList(), habitDao.observeHabits().map { list -> list.filter { !it.archived } })

    /** The ids of the habits you picked, in the order you picked them. */
    val picked: StateFlow<List<String>> = share(emptyList(), watchedIds)

    /** The picked habits that still exist, shaped like the old trackers so the cards and the Diary read them the same way. */
    val trackers: StateFlow<List<FoodTrackerEntity>> = share(
        emptyList(),
        combine(habitDao.observeHabits(), watchedIds) { all, ids ->
            ids.mapNotNull { id -> all.firstOrNull { it.id == id && !it.archived } }
                .mapIndexed { i, h -> FoodTrackerEntity(h.id, h.name, h.icon, h.color, i, false) }
        },
    )

    /** Every check of the picked habits, one line each. */
    private val marks = combine(habitDao.observeCompletions(), trackers) { completions, picked ->
        HabitMarks.events(completions, picked.map { it.id }.toSet())
    }

    /** Every check of the picked habits, for the month grid and the timeline. */
    val checks: StateFlow<List<TrackerEventEntity>> = share(emptyList(), marks)

    private val month = MutableStateFlow(YearMonth.now())

    /** The month the month grid shows. */
    fun setMonth(m: YearMonth) {
        month.value = m
    }

    /** The meals of the month on show, for the rings of the month grid. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val monthMeals: StateFlow<List<MealEntity>> = share(
        emptyList(),
        month.flatMapLatest { m ->
            val (from, to) = MonthMath.span(m)
            foodDao.observeMealsBetween(from, to)
        },
    )

    /** Every meal ever logged, for the history of one food. */
    suspend fun allMeals(): List<MealEntity> = foodDao.allMeals()

    // ---- the stats window ----

    private val statSpan = MutableStateFlow<StatSpan?>(null)

    /** The days the stats window shows. */
    fun setStatSpan(span: StatSpan) {
        statSpan.value = span
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val statMeals: StateFlow<List<MealEntity>> = share(
        emptyList(),
        statSpan.flatMapLatest { span ->
            if (span == null) kotlinx.coroutines.flow.flowOf(emptyList()) else foodDao.observeMealsBetween(span.from.toString(), span.to.toString())
        },
    )

    /** The first day with food logged, for the earliest period and "All time". */
    val firstMealDate: StateFlow<LocalDate?> = share(null, foodDao.observeFirstMealDate().map { it?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } })

    /** Days that have at least one check, for the dots on the week strip. */
    val eventDays: StateFlow<Set<String>> = share(emptySet(), marks.map { list -> list.map { it.date }.toSet() })

    /** The checks of the week of the chosen day (Monday to Sunday). */
    val weekEvents: StateFlow<List<TrackerEventEntity>> = share(
        emptyList(),
        combine(marks, day) { list, d ->
            val week = FoodLogic.weekOf(d)
            val from = week.first().toString()
            val to = week.last().toString()
            list.filter { it.date in from..to }
        },
    )

    /** Checks and meals of the last [days] days up to the chosen day, for the Diary timeline. */
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
            combine(foodDao.observeMealsBetween(from.toString(), to.toString()), marks) { meals, events ->
                DiaryMath.days(meals, events.filter { it.date in from.toString()..to.toString() })
            }
        },
    )

    val diarySpanDays: StateFlow<Int> = diaryDays

    /** Shows a habit in the Food tab, or stops showing it. The habit itself is not touched. */
    fun setPicked(habitId: String, on: Boolean) {
        val now = FoodSettings.watchedHabits(context)
        FoodSettings.setWatchedHabits(context, if (on) now + habitId else now - habitId)
    }

    /** Makes a new habit (count by tap, no streak) and shows it here. */
    fun createHabit(name: String, icon: String, color: Long) {
        viewModelScope.launch {
            val id = HabitLink.create(context, name.trim(), icon, color)
            setPicked(id, true)
        }
    }

    /** One tap: one more check of the habit at [minuteOfDay] on [date]. */
    fun tap(t: FoodTrackerEntity, date: LocalDate, minuteOfDay: Int) {
        viewModelScope.launch { HabitLink.addCheck(context, t.id, date.toString(), minuteOfDay) }
    }

    /** A check moved to another time. */
    fun saveEvent(e: TrackerEventEntity) {
        val ref = HabitMarks.parseId(e.id) ?: return
        if (ref.minute == e.minuteOfDay) return
        viewModelScope.launch { HabitLink.retimeCheck(context, ref.habitId, ref.date, ref.minute, e.minuteOfDay) }
    }

    fun deleteEvent(e: TrackerEventEntity) {
        val ref = HabitMarks.parseId(e.id) ?: return
        viewModelScope.launch { HabitLink.removeCheck(context, ref.habitId, ref.date, ref.minute) }
    }

    /** The goal in force on [date], for the week summary. */
    fun goalOn(date: String): FoodGoalEntity? = FoodStats.goalFor(goals.value, LocalDate.parse(date))
}
