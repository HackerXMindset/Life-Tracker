package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.FoodEntity
import com.lifetracker.app.data.FoodFavouriteEntity
import com.lifetracker.app.data.FoodGoalEntity
import com.lifetracker.app.data.FoodLibrary
import com.lifetracker.app.data.FoodLibraryData
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodNutrientEntity
import com.lifetracker.app.data.FoodRef
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.Loggable
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.NutrientEntity
import com.lifetracker.app.data.NutrientMath
import com.lifetracker.app.data.ReadyDish
import com.lifetracker.app.data.SavedMealEntity
import com.lifetracker.app.data.SavedMealItem
import com.lifetracker.app.data.SavedMealItems
import com.lifetracker.app.data.WaterEntity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
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

    /** The built-in food list; null for the first moment while it is read. */
    private val libraryState = MutableStateFlow<FoodLibraryData?>(null)
    val library: StateFlow<FoodLibraryData?> = libraryState

    init {
        viewModelScope.launch(Dispatchers.IO) { libraryState.value = FoodLibrary.get(app) }
    }

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
        share(emptyList(), dao.observeRecentMeals().map { FoodStats.recentDistinct(it, 12) })

    /** Every day that has at least one meal, as yyyy-mm-dd, for the dots on the week strip. */
    val datesWithMeals: StateFlow<Set<String>> =
        share(emptySet(), dao.datesWithMeals().map { it.toSet() })

    val ownFoods: StateFlow<List<FoodEntity>> = share(emptyList(), dao.observeFoods())
    val favourites: StateFlow<List<FoodFavouriteEntity>> = share(emptyList(), dao.observeFavourites())
    val savedMeals: StateFlow<List<SavedMealEntity>> = share(emptyList(), dao.observeSavedMeals())
    val nutrients: StateFlow<List<NutrientEntity>> = share(emptyList(), dao.observeNutrients())

    fun select(day: LocalDate) {
        selected.value = day
    }

    fun shift(days: Long) {
        selected.value = selected.value.plusDays(days)
    }

    fun goToday() {
        selected.value = LocalDate.now()
    }

    // ---- finding foods ----

    /** A food of either kind by its reference ("a:banana", "m:..."), without the numbers you typed for its vitamins. */
    fun loggable(ref: String): Loggable? {
        val lib = libraryState.value
        if (FoodRef.isLibrary(ref)) return lib?.byRef(ref)?.let { FoodLogic.of(it, lib.microOf(it.id)) }
        return ownFoods.value.firstOrNull { it.id == ref }?.let { FoodLogic.of(it, emptyMap()) }
    }

    /** [food] with the vitamin numbers you typed laid over the ones that came with the app. */
    suspend fun withYourNumbers(food: Loggable): Loggable {
        if (food.ref.isEmpty()) return food
        return food.copy(micro = NutrientMath.merge(food.micro, dao.foodNutrients(food.ref)))
    }

    /** What you typed for a food's vitamins, by nutrient id (not including the numbers that came with the app). */
    suspend fun yourNumbers(ref: String): Map<String, Double> =
        dao.foodNutrients(ref).filter { it.amount100 > 0 }.associate { it.nutrientId to it.amount100 }

    // ---- logging ----

    fun addMeal(meal: MealEntity) {
        viewModelScope.launch { dao.upsertMeal(meal) }
    }

    fun deleteMeal(meal: MealEntity) {
        viewModelScope.launch { dao.deleteMeal(meal) }
    }

    /** Logs [grams] of [food] on the chosen day. */
    fun logFood(food: Loggable, grams: Double, mealType: String, date: LocalDate, minuteOfDay: Int) {
        viewModelScope.launch {
            val full = withYourNumbers(food)
            dao.upsertMeal(FoodLogic.toMeal(full, grams, date.toString(), minuteOfDay, mealType))
        }
    }

    /** Quick add: logs what you typed once and, when [save] is on, also keeps it as one of your foods. */
    fun quickAdd(
        name: String, grams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double,
        mealType: String, date: LocalDate, minuteOfDay: Int, save: Boolean,
    ) {
        viewModelScope.launch {
            val amount = if (grams > 0) grams else 100.0
            if (save) {
                val food = FoodLogic.foodFromQuickAdd(name, grams, kcal, protein, carbs, fat, nowMs = System.currentTimeMillis())
                dao.upsertFood(food)
                dao.upsertMeal(FoodLogic.toMeal(FoodLogic.of(food, emptyMap()), amount, date.toString(), minuteOfDay, mealType))
            } else {
                val k = 100.0 / amount
                dao.upsertMeal(
                    MealEntity(
                        id = FoodLogic.newMealId(), date = date.toString(), minuteOfDay = minuteOfDay, mealType = mealType,
                        name = name.trim().ifEmpty { "Food" }, grams = amount, kcal100 = kcal * k, carbs100 = carbs * k,
                        fat100 = fat * k, protein100 = protein * k, source = "app", raw = "",
                    ),
                )
            }
        }
    }

    /** Adds every food of a ready dish to the chosen day. */
    fun logDish(dish: ReadyDish, mealType: String, date: LocalDate, minuteOfDay: Int) {
        viewModelScope.launch {
            val lib = libraryState.value ?: return@launch
            val meals = dish.items.mapNotNull { item ->
                val f = lib.food(item.foodId) ?: return@mapNotNull null
                val base = withYourNumbers(FoodLogic.of(f, lib.microOf(f.id)))
                FoodLogic.toMeal(if (item.cooked) base.cooked() else base, item.grams, date.toString(), minuteOfDay, mealType)
            }
            dao.upsertMeals(meals)
        }
    }

    fun addWater(deltaMl: Int) {
        viewModelScope.launch {
            val day = selected.value.toString()
            val current = dao.water(day)?.ml ?: 0
            dao.setWater(WaterEntity(day, (current + deltaMl).coerceAtLeast(0)))
        }
    }

    /** Copies meals to another day, at the same times. */
    fun copyMeals(source: List<MealEntity>, to: LocalDate) {
        viewModelScope.launch {
            dao.upsertMeals(source.map { it.copy(id = FoodLogic.newMealId(), date = to.toString(), source = "app") })
        }
    }

    fun deleteMeals(list: List<MealEntity>) {
        viewModelScope.launch { list.forEach { dao.deleteMeal(it) } }
    }

    /** The goal from the chosen day on. */
    fun setGoal(kcal: Double, protein: Double, carbs: Double, fat: Double) {
        viewModelScope.launch { dao.upsertGoals(listOf(FoodGoalEntity(selected.value.toString(), kcal, carbs, fat, protein))) }
    }

    // ---- your foods, stars and saved meals ----

    /** Saves one of your foods, with the vitamin numbers you typed ([micro], by nutrient id). */
    fun saveFood(food: FoodEntity, micro: Map<String, Double>, onSaved: (Loggable) -> Unit = {}) {
        viewModelScope.launch {
            val placed = if (food.createdMs == 0L) food.copy(createdMs = System.currentTimeMillis()) else food
            dao.upsertFood(placed)
            saveNumbers(placed.id, micro)
            onSaved(FoodLogic.of(placed, micro))
        }
    }

    /** Replaces the vitamin numbers you typed for any food (built in or yours). */
    fun saveNumbers(ref: String, micro: Map<String, Double>) {
        viewModelScope.launch {
            dao.deleteFoodNutrientsOfFood(ref)
            dao.upsertFoodNutrients(micro.filter { it.value > 0 }.map { FoodNutrientEntity(ref, it.key, it.value) })
        }
    }

    fun deleteFood(food: FoodEntity) {
        viewModelScope.launch {
            dao.deleteFood(food.id, System.currentTimeMillis())
            dao.removeFavourite(food.id)
        }
    }

    fun toggleFavourite(ref: String, on: Boolean) {
        if (ref.isEmpty()) return
        viewModelScope.launch {
            if (on) dao.addFavourite(FoodFavouriteEntity(ref, System.currentTimeMillis())) else dao.removeFavourite(ref)
        }
    }

    /** Keeps the meals of one meal type as a saved meal you can add again. */
    fun saveMealAs(name: String, from: List<MealEntity>) {
        if (from.isEmpty() || name.isBlank()) return
        viewModelScope.launch {
            dao.upsertSavedMeal(
                SavedMealEntity(
                    id = FoodLogic.newSavedMealId(), name = name.trim(),
                    items = SavedMealItems.encode(from.map { SavedMealItem.of(it) }), createdMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun renameSavedMeal(meal: SavedMealEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { dao.upsertSavedMeal(meal.copy(name = name.trim())) }
    }

    fun deleteSavedMeal(meal: SavedMealEntity) {
        viewModelScope.launch { dao.deleteSavedMeal(meal.id, System.currentTimeMillis()) }
    }

    /** Adds every food of a saved meal to the chosen day. */
    fun logSavedMeal(saved: SavedMealEntity, mealType: String, date: LocalDate, minuteOfDay: Int) {
        viewModelScope.launch {
            val meals = SavedMealItems.decode(saved.items).map {
                it.asMeal(FoodLogic.newMealId(), date.toString(), minuteOfDay, mealType)
            }
            dao.upsertMeals(meals)
        }
    }
}
