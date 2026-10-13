package com.lifetracker.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitEditor
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.HabitForm
import com.lifetracker.app.data.HabitPlan
import com.lifetracker.app.data.HabitStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val START_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun numberText(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else value.toString()

private fun number(text: String): Double? = text.replace(",", ".").trim().toDoubleOrNull()

/**
 * Add a habit ([initial] null) or change one. Saving gives back a form; the screen turns it into a stored habit.
 * [categories] are the names to pick from; a new name typed in the box is added as a category.
 */
@Composable
fun HabitEditorDialog(
    initial: HabitEntity?,
    categories: List<String>,
    onSave: (HabitForm) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember(initial) { initial?.let { HabitEditor.formFrom(it) } ?: HabitForm() }
    val context = LocalContext.current

    var name by remember { mutableStateOf(start.name) }
    var icon by remember { mutableStateOf(start.icon) }
    var color by remember { mutableStateOf(start.color) }
    var category by remember { mutableStateOf(start.category) }
    var newCategory by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(start.kind) }
    var target by remember { mutableStateOf(numberText(start.target)) }
    var unit by remember { mutableStateOf(start.unitLabel) }
    var step by remember { mutableStateOf(numberText(start.step)) }
    var anyAmount by remember { mutableStateOf(start.anyAmount) }
    var description by remember { mutableStateOf(start.description) }

    var interval by remember { mutableStateOf(start.plan.interval) }
    var weekdays by remember { mutableStateOf(start.plan.weekdays.toSet()) }
    var times by remember { mutableStateOf(start.plan.timesPerPeriod.toString()) }
    var every by remember { mutableStateOf(start.plan.every.toString()) }
    var everyUnit by remember { mutableStateOf(start.plan.unit) }
    var from by remember { mutableStateOf(start.plan.scheduleStart ?: LocalDate.now()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun form(): HabitForm {
        val chosen = newCategory.trim().ifEmpty { category }
        val counted = kind == HabitStats.KIND_COUNTED
        return HabitForm(
            name = name,
            icon = icon,
            color = color,
            category = chosen,
            kind = kind,
            target = if (counted) number(target) ?: 0.0 else start.target,
            unitLabel = unit,
            step = if (counted) number(step) ?: 0.0 else start.step,
            anyAmount = anyAmount,
            // The holidays and rest days of an imported habit are kept; this form does not edit them.
            plan = start.plan.copy(
                interval = interval,
                weekdays = weekdays.sorted(),
                timesPerPeriod = times.trim().toIntOrNull() ?: 0,
                every = every.trim().toIntOrNull() ?: 0,
                unit = everyUnit,
                scheduleStart = if (interval == HabitPlan.EVERY_X_DAYS) from else null,
            ),
            description = description,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New habit" else "Edit habit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Read 20 pages") },
                    singleLine = true,
                )

                MonoLabel("Icon")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HabitEditor.QUICK_ICONS.forEach { e ->
                        FilterChip(selected = icon == e, onClick = { icon = if (icon == e) "" else e }, label = { Text(e) })
                    }
                }
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it.take(8) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Or type any emoji") },
                    singleLine = true,
                )

                MonoLabel("Colour")
                HabitEditor.PALETTE.chunked(8).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowColors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.toInt()))
                                    .then(
                                        if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                        else Modifier,
                                    )
                                    .clickable { color = c },
                            )
                        }
                    }
                }

                MonoLabel("Category")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = category.isEmpty() && newCategory.isBlank(), onClick = { category = ""; newCategory = "" }, label = { Text("None") })
                    categories.forEach { c ->
                        FilterChip(selected = category == c && newCategory.isBlank(), onClick = { category = c; newCategory = "" }, label = { Text(c) })
                    }
                }
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Or a new category") },
                    singleLine = true,
                )

                MonoLabel("Type")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = kind == HabitStats.KIND_YES_NO, onClick = { kind = HabitStats.KIND_YES_NO }, label = { Text("Yes / no") })
                    FilterChip(selected = kind == HabitStats.KIND_RELAPSE, onClick = { kind = HabitStats.KIND_RELAPSE }, label = { Text("Avoid") })
                    FilterChip(selected = kind == HabitStats.KIND_COUNTED, onClick = { kind = HabitStats.KIND_COUNTED }, label = { Text("Counted") })
                }
                Text(
                    text = when (kind) {
                        HabitStats.KIND_RELAPSE -> "Counts the days you stay clear of something. You log a relapse when it happens."
                        HabitStats.KIND_COUNTED -> "You add an amount each day, like 8 glasses of water."
                        else -> "Done or not done each day."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (kind == HabitStats.KIND_COUNTED) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Goal for one day") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Unit (glasses, pages, minutes)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = step,
                        onValueChange = { step = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Added by each tap") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    SwitchRow("Any amount counts as done", anyAmount) { anyAmount = it }
                }

                MonoLabel("How often")
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        HabitPlan.DAILY to "Every day",
                        HabitPlan.WEEKDAYS to "Some days",
                        HabitPlan.WEEKLY to "Per week",
                        HabitPlan.MONTHLY to "Per month",
                        HabitPlan.EVERY_X_DAYS to "Every X",
                    ).forEach { (value, label) ->
                        FilterChip(selected = interval == value, onClick = { interval = value; error = null }, label = { Text(label) })
                    }
                }
                when (interval) {
                    HabitPlan.WEEKDAYS -> Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HabitEditor.DAY_NAMES.forEachIndexed { i, label ->
                            val day = i + 1
                            FilterChip(
                                selected = day in weekdays,
                                onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day; error = null },
                                label = { Text(label) },
                            )
                        }
                    }
                    HabitPlan.WEEKLY, HabitPlan.MONTHLY -> OutlinedTextField(
                        value = times,
                        onValueChange = { times = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (interval == HabitPlan.WEEKLY) "Days per week" else "Days per month") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    HabitPlan.EVERY_X_DAYS -> {
                        OutlinedTextField(
                            value = every,
                            onValueChange = { every = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Every how many") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(HabitPlan.UNIT_DAYS to "days", HabitPlan.UNIT_WEEKS to "weeks", HabitPlan.UNIT_MONTHS to "months")
                                .forEach { (value, label) ->
                                    FilterChip(selected = everyUnit == value, onClick = { everyUnit = value }, label = { Text(label) })
                                }
                        }
                        TextButton(onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> from = LocalDate.of(y, m + 1, d) },
                                from.year, from.monthValue - 1, from.dayOfMonth,
                            ).show()
                        }) { Text("Counting from ${from.format(START_LABEL)}") }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description (optional)") },
                )

                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val built = form()
                val problem = HabitEditor.validate(built)
                if (problem != null) error = problem else onSave(built)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
