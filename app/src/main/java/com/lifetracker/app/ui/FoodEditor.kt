package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.FoodCat
import com.lifetracker.app.data.FoodEntity
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.Loggable
import com.lifetracker.app.data.NutrientEntity
import com.lifetracker.app.data.NutrientGroup
import com.lifetracker.app.data.PortionKind
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperToggle
import androidx.compose.runtime.collectAsState

internal val DecimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** A number as short text for a text box: "12", "0.45", "1250"; empty for zero. */
internal fun foodNum(v: Double): String =
    if (v == 0.0) "" else java.math.BigDecimal(v).round(java.math.MathContext(4)).stripTrailingZeros().toPlainString()

/** What was typed as a number (a comma works as a decimal point); null when it is empty or not a number. */
internal fun foodParse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Only the numbers in [edited] that differ from [base], so the food keeps following the numbers that came with the app. */
internal fun onlyChanged(base: Map<String, Double>, edited: Map<String, Double>): Map<String, Double> =
    edited.filter { (id, v) -> v > 0 && Math.abs(v - (base[id] ?: 0.0)) > 1e-9 }

/**
 * Make one of your foods ([initial] null) or change one: name, group, numbers per 100 g, how one portion is counted,
 * cooked weight and vitamins. Foods you saved from Quick add can be changed here too.
 */
