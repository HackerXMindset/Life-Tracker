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
    /** The food it came from ("a:banana", "m:<id>", see [FoodRef]), or "" for one typed in once (and for meals imported before this). */
    val foodId: String = "",
    val fiber100: Double = 0.0,
    val sugar100: Double = 0.0,
    /** Sodium in milligrams per 100 g. */
    val sodium100: Double = 0.0,
    val sat100: Double = 0.0,
    val mono100: Double = 0.0,
    val poly100: Double = 0.0,
    /** Protein per 100 g that comes from animals. */
    val animal100: Double = 0.0,
    /** The nine essential amino acids in g per 100 g, separated by commas, or "" when not known. */
    val amino: String = "",
    /** Vitamins, minerals and your own nutrients per 100 g, written by [NutrientValues]; "" when the food had none. */
    val extra: String = "",
) {
    val kcal: Double get() = kcal100 * grams / 100
    val carbs: Double get() = carbs100 * grams / 100
    val fat: Double get() = fat100 * grams / 100
    val protein: Double get() = protein100 * grams / 100
    val fiber: Double get() = fiber100 * grams / 100
    val sugar: Double get() = sugar100 * grams / 100
    val sodiumMg: Double get() = sodium100 * grams / 100
}

/**
 * A food you made yourself (database version 33). The built-in foods are not stored here; see [FoodLibrary].
 * [id] starts with "m:". [portionKind] is a [PortionKind.key] or "", [category] a [FoodCat.key] or "".
 * Deleting a food only sets [deletedMs]; meals you already logged keep their own copy of the numbers.
 */
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val fiber100: Double = 0.0,
    val sugar100: Double = 0.0,
    val sodium100: Double = 0.0,
    val sat100: Double = 0.0,
    val mono100: Double = 0.0,
    val poly100: Double = 0.0,
    val animal100: Double = 0.0,
    val amino: String = "",
    val liquid: Boolean = false,
    val portionKind: String = "",
    val portionGrams: Double = 0.0,
    val category: String = "",
    val cookedYield: Double = 0.0,
    val createdMs: Long = 0,
    val deletedMs: Long = 0,
)

/** A food (built-in "a:..." or your own "m:...") you marked with a star. */
@Entity(tableName = "food_favourites")
data class FoodFavouriteEntity(
    @PrimaryKey val foodId: String,
    val addedMs: Long = 0,
)

/**
 * A meal you saved to use again ("Saved meals"). [items] is a list of foods with their amounts, written by [SavedMealItems];
 * [color] is a colour for its card, or 0.
 */
@Entity(tableName = "saved_meals")
data class SavedMealEntity(
    @PrimaryKey val id: String,
    val name: String,
    val items: String,
    val color: Long = 0,
    val createdMs: Long = 0,
    val deletedMs: Long = 0,
)

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

    @Query("SELECT * FROM meals WHERE date BETWEEN :from AND :to ORDER BY date, minuteOfDay, id")
    fun observeMealsBetween(from: String, to: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE date = :date ORDER BY minuteOfDay, id")
    suspend fun mealsOnceOn(date: String): List<MealEntity>

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

    @Query("SELECT * FROM foods WHERE deletedMs = 0 ORDER BY name COLLATE NOCASE")
    fun observeFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods")
    suspend fun allFoods(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun food(id: String): FoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFood(food: FoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoods(foods: List<FoodEntity>)

    @Query("UPDATE foods SET deletedMs = :now WHERE id = :id")
    suspend fun deleteFood(id: String, now: Long)

    @Query("DELETE FROM foods")
    suspend fun deleteAllFoods()

    @Query("SELECT * FROM food_favourites ORDER BY addedMs DESC")
    fun observeFavourites(): Flow<List<FoodFavouriteEntity>>

    @Query("SELECT * FROM food_favourites")
    suspend fun allFavourites(): List<FoodFavouriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavourite(favourite: FoodFavouriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavourites(favourites: List<FoodFavouriteEntity>)

    @Query("DELETE FROM food_favourites WHERE foodId = :foodId")
    suspend fun removeFavourite(foodId: String)

    @Query("DELETE FROM food_favourites")
    suspend fun deleteAllFavourites()

    @Query("SELECT * FROM saved_meals WHERE deletedMs = 0 ORDER BY name COLLATE NOCASE")
    fun observeSavedMeals(): Flow<List<SavedMealEntity>>

    @Query("SELECT * FROM saved_meals")
    suspend fun allSavedMeals(): List<SavedMealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedMeal(meal: SavedMealEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedMeals(meals: List<SavedMealEntity>)

    @Query("UPDATE saved_meals SET deletedMs = :now WHERE id = :id")
    suspend fun deleteSavedMeal(id: String, now: Long)

    @Query("DELETE FROM saved_meals")
    suspend fun deleteAllSavedMeals()

    @Query("SELECT * FROM nutrients ORDER BY `group` = 'other', `group` = 'mineral', sortOrder, name COLLATE NOCASE")
    fun observeNutrients(): Flow<List<NutrientEntity>>

    @Query("SELECT * FROM nutrients ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun allNutrients(): List<NutrientEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM nutrients")
    suspend fun maxNutrientOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNutrient(nutrient: NutrientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNutrients(nutrients: List<NutrientEntity>)

    @Query("DELETE FROM nutrients WHERE id = :id")
    suspend fun deleteNutrient(id: String)

    @Query("DELETE FROM nutrients")
    suspend fun deleteAllNutrients()

    @Query("SELECT * FROM food_nutrients")
    suspend fun allFoodNutrients(): List<FoodNutrientEntity>

    @Query("SELECT * FROM food_nutrients WHERE foodRef = :foodRef")
    suspend fun foodNutrients(foodRef: String): List<FoodNutrientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFoodNutrient(value: FoodNutrientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodNutrients(values: List<FoodNutrientEntity>)

    @Query("DELETE FROM food_nutrients WHERE nutrientId = :nutrientId")
    suspend fun deleteFoodNutrientsOf(nutrientId: String)

    @Query("DELETE FROM food_nutrients WHERE foodRef = :foodRef")
    suspend fun deleteFoodNutrientsOfFood(foodRef: String)

    @Query("DELETE FROM food_nutrients")
    suspend fun deleteAllFoodNutrients()

    @Query("DELETE FROM meals")
    suspend fun deleteAllMeals()

    @Query("DELETE FROM food_goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM water_log")
    suspend fun deleteAllWater()
}
