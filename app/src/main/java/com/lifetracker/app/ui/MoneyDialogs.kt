package com.lifetracker.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.lifetracker.app.data.SOURCE_MINE
import com.lifetracker.app.data.SOURCE_OTHER
import com.lifetracker.app.data.SOURCE_UNSURE
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The three kinds of thing you can add. */
const val ADD_MONTHLY = 0
const val ADD_OFTEN = 1
const val ADD_ONCE = 2

private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun parseDateOrNull(text: String): LocalDate? =
    if (text.isBlank()) null else runCatching { LocalDate.parse(text) }.getOrNull()

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

/** Category choices for one kind, with "+ New" at the end. A hidden category stays in the list while it is the chosen one. */
@Composable
private fun CategoryPicker(
    kind: Int,
    categories: List<MoneyCategoryEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
) {
    MonoLabel("Category")
    ChipFlow(maxLines = 4) {
        categories.filter { it.kind == kind && (!it.archived || it.id == selectedId) }.forEach { c ->
            PaperPill(c.name, c.id == selectedId, { onSelect(c.id) }, dot = argbColor(c.color))
        }
        PaperPill("+ New", false, onNew)
    }
}

/** "Deduct it from": whose money an expense came out of. Only expenses ask. */
@Composable
private fun DeductPicker(source: Int, paidBy: String, onSource: (Int) -> Unit, onPaidBy: (String) -> Unit) {
    val paper = LocalPaper.current
    MonoLabel("Deduct it from")
    ChipFlow {
        listOf(SOURCE_MINE, SOURCE_OTHER, SOURCE_UNSURE).forEach { s ->
            PaperPill(MoneyStats.sourceLabel(s), source == s, { onSource(s) })
        }
    }
    if (source == SOURCE_OTHER) {
        PaperField(paidBy, onPaidBy, "Who paid? (optional)", singleLine = true)
    }
    Text(MoneyStats.sourceHelp(source), fontSize = 12.sp, color = paper.ink2)
}

@Composable
private fun ErrorLine(text: String?) {
    if (text != null) Text(text, color = LocalPaper.current.bad, fontSize = 13.sp)
}

