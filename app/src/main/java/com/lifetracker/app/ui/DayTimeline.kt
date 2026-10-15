package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.CallEntity
import com.lifetracker.app.data.CallStats
import com.lifetracker.app.data.ChargeSessionEntity
import com.lifetracker.app.data.ChargeStats
import com.lifetracker.app.data.DayLayout
import com.lifetracker.app.data.EntryEntity
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.SleepNightEntity
import com.lifetracker.app.data.SleepStats
import com.lifetracker.app.data.TimelineEditEntity
import com.lifetracker.app.data.TimelineEdits
import com.lifetracker.app.data.TodoEntity
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.data.UsageDays
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.Wobble
import com.lifetracker.app.ui.theme.pencilShadow
import java.time.Instant
import java.time.ZoneId

/** What tapping each kind of thing on the Timeline does, and what the cards need to know to draw themselves. */
class DayActions(
    val types: List<ActivityTypeEntity>,
    val projectNames: Map<String, String>,
    val habitNames: Map<String, String>,
    val onEntry: (EntryEntity) -> Unit,
    val onApp: (UsageDays.Block) -> Unit,
    val onTodo: (TodoEntity) -> Unit,
    val onPlaces: () -> Unit,
    val onCalls: () -> Unit,
    val onCharging: () -> Unit,
    val onSleep: () -> Unit,
    val onFood: () -> Unit,
    val onFillIn: (startMs: Long, endMs: Long) -> Unit,
    val onTripMode: (TripEntity, String) -> Unit,
    val onUnknownJourney: (DayLayout.Travel, String) -> Unit,
    /** What you changed about things on the Timeline, by [TimelineEdits.key]. */
    val edits: Map<String, TimelineEditEntity>,
    val onEditItem: (DayLayout.Item) -> Unit,
    val onEditPlace: (DayLayout.Place) -> Unit,
    val onEditTrip: (TripEntity) -> Unit,
)

private val RAIL_WIDTH = 12.dp
private val GAP = 10.dp
private val TIME_WIDTH = 58.dp
private val PLACE_COLORS = listOf(
    Color(0xFF8E94E8), Color(0xFF4FB38A), Color(0xFFE0A84A), Color(0xFFE08A5F), Color(0xFFD27BA8), Color(0xFF62A9D6),
)

/** The colour a place gets when you have not given its stay a category: the same name always gets the same colour. */
internal fun placeColorOf(name: String): Color = PLACE_COLORS[Math.floorMod(name.hashCode(), PLACE_COLORS.size)]

private fun clock(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

private fun lengthText(ms: Long): String = formatDuration(((ms + 30_000L) / 60_000L).toInt().coerceAtLeast(1))

/** One top-level row of the day: a place block, a journey, a loose item or a gap. */
@Composable
fun DayRow(row: DayLayout.Row, a: DayActions) {
    when (row) {
        is DayLayout.Place -> PlaceBlock(row, a)
        is DayLayout.Travel -> TravelCard(row, a)
        is DayLayout.Thing -> Indented { ThingRow(row.item, a) }
        is DayLayout.Gap -> Indented { GapRow(row, a) }
    }
}

@Composable
private fun Indented(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(start = RAIL_WIDTH + GAP)) { content() }
}

/** A hand-drawn outline in pen: solid, or dashed for what the phone collected on its own. */
private fun Modifier.penOutline(shape: Shape, color: Color, dashed: Boolean = false, width: Dp = 1.25.dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
    )
    drawOutline(outline, color, style = stroke)
}

@Composable
private fun PlaceBlock(p: DayLayout.Place, a: DayActions) {
    val paper = LocalPaper.current
    val chosen = TimelineEdits.category(a.edits[TimelineEdits.key(TimelineEdits.VISIT, p.visit.id)])?.let { a.types.byId(it) }
    val color = chosen?.displayColor() ?: placeColorOf(p.name)
    val shape = Wobble.Small
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        // A tall bar the length of the stay.
        Box(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .pencilShadow(shape, paper.pencil, 1.dp, 2.dp)
                .background(color, shape),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .background(paper.surface.copy(alpha = 0.55f), shape)
                .penOutline(shape, paper.line)
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { a.onEditPlace(p) }.padding(top = 5.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = p.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                    Text(
                        text = clock(p.startMs) + " to " + (if (p.ongoing) "now" else clock(p.endMs)) +
                            (if (chosen != null) " · " + chosen.name else "") +
                            (if (!p.named) " · tap to edit or name it" else ""),
                        fontSize = 11.sp,
                        color = paper.ink2,
                    )
                }
                Text(
                    text = PlaceRules.durationText(p.endMs - p.startMs),
                    modifier = Modifier.padding(start = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = paper.ink,
                )
            }
            if (p.children.isEmpty()) {
                Text(text = "Nothing else recorded here.", fontSize = 12.sp, color = paper.ink2, modifier = Modifier.padding(vertical = 6.dp))
            }
            p.children.forEach { child ->
                when (child) {
                    is DayLayout.Thing -> ThingRow(child.item, a)
                    is DayLayout.Gap -> GapRow(child, a)
                    else -> Unit
                }
            }
            if (p.ongoing) NowRow()
        }
    }
}

