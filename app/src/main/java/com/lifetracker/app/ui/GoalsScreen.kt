package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.Feature
import com.lifetracker.app.data.Features
import com.lifetracker.app.data.GoalDirections
import com.lifetracker.app.data.GoalEntity
import com.lifetracker.app.data.GoalMeasures
import com.lifetracker.app.data.GoalPeriods
import com.lifetracker.app.data.GoalProgress
import com.lifetracker.app.data.GoalStats
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonBox
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperToggle
import com.lifetracker.app.ui.theme.Wobble

/** A ring that fills as the goal is reached. A limit turns red when it is passed. The words in the middle are short. */
@Composable
fun GoalRing(goal: GoalEntity, progress: GoalProgress, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    val color = when {
        progress.over -> p.bad
        progress.good && goal.direction == GoalDirections.AT_LEAST -> p.accent
        else -> p.ink
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.12f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(p.line, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            val sweep = (progress.fraction * 360.0).toFloat().coerceAtLeast(if (progress.value > 0) 4f else 0f)
            drawArc(color, -90f, sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(GoalStats.stateText(goal, progress), fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
    }
}

/** A row of rings for the goals that are switched on, for the Timeline and Habits. Nothing when there are none or Goals is off. */
@Composable
fun GoalStrip(onOpen: () -> Unit, vm: GoalsViewModel = viewModel()) {
    val context = LocalContext.current
    val changes by Features.changes.collectAsState()
    val items by vm.items.collectAsState()
    val shown = items.filter { it.goal.enabled }
    if (changes < 0 || !Features.isOn(context, Feature.GOALS) || shown.isEmpty()) return
    val p = LocalPaper.current
    val shape = Wobble.Small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface, shape)
            .border(1.25.dp, p.line, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        shown.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GoalRing(item.goal, item.progress, 52.dp)
                Text(item.goal.name, fontSize = 11.sp, color = p.ink2, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Goals from your places and trips: a list with a ring each, and a window to make or change one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(onBack: () -> Unit, vm: GoalsViewModel = viewModel()) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val items by vm.items.collectAsState()
    val goals by vm.goals.collectAsState()
    val featureChanges by Features.changes.collectAsState()
    val featureOn = featureChanges >= 0 && Features.isOn(context, Feature.GOALS)
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingsTop("Place goals", "Targets and limits from where you go", onBack) }
        item {
            Text(
                "A goal counts something from your places and trips over a day, a week, a month or the last few days: hours at a place or kind of place, " +
                    "visits, hours out of the house, distance, hours travelling. Set it as a target to reach (10 hours out of the house a week) " +
                    "or a limit to stay under (at most 2 hours of travelling a day). One stay or trip counts towards every goal it fits. " +
                    "The rings show on the Timeline and in Habits.",
                fontSize = 12.5.sp,
                color = p.ink2,
            )
        }
        if (!featureOn) item { Text("Place goals are switched off in Settings > Features, so no ring is shown.", fontSize = 13.sp, color = p.bad) }
        items(items, key = { it.goal.id }) { item ->
            val g = item.goal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (g.enabled) 1f else 0.6f)
                    .background(p.surface, Wobble.Small)
                    .border(1.25.dp, p.line, Wobble.Small)
                    .clip(Wobble.Small)
                    .clickable(role = Role.Button) { editing = g }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoalRing(g, item.progress, 56.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(g.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 2)
                    Text(GoalStats.lineText(g, item.progress), fontSize = 12.5.sp, color = p.ink2)
                }
                PaperToggle(checked = g.enabled, onChange = { vm.setEnabled(g.id, it) })
            }
        }
        if (goals.isEmpty()) item { Text("No goal yet.", fontSize = 13.sp, color = p.ink2) }
        item {
            PaperButtonBox(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                PaperIcon(R.drawable.ic_p_plus, size = 18.dp, tint = LocalContentColor.current)
                Text("New goal")
            }
        }
    }
    if (creating) GoalEditor(initial = null, vm = vm, onClose = { creating = false })
    editing?.let { g -> GoalEditor(initial = g, vm = vm, onClose = { editing = null }) }
}

