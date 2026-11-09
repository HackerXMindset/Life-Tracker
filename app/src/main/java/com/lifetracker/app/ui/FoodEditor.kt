package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.FoodCat
import com.lifetracker.app.data.FieldDef
import com.lifetracker.app.data.FoodEntity
import com.lifetracker.app.data.FoodFields
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

    val context = LocalContext.current
    var fieldIds by remember { mutableStateOf(FoodFields.load(context)) }
    val defs = remember(fieldIds, nutrients) { FoodFields.defsFor(fieldIds, nutrients) }

    var name by remember { mutableStateOf(initial?.name ?: presetName) }
    var cat by remember { mutableStateOf(FoodCat.of(initial?.category.orEmpty())) }
    var liquid by remember { mutableStateOf(initial?.liquid ?: false) }
    // What is typed in each fixed box, by field id. Boxes that are taken out of the form keep what the food already holds.
    val box = remember {
        mutableStateMapOf(
            "kcal" to foodNum(initial?.kcal100 ?: 0.0),
            "protein" to foodNum(initial?.protein100 ?: 0.0),
            "carbs" to foodNum(initial?.carbs100 ?: 0.0),
            "fat" to foodNum(initial?.fat100 ?: 0.0),
            "fiber" to foodNum(initial?.fiber100 ?: 0.0),
            "sugar" to foodNum(initial?.sugar100 ?: 0.0),
            "sodium" to foodNum(initial?.sodium100 ?: 0.0),
            "sat" to foodNum(initial?.sat100 ?: 0.0),
            "mono" to foodNum(initial?.mono100 ?: 0.0),
            "poly" to foodNum(initial?.poly100 ?: 0.0),
        )
    }
    var kind by remember { mutableStateOf(PortionKind.of(initial?.portionKind.orEmpty())) }
    var portionGrams by remember { mutableStateOf(foodNum(initial?.portionGrams ?: 0.0)) }
    var cooked by remember { mutableStateOf(foodNum(initial?.cookedYield ?: 0.0)) }
    var micro by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    // What is typed in the vitamin and mineral boxes of the form, by nutrient id (text, so "1." can be typed on the way to "1.5").
    val nutrientText = remember { mutableStateMapOf<String, String>() }
    var showVitamins by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(initial != null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun setMicro(values: Map<String, Double>) {
        micro = values
        nutrientText.clear()
        defs.filter { !it.fixed }.forEach { d -> nutrientText[d.id] = foodNum(values[d.id] ?: 0.0) }
    }

    LaunchedEffect(initial?.id) {
        if (initial != null) setMicro(vm.yourNumbers(initial.id))
    }
    // Fills the nutrient boxes from what is known whenever a box is added or the numbers change.
    LaunchedEffect(defs, micro) {
        defs.filter { !it.fixed }.forEach { d -> if (d.id !in nutrientText) nutrientText[d.id] = foodNum(micro[d.id] ?: 0.0) }
    }

    val unit = if (liquid) "ml" else "g"

    EditWindow(
        title = if (initial == null) "New food" else name.ifBlank { "Food" },
        sub = "Numbers are for 100 $unit, as printed on the pack",
        onClose = onDismiss,
        onSave = {
            fun num(id: String): Double = foodParse(box[id].orEmpty()) ?: 0.0
            val k = foodParse(box["kcal"].orEmpty())
            val portion = foodParse(portionGrams) ?: 0.0
            val cookedBy = foodParse(cooked) ?: 0.0
            when {
                name.isBlank() -> error = "Give the food a name."
                k == null || k < 0 -> error = "Enter the calories in 100 $unit."
                kind != null && portion <= 0 -> { more = true; error = "Enter how many $unit one ${kind!!.one} is, or choose No portion." }
                cookedBy != 0.0 && cookedBy < 0.2 -> { more = true; error = "Cooked weight is how heavy 1 g becomes once cooked, such as 2.7 for rice." }
                else -> {
                    val food = FoodEntity(
                        id = initial?.id ?: FoodLogic.newFoodId(),
                        name = name.trim(),
                        kcal100 = k,
                        protein100 = num("protein"),
                        carbs100 = num("carbs"),
                        fat100 = num("fat"),
                        fiber100 = num("fiber"),
                        sugar100 = num("sugar"),
                        sodium100 = num("sodium"),
                        sat100 = num("sat"),
                        mono100 = num("mono"),
                        poly100 = num("poly"),
                        animal100 = initial?.animal100 ?: 0.0,
                        amino = initial?.amino.orEmpty(),
                        liquid = liquid,
                        portionKind = kind?.key.orEmpty(),
                        portionGrams = if (kind != null) portion else 0.0,
                        category = cat?.key.orEmpty(),
                        cookedYield = cookedBy,
                        createdMs = initial?.createdMs ?: 0L,
                    )
                    val finalMicro = LinkedHashMap(micro)
                    for (d in defs) {
                        if (d.fixed) continue
                        val text = nutrientText[d.id] ?: continue
                        val v = foodParse(text)
                        if (v != null && v > 0) finalMicro[d.id] = v else finalMicro.remove(d.id)
                    }
                    vm.saveFood(food, finalMicro, onSaved)
                    onDismiss()
                }
            }
        },
    ) {
        if (initial == null) {
            PhotoFill(
                onFilled = { food ->
                    val h = food.per100()
                    name = food.name
                    liquid = food.drink
                    box["kcal"] = foodNum(h.kcal)
                    box["protein"] = foodNum(h.protein)
                    box["carbs"] = foodNum(h.carbs)
                    box["fat"] = foodNum(h.fat)
                    box["fiber"] = foodNum(h.fiber)
                    box["sugar"] = foodNum(h.sugar)
                    box["sodium"] = foodNum(h.sodiumMg)
                    box["sat"] = foodNum(h.sat)
                    box["mono"] = foodNum(h.mono)
                    box["poly"] = foodNum(h.poly)
                    // Put the typed text of the nutrient boxes into [micro] first so nothing typed is lost, then add what the photo gave.
                    val typed = LinkedHashMap(micro)
                    for ((id, t) in nutrientText) foodParse(t)?.takeIf { it > 0 }?.let { typed[id] = it }
                    setMicro(typed + h.micro)
                    kind = PortionKind.Serving
                    portionGrams = foodNum(food.servingGrams)
                    error = null
                },
            )
        }
        PaperField(name, { name = it; error = null }, "Name", placeholder = "e.g. Paneer tikka", singleLine = true)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("It is a drink (counted in ml)", color = p.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PaperToggle(liquid, { liquid = it })
        }

        MonoLabel("In 100 $unit")
        FieldBoxes(defs, box, nutrientText, onTyped = { error = null })
        PaperButton("Change these boxes", { choosing = true }, style = PaperButtonStyle.Ghost, small = true)

        PaperButton(
            if (more) "Hide group, portion and vitamins" else "More: group, portion, cooked, vitamins",
            { more = !more }, style = PaperButtonStyle.Ghost, small = true,
        )
        if (more) {
            MonoLabel("Group (optional)")
            ChipFlow {
                PaperPill("None", cat == null, { cat = null })
                FoodCat.entries.forEach { c -> PaperPill(c.label, cat == c, { cat = c }) }
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

            MonoLabel("All vitamins and minerals")
            val typed = micro.count { it.value > 0 }
            PaperButton(
                if (typed == 0) "Type in vitamins and minerals" else "Vitamins and minerals ($typed typed)",
                { showVitamins = true }, style = PaperButtonStyle.Ghost, small = true,
            )
        }

        if (error != null) Text(error!!, color = p.bad, fontSize = 13.sp)
        if (initial != null) PaperButton("Delete this food", { confirmDelete = true }, style = PaperButtonStyle.Danger, small = true)
    }

    if (choosing) {
        FieldChooser(
            ids = fieldIds, nutrients = nutrients,
            onChange = {
                fieldIds = FoodFields.sanitize(it)
                FoodFields.save(context, fieldIds)
            },
            onClose = { choosing = false },
        )
    }

    if (showVitamins) {
        VitaminValuesWindow(
            nutrients = nutrients, name = name.ifBlank { "this food" }, initial = micro,
            onSave = { saved ->
                // Anything typed in the form's own nutrient boxes is kept, then what was set in the window wins.
                setMicro(saved)
            },
            onClose = { showVitamins = false },
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

/** The number boxes of the form: calories alone on top, then the other basics three to a row, then the vitamins and minerals two to a row. */
@Composable
private fun FieldBoxes(
    defs: List<FieldDef>,
    box: MutableMap<String, String>,
    nutrientText: MutableMap<String, String>,
    onTyped: () -> Unit,
) {
    fun valueOf(d: FieldDef): String = if (d.fixed) box[d.id].orEmpty() else nutrientText[d.id].orEmpty()
    fun change(d: FieldDef, text: String) {
        if (d.fixed) box[d.id] = text else nutrientText[d.id] = text
        onTyped()
    }
    defs.firstOrNull { it.id == FoodFields.KCAL }?.let { d ->
        PaperField(valueOf(d), { change(d, it) }, d.caption, singleLine = true, keyboardOptions = DecimalKeys)
    }
    val others = defs.filter { it.fixed && it.id != FoodFields.KCAL }
    others.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { d -> PaperField(valueOf(d), { change(d, it) }, d.caption, Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    val nutrients = defs.filter { !it.fixed }
    nutrients.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { d -> PaperField(valueOf(d), { change(d, it) }, d.caption, Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys) }
            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * Pick the boxes of the new-food form. The photo reader, the label reader and the widget fill exactly these boxes, so a vitamin you
 * add here is also asked for from a photo. Changes are kept as you tap.
 */
@Composable
private fun FieldChooser(ids: List<String>, nutrients: List<NutrientEntity>, onChange: (List<String>) -> Unit, onClose: () -> Unit) {
    val p = LocalPaper.current
    val inForm = FoodFields.defsFor(ids, nutrients)
    val addable = FoodFields.addable(ids, nutrients)
    PaperDialog(
        title = "Boxes in this form",
        onDismiss = onClose,
        confirmText = "Done",
        onConfirm = onClose,
        dismissText = "Close",
    ) {
        Column(
            modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Tap a box to take it out. Calories always stay. A box you take out is only hidden, so foods keep the numbers they already have.",
                fontSize = 13.sp, color = p.ink2,
            )
            MonoLabel("In the form")
            ChipFlow(maxLines = 40) {
                inForm.forEach { d ->
                    if (d.id == FoodFields.KCAL) PaperPill(d.short, true, {})
                    else PaperPill("x  " + d.short, true, { onChange(ids.filter { it != d.id }) })
                }
            }
            if (addable.isNotEmpty()) {
                listOf(FoodFields.BASIC, NutrientGroup.VITAMIN, NutrientGroup.MINERAL, NutrientGroup.OTHER).forEach { group ->
                    val list = addable.filter { it.group == group }
                    if (list.isNotEmpty()) {
                        MonoLabel("Add: " + if (group == FoodFields.BASIC) "basics" else NutrientGroup.label(group).lowercase())
                        ChipFlow(maxLines = 40) {
                            list.forEach { d -> PaperPill("+  " + d.short, false, { onChange(ids + d.id) }) }
                        }
                    }
                }
            }
            Text(
                "Taking a photo fills these same boxes. Vitamin and mineral numbers read from a photo are only rough guesses.",
                fontSize = 12.sp, color = p.ink2,
            )
            PaperButton("Back to the usual boxes", { onChange(FoodFields.DEFAULT_IDS) }, style = PaperButtonStyle.Ghost, small = true)
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
