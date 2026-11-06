package com.lifetracker.app.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.DefaultNutrients
import com.lifetracker.app.data.MoneyStats
import com.lifetracker.app.data.NutrientEntity
import com.lifetracker.app.data.NutrientGroup
import com.lifetracker.app.data.NutrientValues
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonBox
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.Wobble
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NutrientsViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).foodDao()

    val nutrients: StateFlow<List<NutrientEntity>> =
        dao.observeNutrients().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a nutrient. A new one (sortOrder -1) goes to the end of the list. */
    fun save(nutrient: NutrientEntity) {
        viewModelScope.launch {
            val placed = if (nutrient.sortOrder < 0) nutrient.copy(sortOrder = dao.maxNutrientOrder() + 1) else nutrient
            dao.upsertNutrient(placed)
        }
    }

    /** Deletes a nutrient you added, with the numbers you typed for it. Meals already logged keep their own copy. */
    fun delete(nutrient: NutrientEntity) {
        viewModelScope.launch {
            dao.deleteNutrient(nutrient.id)
            dao.deleteFoodNutrientsOf(nutrient.id)
        }
    }
}

/** Settings page: which vitamins and minerals the Food tab counts, their daily targets, and your own additions. */
@Composable
fun NutrientsScreen(onBack: () -> Unit, vm: NutrientsViewModel = viewModel()) {
    val p = LocalPaper.current
    val nutrients by vm.nutrients.collectAsState()
    BackHandler(onBack = onBack)
    var editing by remember { mutableStateOf<NutrientEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingsTop("Vitamins and minerals", "What the Food tab counts", onBack) }
        item {
            Text(
                text = "Each one has a daily target (the amounts printed on food labels, change them to suit you). Hide the ones you do not care about, or add your own, such as Omega 3 or Vitamin B9 in another unit. A hidden one keeps its numbers.",
                fontSize = 12.5.sp,
                color = p.ink2,
            )
        }
        for (group in listOf(NutrientGroup.VITAMIN, NutrientGroup.MINERAL, NutrientGroup.OTHER)) {
            val rows = nutrients.filter { it.group == group }
            if (rows.isEmpty()) continue
            item(key = "g$group") { PaperSection(NutrientGroup.label(group)) }
            items(rows, key = { it.id }) { n -> NutrientRow(n) { editing = n } }
        }
        item {
            PaperButtonBox(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                PaperIcon(R.drawable.ic_p_plus, size = 18.dp, tint = LocalContentColor.current)
                Text("Add a vitamin or mineral")
            }
        }
    }

    if (creating) {
        NutrientEditor(initial = null, onSave = { vm.save(it); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { n ->
        NutrientEditor(
            initial = n,
            onSave = { vm.save(it); editing = null },
            onDismiss = { editing = null },
            onDelete = if (n.builtIn) null else ({ vm.delete(n); editing = null }),
        )
    }
}

@Composable
private fun NutrientRow(n: NutrientEntity, onClick: () -> Unit) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (n.hidden) 0.6f else 1f)
            .background(p.surface, shape)
            .border(1.25.dp, p.line, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(n.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 2)
            val target = if (n.target > 0) "${MoneyStats.amountText(n.target)} ${n.unit} a day" else "No daily target"
            Text(target + if (n.hidden) " · hidden" else "", fontSize = 12.5.sp, color = p.ink2)
        }
        PaperIcon(if (n.hidden) R.drawable.ic_p_eyeoff else R.drawable.ic_p_edit, size = 18.dp, tint = p.ink2)
    }
}

private val UNITS = listOf("mg", "µg", "g", "IU")

/** Make a nutrient ([initial] is null) or change one: name, unit, group, daily target, hide it, delete it (only ones you added). */
@Composable
fun NutrientEditor(
    initial: NutrientEntity?,
    onSave: (NutrientEntity) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val p = LocalPaper.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "mg") }
    var group by remember { mutableStateOf(initial?.group ?: NutrientGroup.VITAMIN) }
    var target by remember { mutableStateOf(initial?.target?.takeIf { it > 0 }?.let(MoneyStats::amountText).orEmpty()) }
    var hidden by remember { mutableStateOf(initial?.hidden ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val standard = DefaultNutrients.list.firstOrNull { it.id == initial?.id }

    EditWindow(
        title = if (initial == null) "New vitamin or mineral" else name.ifBlank { "Nutrient" },
        sub = if (initial == null) "Something else to count" else if (initial.builtIn) "A starter nutrient" else "One you added",
        onClose = onDismiss,
        onSave = {
            val amount = target.replace(",", "").trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }
            when {
                name.isBlank() -> error = "Give it a name."
                unit.isBlank() -> error = "Give it a unit, such as mg."
                amount == null || amount < 0 -> error = "The daily target must be a number, or empty."
                else -> onSave(
                    NutrientEntity(
                        id = initial?.id ?: NutrientValues.newId(),
                        name = name.trim(),
                        unit = unit.trim(),
                        group = group,
                        target = amount,
                        hidden = hidden,
                        builtIn = initial?.builtIn ?: false,
                        sortOrder = initial?.sortOrder ?: -1,
                    ),
                )
            }
        },
    ) {
        PaperField(name, { name = it; error = null }, "Name", placeholder = "e.g. Vitamin B9", singleLine = true)
        MonoLabel("Group")
        ChipFlow {
            PaperPill("Vitamin", group == NutrientGroup.VITAMIN, { group = NutrientGroup.VITAMIN })
            PaperPill("Mineral", group == NutrientGroup.MINERAL, { group = NutrientGroup.MINERAL })
            PaperPill("Other", group == NutrientGroup.OTHER, { group = NutrientGroup.OTHER })
        }
        MonoLabel("Unit")
        ChipFlow { UNITS.forEach { u -> PaperPill(u, unit == u, { unit = u; error = null }) } }
        PaperField(unit, { unit = it.take(8); error = null }, "Unit", singleLine = true)
        PaperField(
            target, { target = it; error = null }, "Daily target ($unit), empty for none",
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (error != null) Text(error!!, color = p.bad, fontSize = 13.sp)
        if (initial != null) {
            PaperButton(if (hidden) "Show it again" else "Hide it", { hidden = !hidden }, style = PaperButtonStyle.Ghost, small = true)
            Text(
                if (hidden) "Hidden: it is not shown or counted, and keeps its numbers. Save to apply." else "Hiding keeps every number you typed.",
                fontSize = 12.sp, color = p.ink2,
            )
        }
        if (standard != null) {
            PaperButton(
                "Put the standard name, unit and target back",
                { name = standard.name; unit = standard.unit; target = MoneyStats.amountText(standard.target); group = standard.group; error = null },
                style = PaperButtonStyle.Ghost, small = true,
            )
        }
        if (onDelete != null) PaperButton("Delete this", { confirmDelete = true }, style = PaperButtonStyle.Danger, small = true)
    }

    if (confirmDelete && onDelete != null) {
        PaperDialog(
            title = "Delete ${initial?.name.orEmpty()}?",
            onDismiss = { confirmDelete = false },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = { confirmDelete = false; onDelete() },
        ) {
            Text("Its numbers on foods are removed. Meals you already logged keep theirs, but it is no longer shown.", color = p.ink)
        }
    }
}
