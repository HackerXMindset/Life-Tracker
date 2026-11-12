package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.DiaryDay
import com.lifetracker.app.data.FoodCalendar
import com.lifetracker.app.data.FoodTrackerEntity
import com.lifetracker.app.data.FoodView
import com.lifetracker.app.data.MonthMath
import com.lifetracker.app.data.TimelineEntry
import com.lifetracker.app.data.TimelineMath
import com.lifetracker.app.data.TrackerEventEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/*
 * The three ways to look at your days on the Food tab: the week strip (as it always was), a month grid with a tick under every day
 * a habit you picked was done, and a timeline that lists foods and ticks together in the order they happened. Together they
 * answer "what did I eat before I felt like this?".
 */

/** One strip to choose between the views. */
@Composable
fun FoodViewSwitch(view: FoodView, onSelect: (FoodView) -> Unit) {
    PaperSegmented(
        options = FoodView.entries.map { it.label },
        selected = view.ordinal,
        onSelect = { onSelect(FoodView.entries[it]) },
    )
}

/** A check mark drawn as one line, so it looks the same at any size. */
@Composable
private fun TickMark(color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.14f, h * 0.55f)
            lineTo(w * 0.40f, h * 0.80f)
            lineTo(w * 0.86f, h * 0.22f)
        }
        drawPath(path, color, style = Stroke(width = this.size.width * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The habits you picked, as pills. [off] are the ones switched off in this view. Tapping one switches it. */
@Composable
private fun HabitPills(trackers: List<FoodTrackerEntity>, off: Set<String>, onToggle: (String) -> Unit) {
    ChipFlow {
        trackers.forEach { t -> PaperPill(t.name, t.id !in off, { onToggle(t.id) }, dot = argbColor(t.color)) }
    }
}

// ---- the month ----

/**
 * The month as a grid, Monday first. Each day shows its calorie ring and, under it, a tick in the colour of every habit done that
 * day (up to three). Swipe sideways to change month. Tapping a day opens it.
 */
@Composable
fun MonthView(
    month: YearMonth,
    date: LocalDate,
    today: LocalDate,
    ringPct: (LocalDate) -> Float,
    trackers: List<FoodTrackerEntity>,
    checks: List<TrackerEventEntity>,
    off: Set<String>,
    onToggleHabit: (String) -> Unit,
    onMonth: (YearMonth) -> Unit,
    onPick: (LocalDate) -> Unit,
    onPickHabits: () -> Unit,
    onPatterns: () -> Unit,
) {
    val p = LocalPaper.current
    val order = trackers.map { it.id }
    val ticks = MonthMath.ticks(checks, order, order.filter { it !in off }.toSet())
    val colours = trackers.associate { it.id to it.color }
    val rows = FoodCalendar.cells(month).let { cells -> (cells + List((7 - cells.size % 7) % 7) { null }).chunked(7) }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArrowButton(R.drawable.ic_p_back, "Earlier month") { onMonth(MonthMath.shift(month, -1)) }
            Column(
                modifier = Modifier.weight(1f).clickable(role = Role.Button) { onMonth(MonthMath.of(today)) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = p.ink,
                )
                if (month != MonthMath.of(today)) Text("Tap for this month", fontSize = 11.sp, color = p.ink2)
            }
            ArrowButton(R.drawable.ic_p_chev, "Later month") { onMonth(MonthMath.shift(month, 1)) }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth().pointerInput(month) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 120f) onMonth(MonthMath.shift(month, -1)) else if (total < -120f) onMonth(MonthMath.shift(month, 1)) },
                ) { _, delta -> total += delta }
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(1, 2, 3, 4, 5, 6, 7).forEach { d ->
                    Text(
                        java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1).uppercase(),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = p.ink2,
                    )
                }
            }
            rows.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val ids = ticks[day.toString()].orEmpty()
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) { onPick(day) }
                                    .padding(vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Box(
                                    modifier = if (day == today && day != date) Modifier.border(1.5.dp, p.ink2, CircleShape) else Modifier,
                                    contentAlignment = Alignment.Center,
                                ) { DayRing(day.dayOfMonth, ringPct(day), selected = day == date, future = day.isAfter(today)) }
                                Row(modifier = Modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ids.take(3).forEach { id -> TickMark(argbColor(colours[id] ?: 0xFF5B5FC7L), 10.dp) }
                                    if (ids.size > 3) Text("+" + (ids.size - 3), fontSize = 9.sp, color = p.ink2)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (trackers.isEmpty()) {
            Text(
                "Pick the habits you want to watch, such as a headache or reflux, and every day you did one gets a tick here.",
                fontSize = 12.5.sp, color = p.ink2,
            )
            Spacer(Modifier.height(8.dp))
            PaperButton("Pick habits", onPickHabits, style = PaperButtonStyle.Ghost, small = true)
        } else {
            Text("A tick under a day means that habit was done that day. Tap a habit to hide or show its ticks.", fontSize = 12.sp, color = p.ink2)
            Spacer(Modifier.height(8.dp))
            HabitPills(trackers, off, onToggleHabit)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton("Change habits", onPickHabits, style = PaperButtonStyle.Ghost, small = true)
                PaperButton("What came before", onPatterns, style = PaperButtonStyle.Ghost, small = true)
            }
        }
    }
}

