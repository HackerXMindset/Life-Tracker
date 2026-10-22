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
import androidx.compose.material3.Text
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
import com.lifetracker.app.data.HabitStep
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
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
    var tracking by remember { mutableStateOf(start.tracking) }
    var steps by remember { mutableStateOf(start.steps) }
    var anySteps by remember { mutableStateOf(start.anySteps) }
    var stepsNeeded by remember { mutableStateOf(start.stepsNeeded.toString()) }

    var interval by remember { mutableStateOf(start.plan.interval) }
    var weekdays by remember { mutableStateOf(start.plan.weekdays.toSet()) }
    var times by remember { mutableStateOf(start.plan.timesPerPeriod.toString()) }
    var every by remember { mutableStateOf(start.plan.every.toString()) }
    var everyUnit by remember { mutableStateOf(start.plan.unit) }
    var from by remember { mutableStateOf(start.plan.scheduleStart ?: LocalDate.now()) }
    var fromLast by remember { mutableStateOf(start.plan.fromLastDone) }
    var monthWeekday by remember { mutableStateOf(start.plan.monthWeekday) }
    var difficulty by remember { mutableStateOf(start.difficulty) }
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
                fromLastDone = fromLast,
                monthWeekday = monthWeekday,
            ),
            difficulty = difficulty,
            description = description,
            tracking = tracking && kind != HabitStats.KIND_RELAPSE,
            steps = if (kind == HabitStats.KIND_YES_NO) steps else start.steps,
            anySteps = if (kind == HabitStats.KIND_YES_NO) anySteps else start.anySteps,
            stepsNeeded = if (kind == HabitStats.KIND_YES_NO) stepsNeeded.trim().toIntOrNull() ?: 0 else start.stepsNeeded,
        )
    }

    val paper = LocalPaper.current
    PaperDialog(
        title = if (initial == null) "New habit" else "Edit habit",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = {
            val built = form()
            val problem = HabitEditor.validate(built)
            if (problem != null) error = problem else onSave(built)
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaperField(
                value = name,
                onValueChange = { name = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = "Name",
                placeholder = "e.g. Read 20 pages",
                singleLine = true,
            )

            MonoLabel("Icon")
            IconPickerPanel(selected = icon, tint = Color(color.toInt()), onSelected = { icon = it })

            MonoLabel("Colour")
            ColorPickerPanel(selected = color, onSelected = { color = it })

            MonoLabel("Category")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("None", category.isEmpty() && newCategory.isBlank(), { category = ""; newCategory = "" })
                categories.forEach { c ->
                    PaperPill(c, category == c && newCategory.isBlank(), { category = c; newCategory = "" })
                }
            }
            PaperField(
                value = newCategory,
                onValueChange = { newCategory = it },
                modifier = Modifier.fillMaxWidth(),
                label = "Or a new category",
                singleLine = true,
            )

            MonoLabel("Type")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Yes / no", kind == HabitStats.KIND_YES_NO, { kind = HabitStats.KIND_YES_NO })
                PaperPill("Avoid", kind == HabitStats.KIND_RELAPSE, { kind = HabitStats.KIND_RELAPSE })
                PaperPill("Counted", kind == HabitStats.KIND_COUNTED, { kind = HabitStats.KIND_COUNTED })
            }
            Text(
                text = when (kind) {
                    HabitStats.KIND_RELAPSE -> "Counts the days you stay clear of something. You log a relapse when it happens."
                    HabitStats.KIND_COUNTED -> "You add an amount each day, like 8 glasses of water."
                    else -> "Done or not done each day."
                },
                fontSize = 12.sp,
                color = paper.ink2,
            )
            if (kind == HabitStats.KIND_COUNTED) {
                PaperField(
                    value = target,
                    onValueChange = { target = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Goal for one day (0 = no goal)",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                PaperField(
                    value = unit,
                    onValueChange = { unit = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Unit (glasses, pages, minutes)",
                    singleLine = true,
                )
                PaperField(
                    value = step,
                    onValueChange = { step = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Added by each tap",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                SwitchRow("Any amount counts as done", anyAmount) { anyAmount = it }
            }
            if (kind == HabitStats.KIND_YES_NO) {
                MonoLabel("Steps (optional)")
                Text(
                    text = "Make it a checklist: the day is done when every step is ticked.",
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
                steps.forEachIndexed { i, step ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PaperField(
                            value = step.title,
                            onValueChange = { text ->
                                steps = steps.mapIndexed { j, old -> if (j == i) old.copy(title = text) else old }
                                error = null
                            },
                            modifier = Modifier.weight(1f),
                            label = "Step ${i + 1}",
                            singleLine = true,
                        )
                        PaperButton("Remove", { steps = steps.filterIndexed { j, _ -> j != i }; error = null }, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
                PaperButton(
                    "Add a step",
                    { steps = steps + HabitStep(HabitEditor.newId(), ""); error = null },
                    style = PaperButtonStyle.Ghost,
                    small = true,
                )
                if (steps.any { it.title.isNotBlank() }) {
                    SwitchRow("Any of the steps is enough", anySteps) { anySteps = it; error = null }
                    if (anySteps) {
                        PaperField(
                            value = stepsNeeded,
                            onValueChange = { stepsNeeded = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = "How many steps are needed",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
            if (kind != HabitStats.KIND_RELAPSE) {
                SwitchRow("Just tracking (no streak)", tracking) { tracking = it }
                if (tracking) {
                    Text(
                        text = "No streak, no strength and no missed days. The card shows the last check and this week's and this month's totals, and every tap adds a check.",
                        fontSize = 12.sp,
                        color = paper.ink2,
                    )
                }
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
                    PaperPill(label, interval == value, { interval = value; error = null })
                }
            }
            when (interval) {
                HabitPlan.WEEKDAYS -> Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HabitEditor.DAY_NAMES.forEachIndexed { i, label ->
                        val day = i + 1
                        PaperPill(
                            label,
                            day in weekdays,
                            { weekdays = if (day in weekdays) weekdays - day else weekdays + day; error = null },
                        )
                    }
                }
                HabitPlan.WEEKLY, HabitPlan.MONTHLY -> PaperField(
                    value = times,
                    onValueChange = { times = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = if (interval == HabitPlan.WEEKLY) "Days per week" else "Days per month",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                HabitPlan.EVERY_X_DAYS -> {
                    PaperField(
                        value = every,
                        onValueChange = { every = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Every how many",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(HabitPlan.UNIT_DAYS to "days", HabitPlan.UNIT_WEEKS to "weeks", HabitPlan.UNIT_MONTHS to "months")
                            .forEach { (value, label) ->
                                PaperPill(label, everyUnit == value, { everyUnit = value })
                            }
                    }
                    PaperButton(
                        "Counting from ${from.format(START_LABEL)}",
                        {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> from = LocalDate.of(y, m + 1, d) },
                                from.year, from.monthValue - 1, from.dayOfMonth,
                            ).show()
                        },
                        style = PaperButtonStyle.Ghost,
                        small = true,
                    )
                    SwitchRow("Count from the last time I did it", fromLast) { fromLast = it }
                    if (everyUnit == HabitPlan.UNIT_MONTHS && !fromLast) {
                        SwitchRow("Same weekday each month", monthWeekday) { monthWeekday = it }
                    }
                    Text(
                        text = HabitEditor.describe(
                            HabitPlan(
                                interval = HabitPlan.EVERY_X_DAYS,
                                every = every.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                unit = everyUnit,
                                scheduleStart = from,
                                fromLastDone = fromLast,
                                monthWeekday = monthWeekday,
                            ),
                        ),
                        fontSize = 12.sp,
                        color = paper.ink2,
                    )
                }
            }

            MonoLabel("Difficulty")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Not set", 1 to "Easy", 2 to "Medium", 3 to "Hard").forEach { (value, label) ->
                    PaperPill(label, difficulty == value, { difficulty = value })
                }
            }
            Text(
                text = "Only used if \"Weigh the Today ring by difficulty\" is on in Settings, Habits.",
                fontSize = 12.sp,
                color = paper.ink2,
            )

            PaperField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = "Description (optional)",
            )

            error?.let { Text(text = it, color = paper.bad, fontSize = 13.sp) }
        }
    }
}
