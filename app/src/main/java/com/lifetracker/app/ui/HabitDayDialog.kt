package com.lifetracker.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
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

    val paper = LocalPaper.current
    PaperDialog(title = habit.name, onDismiss = onDismiss, dismissText = "Close") {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.getDefault())) + "  ·  " + status,
                fontSize = 12.sp,
                color = paper.ink2,
            )
            when {
                counted -> Row(
                    verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaperField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = "Amount$unit",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    PaperButton(
                        text = "Set",
                        enabled = amount.replace(",", ".").trim().toDoubleOrNull() != null,
                        small = true,
                        onClick = {
                            amount.replace(",", ".").trim().toDoubleOrNull()?.let {
                                onAmount(it)
                                onDismiss()
                            }
                        },
                    )
                }
                relapse -> PaperButton(
                    text = if (recorded > 0) "Clear this relapse" else "Log a relapse on this day",
                    onClick = { onToggle(); onDismiss() },
                    style = PaperButtonStyle.Ghost,
                    small = true,
                )
                else -> PaperButton(
                    text = if (done) "Mark not done" else "Mark done",
                    onClick = { onToggle(); onDismiss() },
                    style = PaperButtonStyle.Ghost,
                    small = true,
                )
            }

            PaperButton("Add a note for this day", { onNote(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)

            if (rules.canMiss(date) && !relapse) {
                MonoLabel("Why was it missed?")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HabitEditor.reasonOptions(habit).forEach { option ->
                        PaperPill(option, reason == option, { onReason(if (reason == option) null else option) })
                    }
                }
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaperField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = "Or in your own words",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    PaperButton(
                        text = "Save",
                        enabled = custom.isNotBlank(),
                        small = true,
                        onClick = { onReason(custom); custom = "" },
                    )
                }
                if (reason.isNotEmpty()) {
                    Text("Reason: $reason", fontSize = 13.sp, color = paper.ink)
                }
            }

            if (recorded > 0 && !relapse) {
                PaperButton("Clear this day", { onClear(); onDismiss() }, style = PaperButtonStyle.Danger, small = true)
            }
        }
    }
}
