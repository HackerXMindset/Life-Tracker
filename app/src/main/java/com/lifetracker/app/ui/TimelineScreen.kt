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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.setValue
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperFab
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.Wobble
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
import com.lifetracker.app.data.TimelineEdits
import com.lifetracker.app.data.TimelineSettings
import com.lifetracker.app.data.TimelineEditEntity
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.data.UsageCollector
import com.lifetracker.app.data.UsageAppEntity
import com.lifetracker.app.data.UsageCombos
import com.lifetracker.app.data.UsageDays
import com.lifetracker.app.data.UsageStateEntity
import com.lifetracker.app.data.UsageSettings
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The id of the built-in Study activity. */
private const val STUDY_ID = "Study"

private fun EntryEntity.minutes(): Int = ActivityStats.minutes(this)

@Composable
fun TimelineScreen(onOpenCharging: () -> Unit, onOpenSteps: () -> Unit, onOpenPlaces: () -> Unit, onOpenCalls: () -> Unit, onOpenFood: () -> Unit, vm: TimelineViewModel = viewModel()) {
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
    val usageOffs by vm.usageOffs.collectAsState()
    val timelineSettings = TimelineSettings(LocalContext.current)
    LaunchedEffect(Unit) { vm.reloadSettings() }
    val calls by vm.calls.collectAsState()
    val charges by vm.charges.collectAsState()
    val steps by vm.steps.collectAsState()
    val sleepNight by vm.sleep.collectAsState()
    val placeVisits by vm.placeVisits.collectAsState()
    val placeNames by vm.placeNames.collectAsState()
    val dayTrips by vm.trips.collectAsState()
    val edits by vm.edits.collectAsState()

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
    val usageAccess = UsageCollector.hasAccess(context)
    val sleepShown = if (showHealth) {
        sleepNight?.takeIf { it.source != SleepStats.SKIPPED && !TimelineEdits.isHidden(edits[TimelineEdits.key(TimelineEdits.SLEEP, it.date)]) }?.let {
            val (s, en) = TimelineEdits.times(it.startMs, it.endMs, edits[TimelineEdits.key(TimelineEdits.SLEEP, it.date)])
            it.copy(startMs = s, endMs = en)
        }
    } else {
        null
    }
    val showPhone = usageAccess && usageSettings.showOnTimeline
    val minBlockMs = usageSettings.minBlockMinutes * 60_000L
    val callsAccess = CallsCollector.hasAccess(context)
    val showCalls = callsAccess && callsSettings.showOnTimeline
    val minTalk = callsSettings.minTalkMinutes

    // What you changed is laid over the phone's own records: times, category, hidden. The records themselves are never touched.
    fun editOf(kind: String, id: String) = edits[TimelineEdits.key(kind, id)]
    fun typeOf(kind: String, id: String) = TimelineEdits.category(editOf(kind, id))?.let { types.byId(it) }
    val shownCalls = if (showCalls) {
        calls.filter { CallStats.shownOnTimeline(it, minTalk) }.mapNotNull { c ->
            val e = editOf(TimelineEdits.CALL, c.id)
            if (TimelineEdits.isHidden(e)) return@mapNotNull null
            val (s, en) = TimelineEdits.times(c.startMs, c.startMs + c.durationSec * 1000L, e)
            c.copy(startMs = s, durationSec = ((en - s) / 1000L).toInt())
        }
    } else {
        emptyList()
    }
    val chargesEff = charges.mapNotNull { c ->
        val e = editOf(TimelineEdits.CHARGE, c.id)
        if (TimelineEdits.isHidden(e)) return@mapNotNull null
        val (s, en) = TimelineEdits.times(c.startMs, c.endMs, e)
        c.copy(startMs = s, endMs = en)
    }
    val mealsEff = meals.mapNotNull { m ->
        val e = editOf(TimelineEdits.MEAL, m.id)
        if (TimelineEdits.isHidden(e)) return@mapNotNull null
        val origStart = dayStartMs + m.minuteOfDay * 60_000L
        val (s, _) = TimelineEdits.times(origStart, origStart, e)
        m.copy(minuteOfDay = TimelineEdits.minuteOf(s, dayStartMs))
    }
    val visitsEff = placeVisits.mapNotNull { v ->
        val e = editOf(TimelineEdits.VISIT, v.id)
        if (TimelineEdits.isHidden(e)) return@mapNotNull null
        val (s, en) = TimelineEdits.times(v.startMs, v.endMs, e)
        v.copy(startMs = s, endMs = en)
    }
    val tripsEff = dayTrips.mapNotNull { t ->
        val e = editOf(TimelineEdits.TRIP, t.id)
        if (TimelineEdits.isHidden(e)) return@mapNotNull null
        val (s, en) = TimelineEdits.times(t.startMs, t.endMs, e)
        t.copy(startMs = s, endMs = en)
    }
    val blocksEff = usageDay.blocks.mapNotNull { b ->
        val e = editOf(TimelineEdits.APP, TimelineEdits.appRef(b.pkg, b.refStartMs))
        if (TimelineEdits.isHidden(e)) return@mapNotNull null
        val (s, en) = TimelineEdits.times(b.startMs, b.endMs, e)
        val shift = s - b.startMs
        b.copy(
            startMs = s, endMs = en, activityId = TimelineEdits.category(e) ?: b.activityId,
            spans = b.spans.map { (from, to) -> (from + shift) to (to + shift) },
        )
    }

    var showLog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<EntryEntity?>(null) }
    var editEntry by remember { mutableStateOf<EntryEntity?>(null) }
    var editApp by remember { mutableStateOf<UsageAppEntity?>(null) }
    var comboFor by remember { mutableStateOf<UsageDays.Block?>(null) }
    var fillIn by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    val usageApps by vm.usageApps.collectAsState()
    val combos by vm.usageCombos.collectAsState()

    // The three numbers at the top: steps, time on the phone, and time studying (what you logged plus apps linked to Study).
    val stepCount = steps?.steps ?: 0
    val phoneMinutes = (usageDay.totalMs / 60_000L).toInt()
    val studyMinutes = (ActivityStats.minutesByActivity(entries)[STUDY_ID] ?: 0) + (usageDay.minutesByActivity[STUDY_ID] ?: 0)

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
        mealsEff.forEach {
            add(DayLayout.Item("m" + it.id, DayLayout.Kind.MEAL, at(it.minuteOfDay), at(it.minuteOfDay) + 15 * 60_000L, false, it))
        }
        shownCalls.forEach {
            add(DayLayout.Item("call" + it.id, DayLayout.Kind.CALL, it.startMs, it.startMs + it.durationSec * 1000L, it.durationSec > 0, it))
        }
        if (showCharging) chargesEff.forEach {
            add(DayLayout.Item("charge" + it.id, DayLayout.Kind.CHARGE, it.startMs, if (it.ongoing) maxOf(nowMs, it.startMs) else it.endMs, true, it))
        }
        sleepShown?.let {
            add(DayLayout.Item("sleep" + it.date, DayLayout.Kind.SLEEP, maxOf(it.startMs, dayStartMs), minOf(it.endMs, dayEndMs), true, it))
        }
        if (showPhone) blocksEff.filter { it.activeMs >= minBlockMs }.forEach {
            add(DayLayout.Item("p" + it.pkg + it.startMs, DayLayout.Kind.PHONE, it.startMs, it.endMs, true, it))
        }
    }
    // The same day as 24-hour stretches, for the bar and the pie: what you logged, and what the phone collected for you.
    val sleepArgb = types.byId("Sleep").displayColor().toArgb()
    val foodArgb = types.byId("Food").displayColor().toArgb()
    val callArgb = MaterialTheme.colorScheme.primary.toArgb()
    val loggedSegs = entries.filter { it.endMinute != null && it.endMinute > it.startMinute }.map {
        val t = types.byId(it.category)
        DaySegments.Seg(at(it.startMinute), at(it.endMinute!!), t.displayColor().toArgb(), t.name, true)
    }
    val collectedSegs = buildList {
        sleepShown?.let {
            val t = typeOf(TimelineEdits.SLEEP, it.date)
            add(DaySegments.Seg(it.startMs, it.endMs, t?.displayColor()?.toArgb() ?: sleepArgb, t?.name ?: "Sleep", false))
        }
        mealsEff.forEach {
            val t = typeOf(TimelineEdits.MEAL, it.id)
            add(DaySegments.Seg(at(it.minuteOfDay), at(it.minuteOfDay) + 15 * 60_000L, t?.displayColor()?.toArgb() ?: foodArgb, t?.name ?: "Food", false))
        }
        if (usageAccess) blocksEff.forEach {
            val t = it.activityId.takeIf { id -> id.isNotEmpty() }?.let { id -> types.byId(id) }
            // One stretch for each time the app was really on screen, so the pie and the totals count real use, not the whole block.
            val argb = t?.displayColor()?.toArgb() ?: 0xFF738078.toInt()
            val label = t?.name ?: "Phone"
            it.spans.ifEmpty { listOf(it.startMs to it.endMs) }.forEach { (from, to) -> add(DaySegments.Seg(from, to, argb, label, false)) }
        }
        if (showTrips) tripsEff.forEach {
            val t = typeOf(TimelineEdits.TRIP, it.id)
            add(DaySegments.Seg(it.startMs, it.endMs, t?.displayColor()?.toArgb() ?: 0xFF8E94E8.toInt(), t?.name ?: "Travel", false))
        }
        shownCalls.forEach {
            val t = typeOf(TimelineEdits.CALL, it.id)
            add(DaySegments.Seg(it.startMs, it.startMs + it.durationSec * 1000L, t?.displayColor()?.toArgb() ?: callArgb, t?.name ?: "Calls", false))
        }
    }
    val flatAll = DaySegments.flatten(loggedSegs + collectedSegs, dayStartMs, dayEndMs)
    val nowMarker = if (nowMs in dayStartMs until dayEndMs) nowMs else null
    var pie by rememberSaveable { mutableStateOf(false) }

    val offs = usageOffs.map { DayLayout.Off(it.startMs, it.endMs, it.kind == UsageStateEntity.SHUTDOWN) }
    val rows = DayLayout.build(items, visitsEff, tripsEff, placeNames, dayStartMs, dayEndMs, nowMs, showPlaces, showTrips, offs = offs, showGaps = timelineSettings.showGaps)

    // The "Where" line: each stay at a place, and each journey between places.
    val whereSegs = rows.mapNotNull { r ->
        when (r) {
            is DayLayout.Place -> StripSeg(
                r.startMs, r.endMs,
                typeOf(TimelineEdits.VISIT, r.visit.id)?.displayColor() ?: placeColorOf(r.name), r.name,
            )
            is DayLayout.Travel -> {
                val unknown = r.trip == null || (r.trip.mode == "vehicle" && r.trip.modeSource != "you")
                StripSeg(
                    r.startMs, r.endMs, Color(0xFF4A5568),
                    if (unknown) "Unknown way of travel" else TripModes.label(r.trip!!.mode),
                    hatch = true, unknown = unknown,
                )
            }
            else -> null
        }
    }
    val whatSegs = flatAll.map { it.toStrip() }
    fun dayStartOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    fun target(
        kind: String, ref: String, title: String, startMs: Long, endMs: Long?,
        extraLabel: String? = null, onExtra: (() -> Unit)? = null, openLabel: String? = null, onOpen: (() -> Unit)? = null,
    ): EditTarget {
        val e = edits[TimelineEdits.key(kind, ref)]
        return EditTarget(
            kind, ref, title, startMs, endMs, TimelineEdits.category(e).orEmpty(), e != null && !e.hidden, dayStartOf(startMs),
            extraLabel = extraLabel, onExtra = onExtra, openLabel = openLabel, onOpen = onOpen,
        )
    }
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
        onUnknownJourney = vm::setUnknownJourney,
        edits = edits,
        onEditItem = { item ->
            when (item.kind) {
                DayLayout.Kind.CALL -> (item.ref as CallEntity).let { c ->
                    editTarget = target(TimelineEdits.CALL, c.id, CallStats.title(c), item.startMs, item.endMs, openLabel = "Open Calls", onOpen = onOpenCalls)
                }
                DayLayout.Kind.CHARGE -> (item.ref as ChargeSessionEntity).let { c ->
                    editTarget = target(TimelineEdits.CHARGE, c.id, "Charging", item.startMs, item.endMs, openLabel = "Open Charging", onOpen = onOpenCharging)
                }
                DayLayout.Kind.SLEEP -> (item.ref as SleepNightEntity).let { n ->
                    editTarget = target(TimelineEdits.SLEEP, n.date, "Sleep", n.startMs, n.endMs, openLabel = "Open Steps and sleep", onOpen = onOpenSteps)
                }
                DayLayout.Kind.MEAL -> (item.ref as MealEntity).let { m ->
                    editTarget = target(TimelineEdits.MEAL, m.id, m.name, item.startMs, null, openLabel = "Open Food", onOpen = onOpenFood)
                }
                DayLayout.Kind.PHONE -> (item.ref as UsageDays.Block).let { b ->
                    editTarget = target(
                        TimelineEdits.APP, TimelineEdits.appRef(b.pkg, b.refStartMs), b.label, b.startMs, b.endMs,
                        extraLabel = when {
                            b.apps.size < 2 -> "Set this app's category on every day"
                            b.comboId.isEmpty() -> "Always count this combination as..."
                            else -> "Change this combination"
                        },
                        onExtra = {
                            if (b.apps.size > 1) comboFor = b
                            else editApp = usageApps[b.pkg] ?: UsageAppEntity(b.pkg, b.label, "", false)
                        },
                    )
                }
                else -> Unit
            }
        },
        onEditPlace = { p ->
            editTarget = target(
                TimelineEdits.VISIT, p.visit.id, p.name, p.visit.startMs, if (p.visit.ongoing) null else p.visit.endMs,
                openLabel = if (p.named) "Open Places" else "Name this spot in Places", onOpen = onOpenPlaces,
            )
        },
        askHowTravelled = timelineSettings.askHowTravelled,
        onEditTrip = { t ->
            editTarget = target(TimelineEdits.TRIP, t.id, "Trip", t.startMs, t.endMs)
        },
    )
    val hiddenToday = edits.values.filter { it.hidden && it.startMs in dayStartMs until dayEndMs }
    val anyTimeTodos = todos.filter { it.minutes == null }
    val anyTimeNotes = notes.filter { it.minutes == null }
    val plannedMinutes = todos.filter { !it.done }.sumOf { it.estimate ?: 0 }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") { TimelineHeader(date) }
            item(key = "week") {
                PaperWeekStrip(selected = date, datesWithEntries = datesWithEntries, onSelect = vm::selectDate)
            }
            item(key = "view-switch") {
                PaperSegmented(listOf("Timeline", "Pie"), if (pie) 1 else 0, { pie = it == 1 })
            }
            item(key = "day-numbers") {
                PaperTrio(
                    steps = if (stepCount > 0) StepStats.text(stepCount) else "–",
                    phone = if (phoneMinutes > 0) formatDuration(phoneMinutes) else "–",
                    study = if (studyMinutes > 0) formatDuration(studyMinutes) else "–",
                )
            }
            if (pie) {
                item(key = "pie") { DayPieView(whatSegs, whereSegs, if (showTrips) tripsEff else emptyList()) }
            } else {
                item(key = "activity-pills") { ActivityPills(DaySegments.byLabel(flatAll)) }
                if (rows.isNotEmpty()) item(key = "in-order-heading") { PaperSection("In order", "tap a row to open it") }
            }
            if (pie) {
                // Only the pie is shown.
            } else if (rows.isEmpty() && anyTimeTodos.isEmpty() && anyTimeNotes.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Nothing on this day", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LocalPaper.current.ink)
                        Text("Tap Log to add what you did and for how long.", color = LocalPaper.current.ink2, fontSize = 13.sp)
                    }
                }
            } else {
                item(key = "calendar") { DayCalendar(rows, actions) }
                if (anyTimeTodos.isNotEmpty()) {
                    item(key = "todos-header") {
                        PaperSection("To do on this day", if (plannedMinutes > 0) "${formatDuration(plannedMinutes)} planned" else null)
                    }
                    items(anyTimeTodos, key = { "t" + it.id }) { todo ->
                        TodoRow(todo, projectNames[todo.project], onToggle = { vm.toggleTodo(todo) })
                    }
                }
                if (anyTimeNotes.isNotEmpty()) {
                    item(key = "notes-header") { PaperSection("Notes") }
                    items(anyTimeNotes, key = { "n" + it.id }) { note ->
                        NoteRow(note, habitNames[note.habitId])
                    }
                }
                if (hiddenToday.isNotEmpty()) {
                    item(key = "hidden") {
                        PaperButton(
                            text = "Hidden by you (${hiddenToday.size}), tap to bring back",
                            onClick = { showHidden = true },
                            style = PaperButtonStyle.Ghost,
                            small = true,
                        )
                    }
                }
                if (entries.isNotEmpty() || rows.isNotEmpty()) {
                    item {
                        Text(
                            text = "Tap anything to change its time or category, or to hide it. Tap a to-do to tick it off.",
                            fontSize = 11.5.sp,
                            color = LocalPaper.current.ink2,
                        )
                    }
                }
            }
        }

        PaperFab(
            text = "Log",
            icon = R.drawable.ic_add,
            onClick = { showLog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
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

    editTarget?.let { t ->
        val key = TimelineEdits.key(t.kind, t.refId)
        val now = System.currentTimeMillis()
        EditItemDialog(
            target = t,
            types = types,
            onSave = { startMs, endMs, activityId ->
                val old = edits[key]
                // "Remember my category changes": a category given to a single-app period is kept for that app on every day.
                if (t.kind == TimelineEdits.APP && activityId.isNotEmpty() && timelineSettings.rememberCategoryChanges) {
                    val pkg = t.refId.substringBeforeLast('@')
                    if (blocksEff.firstOrNull { it.pkg == pkg && TimelineEdits.appRef(it.pkg, it.refStartMs) == t.refId }?.apps?.size == 1) {
                        vm.linkApp(usageApps[pkg] ?: UsageAppEntity(pkg, t.title, "", false), activityId)
                    }
                }
                val timesChanged = startMs != t.startMs || (endMs ?: -1L) != (t.endMs ?: -1L)
                vm.saveEdit(
                    TimelineEdits.edit(
                        t.kind, t.refId,
                        startMs = if (timesChanged) startMs else old?.startMs ?: -1L,
                        endMs = if (timesChanged) (endMs ?: -1L) else old?.endMs ?: -1L,
                        activityId = activityId, hidden = false, now = now, label = t.title,
                    ),
                )
            },
            onHide = {
                val old = edits[key]
                vm.saveEdit(
                    TimelineEdits.edit(
                        t.kind, t.refId, startMs = t.startMs, endMs = old?.endMs ?: -1L,
                        activityId = old?.activityId.orEmpty(), hidden = true, now = now, label = t.title,
                    ),
                )
            },
            onReset = { vm.resetEdit(t.kind, t.refId) },
            onDismiss = { editTarget = null },
        )
    }

    if (showHidden) {
        val hidden = edits.values.filter { it.hidden && it.startMs in dayStartMs until dayEndMs }.sortedBy { it.startMs }
        PaperDialog(title = "Hidden by you", onDismiss = { showHidden = false }, dismissText = "Close") {
            if (hidden.isEmpty()) Text("Nothing is hidden on this day.", color = LocalPaper.current.ink2)
            hidden.forEach { e ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatMinute(TimelineEdits.minuteOf(e.startMs, dayStartMs)) + "  " + e.label.ifEmpty { e.kind },
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = LocalPaper.current.ink,
                    )
                    PaperButton(
                        "Bring back",
                        { vm.saveEdit(e.copy(hidden = false, updatedMs = System.currentTimeMillis())) },
                        style = PaperButtonStyle.Ghost,
                        small = true,
                    )
                }
            }
        }
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

    comboFor?.let { b ->
        val existing = combos.firstOrNull { it.id == b.comboId }
        val (suggestedName, suggestedActivity) = UsageCombos.suggestionFor(b, types)
        val choices = (b.apps.map { it.pkg to it.label } + (existing?.pkgSet.orEmpty().map { it to (usageApps[it]?.label ?: it) })).distinctBy { it.first }
        ComboDialog(
            existing = existing,
            apps = choices,
            initialPkgs = existing?.pkgSet ?: UsageCombos.mainApps(b).map { it.pkg }.toSet(),
            initialName = existing?.name ?: suggestedName,
            initialActivityId = existing?.activityId ?: suggestedActivity,
            note = if (existing == null && suggestedActivity.isNotEmpty()) "Suggested from a built-in example. Change anything you like." else null,
            types = types,
            onSave = vm::saveCombo,
            onDelete = existing?.let { e -> { vm.deleteCombo(e.id) } },
            onDismiss = { comboFor = null },
        )
    }

    toDelete?.let { entry ->
        PaperDialog(
            title = "Delete this entry?",
            onDismiss = { toDelete = null },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = {
                vm.delete(entry)
                toDelete = null
            },
        ) {
            Text(entry.title, color = LocalPaper.current.ink)
        }
    }
}

