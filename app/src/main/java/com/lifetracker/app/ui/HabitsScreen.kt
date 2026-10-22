package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.DayKind
import com.lifetracker.app.data.HabitCalendar
import com.lifetracker.app.data.HabitCell
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.HabitMode
import com.lifetracker.app.data.HabitNotes
import com.lifetracker.app.data.HabitRules
import com.lifetracker.app.data.HabitSource
import com.lifetracker.app.data.HabitStats
import com.lifetracker.app.data.HabitSyncState
import com.lifetracker.app.data.HabitsSettings
import com.lifetracker.app.data.StreakLink
import com.lifetracker.app.data.TodayProgress
import com.lifetracker.app.data.streak.StreakFolderWriter
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperChip
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.TallyBar
import com.lifetracker.app.ui.theme.Wobble
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PAUSED_BLUE = Color(0xFF4A90D9)

/** The Habits tab, laid out like Streak's classic view. */
@Composable
fun HabitsScreen(vm: HabitsViewModel = viewModel()) {
    val ui by vm.state.collectAsState()
    val context = LocalContext.current
    val source = remember { HabitsSettings(context).source }
    var relapseFor by remember { mutableStateOf<HabitEntity?>(null) }
    var detailsId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<HabitEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var dayMenu by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }
    var showNotes by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf<NoteDraft?>(null) }
    val notes by vm.notes.collectAsState()
    val names by vm.habitNames.collectAsState()

    if (showNotes) HabitNotesPage(
        notes = notes,
        names = names,
        today = ui.today,
        onEdit = { noteDraft = NoteDraft(it.habitId, ui.today, it) },
        onNew = { noteDraft = NoteDraft(it, ui.today) },
        onBack = { showNotes = false },
    ) else LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "top") {
            TopBar(
                compact = ui.compact,
                onToggle = vm::toggleCompact,
                onNew = { adding = true },
                onNotes = { showNotes = true },
            )
        }
        item(key = "streak-strip") { StreakStrip(source) }
        item(key = "today") { TodayCard(ui.today, ui.progress) }
        if (!ui.hasAny) {
            item(key = "empty") { EmptyCard() }
        } else {
            item(key = "modes") { ModeSwitch(ui.mode, vm::setMode) }
            if (ui.chips.isNotEmpty()) {
                item(key = "chips") { CategoryChips(ui.chips, ui.category, vm::setCategory) }
            }
        }
        items(ui.rows, key = { it.habit.id }) { row ->
            HabitCardView(
                row = row,
                today = ui.today,
                mode = ui.mode,
                compact = ui.compact,
                onOpen = {
                    if (source == HabitSource.Streak) {
                        if (!StreakLink.openHabit(context, row.habit.id)) {
                            Toast.makeText(context, "Streak is not installed, or could not be opened.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        detailsId = row.habit.id
                    }
                },
                onHold = { detailsId = row.habit.id },
                onToday = {
                    val relapse = row.habit.kind == HabitStats.KIND_RELAPSE
                    if (relapse && !row.rules.cellIsRelapse(ui.today)) relapseFor = row.habit
                    // Already relapsed today: the day window adds or takes back single relapses, so a tap never wipes the day.
                    else if (relapse) dayMenu = row.habit.id to ui.today
                    else vm.tap(row.habit, ui.today)
                },
                onDay = { vm.tap(row.habit, it) },
                onMenu = { dayMenu = row.habit.id to it },
            )
        }
        if (ui.archived.isNotEmpty()) {
            item(key = "archived") {
                Text(
                    text = (if (showArchived) "Hide archived" else "Archived habits") + " (${ui.archived.size})",
                    modifier = Modifier.clickable { showArchived = !showArchived }.padding(vertical = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalPaper.current.ink,
                    textDecoration = TextDecoration.Underline,
                )
            }
            if (showArchived) {
                items(ui.archived, key = { "archived-" + it.id }) { habit ->
                    ArchivedRow(habit, onShow = { vm.setArchived(habit, false) })
                }
            }
        }
    }

    detailsId?.let { id ->
        val row = ui.rows.firstOrNull { it.habit.id == id }
        if (row == null) detailsId = null
        else HabitDetailsDialog(
            row = row,
            notes = HabitNotes.forHabit(notes, row.habit.id),
            today = ui.today,
            onAddNote = { noteDraft = NoteDraft(row.habit.id, ui.today) },
            onEditNote = { noteDraft = NoteDraft(it.habitId, ui.today, it) },
            onEdit = { editing = row.habit; detailsId = null },
            onArchive = { vm.setArchived(row.habit, true); detailsId = null },
            onDelete = { vm.deleteHabit(row.habit); detailsId = null },
            onClearProgress = { vm.clearAllProgress(row.habit); detailsId = null },
            onDismiss = { detailsId = null },
        )
    }
    dayMenu?.let { (id, date) ->
        val row = ui.rows.firstOrNull { it.habit.id == id }
        if (row == null) dayMenu = null
        else HabitDayDialog(
            row = row,
            date = date,
            onToggle = { vm.tap(row.habit, date) },
            onAmount = { vm.setAmount(row.habit, date, it) },
            onAddCheck = { amount, minute -> vm.addCheck(row.habit, date, amount, minute) },
            onUndo = { vm.undoCheck(row.habit, date) },
            onStep = { vm.toggleStep(row.habit, date, it) },
            onTime = { from, to -> vm.changeTime(row.habit, date, from, to) },
            onReason = { vm.setMissReason(row.habit, date, it) },
            onClear = { vm.clear(row.habit, date) },
            onNote = { noteDraft = NoteDraft(row.habit.id, date) },
            onDismiss = { dayMenu = null },
        )
    }
    noteDraft?.let { draft ->
        val picking = draft.note == null && draft.habitId.isEmpty()
        val choices = names.filterKeys { id -> ui.archived.none { it.id == id } }.toList()
        NoteDialog(
            draft = draft,
            today = ui.today,
            habitName = names[draft.habitId],
            habits = if (picking) choices else null,
            onSave = { habitId, text ->
                if (draft.note != null) vm.editNote(draft.note, text) else vm.addNote(habitId, draft.date, text)
                noteDraft = null
            },
            onDismiss = { noteDraft = null },
            onDelete = draft.note?.let { note -> { vm.deleteNote(note); noteDraft = null } },
        )
    }
    if (adding) {
        HabitEditorDialog(
            initial = null,
            categories = ui.allCategories,
            onSave = { vm.save(it, null); adding = false },
            onDismiss = { adding = false },
        )
    }
    editing?.let { habit ->
        HabitEditorDialog(
            initial = habit,
            categories = ui.allCategories,
            onSave = { vm.save(it, habit); editing = null },
            onDismiss = { editing = null },
        )
    }

    relapseFor?.let { habit ->
        PaperDialog(
            title = "Log a relapse?",
            onDismiss = { relapseFor = null },
            confirmText = "Log relapse",
            confirmStyle = PaperButtonStyle.Danger,
            onConfirm = {
                vm.tap(habit, ui.today)
                relapseFor = null
            },
        ) {
            Text("Log a relapse for \"${habit.name}\" today? This ends the streak of days without one.", color = LocalPaper.current.ink)
        }
    }
}

private fun HabitRules.cellIsRelapse(day: LocalDate): Boolean = cell(day).kind == DayKind.RELAPSE

// ---------------------------------------------------------------- top of the page

@Composable
private fun TopBar(compact: Boolean, onToggle: () -> Unit, onNew: () -> Unit, onNotes: () -> Unit) {
    val paper = LocalPaper.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Habits",
            modifier = Modifier.weight(1f),
            fontSize = 38.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            color = paper.ink,
            maxLines = 1,
        )
        PaperIconButton(icon = R.drawable.ic_notes, description = "All notes", onClick = onNotes)
        // The up-down button: small cards when they are big, big cards when they are small.
        PaperIconButton(
            icon = if (compact) R.drawable.ic_chevrons_updown else R.drawable.ic_chevrons_downup,
            description = if (compact) "Expand cards" else "Collapse cards",
            onClick = onToggle,
        )
        PaperButton(text = "New", onClick = onNew, small = true, icon = R.drawable.ic_add)
    }
}

