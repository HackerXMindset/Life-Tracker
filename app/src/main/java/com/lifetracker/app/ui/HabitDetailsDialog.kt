package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitPlan
import com.lifetracker.app.data.HabitStats
import java.util.Locale

private fun amount(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

/** The habit page: the numbers, and what you can do with the habit. */
@Composable
fun HabitDetailsDialog(
    row: HabitRow,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habit.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Line("Current streak", "${row.rules.currentStreak()} $streakWord")
                Line("Best streak", "${row.rules.longestStreak()} $streakWord")
                Line(totalLabel, streaks.totalDays.toString())
                Line("Strength", "${row.consistency}%")
                Line("How often", HabitEditor.describe(plan))
                Line("Type", kind)
                if (habit.category.isNotEmpty()) Line("Category", habit.category)
                Text(
                    text = "Tap a day on the card to tick it. Press and hold a day to clear it.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onArchive) { Text("Archive") }
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
