package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.EntryEntity
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.data.TodoEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val STUDY_GOAL_MINUTES = 8 * 60
private const val DAYS_BACK = 60
private const val DAYS_AHEAD = 30

private fun EntryEntity.minutes(): Int = ((endMinute ?: startMinute) - startMinute).coerceAtLeast(0)

@Composable
fun TimelineScreen(onOpenData: () -> Unit, vm: TimelineViewModel = viewModel()) {
    val date by vm.date.collectAsState()
    val entries by vm.entries.collectAsState()
    val todos by vm.todos.collectAsState()
    val notes by vm.notes.collectAsState()
    val meals by vm.meals.collectAsState()
    val projectNames by vm.projectNames.collectAsState()
    val habitNames by vm.habitNames.collectAsState()
    val datesWithEntries by vm.datesWithEntries.collectAsState()

    var showLog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<EntryEntity?>(null) }

    val studyMinutes = entries.filter { it.category == ActivityCategory.Study.name }.sumOf { it.minutes() }
    val trackedMinutes = entries.sumOf { it.minutes() }
    val title = remember(date) { date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) }

    // Timed items sit in order with the entries; items without a time get their own sections below.
    val timed: List<TimelineItem> = buildList {
        entries.forEach { add(EntryItem(it)) }
        todos.filter { it.minutes != null }.forEach { add(TodoItem(it)) }
        notes.filter { it.minutes != null }.forEach { add(NoteItem(it)) }
        meals.forEach { add(MealItem(it)) }
    }.sortedBy { it.minute ?: 0 }
    val anyTimeTodos = todos.filter { it.minutes == null }
    val anyTimeNotes = notes.filter { it.minutes == null }
    val plannedMinutes = todos.filter { !it.done }.sumOf { it.estimate ?: 0 }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ScreenHeader(
                    label = "Timeline",
                    badge = "Saved on this phone",
                    title = title,
                    trailing = {
                        IconButton(onClick = onOpenData) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Data sources")
                        }
                    },
                )
            }
            item {
                DayStrip(selected = date, datesWithEntries = datesWithEntries, onSelect = vm::selectDate)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryStat("Study", formatDuration(studyMinutes), Modifier.weight(1f))
                    SummaryStat("Tracked", formatDuration(trackedMinutes), Modifier.weight(1f))
                    SummaryStat("Entries", entries.size.toString(), Modifier.weight(1f))
                }
            }
            item {
                GoalBar(studyMinutes)
            }
            if (timed.isEmpty() && anyTimeTodos.isEmpty() && anyTimeNotes.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Nothing on this day",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Tap Log to add what you did and for how long.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(timed, key = { it.key }) { item ->
                    when (item) {
                        is EntryItem -> EntryRow(item.entry, onClick = { toDelete = item.entry })
                        is TodoItem -> TodoRow(item.todo, projectNames[item.todo.project], onToggle = { vm.toggleTodo(item.todo) })
                        is NoteItem -> NoteRow(item.note, habitNames[item.note.habitId])
                        is MealItem -> MealRow(item.meal)
                    }
                }
                if (anyTimeTodos.isNotEmpty()) {
                    item(key = "todos-header") {
                        val estimate = if (plannedMinutes > 0) " · ${formatDuration(plannedMinutes)} planned" else ""
                        MonoLabel("To do on this day$estimate")
                    }
                    items(anyTimeTodos, key = { "t" + it.id }) { todo ->
                        TodoRow(todo, projectNames[todo.project], onToggle = { vm.toggleTodo(todo) })
                    }
                }
                if (anyTimeNotes.isNotEmpty()) {
                    item(key = "notes-header") { MonoLabel("Notes") }
                    items(anyTimeNotes, key = { "n" + it.id }) { note ->
                        NoteRow(note, habitNames[note.habitId])
                    }
                }
                if (entries.isNotEmpty()) {
                    item {
                        Text(
                            text = "Tap an entry to delete it. Tap a to-do to tick it off.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showLog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text("Log") },
        )
    }

    if (showLog) {
        LogSheet(
            date = date,
            existing = entries,
            onDismiss = { showLog = false },
            onSave = {
                vm.add(it)
                showLog = false
            },
        )
    }

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text(entry.title) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(entry)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun DayStrip(selected: LocalDate, datesWithEntries: Set<String>, onSelect: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    // Oldest on the left; the coming month is on the right, so planned to-dos can be seen ahead of time.
    val days = remember { (-DAYS_BACK..DAYS_AHEAD).map { today.plusDays(it.toLong()) } }
    val listState = rememberLazyListState()
    // Bring today into view near the right edge, with a few coming days visible after it.
    LaunchedEffect(Unit) { listState.scrollToItem((DAYS_BACK - 3).coerceAtLeast(0)) }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days, key = { it.toString() }) { day ->
            val isSelected = day == selected
            val hasEntries = day.toString() in datesWithEntries
            val primary = MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) primary else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(day) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .width(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val textColor =
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(
                    text = day.format(DateTimeFormatter.ofPattern("EEE")).uppercase(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textColor,
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !hasEntries -> androidx.compose.ui.graphics.Color.Transparent
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                else -> primary
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun GoalBar(studyMinutes: Int) {
    val fraction = (studyMinutes.toFloat() / STUDY_GOAL_MINUTES).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("Study goal")
            Text(
                text = "${formatDuration(studyMinutes)} of ${formatDuration(STUDY_GOAL_MINUTES)}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MonoLabel(label)
        Text(
            text = value,
            fontSize = 17.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun EntryRow(entry: EntryEntity, onClick: () -> Unit) {
    val category = ActivityCategory.fromKey(entry.category)
    val color = if (isSystemInDarkTheme()) category.dark else category.light
    val minutes = entry.minutes()
    val isBlock = entry.endMinute != null
    val barHeight = (minutes * 0.45f).coerceIn(56f, 150f).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMinute(entry.startMinute),
            modifier = Modifier
                .width(68.dp)
                .padding(top = 3.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.TopCenter) {
            if (isBlock) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Text(
                        text = category.label,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isBlock) {
                    Text(
                        text = formatDuration(minutes),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            if (entry.note.isNotEmpty()) {
                Text(
                    text = entry.note,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private sealed interface TimelineItem {
    val minute: Int?
    val key: String
}

private class EntryItem(val entry: EntryEntity) : TimelineItem {
    override val minute: Int? = entry.startMinute
    override val key: String = "e" + entry.id
}

private class TodoItem(val todo: TodoEntity) : TimelineItem {
    override val minute: Int? = todo.minutes
    override val key: String = "t" + todo.id
}

private class MealItem(val meal: MealEntity) : TimelineItem {
    override val minute: Int? = meal.minuteOfDay
    override val key: String = "m" + meal.id
}

private class NoteItem(val note: NoteEntity) : TimelineItem {
    override val minute: Int? = note.minutes
    override val key: String = "n" + note.id
}

@Composable
private fun TodoRow(todo: TodoEntity, projectName: String?, onToggle: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val lines = todo.text.trim().split('\n')
    val title = lines.first().ifBlank { "To-do" }
    val body = lines.drop(1).joinToString("\n").trim()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = todo.minutes?.let { formatMinute(it) } ?: "Any time",
            modifier = Modifier
                .width(68.dp)
                .padding(top = 3.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .then(if (todo.done) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape)),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            )
            val detail = listOfNotNull(
                "To do",
                projectName?.takeIf { it.isNotBlank() },
                todo.estimate?.takeIf { it > 0 }?.let { formatDuration(it) },
            ).joinToString(" · ")
            Text(text = detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (body.isNotEmpty()) {
                Text(text = body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, habitName: String?) {
    val lines = note.text.trim().split('\n')
    val title = lines.first().ifBlank { "Note" }
    val body = lines.drop(1).joinToString("\n").trim()
    val kind = listOf("Note", "Planned", "Completed").getOrElse(note.type) { "Note" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = note.minutes?.let { formatMinute(it) } ?: "",
            modifier = Modifier
                .width(68.dp)
                .padding(top = 3.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = listOfNotNull(kind, habitName?.takeIf { it.isNotBlank() }).joinToString(" · "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (body.isNotEmpty()) {
                Text(text = body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MealRow(meal: MealEntity) {
    val category = ActivityCategory.Food
    val color = if (isSystemInDarkTheme()) category.dark else category.light
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMinute(meal.minuteOfDay),
            modifier = Modifier
                .width(68.dp)
                .padding(top = 3.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = meal.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${mealTypeLabel(meal.mealType)} · ${Math.round(meal.kcal)} kcal · ${Math.round(meal.grams)} g",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
