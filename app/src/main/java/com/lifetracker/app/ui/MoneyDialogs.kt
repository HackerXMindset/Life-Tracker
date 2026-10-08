package com.lifetracker.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.KIND_EXPENSE
import com.lifetracker.app.data.KIND_INCOME
import com.lifetracker.app.data.MoneyCategoryEntity
import com.lifetracker.app.data.MoneyEntryEntity
import com.lifetracker.app.data.MoneyItemEntity
import com.lifetracker.app.data.MoneyStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The three kinds of thing you can add. */
const val ADD_MONTHLY = 0
const val ADD_OFTEN = 1
const val ADD_ONCE = 2

private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun parseDateOrNull(text: String): LocalDate? =
    if (text.isBlank()) null else runCatching { LocalDate.parse(text) }.getOrNull()

private fun parseAmount(text: String): Double? =
    text.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0 && it < 1e12 }

/** Android's date picker, as a function. */
@Composable
private fun rememberDatePicker(): (LocalDate, (LocalDate) -> Unit) -> Unit {
    val context = LocalContext.current
    return { initial, onPicked ->
        DatePickerDialog(
            context,
            { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
        ).show()
    }
}

@Composable
private fun ColorSwatches(selected: Long, onSelect: (Long) -> Unit) {
    ACTIVITY_PALETTE.chunked(8).forEach { rowColors ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowColors.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(c.toInt()))
                        .then(
                            if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            else Modifier,
                        )
                        .clickable { onSelect(c) },
                )
            }
        }
    }
}

/** Category chips for one kind, with "+ New" at the end. */
@Composable
private fun CategoryPicker(
    kind: Int,
    categories: List<MoneyCategoryEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
) {
    MonoLabel("Category")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.filter { it.kind == kind && !it.archived }.forEach { c ->
            FilterChip(
                selected = c.id == selectedId,
                onClick = { onSelect(c.id) },
                label = { Text(c.name) },
                leadingIcon = {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(argbColor(c.color)))
                },
            )
        }
        FilterChip(selected = false, onClick = onNew, label = { Text("+ New") })
    }
}

/** Two quick questions, then the form: first Expense or Income, then what kind of thing it is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMoneyChooser(onDismiss: () -> Unit, onChosen: (kind: Int, type: Int) -> Unit) {
    var kind by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val chosen = kind
            if (chosen == null) {
                Text(
                    text = "Add money",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text("Is it money going out or coming in?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { kind = KIND_EXPENSE }, modifier = Modifier.weight(1f)) { Text("Expense") }
                    Button(onClick = { kind = KIND_INCOME }, modifier = Modifier.weight(1f)) { Text("Income") }
                }
            } else {
                val word = if (chosen == KIND_EXPENSE) "expense" else "income"
                Text(
                    text = "What kind of $word?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                KindCard(
                    title = "Every month",
                    example = if (chosen == KIND_EXPENSE) "Rent, a subscription, an EMI. Added for you on its day each month."
                    else "Salary, pocket money, a regular fee. Added for you on its day each month.",
                    onClick = { onChosen(chosen, ADD_MONTHLY) },
                )
                KindCard(
                    title = "Again and again",
                    example = if (chosen == KIND_EXPENSE) "A lassi, chai, a bus ticket. Save it once, then log it in one tap."
                    else "Something small that comes often. Save it once, then log it in one tap.",
                    onClick = { onChosen(chosen, ADD_OFTEN) },
                )
                KindCard(
                    title = "Just once",
                    example = if (chosen == KIND_EXPENSE) "A book, a repair, a gift." else "A gift, a prize, a one-time payment.",
                    onClick = { onChosen(chosen, ADD_ONCE) },
                )
                TextButton(onClick = { kind = null }) { Text("Back") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun KindCard(title: String, example: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(example, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A one-off amount: what, how much, when, which category. */
