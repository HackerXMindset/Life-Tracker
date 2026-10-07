package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.EntryEntity
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.data.TodoEntity
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimelineViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.entryDao()
    private val plan = db.planDao()

    private val selectedDate = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = selectedDate

    private fun <T> share(initial: T, flow: kotlinx.coroutines.flow.Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<EntryEntity>> =
        share(emptyList(), selectedDate.flatMapLatest { dao.forDate(it.toString()) })

    /** To-dos planned for the selected day (only to-dos with a date ever appear on the Timeline). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todos: StateFlow<List<TodoEntity>> =
        share(emptyList(), selectedDate.flatMapLatest { plan.todosOn(it.toString()) })

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteEntity>> =
        share(emptyList(), selectedDate.flatMapLatest { plan.notesOn(it.toString()) })

    /** Project name by id, for labelling to-dos. */
    val projectNames: StateFlow<Map<String, String>> =
        share(emptyMap(), plan.observeTodoTags().map { tags -> tags.associate { it.id to it.name } })

    /** Habit name by id, for labelling notes. */
    val habitNames: StateFlow<Map<String, String>> =
        share(emptyMap(), db.habitDao().observeHabits().map { list -> list.associate { it.id to it.name } })

    /** Days that have anything on them: entries, planned to-dos or notes. */
    val datesWithEntries: StateFlow<Set<String>> = share(
        emptySet(),
        combine(dao.datesWithEntries(), plan.datesWithTodos(), plan.datesWithNotes()) { a, b, c ->
            val all = HashSet<String>()
            all.addAll(a)
            all.addAll(b)
            all.addAll(c)
            all
        },
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun add(entry: EntryEntity) {
        viewModelScope.launch { dao.insert(entry) }
    }

    fun delete(entry: EntryEntity) {
        viewModelScope.launch { dao.delete(entry) }
    }

    fun toggleTodo(todo: TodoEntity) {
        viewModelScope.launch {
            val done = !todo.done
            plan.setTodoDone(todo.id, done, if (done) LocalDateTime.now().toString() else null)
        }
    }
}
