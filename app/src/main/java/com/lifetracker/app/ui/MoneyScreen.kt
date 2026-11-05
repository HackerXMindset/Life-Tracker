package com.lifetracker.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.KIND_EXPENSE
import com.lifetracker.app.data.KIND_INCOME
import com.lifetracker.app.data.MoneyCategoryEntity
import com.lifetracker.app.data.MoneyEntryEntity
import com.lifetracker.app.data.MoneyItemEntity
import com.lifetracker.app.data.MoneyStats
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperFab
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.Wobble
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val GREY = 0xFF738078L

private fun List<MoneyCategoryEntity>.named(id: String): MoneyCategoryEntity =
    firstOrNull { it.id == id } ?: MoneyCategoryEntity(id, "Uncategorised", GREY, KIND_EXPENSE, Int.MAX_VALUE, true)

/** What the add flow is doing right now: which kind and type were chosen. */
private data class Form(val kind: Int, val type: Int)

@Composable
fun MoneyScreen(vm: MoneyViewModel = viewModel()) {
    val paper = LocalPaper.current
    val month by vm.month.collectAsState()
    val entries by vm.entries.collectAsState()
    val categories by vm.categories.collectAsState()
    val items by vm.items.collectAsState()
    val currency by vm.currency.collectAsState()

    var chooser by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Form?>(null) }
    var editEntry by remember { mutableStateOf<MoneyEntryEntity?>(null) }
    var editItem by remember { mutableStateOf<MoneyItemEntity?>(null) }
    var editCategory by remember { mutableStateOf<MoneyCategoryEntity?>(null) }
    // Kind of a brand new category being made (from a button or from inside a window), if any.
    var newCategoryKind by remember { mutableStateOf<Int?>(null) }
    // A category made from inside an open window: that window selects it.
    var pickedCategoryId by remember { mutableStateOf<String?>(null) }
    var changeAmountFor by remember { mutableStateOf<MoneyItemEntity?>(null) }
    var entryToDelete by remember { mutableStateOf<MoneyEntryEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<MoneyItemEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<MoneyCategoryEntity?>(null) }
    var showCurrency by remember { mutableStateOf(false) }
    var sortIndex by remember { mutableStateOf(0) }
    var categoryUses by remember { mutableStateOf<Int?>(null) }

    val editedCategory = editCategory
    LaunchedEffect(editedCategory?.id) {
        categoryUses = if (editedCategory == null) null else vm.usesOf(editedCategory)
    }

    val sort = MoneyStats.Sort.entries[sortIndex]
    val totals = MoneyStats.totals(entries)
    val title = remember(month) { month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) }
    fun money(v: Double) = MoneyStats.format(v, currency)

    val activeItems = items.filter { !it.archived }
    val often = activeItems.filter { it.type == MoneyItemEntity.OFTEN }
    val monthly = activeItems.filter { it.type == MoneyItemEntity.MONTHLY }
    val stopped = items.filter { it.archived }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ScreenHeader(label = "Money", badge = "Saved on this phone", title = title) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PaperButton("‹ Earlier", { vm.shiftMonth(-1) }, style = PaperButtonStyle.Ghost, small = true)
                    PaperButton("This month", vm::goThisMonth, style = PaperButtonStyle.Ghost, small = true, modifier = Modifier.weight(1f))
                    PaperButton("Later ›", { vm.shiftMonth(1) }, style = PaperButtonStyle.Ghost, small = true)
                }
            }
            item {
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MoneyStat("In", money(totals.income), Modifier.weight(1f))
                        MoneyStat("Out", money(totals.spent), Modifier.weight(1f))
                        MoneyStat("Left", money(totals.net), Modifier.weight(1f))
                    }
                    if (totals.paidByOthers > 0) {
                        Text(
                            "Paid by others: ${money(totals.paidByOthers)}",
                            fontSize = 13.sp, color = paper.ink2, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (totals.toDecide > 0) {
                        Text(
                            "To decide: ${money(totals.toDecide)}",
                            fontSize = 13.sp, color = paper.ink2, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            if (often.isNotEmpty()) {
                item { MonoLabel("Quick log") }
                item {
                    ChipFlow {
                        often.forEach { item ->
                            QuickChip(
                                text = (if (item.kind == KIND_INCOME) "+" else "") + item.name + " " + money(item.amount),
                                dot = argbColor(categories.named(item.categoryId).color),
                                onClick = { vm.logItem(item) },
                                onLongClick = { changeAmountFor = item },
                            )
                        }
                    }
                }
                item { Text("Tap to log it for today. Press and hold to change the amount first.", fontSize = 12.sp, color = paper.ink2) }
            }

            if (entries.isEmpty()) {
                item {
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Nothing in $title", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                        Text(
                            "Tap Add to record an expense or income. It asks what kind it is first, so rent, a lassi and a one-off buy each get the right treatment.",
                            color = paper.ink2, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                spendingBlock("Where it went", KIND_EXPENSE, entries, categories, ::money)
                spendingBlock("Where it came from", KIND_INCOME, entries, categories, ::money)

                item { MonoLabel("Entries") }
                item { PaperSegmented(MoneyStats.Sort.entries.map { it.label }, sortIndex, { sortIndex = it }) }
                if (sort == MoneyStats.Sort.Biggest) {
                    items(MoneyStats.sorted(entries, sort), key = { "e" + it.id }) { entry ->
                        EntryRow(entry, categories.named(entry.categoryId), ::money, showDate = true) { editEntry = entry }
                    }
                } else {
                    val sorted = MoneyStats.sorted(entries, sort)
                    sorted.groupBy { it.date }.forEach { (date, dayEntries) ->
                        item(key = "d$date") { MonoLabel(dayLabel(date)) }
                        items(dayEntries, key = { "e" + it.id }) { entry ->
                            EntryRow(entry, categories.named(entry.categoryId), ::money, showDate = false) { editEntry = entry }
                        }
                    }
                }
                item { Text("Tap an entry to change or delete it.", fontSize = 12.sp, color = paper.ink2) }
            }

            if (activeItems.isNotEmpty() || stopped.isNotEmpty()) {
                item { MonoLabel("Saved items") }
                items(monthly, key = { "m" + it.id }) { SavedItemRow(it, categories.named(it.categoryId), ::money) { editItem = it } }
                items(often, key = { "o" + it.id }) { SavedItemRow(it, categories.named(it.categoryId), ::money) { editItem = it } }
                if (stopped.isNotEmpty()) {
                    item { MonoLabel("Stopped or hidden") }
                    items(stopped, key = { "s" + it.id }) { SavedItemRow(it, categories.named(it.categoryId), ::money) { editItem = it } }
                }
            }

            item { MonoLabel("Spending categories") }
            item { CategoryChips(KIND_EXPENSE, categories, onEdit = { editCategory = it }, onNew = { newCategoryKind = KIND_EXPENSE }) }
            item { MonoLabel("Income categories") }
            item { CategoryChips(KIND_INCOME, categories, onEdit = { editCategory = it }, onNew = { newCategoryKind = KIND_INCOME }) }
            item {
                PaperButton("Currency: $currency (change)", { showCurrency = true }, style = PaperButtonStyle.Ghost, small = true)
            }
        }

        PaperFab(
            text = "Add",
            icon = R.drawable.ic_p_plus,
            onClick = { chooser = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (chooser) {
        AddMoneyChooser(
            onDismiss = { chooser = false },
            onChosen = { kind, type ->
                chooser = false
                pickedCategoryId = null
                form = Form(kind, type)
            },
        )
    }

    form?.let { f ->
        val close = { form = null; pickedCategoryId = null }
        if (f.type == ADD_ONCE) {
            MoneyEntryWindow(
                initial = null,
                kind = f.kind,
                categories = categories,
                currency = currency,
                onNewCategory = { newCategoryKind = f.kind },
                onSave = { vm.addEntry(it); close() },
                onDismiss = close,
                pickedCategoryId = pickedCategoryId,
            )
        } else {
            MoneyItemWindow(
                initial = null,
                kind = f.kind,
                type = if (f.type == ADD_MONTHLY) MoneyItemEntity.MONTHLY else MoneyItemEntity.OFTEN,
                categories = categories,
                currency = currency,
                onNewCategory = { newCategoryKind = f.kind },
                onSave = { item, logNow -> vm.saveItem(item, logNow); close() },
                onDismiss = close,
                pickedCategoryId = pickedCategoryId,
            )
        }
    }

    editEntry?.let { entry ->
        val close = { editEntry = null; pickedCategoryId = null }
        MoneyEntryWindow(
            initial = entry,
            kind = entry.kind,
            categories = categories,
            currency = currency,
            onNewCategory = { newCategoryKind = entry.kind },
            onSave = { vm.addEntry(it); close() },
            onDismiss = close,
            onDelete = { entryToDelete = entry },
            pickedCategoryId = pickedCategoryId,
        )
    }

    editItem?.let { item ->
        val close = { editItem = null; pickedCategoryId = null }
        MoneyItemWindow(
            initial = item,
            kind = item.kind,
            type = item.type,
            categories = categories,
            currency = currency,
            onNewCategory = { newCategoryKind = item.kind },
            onSave = { changed, _ -> vm.saveItem(changed, false); close() },
            onDismiss = close,
            onToggleArchived = { vm.setItemArchived(item, !item.archived); close() },
            onDelete = { itemToDelete = item },
            pickedCategoryId = pickedCategoryId,
        )
    }

    newCategoryKind?.let { kind ->
        MoneyCategoryWindow(
            initial = null,
            kind = kind,
            onSave = { made ->
                vm.saveCategory(made) { saved -> pickedCategoryId = saved.id }
                newCategoryKind = null
            },
            onDismiss = { newCategoryKind = null },
        )
    }

    editCategory?.let { category ->
        MoneyCategoryWindow(
            initial = category,
            kind = category.kind,
            onSave = { vm.saveCategory(it); editCategory = null },
            onDismiss = { editCategory = null },
            onToggleArchived = { vm.saveCategory(category.copy(archived = !category.archived)); editCategory = null },
            uses = categoryUses,
            onDelete = { categoryToDelete = category },
        )
    }

    changeAmountFor?.let { item ->
        LogAmountDialog(
            item = item,
            currency = currency,
            onLog = { vm.logItem(item, it); changeAmountFor = null },
            onDismiss = { changeAmountFor = null },
        )
    }

    entryToDelete?.let { entry ->
        PaperDialog(
            title = "Delete this entry?",
            onDismiss = { entryToDelete = null },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = { vm.deleteEntry(entry); entryToDelete = null; editEntry = null; pickedCategoryId = null },
        ) {
            Text(entry.name + " · " + money(entry.amount), color = paper.ink)
        }
    }

    itemToDelete?.let { item ->
        PaperDialog(
            title = "Delete this saved item?",
            onDismiss = { itemToDelete = null },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = { vm.deleteItem(item); itemToDelete = null; editItem = null; pickedCategoryId = null },
        ) {
            Text(item.name + " · " + money(item.amount), color = paper.ink)
            Text("The entries it already made stay.", fontSize = 12.sp, color = paper.ink2)
        }
    }

    categoryToDelete?.let { category ->
        PaperDialog(
            title = "Delete this category?",
            onDismiss = { categoryToDelete = null },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = { vm.deleteCategory(category); categoryToDelete = null; editCategory = null },
        ) {
            Text(category.name, color = paper.ink)
        }
    }

    if (showCurrency) {
        CurrencyDialog(
            current = currency,
            onSave = { vm.setCurrency(it); showCurrency = false },
            onDismiss = { showCurrency = false },
        )
    }
}

private fun dayLabel(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("EEE d MMM")) }.getOrDefault(isoDate)

/** A heading and one bar per category, for spending or for income. Adds nothing if there is none. */
private fun LazyListScope.spendingBlock(
    heading: String,
    kind: Int,
    entries: List<MoneyEntryEntity>,
    categories: List<MoneyCategoryEntity>,
    format: (Double) -> String,
) {
    val rows = MoneyStats.byCategory(entries, kind)
    if (rows.isEmpty()) return
    val biggest = rows.first().second
    item(key = "h$kind") { MonoLabel(heading) }
    items(rows, key = { "b$kind" + it.first }) { (categoryId, total) ->
        val paper = LocalPaper.current
        val category = categories.named(categoryId)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category.name, fontSize = 14.sp, color = paper.ink)
                Text(format(total), fontSize = 13.sp, color = paper.ink2)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(paper.surface2),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((total / biggest).toFloat().coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(argbColor(category.color)),
                )
            }
        }
    }
}

@Composable
private fun MoneyStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MonoLabel(label)
        Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LocalPaper.current.ink)
    }
}

