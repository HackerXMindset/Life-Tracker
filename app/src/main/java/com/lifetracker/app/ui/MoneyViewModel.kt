package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.MoneyCategoryEntity
import com.lifetracker.app.data.MoneyEntryEntity
import com.lifetracker.app.data.MoneyItemEntity
import com.lifetracker.app.data.MoneyPoster
import com.lifetracker.app.data.MoneySettings
import com.lifetracker.app.data.MoneyStats
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoneyViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).moneyDao()
    private val settings = MoneySettings(app)

    private val selectedMonth = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = selectedMonth

    private val currencyFlow = MutableStateFlow(settings.currency)
    val currency: StateFlow<String> = currencyFlow

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<MoneyEntryEntity>> = share(
        emptyList(),
        selectedMonth.flatMapLatest { m ->
            val (from, to) = MoneyStats.range(m)
            dao.entriesBetween(from, to)
        },
    )

    val categories: StateFlow<List<MoneyCategoryEntity>> = share(emptyList(), dao.observeCategories())
    val items: StateFlow<List<MoneyItemEntity>> = share(emptyList(), dao.observeItems())

    init {
        // Add anything a monthly item has come due for since the app was last open.
        viewModelScope.launch { runCatching { MoneyPoster.postDue(app) } }
    }

    fun shiftMonth(months: Long) {
        selectedMonth.value = selectedMonth.value.plusMonths(months)
    }

    fun goThisMonth() {
        selectedMonth.value = YearMonth.now()
    }

    fun setCurrency(symbol: String) {
        settings.currency = symbol
        currencyFlow.value = settings.currency
    }

    fun addEntry(entry: MoneyEntryEntity) {
        viewModelScope.launch { dao.upsertEntry(entry) }
    }

    fun deleteEntry(entry: MoneyEntryEntity) {
        viewModelScope.launch { dao.deleteEntry(entry) }
    }

    /** Saves a category. A new one (sortOrder -1) goes to the end of its own list. */
    fun saveCategory(category: MoneyCategoryEntity, onSaved: (MoneyCategoryEntity) -> Unit = {}) {
        viewModelScope.launch {
            val placed = if (category.sortOrder < 0) {
                category.copy(sortOrder = dao.maxCategoryOrder(category.kind) + 1)
            } else {
                category
            }
            dao.upsertCategory(placed)
            onSaved(placed)
        }
    }

    /**
     * Saves a monthly or often item. A new monthly item is posted straight away if a charge
     * has already come due; a new often item can be logged once right now.
     */
    fun saveItem(item: MoneyItemEntity, logNow: Boolean) {
        viewModelScope.launch {
            val placed = if (item.sortOrder < 0) item.copy(sortOrder = dao.maxItemOrder() + 1) else item
            dao.upsertItem(placed)
            if (placed.type == MoneyItemEntity.MONTHLY) {
                runCatching { MoneyPoster.postDue(getApplication()) }
            } else if (logNow) {
                dao.upsertEntry(entryFor(placed, placed.amount))
            }
        }
    }

    fun setItemArchived(item: MoneyItemEntity, archived: Boolean) {
        viewModelScope.launch {
            dao.upsertItem(item.copy(archived = archived))
            if (!archived && item.type == MoneyItemEntity.MONTHLY) {
                runCatching { MoneyPoster.postDue(getApplication()) }
            }
        }
    }

    /** Logs an often item for today, at its saved amount unless another amount is given. */
    fun logItem(item: MoneyItemEntity, amount: Double = item.amount) {
        viewModelScope.launch { dao.upsertEntry(entryFor(item, amount)) }
    }

    private fun entryFor(item: MoneyItemEntity, amount: Double) = MoneyEntryEntity(
        id = MoneyStats.newId(),
        date = LocalDate.now().toString(),
        kind = item.kind,
        categoryId = item.categoryId,
        name = item.name,
        amount = amount,
        note = "",
        itemId = item.id,
    )
}
