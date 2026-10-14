package com.lifetracker.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun shown(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

/**
 * What you can do with one day of one habit: mark it, set an exact amount, say why you missed it, or clear it.
 * Clearing is the only thing here that removes a day's record.
 */
@Composable
fun HabitDayDialog(
    row: HabitRow,
    date: LocalDate,
    onToggle: () -> Unit,
    onAmount: (Double) -> Unit,
    onReason: (String?) -> Unit,
    onClear: () -> Unit,
    onNote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val habit = row.habit
    val rules = row.rules
    val recorded = rules.amountOn(date)
    val unit = habit.unitLabel.let { if (it.isBlank()) "" else " $it" }
    val counted = habit.kind == HabitStats.KIND_COUNTED
    val relapse = habit.kind == HabitStats.KIND_RELAPSE
    val done = rules.isCompleted(date)
    val reasons = HabitEditor.missReasons(habit.raw)
    val reason = reasons[date].orEmpty()
    var amount by remember(date, recorded) { mutableStateOf(if (recorded > 0) shown(recorded) else "") }
    var custom by remember(date) { mutableStateOf("") }

    val status = when {
        counted -> "${shown(recorded)} of ${if (habit.anyAmount || habit.target <= 0) "any amount" else shown(habit.target)}$unit"
        relapse -> if (recorded > 0) "Relapse logged" else "No relapse"
        done -> "Done"
        else -> "Not done"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habit.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.getDefault())) + "  ·  " + status,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    counted -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Amount$unit") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        TextButton(
                            enabled = amount.replace(",", ".").trim().toDoubleOrNull() != null,
                            onClick = {
                                amount.replace(",", ".").trim().toDoubleOrNull()?.let {
                                    onAmount(it)
                                    onDismiss()
                                }
                            },
                        ) { Text("Set") }
                    }
                    relapse -> TextButton(onClick = { onToggle(); onDismiss() }) {
                        Text(if (recorded > 0) "Clear this relapse" else "Log a relapse on this day")
                    }
                    else -> TextButton(onClick = { onToggle(); onDismiss() }) {
                        Text(if (done) "Mark not done" else "Mark done")
                    }
                }

                TextButton(onClick = { onNote(); onDismiss() }) { Text("Add a note for this day") }

                if (rules.canMiss(date) && !relapse) {
                    MonoLabel("Why was it missed?")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HabitEditor.reasonOptions(habit).forEach { option ->
                            FilterChip(
                                selected = reason == option,
                                onClick = { onReason(if (reason == option) null else option) },
                                label = { Text(option) },
                            )
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = custom,
                            onValueChange = { custom = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Or in your own words") },
                            singleLine = true,
                        )
                        TextButton(
                            enabled = custom.isNotBlank(),
                            onClick = { onReason(custom); custom = "" },
                        ) { Text("Save") }
                    }
                    if (reason.isNotEmpty()) {
                        Text("Reason: $reason", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (recorded > 0 && !relapse) {
                    TextButton(onClick = { onClear(); onDismiss() }) { Text("Clear this day") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