/**
 * Says where your habits are shared. With this app's own data it is the Streak send status ("Shared with Streak"), with a
 * button that sends now; with Streak as the source it just says so.
 */
@Composable
private fun StreakStrip(source: HabitSource) {
    val paper = LocalPaper.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState = remember { HabitSyncState(context) }
    var tick by remember { mutableStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val canWrite = remember(tick) { StreakFolderWriter.canWrite(context) }
    val lastSent = remember(tick) { syncState.lastSentText }
    val note = remember(tick) { syncState.lastSendNote }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (source == HabitSource.Own) {
                PaperChip("Shared with Streak")
                Text(
                    text = result ?: note ?: when {
                        !canWrite -> "Streak's folder is not set up for writing. See Settings, Habits."
                        lastSent != null -> "Last sent $lastSent · Streak picks it up at its next check"
                        else -> "Nothing sent yet · Streak picks it up at its next check"
                    },
                    fontSize = 11.sp,
                    color = paper.ink2,
                )
            } else {
                PaperChip("From Streak")
                Text("Your habits come from Streak's backups. Tap a habit to open it in Streak.", fontSize = 11.sp, color = paper.ink2)
            }
        }
        if (source == HabitSource.Own) {
            PaperButton(
                text = if (sending) "Sending..." else "Write now",
                onClick = {
                    if (!sending) {
                        sending = true
                        scope.launch {
                            result = try {
                                StreakFolderWriter.send(context).message
                            } catch (e: Exception) {
                                e.message ?: "Could not send to Streak."
                            }
                            sending = false
                            tick++
                        }
                    }
                },
                small = true,
                enabled = canWrite && !sending,
            )
        }
    }
}

