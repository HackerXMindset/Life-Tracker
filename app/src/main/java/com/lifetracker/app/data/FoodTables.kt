package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Tables added in database version 3 (food and water).
 *
 * Like OpenNutriTracker, every amount is in grams and every nutrient value is
 * per 100 g, so a meal's calories are `kcal100 * grams / 100`.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_2_3 in Database.kt.
 */

/** One thing eaten. `mealType` is breakfast, lunch, dinner or snack. `source` is "ont" (imported) or "app". */
@Entity(tableName = "meals", indices = [Index("date")])
data class MealEntity(
    @PrimaryKey val id: String,
    val date: String,
    val minuteOfDay: Int,
    val mealType: String,
    val name: String,
    val grams: Double,
    val kcal100: Double,
    val carbs100: Double,
    val fat100: Double,
    val protein100: Double,
    val source: String,
    val raw: String,
) {
    val kcal: Double get() = kcal100 * grams / 100
    val carbs: Double get() = carbs100 * grams / 100
    val fat: Double get() = fat100 * grams / 100
    val protein: Double get() = protein100 * grams / 100
}

/** The daily targets that applied on [date]. A day without its own row uses the latest earlier one. */
@Entity(tableName = "food_goals")
data class FoodGoalEntity(
    @PrimaryKey val date: String,
    val kcal: Double,
    val carbs: Double,
    val fat: Double,
    val protein: Double,
)

/** Water drunk on [date], in millilitres. */
@Entity(tableName = "water_log")
data class WaterEntity(
    @PrimaryKey val date: String,
    val ml: Int,
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM meals WHERE date = :date ORDER BY minuteOfDay, id")
    fun mealsOn(date: String): Flow<List<MealEntity>>

    @Query("SELECT DISTINCT date FROM meals")
    fun datesWithMeals(): Flow<List<String>>

    @Query("SELECT * FROM food_goals ORDER BY date")
    fun observeGoals(): Flow<List<FoodGoalEntity>>

    @Query("SELECT * FROM water_log WHERE date = :date")
    fun waterOn(date: String): Flow<WaterEntity?>

    @Query("SELECT * FROM meals ORDER BY date DESC, minuteOfDay DESC LIMIT 300")
    fun observeRecentMeals(): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals")
    suspend fun allMeals(): List<MealEntity>

    @Query("SELECT * FROM food_goals")
    suspend fun allGoals(): List<FoodGoalEntity>

    @Query("SELECT * FROM water_log")
    suspend fun allWater(): List<WaterEntity>

    @Query("SELECT * FROM water_log WHERE date = :date")
    suspend fun water(date: String): WaterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeals(meals: List<MealEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeal(meal: MealEntity)

    @Delete
    suspend fun deleteMeal(meal: MealEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoals(goals: List<FoodGoalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWater(water: List<WaterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setWater(water: WaterEntity)

    @Query("DELETE FROM meals")
    suspend fun deleteAllMeals()

    @Query("DELETE FROM food_goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM water_log")
    suspend fun deleteAllWater()
}