/** Two quick questions, then the form: first Expense or Income, then what kind of thing it is. */
@Composable
fun AddMoneyChooser(onDismiss: () -> Unit, onChosen: (kind: Int, type: Int) -> Unit) {
    val paper = LocalPaper.current
    var kind by remember { mutableStateOf<Int?>(null) }
    PaperSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val chosen = kind
            if (chosen == null) {
                Text("Add money", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                Text("Is it money going out or coming in?", color = paper.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperButton("Expense", { kind = KIND_EXPENSE }, modifier = Modifier.weight(1f))
                    PaperButton("Income", { kind = KIND_INCOME }, modifier = Modifier.weight(1f))
                }
            } else {
                val word = if (chosen == KIND_EXPENSE) "expense" else "income"
                Text("What kind of $word?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = paper.ink)
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
                PaperButton("Back", { kind = null }, style = PaperButtonStyle.Ghost, small = true)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun KindCard(title: String, example: String, onClick: () -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false, onClick = onClick) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = paper.ink)
        Text(example, fontSize = 13.sp, color = paper.ink2)
    }
}

/**
 * One amount, new or changed: what, how much, when, which category, whose money it came out of, a note. [initial] is null for a
 * new one. [onDelete] (for one that exists) puts a Delete button at the bottom; the screen asks "are you sure" before it deletes.
 */
@Composable
fun MoneyEntryWindow(
    initial: MoneyEntryEntity?,
    kind: Int,
    categories: List<MoneyCategoryEntity>,
    currency: String,
    onNewCategory: () -> Unit,
    onSave: (MoneyEntryEntity) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    /** A category just made from this window: it becomes the chosen one. */
    pickedCategoryId: String? = null,
) {
    val pickDate = rememberDatePicker()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var amount by remember { mutableStateOf(initial?.amount?.let(MoneyStats::amountText).orEmpty()) }
    var date by remember { mutableStateOf(parseDateOrNull(initial?.date.orEmpty()) ?: LocalDate.now()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var source by remember { mutableStateOf(initial?.source ?: SOURCE_MINE) }
    var paidBy by remember { mutableStateOf(initial?.paidBy.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pickedCategoryId) {
        if (pickedCategoryId != null && categories.any { it.id == pickedCategoryId }) categoryId = pickedCategoryId
    }
    val effective = categoryId ?: categories.firstOrNull { it.kind == kind && !it.archived }?.id
    val expense = kind == KIND_EXPENSE

    EditWindow(
        title = when {
            initial != null -> if (expense) "Change this expense" else "Change this income"
            expense -> "Expense, just once"
            else -> "Income, just once"
        },
        sub = if (initial?.id?.startsWith("auto|") == true) "Added automatically from a monthly item. Changing it changes only this one." else "",
        onClose = onDismiss,
        onSave = {
            val value = MoneyStats.parseAmount(amount)
            val category = categories.firstOrNull { it.id == effective }
            when {
                value == null -> error = "Enter an amount above zero."
                category == null -> error = "Pick a category, or make a new one."
                else -> onSave(
                    MoneyEntryEntity(
                        id = initial?.id ?: MoneyStats.newId(),
                        date = date.toString(),
                        kind = kind,
                        categoryId = category.id,
                        name = name.trim().ifEmpty { category.name },
                        amount = value,
                        note = note.trim(),
                        itemId = initial?.itemId.orEmpty(),
                        source = if (expense) source else SOURCE_MINE,
                        paidBy = if (expense && source == SOURCE_OTHER) paidBy.trim() else "",
                    ),
                )
            }
        },
    ) {
        PaperField(name, { name = it }, if (expense) "What was it?" else "Where from?", singleLine = true)
        PaperField(
            amount, { amount = it; error = null }, "Amount ($currency)",
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        MonoLabel("Date")
        PaperButton(date.format(DATE_LABEL), { pickDate(date) { date = it } }, style = PaperButtonStyle.Ghost, small = true)
        CategoryPicker(kind, categories, effective, onSelect = { categoryId = it; error = null }, onNew = onNewCategory)
        if (expense) DeductPicker(source, paidBy, { source = it }, { paidBy = it })
        PaperField(note, { note = it }, "Note (optional)")
        ErrorLine(error)
        if (onDelete != null) PaperButton("Delete this", onDelete, style = PaperButtonStyle.Danger, small = true)
    }
}

/**
 * A saved item, new or changed. [type] is [MoneyItemEntity.MONTHLY] or [MoneyItemEntity.OFTEN].
 * For a new one the returned entity has `sortOrder = -1`, and `logNow` says whether to also log it once straight away (often items only).
 */
@Composable
fun MoneyItemWindow(
    initial: MoneyItemEntity?,
    kind: Int,
    type: Int,
    categories: List<MoneyCategoryEntity>,
    currency: String,
    onNewCategory: () -> Unit,
    onSave: (item: MoneyItemEntity, logNow: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onToggleArchived: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    pickedCategoryId: String? = null,
) {
    val paper = LocalPaper.current
    val pickDate = rememberDatePicker()
    val monthly = type == MoneyItemEntity.MONTHLY
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var amount by remember { mutableStateOf(initial?.amount?.let(MoneyStats::amountText).orEmpty()) }
    var day by remember { mutableStateOf(initial?.chargeDay?.takeIf { it > 0 }?.toString() ?: LocalDate.now().dayOfMonth.toString()) }
    var start by remember { mutableStateOf(parseDateOrNull(initial?.startDate.orEmpty()) ?: LocalDate.now()) }
    var end by remember { mutableStateOf(parseDateOrNull(initial?.endDate.orEmpty())) }
    var logNow by remember { mutableStateOf(true) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var source by remember { mutableStateOf(initial?.source ?: SOURCE_MINE) }
    var paidBy by remember { mutableStateOf(initial?.paidBy.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pickedCategoryId) {
        if (pickedCategoryId != null && categories.any { it.id == pickedCategoryId }) categoryId = pickedCategoryId
    }
    val effective = categoryId ?: categories.firstOrNull { it.kind == kind && !it.archived }?.id
    val expense = kind == KIND_EXPENSE
    val word = if (expense) "expense" else "income"

    EditWindow(
        title = when {
            initial != null -> "Change saved $word"
            monthly -> "New monthly $word"
            else -> "New $word to log again and again"
        },
        sub = if (monthly) "Added for you on its day every month" else "A button on the Money tab: tap it to log it",
        onClose = onDismiss,
        onSave = {
            val value = MoneyStats.parseAmount(amount)
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
                        source = if (expense) source else SOURCE_MINE,
                        paidBy = if (expense && source == SOURCE_OTHER) paidBy.trim() else "",
                    ),
                    logNow && initial == null && !monthly,
                )
            }
        },
    ) {
        PaperField(
            name, { name = it; error = null }, "Name",
            placeholder = if (monthly) "e.g. Rent" else "e.g. Lassi", singleLine = true,
        )
        PaperField(
            amount, { amount = it; error = null }, "Amount ($currency)",
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        CategoryPicker(kind, categories, effective, onSelect = { categoryId = it; error = null }, onNew = onNewCategory)
        if (expense) DeductPicker(source, paidBy, { source = it }, { paidBy = it })

        if (monthly) {
            PaperField(
                day, { day = it.filter(Char::isDigit).take(2); error = null }, "Day of the month it is charged (1 to 31)",
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            MonoLabel("Starts")
            PaperButton(start.format(DATE_LABEL), { pickDate(start) { start = it } }, style = PaperButtonStyle.Ghost, small = true)
            MonoLabel("Ends (optional)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PaperButton(
                    end?.format(DATE_LABEL) ?: "No end date",
                    { pickDate(end ?: start.plusMonths(12)) { end = it } },
                    style = PaperButtonStyle.Ghost, small = true,
                )
                if (end != null) PaperButton("Clear", { end = null }, style = PaperButtonStyle.Ghost, small = true)
            }
            Text(
                "The app adds it on this day every month, from the start date. If a month is shorter, it is added on the last day. Changing the amount only affects future months.",
                fontSize = 12.sp, color = paper.ink2,
            )
        } else if (initial == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperPill("Log it once now", logNow, { logNow = !logNow })
            }
            Text("Press and hold its button on the Money tab to change the amount before logging.", fontSize = 12.sp, color = paper.ink2)
        }
        ErrorLine(error)
        if (onToggleArchived != null && initial != null) {
            PaperButton(
                if (initial.archived) "Bring it back" else if (monthly) "Stop this and hide it" else "Hide it",
                onToggleArchived, style = PaperButtonStyle.Ghost, small = true,
            )
        }
        if (onDelete != null) PaperButton("Delete this saved item", onDelete, style = PaperButtonStyle.Danger, small = true)
        if (onDelete != null) Text("The entries it already made stay.", fontSize = 12.sp, color = paper.ink2)
    }
}

/** Name, colour and icon for a spending or income category; it can be hidden, or deleted when nothing uses it. */
@Composable
fun MoneyCategoryWindow(
    initial: MoneyCategoryEntity?,
    kind: Int,
    onSave: (MoneyCategoryEntity) -> Unit,
    onDismiss: () -> Unit,
    onToggleArchived: (() -> Unit)? = null,
    /** Null when it is new; otherwise how many entries and saved items use it (0 allows Delete). */
    uses: Int? = null,
    onDelete: (() -> Unit)? = null,
) {
    val paper = LocalPaper.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(initial?.color ?: ACTIVITY_PALETTE.first()) }
    var icon by remember { mutableStateOf(initial?.icon.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val word = if (kind == KIND_EXPENSE) "spending" else "income"

    EditWindow(
        title = if (initial == null) "New $word category" else "Change category",
        sub = if (initial == null) "" else "A $word category",
        onClose = onDismiss,
        onSave = {
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
                        icon = icon,
                    ),
                )
            }
        },
    ) {
        PaperField(name, { name = it; error = null }, "Name", singleLine = true)
        MonoLabel("Colour")
        ColorPickerPanel(selected = color, onSelected = { color = it })
        MonoLabel("Icon")
        IconPickerPanel(selected = icon, tint = argbColor(color), onSelected = { icon = it })
        ErrorLine(error)
        if (onToggleArchived != null && initial != null) {
            PaperButton(if (initial.archived) "Show it again" else "Hide it", onToggleArchived, style = PaperButtonStyle.Ghost, small = true)
        }
        if (onDelete != null && uses != null) {
            if (uses == 0) {
                PaperButton("Delete this category", onDelete, style = PaperButtonStyle.Danger, small = true)
            } else {
                Text(
                    "$uses ${if (uses == 1) "entry or saved item uses" else "entries and saved items use"} it, so it can be hidden but not deleted.",
                    fontSize = 12.sp, color = paper.ink2,
                )
            }
        }
    }
}

/** Change the amount for one log of an often item. */
@Composable
fun LogAmountDialog(
    item: MoneyItemEntity,
    currency: String,
    onLog: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(MoneyStats.amountText(item.amount)) }
    var error by remember { mutableStateOf<String?>(null) }
    PaperDialog(
        title = item.name,
        onDismiss = onDismiss,
        confirmText = "Log it",
        onConfirm = {
            val value = MoneyStats.parseAmount(amount)
            if (value == null) error = "Enter an amount above zero." else onLog(value)
        },
    ) {
        PaperField(
            amount, { amount = it; error = null }, "Amount ($currency)",
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text("Only this one entry uses this amount. The saved amount stays as it is.", fontSize = 12.sp, color = LocalPaper.current.ink2)
        ErrorLine(error)
    }
}

/** Choose the currency symbol shown next to amounts. */
@Composable
fun CurrencyDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    PaperDialog(title = "Currency", onDismiss = onDismiss, confirmText = "Save", onConfirm = { onSave(text) }) {
        ChipFlow {
            listOf("₹", "$", "€", "£").forEach { symbol -> PaperPill(symbol, text == symbol, { text = symbol }) }
        }
        PaperField(text, { text = it.take(4) }, "Symbol or code", singleLine = true)
        Text("This only changes how amounts are written. It does not convert anything.", fontSize = 12.sp, color = LocalPaper.current.ink2)
    }
}