@Composable
fun MoneyEntryDialog(
    kind: Int,
    categories: List<MoneyCategoryEntity>,
    currency: String,
    onNewCategory: () -> Unit,
    onSave: (MoneyEntryEntity) -> Unit,
    onDismiss: () -> Unit,
    pickedCategoryId: String? = null,
) {
    val pickDate = rememberDatePicker()
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // A category made from here is selected as soon as it exists.
    val effective = pickedCategoryId?.takeIf { categories.any { c -> c.id == it } && categoryId == null }
        ?: categoryId
        ?: categories.firstOrNull { it.kind == kind && !it.archived }?.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == KIND_EXPENSE) "Expense, just once" else "Income, just once") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (kind == KIND_EXPENSE) "What was it?" else "Where from?") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                MonoLabel("Date")
                OutlinedButton(onClick = { pickDate(date) { date = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text(date.format(DATE_LABEL), fontFamily = FontFamily.Monospace)
                }
                CategoryPicker(kind, categories, effective, onSelect = { categoryId = it }, onNew = onNewCategory)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note (optional)") },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = parseAmount(amount)
                val category = categories.firstOrNull { it.id == effective }
                when {
                    value == null -> error = "Enter an amount above zero."
                    category == null -> error = "Pick a category, or make a new one."
                    else -> onSave(
                        MoneyEntryEntity(
                            id = MoneyStats.newId(),
                            date = date.toString(),
                            kind = kind,
                            categoryId = category.id,
                            name = name.trim().ifEmpty { category.name },
                            amount = value,
                            note = note.trim(),
                            itemId = "",
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A saved item, new or edited. [type] is [MoneyItemEntity.MONTHLY] or [MoneyItemEntity.OFTEN].
 * For a new one the returned entity has `sortOrder = -1`, and `logNow` says whether to also
 * log it once straight away (often items only).
 */
@Composable
fun MoneyItemDialog(
    initial: MoneyItemEntity?,
    kind: Int,
    type: Int,
    categories: List<MoneyCategoryEntity>,
    currency: String,
    onNewCategory: () -> Unit,
    onSave: (item: MoneyItemEntity, logNow: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onToggleArchived: (() -> Unit)? = null,
    pickedCategoryId: String? = null,
) {
    val pickDate = rememberDatePicker()
    val monthly = type == MoneyItemEntity.MONTHLY
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var amount by remember {
        mutableStateOf(initial?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var day by remember { mutableStateOf(initial?.chargeDay?.takeIf { it > 0 }?.toString() ?: LocalDate.now().dayOfMonth.toString()) }
    var start by remember { mutableStateOf(parseDateOrNull(initial?.startDate.orEmpty()) ?: LocalDate.now()) }
    var end by remember { mutableStateOf(parseDateOrNull(initial?.endDate.orEmpty())) }
    var logNow by remember { mutableStateOf(true) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var error by remember { mutableStateOf<String?>(null) }

    val effective = pickedCategoryId?.takeIf { categories.any { c -> c.id == it } && categoryId == null }
        ?: categoryId
        ?: categories.firstOrNull { it.kind == kind && !it.archived }?.id

    val word = if (kind == KIND_EXPENSE) "expense" else "income"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial != null -> "Edit saved $word"
                    monthly -> "New monthly $word"
                    else -> "New $word to log again and again"
                },
            )
        },
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
                    placeholder = { Text(if (monthly) "e.g. Rent" else "e.g. Lassi") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                CategoryPicker(kind, categories, effective, onSelect = { categoryId = it }, onNew = onNewCategory)

                if (monthly) {
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it.filter(Char::isDigit).take(2); error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Day of the month it is charged (1 to 31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    MonoLabel("Starts")
                    OutlinedButton(onClick = { pickDate(start) { start = it } }, modifier = Modifier.fillMaxWidth()) {
                        Text(start.format(DATE_LABEL), fontFamily = FontFamily.Monospace)
                    }
                    MonoLabel("Ends (optional)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { pickDate(end ?: start.plusMonths(12)) { end = it } },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(end?.format(DATE_LABEL) ?: "No end date", fontFamily = FontFamily.Monospace)
                        }
                        if (end != null) TextButton(onClick = { end = null }) { Text("Clear") }
                    }
                    Text(
                        text = "The app adds it on this day every month, from the start date. If a month is shorter, it is added on the last day. Changing the amount only affects future months.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "It will appear as a button on the Money tab. Tap it to log it for today; press and hold to change the amount first.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (initial == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = logNow, onCheckedChange = { logNow = it })
                            Text("Log it once now", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

                if (onToggleArchived != null && initial != null) {
                    TextButton(onClick = onToggleArchived) {
                        Text(
                            if (initial.archived) "Bring it back"
                            else if (monthly) "Stop this and hide it"
                            else "Hide it",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = parseAmount(amount)
                val dayNumber = day.toIntOrNull()
                val category = categories.firstOrNull { it.id == effective }
                val endDate = end
                when {
                    name.isBlank() -> error = "Give it a name."
                    value == null -> error = "Enter an amount above zero."
                    category == null -> error = "Pick a category, or make a new one."
                    monthly && (dayNumber == null || dayNumber !in 1..31) -> error = "The day must be between 1 and 31."
                    monthly && endDate != null && endDate.isBefore(start) -> error = "The end date is before the start date."
                    else -> onSave(
                        MoneyItemEntity(
                            id = initial?.id ?: MoneyStats.newId(),
                            name = name.trim(),
                            kind = kind,
                            categoryId = category.id,
                            amount = value,
                            type = type,
                            chargeDay = if (monthly) dayNumber ?: 1 else 0,
                            startDate = if (monthly) start.toString() else "",
                            endDate = if (monthly) endDate?.toString().orEmpty() else "",
                            lastPosted = initial?.lastPosted.orEmpty(),
                            archived = initial?.archived ?: false,
                            sortOrder = initial?.sortOrder ?: -1,
                        ),
                        logNow && initial == null && !monthly,
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Name and colour for a spending or income category. */
@Composable
fun MoneyCategoryDialog(
    initial: MoneyCategoryEntity?,
    kind: Int,
    onSave: (MoneyCategoryEntity) -> Unit,
    onDismiss: () -> Unit,
    onToggleArchived: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(initial?.color ?: ACTIVITY_PALETTE.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val word = if (kind == KIND_EXPENSE) "spending" else "income"
            Text(if (initial == null) "New $word category" else "Edit category")
        },
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
                    singleLine = true,
                )
                MonoLabel("Colour")
                ColorSwatches(color) { color = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (onToggleArchived != null && initial != null) {
                    TextButton(onClick = onToggleArchived) {
                        Text(if (initial.archived) "Show it again" else "Hide it")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Give it a name."
                } else {
                    onSave(
                        MoneyCategoryEntity(
                            id = initial?.id ?: "c-" + MoneyStats.newId().take(8),
                            name = name.trim(),
                            color = color,
                            kind = kind,
                            sortOrder = initial?.sortOrder ?: -1,
                            archived = initial?.archived ?: false,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Change the amount for one log of an often item. */
@Composable
fun LogAmountDialog(
    item: MoneyItemEntity,
    currency: String,
    onLog: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember {
        mutableStateOf(if (item.amount % 1.0 == 0.0) item.amount.toLong().toString() else item.amount.toString())
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text(
                    "Only this one entry uses this amount. The saved amount stays as it is.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = parseAmount(amount)
                if (value == null) error = "Enter an amount above zero." else onLog(value)
            }) { Text("Log it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Choose the currency symbol shown next to amounts. */
@Composable
fun CurrencyDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("₹", "$", "€", "£").forEach { symbol ->
                        FilterChip(selected = text == symbol, onClick = { text = symbol }, label = { Text(symbol) })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Symbol or code") },
                    singleLine = true,
                )
                Text(
                    "This only changes how amounts are written. It does not convert anything.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
