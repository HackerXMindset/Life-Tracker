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
import com.lifetracker.app.data.MoneyViews
import com.lifetracker.app.data.Period
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoneyViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).moneyDao()
    private val settings = MoneySettings(app)

    private val selectedPeriod = MutableStateFlow(Period.Month)
    val period: StateFlow<Period> = selectedPeriod

    /** Any day inside the period being shown. */
    private val selectedAnchor = MutableStateFlow(LocalDate.now())
    val anchor: StateFlow<LocalDate> = selectedAnchor

    private val currencyFlow = MutableStateFlow(settings.currency)
    val currency: StateFlow<String> = currencyFlow

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<MoneyEntryEntity>> = share(
        emptyList(),
        combine(selectedPeriod, selectedAnchor) { p, a -> p to a }.flatMapLatest { (p, a) ->
            dao.entriesBetween(MoneyViews.start(p, a).toString(), MoneyViews.endExclusive(p, a).toString())
        },
    )

    /** The period just before the one shown, to say whether spending went up or down. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val previousEntries: StateFlow<List<MoneyEntryEntity>> = share(
        emptyList(),
        combine(selectedPeriod, selectedAnchor) { p, a -> p to MoneyViews.shift(p, a, -1) }.flatMapLatest { (p, a) ->
            dao.entriesBetween(MoneyViews.start(p, a).toString(), MoneyViews.endExclusive(p, a).toString())
        },
    )

    val categories: StateFlow<List<MoneyCategoryEntity>> = share(emptyList(), dao.observeCategories())
    val items: StateFlow<List<MoneyItemEntity>> = share(emptyList(), dao.observeItems())

    init {
        // Add anything a monthly item has come due for since the app was last open.
        viewModelScope.launch { runCatching { MoneyPoster.postDue(app) } }
    }

    fun setPeriod(period: Period) {
        selectedPeriod.value = period
    }

    fun shift(n: Int) {
        selectedAnchor.value = MoneyViews.shift(selectedPeriod.value, selectedAnchor.value, n)
    }

    fun goToday() {
        selectedAnchor.value = LocalDate.now()
    }

    /** Opens a month directly (from a bar of the year view). */
    fun open(period: Period, day: LocalDate) {
        selectedPeriod.value = period
        selectedAnchor.value = day
    }

    fun setCurrency(symbol: String) {
        settings.currency = symbol
        currencyFlow.value = settings.currency
    }

    fun addEntry(entry: MoneyEntryEntity) {
        viewModelScope.launch { dao.upsertEntry(entry) }
    }

    /** How many entries and saved items use a category (one that is used can be hidden but not deleted). */
    suspend fun usesOf(category: MoneyCategoryEntity): Int = dao.usesOfCategory(category.id)

    fun deleteEntry(entry: MoneyEntryEntity) {
        viewModelScope.launch { dao.deleteEntry(entry) }
    }

    /** Deletes a saved item. The entries it already made stay. */
    fun deleteItem(item: MoneyItemEntity) {
        viewModelScope.launch { dao.deleteItem(item.id) }
    }

    /** Deletes a category only when no entry or saved item uses it; otherwise hides it. Returns true when it was deleted. */
    fun deleteCategory(category: MoneyCategoryEntity, onDone: (deleted: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (dao.usesOfCategory(category.id) == 0) {
                dao.deleteCategory(category.id)
                onDone(true)
            } else {
                dao.upsertCategory(category.copy(archived = true))
                onDone(false)
            }
        }
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
        source = item.source,
        paidBy = item.paidBy,
    )
}
