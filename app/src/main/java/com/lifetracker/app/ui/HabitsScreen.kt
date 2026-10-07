package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.HabitStats

@Composable
fun HabitsScreen(vm: HabitsViewModel = viewModel()) {
    val groups by vm.groups.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(label = "Habits", badge = "Saved on this phone", title = "Habits")
        }
        if (groups.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "No habits yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Choose your Streak folder under Data sources (the gear on the Timeline). " +
                                "Your habits come in when you next open the app, or tap Import now.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        groups.forEach { group ->
            item(key = "group-" + group.title) { MonoLabel(group.title) }
            items(group.cards, key = { it.habit.id }) { card ->
                HabitCardView(card, onTap = vm::tap, onClear = vm::clear)
            }
        }
        if (groups.isNotEmpty()) {
            item {
                Text(
                    text = "Tap a day to tick it. Counted habits add one step per tap. Press and hold a day to clear it.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun amount(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format("%.1f", value)

@Composable
private fun HabitCardView(
    card: HabitCard,
    onTap: (HabitEntity, java.time.LocalDate) -> Unit,
    onClear: (HabitEntity, java.time.LocalDate) -> Unit,
) {
    val habit = card.habit
    val accent = Color(habit.color.toInt())
    val unit = habit.unitLabel.let { if (it.isBlank()) "" else " $it" }
    val s = card.streaks

    val summary = when (habit.kind) {
        HabitStats.KIND_RELAPSE ->
            "Clean ${s.current} days · best ${s.best} · ${s.totalDays} relapses"
        HabitStats.KIND_COUNTED ->
            "Today ${amount(card.todayCount)} / ${amount(habit.target)}$unit · streak ${s.current} · best ${s.best}"
        else -> "Streak ${s.current} · best ${s.best} · ${s.totalDays} days"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
                Text(
                    text = habit.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = summary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (habit.kind == HabitStats.KIND_COUNTED && card.totalAmount > 0) {
                Text(
                    text = "All time ${amount(card.totalAmount)}$unit",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                card.cells.forEach { cell ->
                    DayDot(
                        cell = cell,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onTap = { onTap(habit, cell.date) },
                        onClear = { onClear(habit, cell.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDot(
    cell: DayCell,
    accent: Color,
    modifier: Modifier,
    onTap: () -> Unit,
    onClear: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

    val dot = Modifier.size(20.dp).clip(CircleShape)
    val shape = when (cell.state) {
        CellState.Done -> dot.background(accent)
        CellState.Partial -> dot.background(accent.copy(alpha = 0.45f))
        CellState.Relapse -> dot.background(error)
        CellState.Clean -> dot.background(primary.copy(alpha = 0.3f))
        CellState.Pending -> dot.border(2.dp, accent, CircleShape)
        CellState.Missed -> dot.border(1.5.dp, outline, CircleShape)
        CellState.Before -> dot.border(1.dp, outline.copy(alpha = 0.35f), CircleShape)
    }

    Column(
        modifier = modifier
            .pointerInput(cell.date) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onClear() })
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(modifier = shape)
        Text(
            text = cell.date.dayOfMonth.toString(),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
