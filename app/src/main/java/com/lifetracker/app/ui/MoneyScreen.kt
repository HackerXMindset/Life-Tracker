package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val GREY = 0xFF738078L

private fun List<MoneyCategoryEntity>.named(id: String): MoneyCategoryEntity =
    firstOrNull { it.id == id } ?: MoneyCategoryEntity(id, "Uncategorised", GREY, KIND_EXPENSE, Int.MAX_VALUE, true)

/** What the add flow is doing right now: which kind and type were chosen, and which category was just made. */
private data class Form(val kind: Int, val type: Int, val pickedCategoryId: String? = null)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoneyScreen(vm: MoneyViewModel = viewModel()) {
    val month by vm.month.collectAsState()
    val entries by vm.entries.collectAsState()
    val categories by vm.categories.collectAsState()
    val items by vm.items.collectAsState()
    val currency by vm.currency.collectAsState()

    var chooser by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Form?>(null) }
    var editItem by remember { mutableStateOf<MoneyItemEntity?>(null) }
    var editCategory by remember { mutableStateOf<MoneyCategoryEntity?>(null) }
    // Kind of a brand new category being made (from a button or from inside a form), if any.
    var newCategoryKind by remember { mutableStateOf<Int?>(null) }
    var changeAmountFor by remember { mutableStateOf<MoneyItemEntity?>(null) }
    var toDelete by remember { mutableStateOf<MoneyEntryEntity?>(null) }
    var showCurrency by remember { mutableStateOf(false) }

    val totals = MoneyStats.totals(entries)
    val title = remember(month) { month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) }
    val byDay = entries.groupBy { it.date }
    fun money(v: Double) = MoneyStats.format(v, currency)

    val activeItems = items.filter { !it.archived }
    val often = activeItems.filter { it.type == MoneyItemEntity.OFTEN }
    val monthly = activeItems.filter { it.type == MoneyItemEntity.MONTHLY }
    val stopped = items.filter { it.archived }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenHeader(label = "Money", badge = "Saved on this phone", title = title)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { vm.shiftMonth(-1) }) { Text("Previous") }
                    TextButton(onClick = vm::goThisMonth) { Text("This month") }
                    TextButton(onClick = { vm.shiftMonth(1) }) { Text("Next") }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoneyStat("In", money(totals.income), Modifier.weight(1f))
                    MoneyStat("Out", money(totals.spent), Modifier.weight(1f))
                    MoneyStat("Left", money(totals.net), Modifier.weight(1f))
                }
            }

            if (often.isNotEmpty()) {
                item { MonoLabel("Quick log") }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        often.forEach { item ->
                            val category = categories.named(item.categoryId)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .combinedClickable(
                                        onClick = { vm.logItem(item) },
                                        onLongClick = { changeAmountFor = item },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(argbColor(category.color)))
                                    Text(
                                        text = (if (item.kind == KIND_INCOME) "+" else "") + item.name + " " + money(item.amount),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "Tap to log it for today. Press and hold to change the amount first.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entries.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Nothing in $title",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Tap Add to record an expense or income. It asks what kind it is first, so rent, a lassi and a one-off buy each get the right treatment.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                spendingBlock(
                    heading = "Where it went",
                    kind = KIND_EXPENSE,
                    entries = entries,
                    categories = categories,
                    format = ::money,
                )
                spendingBlock(
                    heading = "Where it came from",
                    kind = KIND_INCOME,
                    entries = entries,
                    categories = categories,
                    format = ::money,
                )
                byDay.forEach { (date, dayEntries) ->
                    item(key = "d$date") { MonoLabel(dayLabel(date)) }
                    items(dayEntries, key = { "e" + it.id }) { entry ->
                        EntryRow(entry, categories.named(entry.categoryId), ::money, onClick = { toDelete = entry })
                    }
                }
                item {
                    Text(
                        text = "Tap an entry to delete it.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            item {
                CategoryChips(KIND_EXPENSE, categories, onEdit = { editCategory = it }, onNew = { newCategoryKind = KIND_EXPENSE })
            }
            item { MonoLabel("Income categories") }
            item {
                CategoryChips(KIND_INCOME, categories, onEdit = { editCategory = it }, onNew = { newCategoryKind = KIND_INCOME })
            }
            item {
                TextButton(onClick = { showCurrency = true }) { Text("Currency: $currency (change)") }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { chooser = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text("Add") },
        )
    }

    if (chooser) {
        AddMoneyChooser(
            onDismiss = { chooser = false },
            onChosen = { kind, type ->
                chooser = false
                form = Form(kind, type)
            },
        )
    }

    form?.let { f ->
        val onNewCategory = { newCategoryKind = f.kind }
        if (f.type == ADD_ONCE) {
            MoneyEntryDialog(
                kind = f.kind,
                categories = categories,
                currency = currency,
                onNewCategory = onNewCategory,
                onSave = { vm.addEntry(it); form = null },
                onDismiss = { form = null },
                pickedCategoryId = f.pickedCategoryId,
            )
        } else {
            MoneyItemDialog(
                initial = null,
                kind = f.kind,
                type = if (f.type == ADD_MONTHLY) MoneyItemEntity.MONTHLY else MoneyItemEntity.OFTEN,
                categories = categories,
                currency = currency,
                onNewCategory = onNewCategory,
                onSave = { item, logNow -> vm.saveItem(item, logNow); form = null },
                onDismiss = { form = null },
                pickedCategoryId = f.pickedCategoryId,
            )
        }
    }

    editItem?.let { item ->
        MoneyItemDialog(
            initial = item,
            kind = item.kind,
            type = item.type,
            categories = categories,
            currency = currency,
            onNewCategory = { newCategoryKind = item.kind },
            onSave = { changed, _ -> vm.saveItem(changed, false); editItem = null },
            onDismiss = { editItem = null },
            onToggleArchived = { vm.setItemArchived(item, !item.archived); editItem = null },
        )
    }

    newCategoryKind?.let { kind ->
        MoneyCategoryDialog(
            initial = null,
            kind = kind,
            onSave = { made ->
                vm.saveCategory(made) { saved ->
                    // If a form is open, select the new category in it.
                    form = form?.copy(pickedCategoryId = saved.id)
                }
                newCategoryKind = null
            },
            onDismiss = { newCategoryKind = null },
        )
    }

    editCategory?.let { category ->
        MoneyCategoryDialog(
            initial = category,
            kind = category.kind,
            onSave = { vm.saveCategory(it); editCategory = null },
            onDismiss = { editCategory = null },
            onToggleArchived = { vm.saveCategory(category.copy(archived = !category.archived)); editCategory = null },
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

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text(entry.name + " · " + money(entry.amount)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteEntry(entry); toDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Keep") } },
        )
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
private fun androidx.compose.foundation.lazy.LazyListScope.spendingBlock(
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
        val category = categories.named(categoryId)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    format(total),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
        Text(
            text = value,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun EntryRow(
    entry: MoneyEntryEntity,
    category: MoneyCategoryEntity,
    format: (Double) -> String,
    onClick: () -> Unit,
) {
    val income = entry.kind == KIND_INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(argbColor(category.color)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = entry.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val detail = listOfNotNull(
                category.name,
                if (entry.id.startsWith("auto|")) "added automatically" else null,
                entry.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(text = detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = (if (income) "+" else "-") + format(entry.amount),
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            color = if (income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SavedItemRow(
    item: MoneyItemEntity,
    category: MoneyCategoryEntity,
    format: (Double) -> String,
    onClick: () -> Unit,
) {
    val detail = if (item.type == MoneyItemEntity.MONTHLY) {
        val until = runCatching { LocalDate.parse(item.endDate) }.getOrNull()
            ?.let { " · until " + it.format(DateTimeFormatter.ofPattern("MMM yyyy")) }.orEmpty()
        "Every month on the ${MoneyStats.ordinal(item.chargeDay)}$until"
    } else {
        "Log again and again"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(argbColor(category.color)))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = (if (item.kind == KIND_INCOME) "Income · " else "Expense · ") + category.name + " · " + detail,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = format(item.amount),
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.filter { it.kind == kind }.forEach { c ->
            FilterChip(
                selected = false,
                onClick = { onEdit(c) },
                label = { Text(if (c.archived) c.name + " (hidden)" else c.name) },
                leadingIcon = {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(argbColor(c.color)))
                },
            )
        }
        FilterChip(selected = false, onClick = onNew, label = { Text("+ New") })
    }
}
