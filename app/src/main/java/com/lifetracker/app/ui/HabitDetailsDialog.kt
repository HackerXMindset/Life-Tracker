package com.lifetracker.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitPauses
import com.lifetracker.app.data.HabitPlan
import com.lifetracker.app.data.HabitStats
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperPill
import java.time.LocalDate
import java.util.Locale

private fun amount(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

/** The habit page, a whole screen: the numbers, and what you can do with the habit. */
@Composable
fun HabitDetailsPage(
    row: HabitRow,
    notes: List<NoteEntity>,
    today: LocalDate,
    onAddNote: () -> Unit,
    onEditNote: (NoteEntity) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onClearProgress: () -> Unit,
    /** Pause from today (true) or resume (false). */
    onPause: (Boolean) -> Unit,
    /** The weekdays (1 = Monday ... 7 = Sunday) that never count. */
    onRestDays: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var ask by remember { mutableStateOf(0) }
    val habit = row.habit
    val streaks = row.rules.streaks()
    val plan = HabitPlan.fromRaw(habit.raw)
    val unit = habit.unitLabel.let { if (it.isBlank()) "" else " $it" }
    val streakWord = when (plan.interval) {
        HabitPlan.WEEKLY -> "weeks"
        HabitPlan.MONTHLY -> "months"
        else -> "days"
    }
    val kind = when (habit.kind) {
        HabitStats.KIND_RELAPSE -> "Avoid (days without a relapse)"
        HabitStats.KIND_COUNTED -> if (row.tracking) "Counted, tracking only" else if (habit.target <= 0) "Counted, no goal" else if (habit.anyAmount) "Counted, any amount" else "Counted, goal ${amount(habit.target)}$unit a day"
        else -> if (row.tracking) "Yes / no, tracking only" else "Yes / no"
    }
    val totalLabel = if (habit.kind == HabitStats.KIND_RELAPSE) "Relapses" else "Days done"

    val paper = LocalPaper.current
    HabitPage(
        title = habit.name,
        onBack = onDismiss,
        dismissText = "Close",
        confirmText = "Edit",
        onConfirm = onEdit,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (row.tracking) {
                // Tracking only: no streak, no strength, no missed days.
                Line("Last check", row.lastCheck)
                Line("This week", amount(row.weekTotal))
                Line("This month", amount(row.monthTotal))
            } else {
                Line("Current streak", "${row.rules.currentStreak()} $streakWord")
                Line("Best streak", "${row.rules.longestStreak()} $streakWord")
                Line(totalLabel, streaks.totalDays.toString())
                Line("Strength", "${row.consistency}%")
            }
            Line("How often", HabitEditor.describe(plan))
            Line("Type", kind)
            if (habit.category.isNotEmpty()) Line("Category", habit.category)
            Text(
                text = "Tap a day on the card to tick it. Press and hold a day for more: the time of each check, an amount, why you missed it, or clearing it.",
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                fontSize = 11.5.sp,
                color = paper.ink2,
            )
            HabitNotesList(notes, today, onEditNote, onAddNote)
            val paused = HabitPauses.isPaused(habit.raw)
            val rest = HabitPauses.restDays(habit.raw)
            MonoLabel("Pause")
            Text(
                text = if (paused) "Paused. Days while paused count as neither done nor missed, and never break a streak."
                else "Pause it for a holiday or an illness. Nothing recorded is touched.",
                fontSize = 11.5.sp,
                color = paper.ink2,
            )
            PaperButton(
                if (paused) "Resume" else "Pause",
                { onPause(!paused) },
                modifier = Modifier.padding(top = 4.dp),
                style = PaperButtonStyle.Ghost,
                small = true,
            )
            MonoLabel("Rest days (never count)", modifier = Modifier.padding(top = 10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HabitEditor.DAY_NAMES.forEachIndexed { i, label ->
                    val day = i + 1
                    PaperPill(label, day in rest, { onRestDays(if (day in rest) rest - day else rest + day) })
                }
            }
            PaperButton("Archive", onArchive, modifier = Modifier.padding(top = 10.dp), style = PaperButtonStyle.Ghost, small = true)
            PaperButton("Clear all progress", { ask = 1 }, modifier = Modifier.padding(top = 6.dp), style = PaperButtonStyle.Ghost, small = true)
            PaperButton("Delete", { ask = 2 }, modifier = Modifier.padding(top = 6.dp), style = PaperButtonStyle.Danger, small = true)
            Text(
                text = "Clearing one day here does not reach Streak: Streak keeps the larger amount for a day. Clear all progress and Delete do reach it.",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 11.sp,
                color = paper.ink2,
            )
        }
    }
    if (ask == 1) {
        PaperDialog(
            title = "Clear all progress?",
            onDismiss = { ask = 0 },
            confirmText = "Clear",
            onConfirm = { ask = 0; onClearProgress() },
            confirmStyle = PaperButtonStyle.Danger,
        ) {
            Text("Only days after today will count. Every day stays stored in the app and in backups; nothing is erased.", fontSize = 13.sp, color = paper.ink2)
        }
    }
    if (ask == 2) {
        PaperDialog(
            title = "Delete this habit?",
            onDismiss = { ask = 0 },
            confirmText = "Delete",
            onConfirm = { ask = 0; onDelete() },
            confirmStyle = PaperButtonStyle.Danger,
        ) {
            Text(
                "It disappears from the lists, with its notes, and Streak is told. It is kept: Settings, Habits, Deleted habits and notes brings it back.",
                fontSize = 13.sp,
                color = paper.ink2,
            )
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val paper = LocalPaper.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    paper.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
            }
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = paper.ink2)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = paper.ink)
    }
}