@Composable
private fun TodoRow(todo: TodoEntity, projectName: String?, onToggle: () -> Unit) {
    val paper = LocalPaper.current
    val lines = todo.text.trim().split('\n')
    val title = lines.first().ifBlank { "To-do" }
    val body = lines.drop(1).joinToString("\n").trim()
    val detail = listOfNotNull(
        "To do",
        projectName?.takeIf { it.isNotBlank() },
        todo.estimate?.takeIf { it > 0 }?.let { formatDuration(it) },
    ).joinToString(" · ")
    PaperListRow(
        time = todo.minutes?.let { formatMinute(it) } ?: "Any time",
        onClick = onToggle,
        marker = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .then(if (todo.done) Modifier.background(paper.ink) else Modifier.border(2.dp, paper.ink, CircleShape)),
            )
        },
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            color = if (todo.done) paper.ink2 else paper.ink,
        )
        Text(text = detail, fontSize = 11.sp, color = paper.ink2)
        if (body.isNotEmpty()) Text(text = body, fontSize = 11.sp, color = paper.ink2)
    }
}

@Composable
private fun NoteRow(note: NoteEntity, habitName: String?) {
    val paper = LocalPaper.current
    val lines = note.text.trim().split('\n')
    val title = lines.first().ifBlank { "Note" }
    val body = lines.drop(1).joinToString("\n").trim()
    val kind = listOf("Note", "Planned", "Completed").getOrElse(note.type) { "Note" }
    PaperListRow(
        time = note.minutes?.let { formatMinute(it) } ?: "",
        onClick = null,
        marker = { Box(Modifier.size(10.dp).background(paper.ink2, RoundedCornerShape(2.dp))) },
    ) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = paper.ink)
        Text(
            text = listOfNotNull(kind, habitName?.takeIf { it.isNotBlank() }).joinToString(" · "),
            fontSize = 11.sp,
            color = paper.ink2,
        )
        if (body.isNotEmpty()) Text(text = body, fontSize = 11.sp, color = paper.ink2)
    }
}

/** A to-do or note under the day's list: its time, a marker, and a small paper card holding the words. */
@Composable
private fun PaperListRow(
    time: String,
    onClick: (() -> Unit)?,
    marker: @Composable () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val paper = LocalPaper.current
    val shape = Wobble.Small
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = time,
            modifier = Modifier.width(58.dp).padding(top = 12.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = paper.ink,
        )
        Box(modifier = Modifier.width(12.dp).padding(top = 11.dp), contentAlignment = Alignment.TopCenter) { marker() }
        Column(
            modifier = Modifier
                .weight(1f)
                .background(paper.surface, shape)
                .border(1.25.dp, paper.line, shape)
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}