/** A pill you can tap and also press and hold. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickChip(text: String, dot: androidx.compose.ui.graphics.Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    val paper = LocalPaper.current
    val shape = Wobble.Pill
    Row(
        modifier = Modifier
            .background(paper.surface, shape)
            .border(1.25.dp, paper.pen, shape)
            .clip(shape)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(dot, CircleShape))
        Text(text, color = paper.ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun EntryRow(
    entry: MoneyEntryEntity,
    category: MoneyCategoryEntity,
    format: (Double) -> String,
    showDate: Boolean,
    onClick: () -> Unit,
) {
    val paper = LocalPaper.current
    val income = entry.kind == KIND_INCOME
    val tint = argbColor(category.color)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconGlyph(icon = category.icon, tint = tint, size = 26.dp, fallback = category.name)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                val detail = listOfNotNull(
                    if (showDate) dayLabel(entry.date) else null,
                    category.name,
                    if (entry.kind == KIND_EXPENSE) MoneyStats.sourceNote(entry.source, entry.paidBy) else null,
                    if (entry.id.startsWith("auto|")) "added automatically" else null,
                    entry.note.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(detail, fontSize = 12.sp, color = paper.ink2)
            }
            Text(
                text = (if (income) "+" else "-") + format(entry.amount),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (income) paper.accent else paper.ink,
            )
        }
    }
}

@Composable
private fun SavedItemRow(
    item: MoneyItemEntity,
    category: MoneyCategoryEntity,
    format: (Double) -> String,
    onClick: () -> Unit,
) {
    val paper = LocalPaper.current
    val detail = if (item.type == MoneyItemEntity.MONTHLY) {
        val until = runCatching { LocalDate.parse(item.endDate) }.getOrNull()
            ?.let { " · until " + it.format(DateTimeFormatter.ofPattern("MMM yyyy")) }.orEmpty()
        "Every month on the ${MoneyStats.ordinal(item.chargeDay)}$until"
    } else {
        "Log again and again"
    }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconGlyph(icon = category.icon, tint = argbColor(category.color), size = 26.dp, fallback = category.name)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                val source = if (item.kind == KIND_EXPENSE) MoneyStats.sourceNote(item.source, item.paidBy) else null
                Text(
                    listOfNotNull(if (item.kind == KIND_INCOME) "Income" else "Expense", category.name, detail, source).joinToString(" · "),
                    fontSize = 12.sp, color = paper.ink2,
                )
            }
            Text(format(item.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = paper.ink)
        }
    }
}

@Composable
private fun CategoryChips(
    kind: Int,
    categories: List<MoneyCategoryEntity>,
    onEdit: (MoneyCategoryEntity) -> Unit,
    onNew: () -> Unit,
) {
    ChipFlow {
        categories.filter { it.kind == kind }.forEach { c ->
            PaperPill(if (c.archived) c.name + " (hidden)" else c.name, false, { onEdit(c) }, dot = argbColor(c.color))
        }
        PaperPill("+ New", false, onNew)
    }
}
