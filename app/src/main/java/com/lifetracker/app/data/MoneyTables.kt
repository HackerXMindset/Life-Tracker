package com.lifetracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Tables added in database version 5: your money.
 *
 * Three things live here:
 *  - categories you make yourself (one list for spending, one for income);
 *  - saved items: things that come MONTHLY (rent, a subscription, a salary) and
 *    things you log OFTEN (a lassi, a bus ticket);
 *  - the entries: every amount that was actually spent or received.
 *
 * "No value" is stored as an empty string (never null) in the saved items, to keep
 * the database simple. Amounts are plain numbers; the currency symbol is a setting.
 *
 * IMPORTANT: if any column here changes, the database version must go up and a
 * Migration must be added. See MIGRATION_4_5 in Database.kt.
 */

const val KIND_EXPENSE = 0
const val KIND_INCOME = 1

/** A category for spending or income. [kind] is [KIND_EXPENSE] or [KIND_INCOME]. */
@Entity(tableName = "money_categories")
data class MoneyCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val kind: Int,
    val sortOrder: Int,
    val archived: Boolean,
)

/**
 * A saved item. [type] is [MoneyItemEntity.MONTHLY] or [MoneyItemEntity.OFTEN].
 * Monthly items use [chargeDay] (1 to 31), [startDate], an optional [endDate] and
 * [lastPosted] (the last month, as "2026-10", that the app has already added).
 */
@Entity(tableName = "money_items")
data class MoneyItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: Int,
    val categoryId: String,
    val amount: Double,
    val type: Int,
    val chargeDay: Int,
    val startDate: String,
    val endDate: String,
    val lastPosted: String,
    val archived: Boolean,
    val sortOrder: Int,
) {
    companion object {
        const val MONTHLY = 0
        const val OFTEN = 1
    }
}

/** One amount spent or received on a day. [itemId] is the saved item it came from, or "" for a one-off. */
@Entity(tableName = "money_entries", indices = [Index("date")])
data class MoneyEntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val kind: Int,
    val categoryId: String,
    val name: String,
    val amount: Double,
    val note: String,
    val itemId: String,
)

@Dao
interface MoneyDao {
    @Query("SELECT * FROM money_categories ORDER BY kind, sortOrder, name")
    fun observeCategories(): Flow<List<MoneyCategoryEntity>>

    @Query("SELECT * FROM money_categories ORDER BY kind, sortOrder, name")
    suspend fun allCategories(): List<MoneyCategoryEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM money_categories WHERE kind = :kind")
    suspend fun maxCategoryOrder(kind: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: MoneyCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<MoneyCategoryEntity>)

    @Query("DELETE FROM money_categories")
    suspend fun deleteAllCategories()

    @Query("SELECT * FROM money_items ORDER BY type, sortOrder, name")
    fun observeItems(): Flow<List<MoneyItemEntity>>

    @Query("SELECT * FROM money_items ORDER BY type, sortOrder, name")
    suspend fun allItems(): List<MoneyItemEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM money_items")
    suspend fun maxItemOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: MoneyItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<MoneyItemEntity>)

    @Query("DELETE FROM money_items")
    suspend fun deleteAllItems()

    /** Entries from [from] (included) to [to] (not included), as ISO dates. */
    @Query("SELECT * FROM money_entries WHERE date >= :from AND date < :to ORDER BY date DESC, id")
    fun entriesBetween(from: String, to: String): Flow<List<MoneyEntryEntity>>

    @Query("SELECT * FROM money_entries ORDER BY date, id")
    suspend fun allEntries(): List<MoneyEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: MoneyEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<MoneyEntryEntity>)

    @androidx.room.Delete
    suspend fun deleteEntry(entry: MoneyEntryEntity)

    @Query("DELETE FROM money_entries")
    suspend fun deleteAllEntries()
}

/** Starter categories, so adding money works straight away. You can rename, recolour or hide any of them. */
object DefaultMoneyCategories {
    val list: List<MoneyCategoryEntity> = listOf(
        MoneyCategoryEntity("x-food", "Food", 0xFFC28410L, KIND_EXPENSE, 0, false),
        MoneyCategoryEntity("x-travel", "Travel", 0xFF2878B4L, KIND_EXPENSE, 1, false),
        MoneyCategoryEntity("x-bills", "Rent and bills", 0xFF8A5FC2L, KIND_EXPENSE, 2, false),
        MoneyCategoryEntity("x-study", "Study", 0xFF17785AL, KIND_EXPENSE, 3, false),
        MoneyCategoryEntity("x-shopping", "Shopping", 0xFFD6457FL, KIND_EXPENSE, 4, false),
        MoneyCategoryEntity("x-other", "Other", 0xFF738078L, KIND_EXPENSE, 5, false),
        MoneyCategoryEntity("i-pocket", "Pocket money", 0xFF2E9E6BL, KIND_INCOME, 0, false),
        MoneyCategoryEntity("i-salary", "Salary", 0xFF17785AL, KIND_INCOME, 1, false),
        MoneyCategoryEntity("i-other", "Other income", 0xFF738078L, KIND_INCOME, 2, false),
    )

    /** Fills an empty table. Never overwrites a row that is already there. */
    fun seed(db: SupportSQLiteDatabase) {
        for (c in list) {
            db.execSQL(
                "INSERT OR IGNORE INTO `money_categories` (`id`, `name`, `color`, `kind`, `sortOrder`, `archived`) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(c.id, c.name, c.color, c.kind, c.sortOrder, if (c.archived) 1 else 0),
            )
        }
    }
}
