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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.toArgb
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
import com.lifetracker.app.data.DayLayout
import com.lifetracker.app.data.DaySegments
import com.lifetracker.app.data.HealthSettings
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceVisitEntity
import com.lifetracker.app.data.StayConfidence
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.SleepNightEntity
import com.lifetracker.app.data.SleepStats
import com.lifetracker.app.data.StepDayEntity
import com.lifetracker.app.data.StepStats
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.data.UsageCollector
import com.lifetracker.app.data.UsageAppEntity
import com.lifetracker.app.data.UsageDays
import com.lifetracker.app.data.UsageSettings
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val DAYS_BACK = 60
private const val DAYS_AHEAD = 30

/** The id of the built-in Study activity. */
private const val STUDY_ID = "Study"

private fun EntryEntity.minutes(): Int = ActivityStats.minutes(this)

@Composable
fun TimelineScreen(onOpenTrips: () -> Unit, onOpenCharging: () -> Unit, onOpenSteps: () -> Unit, onOpenPlaces: () -> Unit, onOpenCalls: () -> Unit, onOpenFood: () -> Unit, vm: TimelineViewModel = viewModel()) {
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
    val sleepShown = if (showHealth) sleepNight?.takeIf { it.source != SleepStats.SKIPPED } else null
    val usageAccess = UsageCollector.hasAccess(context)
    val showPhone = usageAccess && usageSettings.showOnTimeline
    val minBlockMs = usageSettings.minBlockMinutes * 60_000L
    val callsAccess = CallsCollector.hasAccess(context)
    val showCalls = callsAccess && callsSettings.showOnTimeline

    var showLog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<EntryEntity?>(null) }
    var editEntry by remember { mutableStateOf<EntryEntity?>(null) }
    var editApp by remember { mutableStateOf<UsageAppEntity?>(null) }
    var fillIn by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val usageApps by vm.usageApps.collectAsState()

    // The three numbers at the top: steps, time on the phone, and time studying (what you logged plus apps linked to Study).
    val stepCount = steps?.steps ?: 0
    val phoneMinutes = (usageDay.totalMs / 60_000L).toInt()
    val studyMinutes = (ActivityStats.minutesByActivity(entries)[STUDY_ID] ?: 0) + (usageDay.minutesByActivity[STUDY_ID] ?: 0)
    val title = remember(date) { date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) }

    // Everything that has a time, as items the day layout can place: inside a place block, between places, or on its own.
    fun at(minute: Int) = dayStartMs + minute * 60_000L
    val items = buildList {
        entries.forEach {
            add(DayLayout.Item("e" + it.id, DayLayout.Kind.ENTRY, at(it.startMinute), at(it.endMinute ?: it.startMinute), it.endMinute != null, it))
        }
        todos.filter { it.minutes != null }.forEach {
            add(DayLayout.Item("t" + it.id, DayLayout.Kind.TODO, at(it.minutes!!), at(it.minutes!!), false, it))
        }
        notes.filter { it.minutes != null }.forEach {
            add(DayLayout.Item("n" + it.id, DayLayout.Kind.NOTE, at(it.minutes!!), at(it.minutes!!), false, it))
        }
        meals.forEach {
            add(DayLayout.Item("m" + it.id, DayLayout.Kind.MEAL, at(it.minuteOfDay), at(it.minuteOfDay) + 15 * 60_000L, false, it))
        }
        if (showCalls) calls.forEach {
            add(DayLayout.Item("call" + it.id, DayLayout.Kind.CALL, it.startMs, it.startMs + it.durationSec * 1000L, it.durationSec > 0, it))
        }
        if (showCharging) charges.forEach {
            add(DayLayout.Item("charge" + it.id, DayLayout.Kind.CHARGE, it.startMs, if (it.ongoing) maxOf(nowMs, it.startMs) else it.endMs, true, it))
        }
        sleepShown?.let {
            add(DayLayout.Item("sleep" + it.date, DayLayout.Kind.SLEEP, maxOf(it.startMs, dayStartMs), minOf(it.endMs, dayEndMs), true, it))
        }
        if (showPhone) usageDay.blocks.filter { it.activeMs >= minBlockMs }.forEach {
            add(DayLayout.Item("p" + it.pkg + it.startMs, DayLayout.Kind.PHONE, it.startMs, it.endMs, true, it))
        }
    }
    // The same day as 24-hour stretches, for the bar and the pie: what you logged, and what the phone collected for you.
    val sleepArgb = types.byId("Sleep").displayColor().toArgb()
    val foodArgb = types.byId("Food").displayColor().toArgb()
    val loggedSegs = entries.filter { it.endMinute != null && it.endMinute > it.startMinute }.map {
        val t = types.byId(it.category)
        DaySegments.Seg(at(it.startMinute), at(it.endMinute!!), t.displayColor().toArgb(), t.name, true)
    }
    val collectedSegs = buildList {
        sleepShown?.let { add(DaySegments.Seg(it.startMs, it.endMs, sleepArgb, "Sleep", false)) }
        meals.forEach { add(DaySegments.Seg(at(it.minuteOfDay), at(it.minuteOfDay) + 15 * 60_000L, foodArgb, "Food", false)) }
        if (usageAccess) usageDay.blocks.forEach {
            val t = it.activityId.takeIf { id -> id.isNotEmpty() }?.let { id -> types.byId(id) }
            add(DaySegments.Seg(it.startMs, it.endMs, t?.displayColor()?.toArgb() ?: 0xFF738078.toInt(), t?.name ?: "Phone", false))
        }
        if (showTrips) dayTrips.forEach { add(DaySegments.Seg(it.startMs, it.endMs, 0xFF8E94E8.toInt(), "Travel", false)) }
    }
    val flatAll = DaySegments.flatten(loggedSegs + collectedSegs, dayStartMs, dayEndMs)
    val flatLogged = DaySegments.flatten(loggedSegs, dayStartMs, dayEndMs)
    val flatCollected = DaySegments.flatten(collectedSegs, dayStartMs, dayEndMs)
    val nowMarker = if (nowMs in dayStartMs until dayEndMs) nowMs else null
    var pie by rememberSaveable { mutableStateOf(false) }

    val rows = DayLayout.build(items, placeVisits, dayTrips, placeNames, dayStartMs, dayEndMs, nowMs, showPlaces, showTrips)
    val actions = DayActions(
        types = types,
        projectNames = projectNames,
        habitNames = habitNames,
        onEntry = { editEntry = it },
        onApp = { block -> editApp = usageApps[block.pkg] ?: UsageAppEntity(block.pkg, block.label, "", false) },
        onTodo = vm::toggleTodo,
        onPlaces = onOpenPlaces,
        onCalls = onOpenCalls,
        onCharging = onOpenCharging,
        onSleep = onOpenSteps,
        onFood = onOpenFood,
        onFillIn = { s, e -> fillIn = s to e },
        onTripMode = vm::setTripMode,
    )
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
                )
            }
            item {
                DayStrip(selected = date, datesWithEntries = datesWithEntries, onSelect = vm::selectDate)
            }
            item(key = "day-numbers") {
                DayNumbers(
                    steps = if (stepCount > 0) StepStats.text(stepCount) else "–",
                    phone = if (phoneMinutes > 0) formatDuration(phoneMinutes) else "–",
                    study = if (studyMinutes > 0) formatDuration(studyMinutes) else "–",
                )
            }
            item(key = "view-switch") { ViewSwitch(pie = pie, onChange = { pie = it }) }
            if (pie) {
                item(key = "pie") { DayPie(flatLogged, flatCollected, flatAll, dayStartMs, nowMarker) }
            } else {
                item(key = "hours-bar") { HoursBar(flatAll, dayStartMs, nowMarker) }
            }
            if (pie) {
                // Only the pie is shown.
            } else if (rows.isEmpty() && anyTimeTodos.isEmpty() && anyTimeNotes.isEmpty()) {
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
                items(rows, key = { it.key }) { row -> DayRow(row, actions) }
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
                            text = "Tap an entry or an app to change it. Tap a to-do to tick it off.",
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

    fillIn?.let { (from, to) ->
        LogSheet(
            date = date,
            existing = entries,
            types = types,
            onCreateType = vm::createActivity,
            onDismiss = { fillIn = null },
            onSave = {
                vm.add(it)
                fillIn = null
            },
            presetStartMinute = ((from - dayStartMs) / 60_000L).toInt().coerceIn(0, 1438),
            presetEndMinute = ((to - dayStartMs) / 60_000L).toInt().coerceIn(1, 1439),
        )
    }

    editEntry?.let { entry ->
        LogSheet(
            date = date,
            existing = entries,
            types = types,
            onCreateType = vm::createActivity,
            onDismiss = { editEntry = null },
            onSave = {
                vm.update(it)
                editEntry = null
            },
            initial = entry,
            onDelete = {
                toDelete = entry
                editEntry = null
            },
        )
    }

    editApp?.let { app ->
        AppCategoryDialog(
            app = app,
            types = types,
            onLink = { vm.linkApp(app, it) },
            onIgnore = { vm.setAppIgnored(app, it) },
            onDismiss = { editApp = null },
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
private fun DayNumbers(steps: String, phone: String, study: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            DayNumber(R.drawable.ic_stat_steps, "steps", steps, Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
            DayNumber(R.drawable.ic_stat_phone, "phone", phone, Modifier.weight(1f))
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
            DayNumber(R.drawable.ic_stat_study, "studied", study, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayNumber(icon: Int, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