@Composable
fun FoodEditor(
    vm: FoodViewModel,
    initial: FoodEntity?,
    onDismiss: () -> Unit,
    onSaved: (Loggable) -> Unit = {},
    presetName: String = "",
) {
    val p = LocalPaper.current
    val nutrients by vm.nutrients.collectAsState()

    var name by remember { mutableStateOf(initial?.name ?: presetName) }
    var cat by remember { mutableStateOf(FoodCat.of(initial?.category.orEmpty())) }
    var liquid by remember { mutableStateOf(initial?.liquid ?: false) }
    var kcal by remember { mutableStateOf(foodNum(initial?.kcal100 ?: 0.0)) }
    var protein by remember { mutableStateOf(foodNum(initial?.protein100 ?: 0.0)) }
    var carbs by remember { mutableStateOf(foodNum(initial?.carbs100 ?: 0.0)) }
    var fat by remember { mutableStateOf(foodNum(initial?.fat100 ?: 0.0)) }
    var fiber by remember { mutableStateOf(foodNum(initial?.fiber100 ?: 0.0)) }
    var sugar by remember { mutableStateOf(foodNum(initial?.sugar100 ?: 0.0)) }
    var sodium by remember { mutableStateOf(foodNum(initial?.sodium100 ?: 0.0)) }
    var sat by remember { mutableStateOf(foodNum(initial?.sat100 ?: 0.0)) }
    var mono by remember { mutableStateOf(foodNum(initial?.mono100 ?: 0.0)) }
    var poly by remember { mutableStateOf(foodNum(initial?.poly100 ?: 0.0)) }
    var kind by remember { mutableStateOf(PortionKind.of(initial?.portionKind.orEmpty())) }
    var portionGrams by remember { mutableStateOf(foodNum(initial?.portionGrams ?: 0.0)) }
    var cooked by remember { mutableStateOf(foodNum(initial?.cookedYield ?: 0.0)) }
    var micro by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var showVitamins by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initial?.id) {
        if (initial != null) micro = vm.yourNumbers(initial.id)
    }

    val unit = if (liquid) "ml" else "g"

    EditWindow(
        title = if (initial == null) "New food" else name.ifBlank { "Food" },
        sub = "Numbers are for 100 $unit, as printed on the pack",
        onClose = onDismiss,
        onSave = {
            val k = foodParse(kcal)
            val portion = foodParse(portionGrams) ?: 0.0
            val cookedBy = foodParse(cooked) ?: 0.0
            when {
                name.isBlank() -> error = "Give the food a name."
                k == null || k < 0 -> error = "Enter the calories in 100 $unit."
                kind != null && portion <= 0 -> error = "Enter how many $unit one ${kind!!.one} is, or choose No portion."
                cookedBy != 0.0 && cookedBy < 0.2 -> error = "Cooked weight is how heavy 1 g becomes once cooked, such as 2.7 for rice."
                else -> {
                    val food = FoodEntity(
                        id = initial?.id ?: FoodLogic.newFoodId(),
                        name = name.trim(),
                        kcal100 = k,
                        protein100 = foodParse(protein) ?: 0.0,
                        carbs100 = foodParse(carbs) ?: 0.0,
                        fat100 = foodParse(fat) ?: 0.0,
                        fiber100 = foodParse(fiber) ?: 0.0,
                        sugar100 = foodParse(sugar) ?: 0.0,
                        sodium100 = foodParse(sodium) ?: 0.0,
                        sat100 = foodParse(sat) ?: 0.0,
                        mono100 = foodParse(mono) ?: 0.0,
                        poly100 = foodParse(poly) ?: 0.0,
                        animal100 = initial?.animal100 ?: 0.0,
                        amino = initial?.amino.orEmpty(),
                        liquid = liquid,
                        portionKind = kind?.key.orEmpty(),
                        portionGrams = if (kind != null) portion else 0.0,
                        category = cat?.key.orEmpty(),
                        cookedYield = cookedBy,
                        createdMs = initial?.createdMs ?: 0L,
                    )
                    vm.saveFood(food, micro, onSaved)
                    onDismiss()
                }
            }
        },
    ) {
        PaperField(name, { name = it; error = null }, "Name", placeholder = "e.g. Paneer tikka", singleLine = true)

        MonoLabel("Group (optional)")
        ChipFlow {
            PaperPill("None", cat == null, { cat = null })
            FoodCat.entries.forEach { c -> PaperPill(c.label, cat == c, { cat = c }) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("It is a drink (counted in ml)", color = p.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PaperToggle(liquid, { liquid = it })
        }

        MonoLabel("In 100 $unit")
        PaperField(kcal, { kcal = it; error = null }, "Calories (kcal)", singleLine = true, keyboardOptions = DecimalKeys)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperField(protein, { protein = it }, "Protein g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(carbs, { carbs = it }, "Carbs g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(fat, { fat = it }, "Fat g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperField(fiber, { fiber = it }, "Fibre g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(sugar, { sugar = it }, "Sugar g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(sodium, { sodium = it }, "Sodium mg", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
        }
        MonoLabel("Fat types (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperField(sat, { sat = it }, "Saturated g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(mono, { mono = it }, "Mono g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(poly, { poly = it }, "Poly g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
        }

        MonoLabel("One portion")
        ChipFlow {
            PaperPill("No portion", kind == null, { kind = null; error = null })
            PortionKind.entries.forEach { k -> PaperPill(k.one, kind == k, { kind = k; error = null }) }
        }
        if (kind != null) {
            PaperField(
                portionGrams, { portionGrams = it; error = null }, "One ${kind!!.one} weighs ($unit)",
                singleLine = true, keyboardOptions = DecimalKeys,
            )
        }

        MonoLabel("Cooked (optional)")
        PaperField(
            cooked, { cooked = it; error = null }, "Cooked weight of 1 $unit raw, e.g. 2.7 for rice",
            singleLine = true, keyboardOptions = DecimalKeys,
        )
        Text(
            "Leave it empty for food that does not change weight when cooked. With a number, the food can be logged raw or cooked.",
            fontSize = 12.sp, color = p.ink2,
        )

        MonoLabel("Vitamins and minerals")
        val typed = micro.count { it.value > 0 }
        PaperButton(
            if (typed == 0) "Type in vitamins and minerals" else "Vitamins and minerals ($typed typed)",
            { showVitamins = true }, style = PaperButtonStyle.Ghost, small = true,
        )

        if (error != null) Text(error!!, color = p.bad, fontSize = 13.sp)
        if (initial != null) PaperButton("Delete this food", { confirmDelete = true }, style = PaperButtonStyle.Danger, small = true)
    }

    if (showVitamins) {
        VitaminValuesWindow(
            nutrients = nutrients, name = name.ifBlank { "this food" }, initial = micro,
            onSave = { micro = it }, onClose = { showVitamins = false },
        )
    }

    if (confirmDelete && initial != null) {
        PaperDialog(
            title = "Delete ${initial.name}?",
            onDismiss = { confirmDelete = false },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = { confirmDelete = false; vm.deleteFood(initial); onDismiss() },
        ) {
            Text("Meals you already logged with it stay as they are.", color = p.ink)
        }
    }
}

/** What 100 g of a food holds of each vitamin, mineral or nutrient of yours. Empty boxes mean not known. */
@Composable
fun VitaminValuesWindow(
    nutrients: List<NutrientEntity>,
    name: String,
    initial: Map<String, Double>,
    onSave: (Map<String, Double>) -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPaper.current
    val shown = nutrients.filter { !it.hidden }
    val texts = remember { mutableStateMapOf<String, String>().apply { shown.forEach { n -> put(n.id, foodNum(initial[n.id] ?: 0.0)) } } }

    EditWindow(
        title = "Vitamins and minerals",
        sub = "In 100 g of $name. Leave a box empty when you do not know it.",
        onClose = onClose,
        onSave = {
            val out = LinkedHashMap<String, Double>()
            // Values for nutrients that are hidden right now are kept as they were.
            for ((id, v) in initial) if (shown.none { it.id == id }) out[id] = v
            for (n in shown) foodParse(texts[n.id].orEmpty())?.takeIf { it > 0 }?.let { out[n.id] = it }
            onSave(out)
            onClose()
        },
    ) {
        if (shown.isEmpty()) {
            Text("There is nothing to count yet. Settings, Trackers, Vitamins and minerals lets you add some.", color = p.ink2, fontSize = 13.sp)
        }
        listOf(NutrientGroup.VITAMIN, NutrientGroup.MINERAL, NutrientGroup.OTHER).forEach { group ->
            val list = shown.filter { it.group == group }
            if (list.isNotEmpty()) {
                MonoLabel(NutrientGroup.label(group))
                list.forEach { n ->
                    PaperField(
                        value = texts[n.id].orEmpty(),
                        onValueChange = { texts[n.id] = it },
                        label = "${n.name} (${n.unit})",
                        singleLine = true,
                        keyboardOptions = DecimalKeys,
                    )
                }
            }
        }
    }
}