/** A dashed orange line with the time, at the bottom of a stay that is still going. */
@Composable
private fun NowRow() {
    val paper = LocalPaper.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = clock(System.currentTimeMillis()) + " now", fontSize = 11.sp, color = paper.ink2)
        Canvas(modifier = Modifier.weight(1f).height(4.dp)) {
            drawLine(
                color = paper.sun,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

/** What a card shows for one kind of thing. */
private class Look(
    val color: Color,
    val dashed: Boolean,
    val title: String,
    val lines: List<String>,
    val chip: String?,
    val trailing: String?,
    val editable: Boolean,
    val struck: Boolean = false,
    val onClick: (() -> Unit)?,
)

@Composable
private fun describe(item: DayLayout.Item, a: DayActions): Look {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val edit = { id: String, kind: String -> a.edits[TimelineEdits.key(kind, id)] }
    val chosen = { id: String, kind: String -> TimelineEdits.category(edit(id, kind))?.let { a.types.byId(it) } }
    val minutes = ((item.endMs - item.startMs) / 60_000L).toInt()
    return when (item.kind) {
        DayLayout.Kind.ENTRY -> {
            val e = item.ref as EntryEntity
            val type = a.types.byId(e.category)
            Look(
                color = type.displayColor(), dashed = false, title = e.title,
                lines = listOfNotNull(e.note.trim().ifEmpty { null }),
                chip = type.name,
                trailing = if (e.endMinute != null) formatDuration(minutes.coerceAtLeast(1)) else "moment",
                editable = true, onClick = { a.onEntry(e) },
            )
        }
        DayLayout.Kind.PHONE -> {
            val b = item.ref as UsageDays.Block
            val type = b.activityId.takeIf { it.isNotEmpty() }?.let { a.types.byId(it) }
            Look(
                color = type?.displayColor() ?: quiet, dashed = true, title = b.label,
                lines = listOfNotNull(if (b.opens > 1) "opened ${b.opens} times" else null),
                chip = type?.name ?: "no category, tap to set",
                trailing = formatDuration(((b.activeMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)),
                editable = true, onClick = { a.onEditItem(item) },
            )
        }
        DayLayout.Kind.MEAL -> {
            val m = item.ref as MealEntity
            val over = chosen(m.id, TimelineEdits.MEAL)
            Look(
                color = (over ?: a.types.byId("Food")).displayColor(), dashed = false, title = m.name,
                lines = listOf(mealTypeLabel(m.mealType) + " · " + Math.round(m.kcal) + " kcal · " + Math.round(m.grams) + " g"),
                chip = over?.name ?: "Food", trailing = null, editable = true, onClick = { a.onEditItem(item) },
            )
        }
        DayLayout.Kind.CALL -> {
            val c = item.ref as CallEntity
            val over = chosen(c.id, TimelineEdits.CALL)
            Look(
                color = over?.displayColor() ?: MaterialTheme.colorScheme.primary, dashed = true, title = CallStats.title(c),
                lines = listOf("Call · " + CallStats.detail(c)),
                chip = over?.name ?: "Calls and people",
                trailing = if (c.durationSec > 0) CallStats.durationText(c.durationSec) else null,
                editable = true, onClick = { a.onEditItem(item) },
            )
        }
        DayLayout.Kind.CHARGE -> {
            val s = item.ref as ChargeSessionEntity
            val over = chosen(s.id, TimelineEdits.CHARGE)
            Look(
                color = over?.displayColor() ?: MaterialTheme.colorScheme.tertiary, dashed = true, title = "Charging · " + ChargeStats.title(s),
                lines = listOf(ChargeStats.detail(s)),
                chip = over?.name, trailing = lengthText(item.endMs - item.startMs), editable = true, onClick = { a.onEditItem(item) },
            )
        }
        DayLayout.Kind.SLEEP -> {
            val n = item.ref as SleepNightEntity
            val over = chosen(n.date, TimelineEdits.SLEEP)
            Look(
                color = (over ?: a.types.byId("Sleep")).displayColor(), dashed = true, title = "Slept " + formatDuration(SleepStats.minutes(n)),
                lines = listOf(clock(n.startMs) + " → " + clock(n.endMs) + " · " + SleepStats.label(n.source)),
                chip = over?.name ?: "Sleep", trailing = null, editable = true, onClick = { a.onEditItem(item) },
            )
        }
        DayLayout.Kind.TODO -> {
            val t = item.ref as TodoEntity
            val first = t.text.trim().split('\n').first().ifBlank { "To-do" }
            Look(
                color = MaterialTheme.colorScheme.primary, dashed = false, title = first,
                lines = listOfNotNull(
                    "To do",
                    a.projectNames[t.project]?.takeIf { it.isNotBlank() },
                    t.estimate?.takeIf { it > 0 }?.let { formatDuration(it) },
                ).joinToString(" · ").let { listOf(it) },
                chip = null, trailing = if (t.done) "done" else null, editable = false, struck = t.done, onClick = { a.onTodo(t) },
            )
        }
        DayLayout.Kind.NOTE -> {
            val n = item.ref as NoteEntity
            val first = n.text.trim().split('\n').first().ifBlank { "Note" }
            val kind = listOf("Note", "Planned", "Completed").getOrElse(n.type) { "Note" }
            Look(
                color = quiet, dashed = false, title = first,
                lines = listOf(listOfNotNull(kind, a.habitNames[n.habitId]?.takeIf { it.isNotBlank() }).joinToString(" · ")),
                chip = null, trailing = null, editable = false, onClick = null,
            )
        }
    }
}

@Composable
private fun TimeColumn(startMs: Long, endMs: Long?) {
    val paper = LocalPaper.current
    Column(modifier = Modifier.width(TIME_WIDTH).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = clock(startMs), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = paper.ink, maxLines = 1)
        if (endMs != null) {
            Text(text = clock(endMs), fontSize = 10.5.sp, color = paper.ink2, maxLines = 1)
        }
    }
}

/** The coloured line beside a card: solid for what you logged, dashed for what the phone collected. */
@Composable
private fun SegmentLine(color: Color, dashed: Boolean) {
    Box(
        modifier = Modifier
            .width(8.dp)
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = 4.dp)
            .drawBehind {
                if (dashed) {
                    drawLine(
                        color = color,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = size.width,
                        cap = StrokeCap.Butt,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx())),
                    )
                } else {
                    drawRoundRect(color = color, cornerRadius = CornerRadius(4.dp.toPx()))
                }
            },
    )
}

