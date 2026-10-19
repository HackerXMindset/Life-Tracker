package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Added in database version 21: the categories a place can have (Home, Study, Gym...). Each has a name, a colour and an
 * icon. A place keeps pointing at its category by [id], which is what its `kind` has always held: the eight that were
 * built in keep their old ids ("home", "study", "gym", "work", "food", "shop", "people", "other"), so no place changes.
 * Categories are never deleted; [archived] only hides one from the list when picking.
 */
@Entity(tableName = "place_categories")
data class PlaceCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val icon: String,
    val sortOrder: Int,
    val archived: Boolean,
)

@Dao
interface PlaceCategoryDao {
    @Query("SELECT * FROM place_categories ORDER BY archived, sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlaceCategoryEntity>>

    @Query("SELECT * FROM place_categories ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun all(): List<PlaceCategoryEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM place_categories")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: PlaceCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<PlaceCategoryEntity>)
}

/** How a place is drawn on the Timeline: the colour of its line, its icon and its category's name. */
class PlaceLook(val color: Long, val icon: String, val category: String)

object DefaultPlaceCategories {
    val list: List<PlaceCategoryEntity> = listOf(
        PlaceCategoryEntity("home", "Home", 0xFF8E94E8L, "house", 0, false),
        PlaceCategoryEntity("study", "Study", 0xFF4FB38AL, "bookOpen", 1, false),
        PlaceCategoryEntity("gym", "Gym", 0xFFE08A5FL, "dumbbell", 2, false),
        PlaceCategoryEntity("work", "Work", 0xFF62A9D6L, "briefcase", 3, false),
        PlaceCategoryEntity("food", "Food", 0xFFE0A84AL, "utensils", 4, false),
        PlaceCategoryEntity("shop", "Shop", 0xFFD27BA8L, "shoppingBag", 5, false),
        PlaceCategoryEntity("people", "Friends and family", 0xFFE0657BL, "users", 6, false),
        PlaceCategoryEntity("other", "Other", 0xFF8A8F98L, "mapPin", 7, false),
    )

    /** Adds the built-in categories that are missing. Never overwrites a row that is already there. */
    fun seed(db: SupportSQLiteDatabase) {
        for (c in list) {
            db.execSQL(
                "INSERT OR IGNORE INTO `place_categories` (`id`, `name`, `color`, `icon`, `sortOrder`, `archived`) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(c.id, c.name, c.color, c.icon, c.sortOrder, 0),
            )
        }
    }
}

object PlaceLooks {
    /** The look of each place by its id: its category's colour, icon and name. A place whose category is unknown looks like "other". */
    fun resolve(places: List<PlaceEntity>, categories: List<PlaceCategoryEntity>): Map<String, PlaceLook> {
        val byId = categories.associateBy { it.id }
        val fallback = byId["other"] ?: DefaultPlaceCategories.list.last()
        return places.associate { p ->
            val c = byId[p.kind] ?: DefaultPlaceCategories.list.firstOrNull { it.id == p.kind } ?: fallback
            p.id to PlaceLook(c.color, c.icon, c.name)
        }
    }

    /** A fresh id for a category you make. */
    fun newId(nowMs: Long): String = "pc$nowMs"
}
