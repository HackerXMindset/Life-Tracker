package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.lifetracker.app.data.TodoEntity
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.data.UsageDays
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
)

private val RAIL_WIDTH = 26.dp
private val GAP = 8.dp
private val TIME_WIDTH = 58.dp
private val UNKNOWN_GOLD = Color(0xFFD9A441)
private val PLACE_COLORS = listOf(
    Color(0xFF8E94E8), Color(0xFF4FB38A), Color(0xFFE0A84A), Color(0xFFE08A5F), Color(0xFFD27BA8), Color(0xFF62A9D6),
)

private fun clock(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

private fun lengthText(ms: Long): String = formatDuration(((ms + 30_000L) / 60_000L).toInt().coerceAtLeast(1))

/** One top-level row of the day: a place block, a journey, a loose item or a gap. */
@Composable
fun DayRow(row: DayLayout.Row, a: DayActions) {
    when (row) {
        is DayLayout.Place -> PlaceBlock(row, a)
        is DayLayout.Travel -> Indented { TravelCard(row, a) }
        is DayLayout.Thing -> Indented { ThingRow(row.item, a) }
        is DayLayout.Gap -> Indented { GapRow(row, a) }
    }
}

@Composable
private fun Indented(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(start = RAIL_WIDTH + GAP)) { content() }
}

