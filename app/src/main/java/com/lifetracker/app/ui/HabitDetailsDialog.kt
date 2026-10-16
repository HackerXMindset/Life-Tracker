package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitPlan
import com.lifetracker.app.data.HabitStats
import com.lifetracker.app.data.NoteEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import java.time.LocalDate
import java.util.Locale

private fun amount(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

/** The habit page: the numbers, and what you can do with the habit. */
@Composable
fun HabitDetailsDialog(
    row: HabitRow,
    notes: List<NoteEntity>,
    today: LocalDate,
    onAddNote: () -> Unit,
    onEditNote: (NoteEntity) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
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
        HabitStats.KIND_COUNTED -> if (habit.anyAmount || habit.target <= 0) "Counted, any amount" else "Counted, goal ${amount(habit.target)}$unit a day"
        else -> "Yes / no"
    }
    val totalLabel = if (habit.kind == HabitStats.KIND_RELAPSE) "Relapses" else "Days done"

    val paper = LocalPaper.current
    PaperDialog(
        title = habit.name,
        onDismiss = onDismiss,
        dismissText = "Close",
        confirmText = "Edit",
        onConfirm = onEdit,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Line("Current streak", "${row.rules.currentStreak()} $streakWord")
            Line("Best streak", "${row.rules.longestStreak()} $streakWord")
            Line(totalLabel, streaks.totalDays.toString())
            Line("Strength", "${row.consistency}%")
            Line("How often", HabitEditor.describe(plan))
            Line("Type", kind)
            if (habit.category.isNotEmpty()) Line("Category", habit.category)
            Text(
                text = "Tap a day on the card to tick it. Press and hold a day for more: an exact amount, why you missed it, or clearing it.",
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                fontSize = 11.5.sp,
                color = paper.ink2,
            )
            HabitNotesList(notes, today, onEditNote, onAddNote)
            PaperButton("Archive", onArchive, modifier = Modifier.padding(top = 10.dp), style = PaperButtonStyle.Ghost, small = true)
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