@Composable
private fun ThingRow(item: DayLayout.Item, a: DayActions) {
    val paper = LocalPaper.current
    val look = describe(item, a)
    val shape = Wobble.Small
    // The taller the stretch, the taller its row (half a dp per minute, between 46 and 150).
    val minutes = ((item.endMs - item.startMs) / 60_000L).toInt()
    val minHeight = (minutes * 0.5f).coerceIn(46f, 150f).dp
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = minHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeColumn(item.startMs, if (item.showEnd) item.endMs else null)
        SegmentLine(look.color, look.dashed)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .then(if (look.dashed) Modifier else Modifier.background(paper.surface, shape))
                .penOutline(shape, paper.line, dashed = look.dashed)
                .clip(shape)
                .then(if (look.onClick != null) Modifier.clickable { look.onClick.invoke() } else Modifier),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = look.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (look.struck) TextDecoration.LineThrough else null,
                        color = if (look.struck) paper.ink2 else paper.ink,
                    )
                    look.lines.filter { it.isNotBlank() }.forEach {
                        Text(text = it, fontSize = 11.sp, color = paper.ink2)
                    }
                    if (look.chip != null) {
                        Text(
                            text = look.chip,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .background(look.color.copy(alpha = 0.18f), RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                            fontSize = 10.5.sp,
                            color = paper.ink,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (look.trailing != null) {
                        Text(text = look.trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = paper.ink2)
                    }
                    if (look.editable) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = "Edit",
                            modifier = Modifier.size(14.dp),
                            tint = paper.ink2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GapRow(gap: DayLayout.Gap, a: DayActions) {
    val paper = LocalPaper.current
    val shape = Wobble.Small
    val hatch = paper.ink2.copy(alpha = 0.14f)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeColumn(gap.startMs, gap.endMs)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .penOutline(shape, paper.line, dashed = true)
                .clip(shape)
                .drawBehind {
                    // Faint slanted pencil lines, the way the demo marks "no data".
                    val step = 7.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(hatch, Offset(x, size.height), Offset(x + size.height, 0f), 1.dp.toPx())
                        x += step
                    }
                }
                .clickable { a.onFillIn(gap.startMs, gap.endMs) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 9.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "No data", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                    Text(text = gap.note, fontSize = 11.sp, color = paper.ink2)
                }
                Text(text = lengthText(gap.endMs - gap.startMs), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = paper.ink2)
                PaperPill("+ Fill in", false, { a.onFillIn(gap.startMs, gap.endMs) })
            }
        }
    }
}