@Composable
private fun PlaceBlock(p: DayLayout.Place, a: DayActions) {
    val color = PLACE_COLORS[Math.floorMod(p.name.hashCode(), PLACE_COLORS.size)]
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        // A tall bar the length of the stay, with the place's name running up it.
        Box(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .clip(RoundedCornerShape(RAIL_WIDTH / 2))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = p.name.uppercase(),
                modifier = Modifier.wrapContentWidth(unbounded = true).rotate(-90f),
                maxLines = 1,
                softWrap = false,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = Color(0xFF101414),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 96.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .drawBehind {
                    drawRoundRect(
                        color = color.copy(alpha = 0.55f),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = a.onPlaces),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = clock(p.startMs) + " to " + (if (p.ongoing) "now" else clock(p.endMs)) +
                                (if (!p.named) " · tap to name it" else ""),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = PlaceRules.durationText(p.endMs - p.startMs),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (p.children.isEmpty()) {
                    Text(
                        text = "Nothing else recorded here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                p.children.forEach { child ->
                    when (child) {
                        is DayLayout.Thing -> ThingRow(child.item, a)
                        is DayLayout.Gap -> GapRow(child, a)
                        else -> Unit
                    }
                }
            }
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
                editable = true, onClick = { a.onApp(b) },
            )
        }
        DayLayout.Kind.MEAL -> {
            val m = item.ref as MealEntity
            Look(
                color = a.types.byId("Food").displayColor(), dashed = false, title = m.name,
                lines = listOf(mealTypeLabel(m.mealType) + " · " + Math.round(m.kcal) + " kcal · " + Math.round(m.grams) + " g"),
                chip = "Food", trailing = null, editable = true, onClick = a.onFood,
            )
        }
        DayLayout.Kind.CALL -> {
            val c = item.ref as CallEntity
            Look(
                color = MaterialTheme.colorScheme.primary, dashed = true, title = CallStats.title(c),
                lines = listOf("Call · " + CallStats.detail(c)),
                chip = "Calls and people",
                trailing = if (c.durationSec > 0) CallStats.durationText(c.durationSec) else null,
                editable = false, onClick = a.onCalls,
            )
        }
        DayLayout.Kind.CHARGE -> {
            val s = item.ref as ChargeSessionEntity
            Look(
                color = MaterialTheme.colorScheme.tertiary, dashed = true, title = "Charging · " + ChargeStats.title(s),
                lines = listOf(ChargeStats.detail(s)),
                chip = null, trailing = lengthText(item.endMs - item.startMs), editable = true, onClick = a.onCharging,
            )
        }
        DayLayout.Kind.SLEEP -> {
            val n = item.ref as SleepNightEntity
            Look(
                color = a.types.byId("Sleep").displayColor(), dashed = true, title = "Slept " + formatDuration(SleepStats.minutes(n)),
                lines = listOf(clock(n.startMs) + " → " + clock(n.endMs) + " · " + SleepStats.label(n.source)),
                chip = "Sleep", trailing = null, editable = true, onClick = a.onSleep,
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
    Column(modifier = Modifier.width(TIME_WIDTH).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = clock(startMs),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (endMs != null) {
            Text(
                text = clock(endMs),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The coloured line beside a card: solid for what you logged, dashed for what the phone collected. */
@Composable
private fun SegmentLine(color: Color, dashed: Boolean) {
    Box(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
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
                    drawRoundRect(color = color, cornerRadius = CornerRadius(size.width / 2))
                }
            },
    )
}

private fun Modifier.outline(color: Color, dashed: Boolean, radius: Float = 14f): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.dp.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
        ),
    )
}

@Composable
private fun ThingRow(item: DayLayout.Item, a: DayActions) {
    val look = describe(item, a)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        TimeColumn(item.startMs, if (item.showEnd) item.endMs else null)
        SegmentLine(look.color, look.dashed)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .outline(MaterialTheme.colorScheme.outline, look.dashed)
                .then(if (look.onClick != null) Modifier.clickable { look.onClick.invoke() } else Modifier),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = look.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (look.struck) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    look.lines.filter { it.isNotBlank() }.forEach {
                        Text(text = it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (look.chip != null) {
                        Text(
                            text = look.chip,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(look.color.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (look.trailing != null) {
                        Text(
                            text = look.trailing,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (look.editable) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp),
                            tint = look.color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GapRow(gap: DayLayout.Gap, a: DayActions) {
    val line = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        TimeColumn(gap.startMs, gap.endMs)
        Spacer(Modifier.width(5.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .drawBehind {
                    val step = 10.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(line.copy(alpha = 0.45f), Offset(x, size.height), Offset(x + size.height, 0f), 1.dp.toPx())
                        x += step
                    }
                }
                .outline(line, true),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No data",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(text = gap.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = lengthText(gap.endMs - gap.startMs),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { a.onFillIn(gap.startMs, gap.endMs) }) { Text("+ Fill in", fontSize = 13.sp) }
            }
        }
    }
}

/** Chip label, then the mode id it sets. */
private val QUICK_MODES = listOf(
    "Walked" to "walk", "Cycled" to "cycle", "Bus" to "bus", "Drove" to "car",
    "Auto" to "auto", "Metro" to "train", "Train" to "train",
)

@Composable
private fun TravelCard(t: DayLayout.Travel, a: DayActions) {
    val trip = t.trip
    val unknown = trip == null || (trip.mode == "vehicle" && trip.modeSource != "you")
    var picking by remember { mutableStateOf(false) }
    val length = lengthText(t.endMs - t.startMs)
    val title = when {
        trip == null -> "Unknown way of travel · $length"
        unknown -> "Unknown way of travel · $length · " + TripRules.distanceText(trip.distanceM)
        else -> TripModes.label(trip.mode) + (if (trip.modeSource == "you") "" else " (guess)") + " · $length · " + TripRules.distanceText(trip.distanceM)
    }
    val accent = if (unknown) UNKNOWN_GOLD else MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (unknown) UNKNOWN_GOLD.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                .outline(if (unknown) UNKNOWN_GOLD else MaterialTheme.colorScheme.outline, unknown)
                .then(if (trip != null) Modifier.clickable { picking = true } else Modifier),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = clock(t.startMs) + " to " + clock(t.endMs) + " · " + tripEnd(t.fromName) + " to " + tripEnd(t.toName),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (trip != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = "Change how you travelled",
                        modifier = Modifier.size(16.dp),
                        tint = accent,
                    )
                }
            }
        }
        if (trip != null && unknown) {
            Text(
                text = "How did you go? Distance is worked out from the position readings.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_MODES.forEach { (label, mode) ->
                    FilterChip(selected = false, onClick = { a.onTripMode(trip, mode) }, label = { Text(label) })
                }
            }
        }
    }
    if (picking && trip != null) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("How did you travel?") },
            text = {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TripModes.list.filter { it.id != "vehicle" }.forEach { m ->
                        FilterChip(
                            selected = trip.mode == m.id && trip.modeSource == "you",
                            onClick = { a.onTripMode(trip, m.id); picking = false },
                            label = { Text(m.label) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Close") } },
        )
    }
}
