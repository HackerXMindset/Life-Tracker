package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.ActivityStats
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.CallEntity
import com.lifetracker.app.data.EntryEntity
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.data.TodoEntity
import com.lifetracker.app.data.UsageDays
import java.time.ZoneId
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

    /** Every activity (including hidden ones, so old entries still get their colour). */
    val activityTypes: StateFlow<List<ActivityTypeEntity>> =
        share(emptyList(), db.activityDao().observeAll())

    /** Saves a new activity made from the Log pop-up and hands it back so it can be selected. */
    fun createActivity(type: ActivityTypeEntity, onSaved: (ActivityTypeEntity) -> Unit) {
        viewModelScope.launch {
            val placed = type.copy(sortOrder = db.activityDao().maxSortOrder() + 1)
            db.activityDao().upsert(placed)
            onSaved(placed)
        }
    }

    /** App use on the selected day: blocks for the Timeline, the total, and time per linked activity. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val usageDay: StateFlow<UsageDays.Day> = share(
        UsageDays.Day(emptyList(), 0L, emptyList(), emptyMap()),
        selectedDate.flatMapLatest { d ->
            val zone = ZoneId.systemDefault()
            val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            combine(db.usageDao().sessionsBetween(start, end), db.usageDao().observeApps()) { sessions, apps ->
                UsageDays.build(sessions, apps.associateBy { it.pkg }, start, end)
            }
        },
    )

    /** Phone calls that started on the selected day. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val calls: StateFlow<List<CallEntity>> = share(
        emptyList(),
        selectedDate.flatMapLatest { d ->
            val zone = ZoneId.systemDefault()
            val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            db.callDao().callsBetween(start, end)
        },
    )

    /** To-dos planned for the selected day (only to-dos with a date ever appear on the Timeline). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todos: StateFlow<List<TodoEntity>> =
        share(emptyList(), selectedDate.flatMapLatest { plan.todosOn(it.toString()) })

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteEntity>> =
        share(emptyList(), selectedDate.flatMapLatest { plan.notesOn(it.toString()) })

    /** Meals eaten on the selected day (read-only here; they are edited on the Food tab). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val meals: StateFlow<List<MealEntity>> =
        share(emptyList(), selectedDate.flatMapLatest { db.foodDao().mealsOn(it.toString()) })

    /** Project name by id, for labelling to-dos. */
    val projectNames: StateFlow<Map<String, String>> =
        share(emptyMap(), plan.observeTodoTags().map { tags -> tags.associate { it.id to it.name } })

    /** Habit name by id, for labelling notes. */
    val habitNames: StateFlow<Map<String, String>> =
        share(emptyMap(), db.habitDao().observeHabits().map { list -> list.associate { it.id to it.name } })

    /** Days that have anything on them: entries, planned to-dos, notes or meals. */
    val datesWithEntries: StateFlow<Set<String>> = share(
        emptySet(),
        combine(
            dao.datesWithEntries(),
            plan.datesWithTodos(),
            plan.datesWithNotes(),
            db.foodDao().datesWithMeals(),
        ) { a, b, c, d ->
            val all = HashSet<String>()
            all.addAll(a)
            all.addAll(b)
            all.addAll(c)
            all.addAll(d)
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