@Composable
internal fun ArrowButton(icon: Int, label: String, onClick: () -> Unit) {
    val p = LocalPaper.current
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // The back arrow already points left and the chevron right.
        PaperIcon(icon, size = 18.dp, tint = p.ink)
    }
}

// ---- the timeline ----

/**
 * Days newest first. Each day lists what you ate and which habits you did, in the order they happened, so a headache can be read
 * against the meals before it. Tapping a day opens it.
 */
fun LazyListScope.foodTimeline(
    days: List<DiaryDay>,
    trackers: List<FoodTrackerEntity>,
    today: LocalDate,
    spanDays: Int,
    onDay: (LocalDate) -> Unit,
    onEarlier: () -> Unit,
    onPickHabits: () -> Unit,
    onPatterns: () -> Unit,
) {
    val colours = trackers.associate { it.id to it }
    item {
        TimelineHeader(trackers, onPickHabits, onPatterns)
    }
    if (days.isEmpty()) {
        item { TimelineEmpty() }
    } else {
        items(days.size, key = { days[it].date.toString() }) { i ->
            TimelineDayCard(days[i], colours, today, onClick = { onDay(days[i].date) })
        }
    }
    item {
        PaperButton("Show 30 days earlier (now $spanDays)", onEarlier, style = PaperButtonStyle.Ghost, small = true)
    }
}

@Composable
private fun TimelineHeader(trackers: List<FoodTrackerEntity>, onPickHabits: () -> Unit, onPatterns: () -> Unit) {
    val p = LocalPaper.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (trackers.isEmpty()) "Foods in time order. Pick habits to see their ticks between them."
            else "Foods and the ticks of " + trackers.joinToString(", ") { it.name } + ", in time order.",
            fontSize = 12.5.sp, color = p.ink2, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        PaperPill(if (trackers.isEmpty()) "Pick" else "Change", false, onPickHabits)
        if (trackers.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            PaperPill("Patterns", false, onPatterns)
        }
    }
}

@Composable
private fun TimelineEmpty() {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, shadow = false) {
        Text("Nothing in this time yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text("Foods you log and habits you tick will appear here together, in the order they happened.", fontSize = 12.5.sp, color = p.ink2)
    }
}

@Composable
private fun TimelineDayCard(day: DiaryDay, habits: Map<String, FoodTrackerEntity>, today: LocalDate, onClick: () -> Unit) {
    val p = LocalPaper.current
    val entries = TimelineMath.entries(day)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(TimelineTop.title(day.date, today), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + day.date.dayOfMonth + " " +
                        day.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    fontSize = 11.5.sp, color = p.ink2,
                )
            }
            if (day.meals.isNotEmpty()) Text(kc(day.kcal) + " kcal", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        }
        Spacer(Modifier.height(8.dp))
        entries.forEachIndexed { i, entry ->
            TimelineRow(entry, habits, first = i == 0, last = i == entries.lastIndex)
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry, habits: Map<String, FoodTrackerEntity>, first: Boolean, last: Boolean) {
    val p = LocalPaper.current
    val line = p.line
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 52.dp.toPx() + 8.dp.toPx()
                val centre = 11.dp.toPx()
                drawLine(line, Offset(x, if (first) centre else 0f), Offset(x, if (last) centre else size.height), 1.5.dp.toPx())
            }
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val time = if (entry.minute < 0) "no time" else formatMinute(entry.minute)
        Text(time, fontSize = 12.sp, color = p.ink2, modifier = Modifier.width(52.dp).padding(top = 3.dp), maxLines = 1)
        when (entry) {
            is TimelineEntry.Meal -> {
                Box(Modifier.padding(top = 5.dp).size(16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(10.dp).background(mealColor(entry.meal.mealType), CircleShape))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.meal.name, fontSize = 14.sp, color = p.ink, maxLines = 2)
                    Text(
                        mealTypeLabel(entry.meal.mealType) + " · " + kc(entry.meal.grams) + " g · " + kc(entry.meal.kcal) + " kcal",
                        fontSize = 11.5.sp, color = p.ink2,
                    )
                }
            }
            is TimelineEntry.Check -> {
                val habit = habits[entry.event.trackerId]
                val colour = argbColor(habit?.color ?: 0xFF5B5FC7L)
                Box(Modifier.padding(top = 3.dp).size(20.dp).background(colour, CircleShape), contentAlignment = Alignment.Center) {
                    TickMark(p.bg, 12.dp)
                }
                Text(
                    habit?.name ?: "Habit", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .background(colour.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    maxLines = 1,
                )
            }
        }
    }
}
