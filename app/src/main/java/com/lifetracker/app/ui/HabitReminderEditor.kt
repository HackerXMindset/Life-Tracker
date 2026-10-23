package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitReminders
import com.lifetracker.app.data.HabitReminders.Reminder
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import java.time.LocalDate

/** The Reminders part of New habit / Edit habit (Habits H12): the list, with Add, Edit and Delete for each. */
@Composable
fun RemindersSection(reminders: List<Reminder>, onChange: (List<Reminder>) -> Unit) {
    val paper = LocalPaper.current
    // The reminder being edited, or a new one; null when the pop-up is closed.
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var isNew by remember { mutableStateOf(false) }

    MonoLabel("Reminders")
    if (reminders.isEmpty()) {
        Text("No reminders.", fontSize = 12.sp, color = paper.ink2)
    }
    reminders.forEach { r ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(HabitReminders.describe(r), fontSize = 14.sp, color = paper.ink)
                if (r.message.isNotBlank()) Text(r.message, fontSize = 12.sp, color = paper.ink2, maxLines = 1)
            }
            PaperButton("Edit", { editing = r; isNew = false }, style = PaperButtonStyle.Ghost, small = true)
            PaperButton("Delete", { onChange(reminders.filter { it.id != r.id }) }, style = PaperButtonStyle.Ghost, small = true)
        }
    }
    PaperButton(
        "Add reminder",
        { editing = Reminder(id = HabitEditor.newId(), hour = 9, minute = 0); isNew = true },
        style = PaperButtonStyle.Ghost,
        small = true,
    )
    Text(
        text = "Needs the Notifications permission (Settings > Permissions). A reminder only rings on days the habit is due, and stays quiet once the habit is done.",
        fontSize = 11.5.sp,
        color = paper.ink2,
    )

    editing?.let { start ->
        ReminderDialog(
            start = start,
            onDismiss = { editing = null },
            onSave = { saved ->
                onChange(if (isNew) reminders + saved else reminders.map { if (it.id == saved.id) saved else it })
                editing = null
            },
        )
    }
}

@Composable
private fun ReminderDialog(start: Reminder, onDismiss: () -> Unit, onSave: (Reminder) -> Unit) {
    val paper = LocalPaper.current
    val context = LocalContext.current
    var minute by remember { mutableStateOf(start.startMinute) }
    var days by remember { mutableStateOf(start.days.toSet()) }
    var byInterval by remember { mutableStateOf(start.isInterval) }
    var everyDays by remember { mutableStateOf(if (start.isInterval) start.everyDays.toString() else "2") }
    var hourly by remember { mutableStateOf(start.isHourly) }
    var everyHours by remember { mutableStateOf(if (start.isHourly) start.everyHours.toString() else "2") }
    var until by remember { mutableStateOf(start.untilMinute) }
    var message by remember { mutableStateOf(start.message) }
    var snooze by remember { mutableStateOf(start.snoozeMinutes) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }

    fun build(): Reminder {
        val n = everyDays.trim().toIntOrNull() ?: 0
        val interval = byInterval && !hourly
        return start.copy(
            hour = minute / 60,
            minute = minute % 60,
            days = days.sorted(),
            message = message.trim(),
            everyDays = if (interval) n else 1,
            // A reminder for every N days counts from the day it is saved; saving it again keeps its rhythm.
            anchorEpochDay = if (interval) (if (start.everyDays == n) start.anchorEpochDay else null) ?: LocalDate.now().toEpochDay() else null,
            everyHours = if (hourly) everyHours.trim().toIntOrNull() ?: 0 else 0,
            untilMinute = if (hourly) until else null,
            snoozeMinutes = snooze,
        )
    }

    PaperDialog(
        title = "Reminder",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = {
            val r = build()
            val problem = HabitReminders.problem(r) ?: if (byInterval && !hourly && r.everyDays < 2) "Every N days needs 2 or more." else null
            if (problem != null) error = problem else onSave(r)
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            error?.let { Text(it, fontSize = 13.sp, color = paper.bad) }
            MonoLabel(if (hourly) "First time" else "Time")
            PaperButton(HabitReminders.clock(minute), { pickTime(minute) { minute = it; error = null } }, style = PaperButtonStyle.Ghost, small = true)

            SwitchRow("Repeat during the day", hourly) { hourly = it; error = null }
            if (hourly) {
                PaperField(
                    value = everyHours,
                    onValueChange = { everyHours = it; error = null },
                    label = "Every how many hours",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Last time", fontSize = 13.sp, color = paper.ink2)
                    PaperButton(
                        until?.let { HabitReminders.clock(it) } ?: "End of day",
                        { pickTime(until ?: minute) { until = it; error = null } },
                        style = PaperButtonStyle.Ghost,
                        small = true,
                    )
                    if (until != null) PaperButton("Clear", { until = null }, style = PaperButtonStyle.Ghost, small = true)
                }
            } else {
                MonoLabel("Which days")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaperPill("Days of the week", !byInterval, { byInterval = false; error = null })
                    PaperPill("Every N days", byInterval, { byInterval = true; error = null })
                }
                if (byInterval) {
                    PaperField(
                        value = everyDays,
                        onValueChange = { everyDays = it; error = null },
                        label = "Every how many days (counted from today)",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            if (!(byInterval && !hourly)) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HabitEditor.DAY_NAMES.forEachIndexed { i, label ->
                        val day = i + 1
                        PaperPill(label, day in days, { days = if (day in days) days - day else days + day; error = null })
                    }
                }
            }

            PaperField(
                value = message,
                onValueChange = { message = it },
                label = "Message (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            MonoLabel("Snooze for")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                HabitReminders.SNOOZE_CHOICES.forEach { m -> PaperPill("$m min", snooze == m, { snooze = m }) }
            }
        }
    }
}
