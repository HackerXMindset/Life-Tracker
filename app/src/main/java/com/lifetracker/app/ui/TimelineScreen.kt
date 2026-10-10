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
import com.lifetracker.app.data.ActivityStats
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.EntryEntity
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.data.TodoEntity
import com.lifetracker.app.data.CallEntity
import com.lifetracker.app.data.CallStats
import com.lifetracker.app.data.CallsCollector
import com.lifetracker.app.data.CallsSettings
import com.lifetracker.app.data.ChargeSessionEntity
import com.lifetracker.app.data.ChargeSettings
import com.lifetracker.app.data.ChargeStats
import com.lifetracker.app.data.HealthSettings
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceVisitEntity
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.SleepNightEntity
import com.lifetracker.app.data.SleepStats
import com.lifetracker.app.data.StepDayEntity
import com.lifetracker.app.data.StepStats
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.data.UsageCollector
import com.lifetracker.app.data.UsageDays
import com.lifetracker.app.data.UsageSettings
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val DAYS_BACK = 60
private const val DAYS_AHEAD = 30

private fun EntryEntity.minutes(): Int = ActivityStats.minutes(this)

@Composable
fun TimelineScreen(onOpenTrips: () -> Unit, onOpenData: () -> Unit, onOpenActivities: () -> Unit, onOpenPhone: () -> Unit, onOpenCalls: () -> Unit, onOpenCharging: () -> Unit, onOpenSteps: () -> Unit, onOpenPlaces: () -> Unit, vm: TimelineViewModel = viewModel()) {
    val date by vm.date.collectAsState()
    val entries by vm.entries.collectAsState()
    val todos by vm.todos.collectAsState()
    val notes by vm.notes.collectAsState()
    val meals by vm.meals.collectAsState()
    val projectNames by vm.projectNames.collectAsState()
    val habitNames by vm.habitNames.collectAsState()
    val datesWithEntries by vm.datesWithEntries.collectAsState()
    val types by vm.activityTypes.collectAsState()
    val usageDay by vm.usageDay.collectAsState()
    val calls by vm.calls.collectAsState()
    val charges by vm.charges.collectAsState()
    val steps by vm.steps.collectAsState()
    val sleepNight by vm.sleep.collectAsState()
    val placeVisits by vm.placeVisits.collectAsState()
    val placeNames by vm.placeNames.collectAsState()
    val dayTrips by vm.trips.collectAsState()

    // Read fresh each time the Timeline comes into view, so changes made on the Phone usage screen show up.
    val context = LocalContext.current
    val usageSettings = remember { UsageSettings(context) }
    val callsSettings = remember { CallsSettings(context) }
    val showCharging = remember { ChargeSettings(context) }.showOnTimeline
    val showHealth = remember { HealthSettings(context) }.showOnTimeline
    val showPlaces = remember { PlacesSettings(context) }.showOnTimeline
    val showTrips = remember { PlacesSettings(context) }.showTripsOnTimeline
    val zone = ZoneId.systemDefault()
    val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val nowMs = System.currentTimeMillis()
    val dayPlaces = PlaceRules.totals(placeVisits, dayStartMs, dayEndMs, nowMs)
    val sleepShown = if (showHealth) sleepNight?.takeIf { it.source != SleepStats.SKIPPED } else null
    val usageAccess = UsageCollector.hasAccess(context)
    val showPhone = usageAccess && usageSettings.showOnTimeline
    val minBlockMs = usageSettings.minBlockMinutes * 60_000L
    var promptDismissed by remember { mutableStateOf(usageSettings.promptDismissed) }
    val callsAccess = CallsCollector.hasAccess(context)
    val showCalls = callsAccess && callsSettings.showOnTimeline
    var callsPromptDismissed by remember { mutableStateOf(callsSettings.promptDismissed) }

    var showLog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<EntryEntity?>(null) }

    // Sleep from the Steps and sleep screen counts towards the Sleep goal, unless you logged Sleep yourself that day.
    val extraMinutes = usageDay.minutesByActivity.toMutableMap().also { map ->
        if (sleepShown != null && entries.none { it.category == "Sleep" }) {
            map["Sleep"] = (map["Sleep"] ?: 0) + SleepStats.minutes(sleepShown)
        }
    }
    val goals = ActivityStats.goalProgress(types, entries, extraMinutes)
    val reachedGoals = goals.count { !it.isLimit && it.ok }
    val minimumGoals = goals.count { !it.isLimit }
    val trackedMinutes = entries.sumOf { it.minutes() }
    val title = remember(date) { date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) }

    // Timed items sit in order with the entries; items without a time get their own sections below.
    val timed: List<TimelineItem> = buildList {
        entries.forEach { add(EntryItem(it)) }
        todos.filter { it.minutes != null }.forEach { add(TodoItem(it)) }
        notes.filter { it.minutes != null }.forEach { add(NoteItem(it)) }
        meals.forEach { add(MealItem(it)) }
        if (showCalls) calls.forEach { add(CallItem(it)) }
        if (showCharging) charges.forEach { add(ChargeItem(it)) }
        sleepShown?.let { add(SleepItem(it)) }
        if (showTrips) dayTrips.forEach { add(TripItem(it, placeNames)) }
        if (showPlaces) {
            placeVisits.filter { PlaceRules.isShown(it, nowMs) }.forEach { add(PlaceItem(it, placeNames[it.placeId] ?: "Place", dayStartMs)) }
        }
        if (showPhone) usageDay.blocks.filter { it.activeMs >= minBlockMs }.forEach { add(PhoneItem(it)) }
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
                        IconButton(onClick = onOpenActivities) {
                            Icon(painterResource(R.drawable.ic_activities), contentDescription = "Activities and goals")
                        }
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
                    SummaryStat("Tracked", formatDuration(trackedMinutes), Modifier.weight(1f))
                    SummaryStat("Entries", entries.size.toString(), Modifier.weight(1f))
                    if (minimumGoals > 0) {
                        SummaryStat("Goals met", "$reachedGoals of $minimumGoals", Modifier.weight(1f))
                    }
                }
            }
            items(goals, key = { "g" + it.type.id }) { GoalBar(it) }
            if (usageAccess) {
                if (usageDay.totalMs >= 60_000L) {
                    item(key = "phone-summary") { PhoneSummary(usageDay, onClick = onOpenPhone) }
                }
            } else if (!promptDismissed) {
                item(key = "phone-prompt") {
                    PhonePrompt(
                        onOpen = onOpenPhone,
                        onDismiss = { usageSettings.promptDismissed = true; promptDismissed = true },
                    )
                }
            }
            item(key = "health-summary") { HealthSummary(steps, sleepNight, onClick = onOpenSteps) }
            item(key = "places-summary") { PlacesSummary(dayPlaces, placeNames, onClick = onOpenPlaces) }
            if (dayTrips.isNotEmpty()) {
                item(key = "trips-summary") { TripsSummary(dayTrips, onClick = onOpenTrips) }
            }
            if (callsAccess) {
                if (calls.isNotEmpty()) {
                    item(key = "calls-summary") { CallsSummary(calls, onClick = onOpenCalls) }
                }
            } else if (!callsPromptDismissed) {
                item(key = "calls-prompt") {
                    CallsPrompt(
                        onOpen = onOpenCalls,
                        onDismiss = { callsSettings.promptDismissed = true; callsPromptDismissed = true },
                    )
                }
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
                        is EntryItem -> EntryRow(item.entry, types.byId(item.entry.category), onClick = { toDelete = item.entry })
                        is TodoItem -> TodoRow(item.todo, projectNames[item.todo.project], onToggle = { vm.toggleTodo(item.todo) })
                        is NoteItem -> NoteRow(item.note, habitNames[item.note.habitId])
                        is MealItem -> MealRow(item.meal, types.byId("Food"))
                        is CallItem -> CallRow(item.call)
                        is ChargeItem -> ChargeTimelineRow(item.session, onClick = onOpenCharging)
                        is SleepItem -> SleepTimelineRow(item.night, onClick = onOpenSteps)
                        is PlaceItem -> PlaceTimelineRow(item, nowMs, onClick = onOpenPlaces)
                        is TripItem -> TripTimelineRow(item, onClick = onOpenTrips)
                        is PhoneItem -> PhoneRow(item.block, item.block.activityId.takeIf { it.isNotEmpty() }?.let { types.byId(it) })
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
            types = types,
            onCreateType = vm::createActivity,
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
private fun GoalBar(progress: ActivityStats.GoalProgress) {
    val type = progress.type
    val color = if (progress.isLimit && !progress.ok) MaterialTheme.colorScheme.error else type.displayColor()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel(if (progress.isLimit) "${type.name} limit" else "${type.name} goal")
            Text(
                text = "${formatDuration(progress.minutes)} of ${formatDuration(progress.goal)}",
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
            if (progress.fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
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
private fun EntryRow(entry: EntryEntity, type: ActivityTypeEntity, onClick: () -> Unit) {
    val color = type.displayColor()
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
                        text = type.name,
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

private class CallItem(val call: CallEntity) : TimelineItem {
    override val minute: Int? = Instant.ofEpochMilli(call.startMs).atZone(ZoneId.systemDefault())
        .let { it.hour * 60 + it.minute }
    override val key: String = "call" + call.id
}

private class ChargeItem(val session: ChargeSessionEntity) : TimelineItem {
    override val minute: Int? = Instant.ofEpochMilli(session.startMs).atZone(ZoneId.systemDefault())
        .let { it.hour * 60 + it.minute }
    override val key: String = "charge" + session.id
}

private class SleepItem(val night: SleepNightEntity) : TimelineItem {
    // Sleep is listed at the moment you woke up.
    override val minute: Int? = Instant.ofEpochMilli(night.endMs).atZone(ZoneId.systemDefault())
        .let { it.hour * 60 + it.minute }
    override val key: String = "sleep" + night.date
}

private class PlaceItem(val visit: PlaceVisitEntity, val name: String, dayStartMs: Long) : TimelineItem {
    // A stay that began on an earlier day is listed at the start of this one.
    override val minute: Int? = if (visit.startMs < dayStartMs) 0 else
        Instant.ofEpochMilli(visit.startMs).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
    override val key: String = "place" + visit.id
}

private class TripItem(val trip: TripEntity, names: Map<String, String>) : TimelineItem {
    val from: String = names[trip.fromPlaceId] ?: ""
    val to: String = names[trip.toPlaceId] ?: ""
    override val minute: Int? = Instant.ofEpochMilli(trip.startMs).atZone(ZoneId.systemDefault())
        .let { it.hour * 60 + it.minute }
    override val key: String = "trip" + trip.id
}

private class PhoneItem(val block: UsageDays.Block) : TimelineItem {
    override val minute: Int? = Instant.ofEpochMilli(block.startMs).atZone(ZoneId.systemDefault())
        .let { it.hour * 60 + it.minute }
    override val key: String = "p" + block.pkg + block.startMs
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
private fun MealRow(meal: MealEntity, foodType: ActivityTypeEntity) {
    val color = foodType.displayColor()
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

@Composable
private fun PhoneSummary(day: UsageDays.Day, onClick: () -> Unit) {
    val top = day.perApp.take(3)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("On your phone")
                Text(
                    text = formatDuration((day.totalMs / 60_000L).toInt()),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            top.forEach { (pkg, ms) ->
                val label = day.blocks.firstOrNull { it.pkg == pkg }?.label ?: pkg
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatDuration((ms / 60_000L).toInt()),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Tap for every app and settings",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhonePrompt(onOpen: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "See your phone use here",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Allow usage access and every app you open appears on the Timeline with its exact time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("Set up") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun PhoneRow(block: UsageDays.Block, activity: ActivityTypeEntity?) {
    val color = activity?.displayColor() ?: MaterialTheme.colorScheme.onSurfaceVariant
    val minutes = (block.activeMs / 60_000L).toInt()
    val startMinute = Instant.ofEpochMilli(block.startMs).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMinute(startMinute),
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
                    .border(2.dp, color, RoundedCornerShape(2.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = block.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Phone · " + formatDuration(minutes.coerceAtLeast(1)) + (activity?.let { " · " + it.name } ?: ""),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallsSummary(calls: List<CallEntity>, onClick: () -> Unit) {
    val totals = CallStats.totals(calls)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Calls")
                Text(
                    text = "${totals.calls} · " + CallStats.durationText(totals.talkSec),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (totals.missed > 0) {
                Text(
                    text = "${totals.missed} missed",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = "Tap for people and settings",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallsPrompt(onOpen: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "See your calls here",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Allow call log access and every call appears on the Timeline with who it was and how long it lasted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("Set up") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun CallRow(call: CallEntity) {
    val missed = call.type == CallStats.MISSED
    val color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val startMinute = Instant.ofEpochMilli(call.startMs).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMinute(startMinute),
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
                text = CallStats.title(call),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Call · " + CallStats.detail(call),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChargeTimelineRow(session: ChargeSessionEntity, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.tertiary
    val startMinute = Instant.ofEpochMilli(session.startMs).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMinute(startMinute),
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
                text = "Charging · " + ChargeStats.title(session),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = ChargeStats.detail(session),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthSummary(steps: StepDayEntity?, sleep: SleepNightEntity?, onClick: () -> Unit) {
    val stepsText = steps?.takeIf { it.steps > 0 }?.let { StepStats.text(it.steps) }
    val sleepMinutes = sleep?.takeIf { it.source != SleepStats.SKIPPED }?.let { SleepStats.minutes(it) } ?: 0
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Steps and sleep")
                Text(
                    text = listOfNotNull(
                        stepsText?.let { "$it steps" },
                        if (sleepMinutes > 0) "slept " + formatDuration(sleepMinutes) else null,
                    ).joinToString(" · ").ifEmpty { "–" },
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = if (stepsText == null && sleepMinutes == 0) "Tap to set up steps and sleep" else "Tap for days, nights and settings",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SleepTimelineRow(night: SleepNightEntity, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val wake = Instant.ofEpochMilli(night.endMs).atZone(zone).let { it.hour * 60 + it.minute }
    val bed = Instant.ofEpochMilli(night.startMs).atZone(zone).let { it.hour * 60 + it.minute }
    val color = MaterialTheme.colorScheme.secondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMinute(wake),
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
                text = "Slept " + formatDuration(SleepStats.minutes(night)),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = formatMinute(bed) + " → " + formatMinute(wake) + " · " + SleepStats.label(night.source),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlacesSummary(totals: List<PlaceRules.PlaceTotal>, names: Map<String, String>, onClick: () -> Unit) {
    val text = totals.take(3).joinToString(" · ") { (names[it.placeId] ?: "Place") + " " + PlaceRules.durationText(it.ms) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Places")
                Text(
                    text = text.ifEmpty { "–" },
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = if (totals.isEmpty()) "Tap to set up places" else "Tap for places, visits and stops to review",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceTimelineRow(item: PlaceItem, now: Long, onClick: () -> Unit) {
    val v = item.visit
    val zone = ZoneId.systemDefault()
    val color = MaterialTheme.colorScheme.primary
    val startText = formatMinute(item.minute ?: 0)
    val endText = if (v.ongoing) "now" else Instant.ofEpochMilli(v.endMs).atZone(zone).let { formatMinute(it.hour * 60 + it.minute) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = startText,
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
                text = "At " + item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = startText + " → " + endText + " · " + PlaceRules.durationText(PlaceRules.lengthMs(v, now)),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TripsSummary(trips: List<TripEntity>, onClick: () -> Unit) {
    val distance = trips.sumOf { it.distanceM.toLong() }.toInt()
    val ms = trips.sumOf { it.endMs - it.startMs }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Trips")
                Text(
                    text = "${trips.size} · " + TripRules.distanceText(distance) + " · " + PlaceRules.durationText(ms),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Tap for every trip and to say how you travelled",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TripTimelineRow(item: TripItem, onClick: () -> Unit) {
    val t = item.trip
    val color = MaterialTheme.colorScheme.tertiary
    val startText = formatMinute(item.minute ?: 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = startText,
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
                text = tripEnd(item.from) + " → " + tripEnd(item.to),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = TripModes.label(t.mode) + (if (t.modeSource == "you") "" else " (guess)") + " · " + TripRules.distanceText(t.distanceM) +
                    " · " + PlaceRules.durationText(t.endMs - t.startMs) + " · " + TripRules.avgKmh(t.distanceM, t.endMs - t.startMs) + " km/h",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
