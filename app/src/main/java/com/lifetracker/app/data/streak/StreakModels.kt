package com.lifetracker.app.data.streak

import java.time.LocalDate
import java.time.LocalDateTime

/** What a Streak backup holds, in the parts this app uses. Nothing here touches the phone or the database. */
data class StreakData(
    val exportedAt: String,
    val habits: List<StreakHabit>,
    val categories: List<StreakCategory>,
    val notes: List<StreakNote>,
    val focus: List<StreakFocus>,
    val todos: List<StreakTodo>,
    val todoTags: List<StreakTodoTag>,
) {
    val isEmpty: Boolean
        get() = habits.isEmpty() && notes.isEmpty() && focus.isEmpty() && todos.isEmpty()
}

data class StreakCompletion(val date: LocalDate, val count: Double, val minuteOfDay: Int?)

data class StreakHabit(
    val id: String,
    val name: String,
    val icon: String,
    val category: String,
    val color: Long,
    val order: Int,
    /** 0 = yes/no, 1 = relapse, 2 = counted amount. */
    val kind: Int,
    val quantKind: Int,
    val unitLabel: String,
    val target: Double,
    val step: Double,
    val anyAmount: Boolean,
    val startMinute: Int,
    val durationMinutes: Int,
    val createdOn: LocalDate?,
    val archived: Boolean,
    val completions: List<StreakCompletion>,
    /** The original record, without its completions. */
    val raw: String,
)

data class StreakCategory(val id: String, val name: String, val color: Long, val order: Int)

data class StreakNote(
    val id: String,
    val habitId: String,
    val date: LocalDate?,
    val type: Int,
    val text: String,
    val minutes: Int?,
    val createdAt: String,
)

data class StreakFocus(
    val id: String,
    val habitId: String,
    val targetMinutes: Int,
    val seconds: Int,
    val completed: Boolean,
    val startedAt: LocalDateTime,
    val label: String,
)

data class StreakTodo(
    val id: String,
    val text: String,
    val done: Boolean,
    val date: LocalDate?,
    val minutes: Int?,
    val estimate: Int?,
    val priority: Int,
    val project: String,
    val createdAt: String,
    val doneAt: String?,
    val raw: String,
)

data class StreakTodoTag(val id: String, val name: String, val color: Long, val kind: String, val order: Int)
