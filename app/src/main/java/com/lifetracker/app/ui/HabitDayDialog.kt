package com.lifetracker.app.ui

import android.app.TimePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitStats
import com.lifetracker.app.data.HabitSteps
import com.lifetracker.app.data.HabitTicks
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
 * What you can do with one day of one habit: mark it, see the time of each check and change it, add a check (at a time you
 * pick) or an amount, replace the day's total, take back the last check, say why you missed it, or clear the day.
 * Clearing is the only thing here that removes a whole day's record.
 */
@Composable
fun HabitDayDialog(
    row: HabitRow,
    date: LocalDate,
    onToggle: () -> Unit,
    onAmount: (Double) -> Unit,
    /** One more check: an amount (null = one step) at a minute of the day (null = now, for today). */
    onAddCheck: (Double?, Int?) -> Unit,
    onUndo: () -> Unit,
    /** Ticks or unticks one step of a checklist habit (by step id). */
    onStep: (String) -> Unit,
    /** Changes one check's time: from (null = it had none) to a minute of the day. */
    onTime: (Int?, Int) -> Unit,
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
    val tracking = rules.tracking
    val steps = if (row.stepsTotal > 0) HabitSteps.read(habit.raw) else emptyList()
    val checklist = steps.isNotEmpty()
    val tickedSteps = rules.tickedOn(date)
    val checks = rules.timesOn(date)
    val context = LocalContext.current
    val reasons = HabitEditor.missReasons(habit.raw)
    val reason = reasons[date].orEmpty()
    // Blank at first: "Add" puts this on top of the day, "Replace total" sets the day to it.
    var amount by remember(date, recorded) { mutableStateOf("") }
    var custom by remember(date) { mutableStateOf("") }

    val status = when {
        checklist -> "${shown(recorded)} of ${row.stepsTotal} steps" + if (done) "  ·  Done" else ""
        counted -> "${shown(recorded)} of ${if (habit.target <= 0 && !habit.anyAmount) "no goal" else if (habit.anyAmount || habit.target <= 0) "any amount" else shown(habit.target)}$unit"
        relapse -> if (recorded > 0) "${shown(recorded)} ${if (recorded == 1.0) "relapse" else "relapses"} logged" else "No relapse"
        tracking -> if (recorded > 0) "${shown(recorded)} checked$unit" else "Nothing yet"
        done -> "Done"
        else -> "Not done"
    }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }
    val nowMinute = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    // The amount typed for adding: blank means one step.
    val typed = amount.replace(",", ".").trim().toDoubleOrNull()
    val addAmount: Double? = if (amount.isBlank() || !counted) null else typed

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
                checklist -> {
                    steps.forEach { step ->
                        TimeRow(step.title.ifBlank { "Step" }, if (step.id in tickedSteps) "Ticked ✓" else "Tick") { onStep(step.id) }
                    }
                    PaperButton(
                        text = if (done) "Clear the day" else "Tick every step",
                        onClick = { onToggle(); onDismiss() },
                        style = PaperButtonStyle.Ghost,
                        small = true,
                    )
                }
                counted -> {
                    PaperField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = "Amount$unit (blank = one step)",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperButton(
                            text = "Add",
                            enabled = amount.isBlank() || (typed != null && typed > 0),
                            small = true,
                            onClick = { onAddCheck(addAmount, null); amount = "" },
                        )
                        PaperButton(
                            text = "Add at a time…",
                            enabled = amount.isBlank() || (typed != null && typed > 0),
                            small = true,
                            style = PaperButtonStyle.Ghost,
                            onClick = { pickTime(nowMinute) { onAddCheck(addAmount, it); amount = "" } },
                        )
                    }
                    PaperButton(
                        text = "Replace the day's total with this",
                        enabled = typed != null,
                        small = true,
                        style = PaperButtonStyle.Ghost,
                        onClick = { typed?.let { onAmount(it); onDismiss() } },
                    )
                }
                relapse -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperButton(
                        text = if (recorded > 0) "Log another" else "Log a relapse",
                        onClick = { onAddCheck(null, null) },
                        style = PaperButtonStyle.Ghost,
                        small = true,
                    )
                    PaperButton(
                        text = "At a time…",
                        onClick = { pickTime(nowMinute) { onAddCheck(null, it) } },
                        style = PaperButtonStyle.Ghost,
                        small = true,
                    )
                }
                tracking -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperButton("Add a check", { onAddCheck(null, null) }, style = PaperButtonStyle.Ghost, small = true)
                    PaperButton("At a time…", { pickTime(nowMinute) { onAddCheck(null, it) } }, style = PaperButtonStyle.Ghost, small = true)
                }
                else -> PaperButton(
                    text = if (done) "Mark not done" else "Mark done",
                    onClick = { onToggle(); onDismiss() },
                    style = PaperButtonStyle.Ghost,
                    small = true,
                )
            }

            if (recorded > 0 && !checklist) {
                MonoLabel(if (checks.size == 1) "Time of the check" else "Times of the checks")
                if (checks.isEmpty()) {
                    TimeRow("No time noted", "Set a time") { pickTime(nowMinute) { onTime(null, it) } }
                }
                checks.forEach { minute ->
                    TimeRow(HabitTicks.clock(minute), "Change") { pickTime(minute) { onTime(minute, it) } }
                }
                if ((counted || relapse || tracking) && recorded > 0) {
                    PaperButton(
                        text = if (relapse) "Take back the last relapse" else "Take back the last check",
                        onClick = onUndo,
                        style = PaperButtonStyle.Ghost,
                        small = true,
                    )
                }
            }

            PaperButton("Add a note for this day", { onNote(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)

            if (rules.canMiss(date) && !relapse && !tracking) {
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

            if (recorded > 0) {
                PaperButton(if (relapse) "Clear every relapse of this day" else "Clear this day", { onClear(); onDismiss() }, style = PaperButtonStyle.Danger, small = true)
            }
        }
    }
}

@Composable
private fun TimeRow(text: String, action: String, onClick: () -> Unit) {
    val paper = LocalPaper.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, fontSize = 14.sp, color = paper.ink)
        PaperButton(action, onClick, style = PaperButtonStyle.Ghost, small = true)
    }
}