private fun clampInt(text: String, lo: Int, hi: Int, default: Int): Int = (text.trim().toIntOrNull() ?: default).coerceIn(lo, hi)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalEditor(initial: GoalEntity?, vm: GoalsViewModel, onClose: () -> Unit) {
    val p = LocalPaper.current
    val places by vm.places.collectAsState()
    val categories by vm.categories.collectAsState()

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var measure by remember { mutableStateOf(initial?.measure ?: GoalMeasures.HOURS_AT) }
    var whereMode by remember { mutableStateOf(if (initial?.placeId.orEmpty().isNotEmpty()) 1 else if (initial?.category.orEmpty().isNotEmpty() && initial?.measure != GoalMeasures.HOURS_OUT) 2 else 0) }
    var placeId by remember { mutableStateOf(initial?.placeId.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var mode by remember { mutableStateOf(initial?.mode.orEmpty()) }
    var period by remember { mutableStateOf(initial?.period ?: GoalPeriods.WEEK) }
    var days by remember { mutableStateOf((initial?.days ?: 7).toString()) }
    var direction by remember { mutableStateOf(initial?.direction ?: GoalDirections.AT_LEAST) }
    var target by remember { mutableStateOf(initial?.target?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "10") }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(): GoalEntity? {
        val t = target.replace(',', '.').toDoubleOrNull()
        if (t == null || t < 0.0) { error = "Write the target as a number."; return null }
        if (direction == GoalDirections.AT_LEAST && t <= 0.0) { error = "A target to reach must be above 0."; return null }
        val usesPlace = GoalMeasures.usesPlace(measure)
        if (usesPlace && whereMode == 1 && placeId.isEmpty()) { error = "Pick the place."; return null }
        if (usesPlace && whereMode == 2 && category.isEmpty()) { error = "Pick the kind of place."; return null }
        val where = when {
            usesPlace && whereMode == 1 -> places.firstOrNull { it.id == placeId }?.name.orEmpty()
            usesPlace && whereMode == 2 -> categories.firstOrNull { it.id == category }?.name.orEmpty()
            else -> ""
        }
        val now = System.currentTimeMillis()
        val base = GoalEntity(
            id = initial?.id ?: "g$now",
            name = "",
            measure = measure,
            placeId = if (usesPlace && whereMode == 1) placeId else "",
            category = when {
                usesPlace && whereMode == 2 -> category
                measure == GoalMeasures.HOURS_OUT -> category.ifEmpty { "home" }
                else -> ""
            },
            mode = if (GoalMeasures.usesMode(measure)) mode else "",
            period = period,
            days = clampInt(days, 1, 366, 7),
            direction = direction,
            target = t,
            enabled = initial?.enabled ?: true,
            createdMs = initial?.createdMs ?: now,
            sortOrder = initial?.sortOrder ?: 0,
        )
        return base.copy(name = name.trim().ifEmpty { GoalStats.defaultName(base, where) })
    }

    EditWindow(
        title = if (initial == null) "New goal" else name.ifBlank { "Goal" },
        sub = "A target or a limit from your places and trips",
        onClose = onClose,
        onSave = { build()?.let { vm.save(it); onClose() } },
    ) {
        PaperField(value = name, onValueChange = { name = it }, label = "Name (optional)", modifier = Modifier.fillMaxWidth(), placeholder = "e.g. Gym time", singleLine = true)

        MonoLabel("What to count")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalMeasures.ALL.forEach { (k, label) -> PaperPill(label, measure == k, { measure = k }) }
        }
        if (GoalMeasures.usesPlace(measure)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Any place I named", whereMode == 0, { whereMode = 0 })
                PaperPill("One place", whereMode == 1, { whereMode = 1 })
                PaperPill("A kind of place", whereMode == 2, { whereMode = 2 })
            }
            if (whereMode == 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    places.filter { !it.archived }.forEach { pl -> PaperPill(pl.name, placeId == pl.id, { placeId = pl.id }) }
                }
            }
            if (whereMode == 2) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.filter { !it.archived }.forEach { c -> PaperPill(c.name, category == c.id, { category = c.id }) }
                }
            }
            if (measure == GoalMeasures.VISITS) Text("A visit counts on the day it starts, and only stays of a few minutes or more.", fontSize = 12.sp, color = p.ink2)
        }
        if (measure == GoalMeasures.HOURS_OUT) {
            Text("Out of the house means any stay at a place that is not of this kind, plus trips. Which kind is home?", fontSize = 12.sp, color = p.ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.filter { !it.archived }.forEach { c -> PaperPill(c.name, (category.ifEmpty { "home" }) == c.id, { category = c.id }) }
            }
        }
        if (GoalMeasures.usesMode(measure)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Any way", mode.isEmpty(), { mode = "" })
                TripModes.list.forEach { m -> PaperPill(m.label, mode == m.id, { mode = m.id }) }
            }
        }

        MonoLabel("Over")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalPeriods.ALL.forEach { (k, label) -> PaperPill(label, period == k, { period = k }) }
        }
        if (period == GoalPeriods.DAYS) {
            PaperField(
                value = days, onValueChange = { days = it.filter(Char::isDigit).take(3) }, label = "Number of days (up to today)",
                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Text("A week runs Monday to Sunday and a month from the 1st.", fontSize = 12.sp, color = p.ink2)

        MonoLabel("Aim")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PaperPill("Reach at least", direction == GoalDirections.AT_LEAST, { direction = GoalDirections.AT_LEAST })
            PaperPill("Stay under (a limit)", direction == GoalDirections.AT_MOST, { direction = GoalDirections.AT_MOST })
        }
        PaperField(
            value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) },
            label = "Target (" + (when (GoalMeasures.unit(measure)) { "h" -> "hours"; "km" -> "kilometres"; else -> "visits" }) + ")",
            modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        if (initial != null) {
            PaperButton("Delete goal", { vm.delete(initial.id); onClose() }, style = PaperButtonStyle.Danger, small = true)
        }
        error?.let { Text(it, color = p.bad, fontSize = 13.sp) }
    }
}
