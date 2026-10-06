package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.EntryEntity
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).entryDao()

    private val selectedDate = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = selectedDate

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<EntryEntity>> = selectedDate
        .flatMapLatest { dao.forDate(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val datesWithEntries: StateFlow<Set<String>> = dao.datesWithEntries()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun add(entry: EntryEntity) {
        viewModelScope.launch { dao.insert(entry) }
    }

    fun delete(entry: EntryEntity) {
        viewModelScope.launch { dao.delete(entry) }
    }
}