/** Chip label, then the mode id it sets. */
internal val QUICK_MODES = listOf(
    "Walked" to "walk", "Cycled" to "cycle", "Bus" to "bus", "Drove" to "car",
    "Auto" to "auto", "Metro" to "train", "Train" to "train",
)

@Composable
private fun TravelCard(t: DayLayout.Travel, a: DayActions) {
    val paper = LocalPaper.current
    val trip = t.trip
    val unknown = trip == null || (trip.mode == "vehicle" && trip.modeSource != "you")
    var picking by remember { mutableStateOf(false) }
    val length = lengthText(t.endMs - t.startMs)
    val title = when {
        trip == null -> "Unknown way of travel · $length" + (if (t.straightLineM > 0) " · " + TripRules.distanceText(t.straightLineM) + " in a straight line" else "")
        unknown -> "Unknown way of travel · $length · " + TripRules.distanceText(trip.distanceM)
        else -> TripModes.label(trip.mode) + (if (trip.modeSource == "you") "" else " (guess)") + " · $length · " + TripRules.distanceText(trip.distanceM)
    }
    val shape = Wobble.Small
    val lineColor = if (unknown) paper.sun else paper.ink2
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        // A dashed line under the place bars: the way from one place to the next.
        Box(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .heightIn(min = 46.dp)
                .drawBehind {
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                },
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (unknown) paper.sun.copy(alpha = 0.14f).compositeOver(paper.surface) else paper.surface, shape)
                    .penOutline(shape, if (unknown) paper.sun else paper.line, dashed = unknown, width = if (unknown) 1.5.dp else 1.25.dp)
                    .clip(shape)
                    .then(if (trip != null) Modifier.clickable { picking = true } else Modifier),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                        Text(
                            text = clock(t.startMs) + " to " + clock(t.endMs) + " · " + tripEnd(t.fromName) + " to " + tripEnd(t.toName),
                            fontSize = 11.sp,
                            color = paper.ink2,
                        )
                    }
                    if (trip != null) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = "Change how you travelled",
                            modifier = Modifier.size(14.dp),
                            tint = paper.ink2,
                        )
                    }
                }
            }
            if (trip == null && t.from != null && t.to != null) {
                Text(
                    text = "How did you go? The phone has no record of the way, so the distance is a straight line.",
                    fontSize = 11.sp,
                    color = paper.ink2,
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK_MODES.forEach { (label, mode) ->
                        PaperPill(label, false, { a.onUnknownJourney(t, mode) })
                    }
                }
            }
            if (trip != null && unknown) {
                Text(
                    text = "How did you go? Distance is worked out from the position readings.",
                    fontSize = 11.sp,
                    color = paper.ink2,
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK_MODES.forEach { (label, mode) ->
                        PaperPill(label, false, { a.onTripMode(trip, mode) })
                    }
                }
            }
        }
    }
    if (picking && trip != null) {
        PaperDialog(
            title = "How did you travel?",
            onDismiss = { picking = false },
            confirmText = "Edit time and category",
            onConfirm = { picking = false; a.onEditTrip(trip) },
            dismissText = "Close",
        ) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TripModes.list.filter { it.id != "vehicle" }.forEach { m ->
                    PaperPill(
                        m.label,
                        trip.mode == m.id && trip.modeSource == "you",
                        { a.onTripMode(trip, m.id); picking = false },
                    )
                }
            }
        }
    }
}