@Composable
private fun ArchivedRow(habit: HabitEntity, onShow: () -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp, shadow = false) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = habit.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = paper.ink,
            )
            PaperButton("Show again", onShow, style = PaperButtonStyle.Ghost, small = true)
        }
    }
}

@Composable
private fun TodayCard(today: LocalDate, progress: TodayProgress) {
    val paper = LocalPaper.current
    val title = when {
        progress.total == 0 -> "No habits due today"
        progress.allDone -> "All done today"
        else -> "${progress.done} of ${progress.total} completed"
    }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp, shadow = false) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())),
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = paper.ink)
            }
            Ring(progress.ratio)
        }
    }
}

@Composable
private fun Ring(ratio: Float) {
    val paper = LocalPaper.current
    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = 7.dp.toPx()
            val inset = width / 2
            val arc = Size(size.width - width, size.height - width)
            drawArc(paper.surface2, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
            if (ratio > 0f) {
                drawArc(paper.accent, -90f, 360f * ratio, false, Offset(inset, inset), arc, style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        Text(text = "${Math.round(ratio * 100)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = paper.ink)
    }
}

@Composable
private fun EmptyCard() {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Text(text = "No habits yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = paper.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap New to add a habit. Or choose your Streak folder under Data sources (the gear on the Timeline); " +
                "your Streak habits come in when you next open the app, or tap Import now.",
            fontSize = 13.sp,
            color = paper.ink2,
        )
    }
}

/** Week | Month | Year, as in Streak. */
@Composable
private fun ModeSwitch(mode: HabitMode, onChange: (HabitMode) -> Unit) {
    val options = HabitMode.entries
    PaperSegmented(options.map { it.label }, options.indexOf(mode), { onChange(options[it]) })
}

@Composable
private fun CategoryChips(chips: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { PaperPill("All", selected.isEmpty(), { onSelect("") }) }
        items(chips) { name -> PaperPill(name, name == selected, { onSelect(name) }) }
    }
}

// ---------------------------------------------------------------- one habit

private fun amount(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCardView(
    row: HabitRow,
    today: LocalDate,
    mode: HabitMode,
    compact: Boolean,
    onOpen: () -> Unit,
    onHold: () -> Unit,
    onToday: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onMenu: (LocalDate) -> Unit,
) {
    val habit = row.habit
    val accent = Color(habit.color.toInt())
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    PaperCard(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onHold),
        padding = if (compact) 11.dp else 16.dp,
        shadow = false,
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphTile(habit, accent, if (compact) 34.dp else 46.dp)
                Spacer(Modifier.width(if (compact) 11.dp else 14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    MetaLine(row, accent, muted)
                    if (!compact) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StrengthBar(row.strength, accent, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${row.consistency}%",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = muted,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                ActionButton(row, accent, if (compact) 34.dp else 46.dp, onToday)
            }
            if (compact) {
                if (mode != HabitMode.Week) {
                    Spacer(Modifier.height(10.dp))
                    CompactStrip(row.rules, mode, today, accent)
                }
            } else {
                Spacer(Modifier.height(16.dp))
                HabitCalendarView(row.rules, mode, today, accent, onDay, onMenu)
            }
    }
}

@Composable
private fun GlyphTile(habit: HabitEntity, accent: Color, size: Dp) {
    // The picked icon (a picture or an emoji); "target" is only what is stored when none was picked, so the first letter shows then.
    val picked = habit.icon.takeIf { it != "target" }.orEmpty()
    Box(
        modifier = Modifier
            .size(size)
            .background(accent.copy(alpha = 0.18f), Wobble.Small)
            .border(1.25.dp, accent.copy(alpha = 0.45f), Wobble.Small)
            .clip(Wobble.Small),
        contentAlignment = Alignment.Center,
    ) {
        IconGlyph(
            icon = picked,
            tint = accent,
            size = if (size < 40.dp) 20.dp else 28.dp,
            fallback = habit.name.trim().take(1),
        )
    }
}

@Composable
private fun MetaLine(row: HabitRow, accent: Color, muted: Color) {
    val habit = row.habit
    val unit = habit.unitLabel.let { if (it.isBlank()) "" else " $it" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (row.paused) {
            Text("Paused", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PAUSED_BLUE)
            Spacer(Modifier.width(8.dp))
        }
        if (row.tracking) {
            // Tracking only: no streak. The last check and the totals take its place.
            Text(
                text = "Last ${row.lastCheck}  ·  Week ${amount(row.weekTotal)}  ·  Month ${amount(row.monthTotal)}",
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_flame),
                contentDescription = "Streak",
                modifier = Modifier.size(14.dp),
                tint = accent,
            )
            Spacer(Modifier.width(3.dp))
            Text(row.streakText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = muted)
        }
        if (row.stepsTotal > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "·  ${row.stepsToday}/${row.stepsTotal} steps",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = muted,
                maxLines = 1,
            )
        } else if (habit.kind == HabitStats.KIND_COUNTED && !row.tracking) {
            Spacer(Modifier.width(8.dp))
            Text(
                // A goal of 0 means no goal: just today's amount.
                text = if (habit.target > 0) "·  ${amount(row.todayCount)}/${amount(habit.target)}$unit" else "·  ${amount(row.todayCount)}$unit today",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (habit.category.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "·  ${habit.category}",
                fontSize = 13.sp,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StrengthBar(value: Float, accent: Color, modifier: Modifier) {
    TallyBar(progress = value.coerceIn(0f, 1f), modifier = modifier, color = accent, height = 14.dp)
}

/** The tick on the right of a card: a check for yes/no, a plus for counted, a ban for a relapse habit. */
@Composable
private fun ActionButton(row: HabitRow, accent: Color, size: Dp, onClick: () -> Unit) {
    val habit = row.habit
    val relapse = habit.kind == HabitStats.KIND_RELAPSE
    val counted = habit.kind == HabitStats.KIND_COUNTED
    val done = row.doneToday
    val ratio = if (counted && habit.target > 0) (row.todayCount / habit.target).toFloat().coerceIn(0f, 1f) else 0f
    val relapsedToday = relapse && !row.doneToday
    val filled = if (relapse) false else done
    val icon = when {
        relapse -> R.drawable.ic_ban
        filled -> R.drawable.ic_check
        counted || row.tracking -> R.drawable.ic_add
        else -> R.drawable.ic_check
    }
    val ink = if (filled) Color.White else accent
    Box(
        modifier = Modifier
            .size(size)
            .background(
                when {
                    filled -> accent
                    relapsedToday -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    else -> accent.copy(alpha = 0.12f)
                },
                Wobble.Small,
            )
            .border(1.25.dp, if (relapsedToday) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else accent.copy(alpha = if (filled) 1f else 0.45f), Wobble.Small)
            .clip(Wobble.Small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (counted && !done && ratio > 0f) {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                drawArc(accent, -90f, 360f * ratio, false, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = if (relapse) "Log a relapse" else if (counted) "Add one" else "Mark done",
            modifier = Modifier.size(size * 0.5f),
            tint = if (relapsedToday) MaterialTheme.colorScheme.error else ink,
        )
    }
}

// ---------------------------------------------------------------- calendars

@Composable
private fun cellColor(cell: HabitCell, accent: Color): Color {
    val base = MaterialTheme.colorScheme.surfaceVariant
    return when (cell.kind) {
        DayKind.DONE, DayKind.PARTIAL -> lerp(accent.copy(alpha = 0.4f), accent, cell.ratio)
        DayKind.COVERED -> accent.copy(alpha = 0.22f)
        DayKind.PAUSED -> PAUSED_BLUE.copy(alpha = 0.5f)
        DayKind.MISSED -> base
        DayKind.OFF, DayKind.FUTURE -> base.copy(alpha = 0.4f)
        DayKind.BEFORE -> base.copy(alpha = 0.4f)
        DayKind.RELAPSE -> MaterialTheme.colorScheme.error
        DayKind.CLEAN -> accent.copy(alpha = 0.4f)
    }
}

private fun inkOn(fill: Color, muted: Color): Color = when {
    fill.alpha < 0.55f -> muted
    fill.luminance() > 0.55f -> Color.Black
    else -> Color.White
}

private fun Modifier.dayTouch(date: LocalDate, today: LocalDate, onTap: (LocalDate) -> Unit, onMenu: (LocalDate) -> Unit): Modifier =
    if (date.isAfter(today)) this
    else this.pointerInput(date) { detectTapGestures(onTap = { onTap(date) }, onLongPress = { onMenu(date) }) }

@Composable
private fun HabitCalendarView(
    rules: HabitRules,
    mode: HabitMode,
    today: LocalDate,
    accent: Color,
    onTap: (LocalDate) -> Unit,
    onMenu: (LocalDate) -> Unit,
) {
    when (mode) {
        HabitMode.Week -> WeekRow(rules, today, accent, onTap, onMenu)
        HabitMode.Month -> MonthGrid(rules, today, accent, onTap, onMenu)
        HabitMode.Year -> YearGrid(rules, today, accent, onTap, onMenu)
    }
}

@Composable
private fun WeekRow(rules: HabitRules, today: LocalDate, accent: Color, onTap: (LocalDate) -> Unit, onMenu: (LocalDate) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val week = remember(today) { HabitCalendar.weekDays(today) }
    Row(modifier = Modifier.fillMaxWidth()) {
        week.forEach { date ->
            val cell = rules.cell(date)
            val fill = cellColor(cell, accent)
            val ink = inkOn(fill, muted).let { if (date.isAfter(today)) it.copy(alpha = 0.45f) else it }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .height(46.dp)
                    .clip(Wobble.Small)
                    .background(fill)
                    .then(if (date == today) Modifier.border(2.dp, ringColor(fill, accent), Wobble.Small) else Modifier)
                    .dayTouch(date, today, onTap, onMenu),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3).uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        maxLines = 1,
                    )
                    Text(text = date.dayOfMonth.toString(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                }
            }
        }
    }
}

private fun ringColor(fill: Color, accent: Color): Color =
    if (fill.alpha < 0.55f) accent else if (fill.luminance() > 0.55f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)

@Composable
private fun MonthGrid(rules: HabitRules, today: LocalDate, accent: Color, onTap: (LocalDate) -> Unit, onMenu: (LocalDate) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val weeks = remember(today) { HabitCalendar.monthWeeks(today) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        var side = minOf(40.dp, maxWidth / 7 - 2.dp)
        var gap = (maxWidth - side * 7) / 6
        if (gap > 12.dp) {
            gap = 12.dp
            side = (maxWidth - gap * 6) / 7
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    week.forEach { date ->
                        if (date.month != today.month) {
                            Spacer(Modifier.size(side))
                        } else {
                            val cell = rules.cell(date)
                            val fill = cellColor(cell, accent)
                            val shape = RoundedCornerShape(9.dp)
                            Box(
                                modifier = Modifier
                                    .size(side)
                                    .clip(shape)
                                    .background(fill)
                                    .then(if (date == today) Modifier.border(1.5.dp, ringColor(fill, accent), shape) else Modifier)
                                    .dayTouch(date, today, onTap, onMenu),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (side >= 20.dp) {
                                    val future = date.isAfter(today)
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        fontSize = (side.value * 0.36f).coerceIn(9f, 14f).sp,
                                        fontWeight = if (fill.alpha >= 0.55f || date == today) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = when {
                                            fill.alpha >= 0.55f -> inkOn(fill, muted)
                                            future -> muted.copy(alpha = 0.5f)
                                            date == today -> MaterialTheme.colorScheme.primary
                                            else -> muted
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGrid(rules: HabitRules, today: LocalDate, accent: Color, onTap: (LocalDate) -> Unit, onMenu: (LocalDate) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val columns = remember(today) { HabitCalendar.yearColumns(today) }
    val labels = remember(columns) { HabitCalendar.yearMonthLabels(columns) }
    val scroll = rememberScrollState()
    // Open on the newest weeks, at the right-hand end.
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(modifier = Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        columns.forEachIndexed { index, column ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(modifier = Modifier.height(14.dp).width(13.dp)) {
                    labels[index]?.let { month ->
                        Text(
                            text = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            color = muted,
                        )
                    }
                }
                column.forEach { date ->
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(cellColor(rules.cell(date), accent))
                            .dayTouch(date, today, onTap, onMenu),
                    )
                }
            }
        }
    }
}

/** The thin coloured bar on a small card: this month's days, or the twelve months of the year. */
@Composable
private fun CompactStrip(rules: HabitRules, mode: HabitMode, today: LocalDate, accent: Color) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val colors: List<Color> = if (mode == HabitMode.Year) {
        HabitCalendar.monthShares(rules, today).map { share ->
            when {
                share == null -> base.copy(alpha = 0.4f)
                share <= 0f -> base
                else -> lerp(accent.copy(alpha = 0.3f), accent, share)
            }
        }
    } else {
        (1..today.lengthOfMonth()).map { day -> cellColor(rules.cell(today.withDayOfMonth(day)), accent) }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (mode == HabitMode.Year) 8.dp else 7.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}
