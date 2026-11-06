package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.BatchEntity
import com.lifetracker.app.data.BatchMath
import com.lifetracker.app.data.FastMath
import com.lifetracker.app.data.FoodGoalEntity
import com.lifetracker.app.data.FoodLibrary
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodPart
import com.lifetracker.app.data.FoodSettings
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.Loggable
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.MealPlanEntity
import com.lifetracker.app.data.NutrientValues
import com.lifetracker.app.data.PlanMath
import com.lifetracker.app.data.QualityMath
import com.lifetracker.app.data.SavedMealItem
import com.lifetracker.app.data.SupplementEntity
import com.lifetracker.app.data.SupplementRules
import com.lifetracker.app.data.WeekMath
import com.lifetracker.app.data.WeightMath
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSheet
import com.lifetracker.app.ui.theme.PaperToggle
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WATER_COLOR = Color(0xFF2878B4)

// ---- choosing what to show ----

@Composable
fun TrackersSheet(onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle("What to show", "Switch parts of the Food tab on or off. Nothing you logged is deleted.")
            FoodPart.entries.forEach { part ->
                val on = remember(tick) { FoodSettings.isOn(context, part) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(part.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        Text(part.hint, fontSize = 12.sp, color = p.ink2)
                    }
                    PaperToggle(on, { FoodSettings.setOn(context, part, it) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- water ----

@Composable
fun WaterTile(ml: Int, onAdd: (Int) -> Unit) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    val goal = remember(tick) { FoodSettings.waterGoalMl(context) }
    val glass = remember(tick) { FoodSettings.glassMl(context) }
    var editing by remember { mutableStateOf(false) }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperIcon(R.drawable.ic_p_drop, size = 22.dp, tint = WATER_COLOR)
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { editing = true },
            ) {
                Text("Water", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text("$ml of $goal ml", fontSize = 12.sp, color = p.ink2)
            }
            PaperPill("-$glass", false, { onAdd(-glass) })
            PaperPill("+$glass", false, { onAdd(glass) })
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(p.surface2)) {
            val f = (ml.toFloat() / goal).coerceIn(0f, 1f)
            if (f > 0f) Box(modifier = Modifier.fillMaxWidth(f).height(7.dp).background(WATER_COLOR))
        }
    }
    if (editing) {
        var goalText by remember { mutableStateOf(goal.toString()) }
        var glassText by remember { mutableStateOf(glass.toString()) }
        PaperDialog(
            title = "Water", onDismiss = { editing = false }, confirmText = "Save",
            onConfirm = {
                FoodSettings.setWater(context, goalText.toIntOrNull() ?: goal, glassText.toIntOrNull() ?: glass)
                editing = false
            },
        ) {
            PaperField(goalText, { goalText = it }, "Daily goal (ml)", singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(glassText, { glassText = it }, "One glass (ml)", Modifier.padding(top = 8.dp), singleLine = true, keyboardOptions = DecimalKeys)
        }
    }
}

// ---- picking a food (for the shake, a batch or a plan) ----

@Composable
fun FoodPickerSheet(vm: FoodViewModel, title: String, onPick: (Loggable) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val library by vm.library.collectAsState()
    val own by vm.ownFoods.collectAsState()
    val recent by vm.recent.collectAsState()
    var query by remember { mutableStateOf("") }
    val lib = library
    val q = query.trim()

    val rows: List<Loggable> = when {
        lib == null -> emptyList()
        q.isEmpty() -> recent.take(8).map { FoodLogic.ofMeal(it, lib) } + own.take(8).map { FoodLogic.of(it, emptyMap()) }
        else -> FoodLogic.searchOwn(own, q, 10).map { FoodLogic.of(it, emptyMap()) } +
            lib.search(q, null, 25).map { FoodLogic.of(it, lib.microOf(it.id)) }
    }

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle(title)
            PaperField(query, { query = it }, "Search foods", singleLine = true)
            if (rows.isEmpty()) Text(if (q.isEmpty()) "Foods you ate before show up here. Search for any food." else "Nothing found.", fontSize = 13.sp, color = p.ink2)
            rows.forEach { food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onPick(food); onDismiss() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FoodPhoto(food.photoAsset, food.name, 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(food.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
                        Text(kc(food.per100.kcal) + " kcal in 100 " + food.unit, fontSize = 12.sp, color = p.ink2)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- protein shake ----

@Composable
fun ShakeCard(vm: FoodViewModel, mealType: String) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    val library by vm.library.collectAsState()
    val own by vm.ownFoods.collectAsState()
    val date by vm.date.collectAsState()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }

    val refs = remember(tick) { FoodSettings.shakeRefs(context).toList() }
    val current = remember(tick) { FoodSettings.shakeRef(context) }
    val food = if (library != null && own.size >= 0) vm.loggable(current) else null

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (food != null) FoodPhoto(food.photoAsset, food.name, 44.dp) else PaperIcon(R.drawable.ic_p_bolt, size = 24.dp, tint = PROTEIN_COLOR)
            Column(modifier = Modifier.weight(1f)) {
                Text("Protein shake", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    if (food == null) "Pick the shake you drink, then one tap logs it." else food.name + ", " + kc(food.per100.protein * food.startAmount / 100) + " g protein",
                    fontSize = 12.sp, color = p.ink2, maxLines = 2,
                )
            }
            if (food != null) {
                PaperButton("Log it", {
                    scope.launch {
                        val full = vm.withYourNumbers(food)
                        vm.logFood(full, full.startAmount, mealType, date, FoodLogic.minuteFor(mealType, date == LocalDate.now(), minuteNow()))
                    }
                }, small = true, icon = R.drawable.ic_p_plus)
            }
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperPill(if (food == null) "Pick your shake" else "Change", false, { picking = true })
            if (food != null) PaperPill("Not a shake", false, { FoodSettings.removeShake(context, current) })
        }
        if (refs.size > 1) {
            ChipFlow(modifier = Modifier.padding(top = 8.dp)) {
                refs.forEach { r ->
                    val f = vm.loggable(r)
                    if (f != null) PaperPill(f.name, r == current, { FoodSettings.setShake(context, r) })
                }
            }
        }
    }
    if (picking) {
        FoodPickerSheet(vm, "Your shake", onPick = { FoodSettings.setShake(context, it.ref) }, onDismiss = { picking = false })
    }
}

// ---- supplements ----

@Composable
fun SupplementsCard(x: FoodExtrasViewModel, date: LocalDate) {
    val p = LocalPaper.current
    val all by x.supplements.collectAsState()
    val taken by x.taken.collectAsState()
    var managing by remember { mutableStateOf(false) }
    val due = all.filter { SupplementRules.dueOn(it, date) }
    val doneIds = taken.map { it.supplementId }.toSet()

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Supplements", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    when {
                        all.isEmpty() -> "Add what you take, then tick it off each day."
                        due.isEmpty() -> "Nothing due this day."
                        else -> "${due.count { it.id in doneIds }} of ${due.size} taken"
                    },
                    fontSize = 12.sp, color = p.ink2,
                )
            }
            PaperPill(if (all.isEmpty()) "Add" else "Manage", false, { managing = true })
        }
        if (due.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                due.forEach { s ->
                    val done = s.id in doneIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Checkbox) { x.setTaken(s, date, minuteNow(), !done) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(if (done) p.ink else Color.Transparent, RoundedCornerShape(8.dp))
                                .border(1.5.dp, p.ink, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) PaperIcon(R.drawable.ic_p_check, size = 16.dp, tint = p.bg)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (done) p.ink2 else p.ink)
                            val line = listOf(s.dose, if (s.atMinute >= 0) formatMinute(s.atMinute) else "").filter { it.isNotBlank() }.joinToString("  ·  ")
                            if (line.isNotEmpty()) Text(line, fontSize = 12.sp, color = p.ink2)
                        }
                    }
                }
            }
        }
    }
    if (managing) SupplementsWindow(x, all, onClose = { managing = false })
}

@Composable
private fun SupplementsWindow(x: FoodExtrasViewModel, all: List<SupplementEntity>, onClose: () -> Unit) {
    val p = LocalPaper.current
    var editing by remember { mutableStateOf<SupplementEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    EditWindow(title = "Supplements", sub = "Tap one to change it", onClose = onClose) {
        all.forEach { s ->
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = { editing = s }) {
                Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    listOf(s.dose, SupplementRules.summary(s), if (s.atMinute >= 0) formatMinute(s.atMinute) else "Any time").filter { it.isNotBlank() }.joinToString("  ·  "),
                    fontSize = 12.sp, color = p.ink2,
                )
            }
        }
        PaperButton("New supplement", { creating = true }, icon = R.drawable.ic_p_plus)
    }
    if (creating) SupplementEditor(x, null, onDismiss = { creating = false })
    editing?.let { SupplementEditor(x, it, onDismiss = { editing = null }) }
}

@Composable
private fun SupplementEditor(x: FoodExtrasViewModel, initial: SupplementEntity?, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var dose by remember { mutableStateOf(initial?.dose.orEmpty()) }
    var at by remember { mutableStateOf(initial?.atMinute ?: -1) }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: 0) }
    var every by remember { mutableStateOf(initial?.everyDays ?: 1) }
    var from by remember { mutableStateOf(initial?.fromDate.orEmpty()) }
    var until by remember { mutableStateOf(initial?.untilDate.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val mode = if (every > 1) 2 else if (weekdays != 0) 1 else 0
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    EditWindow(
        title = if (initial == null) "New supplement" else name.ifBlank { "Supplement" },
        sub = "What you take and when",
        onClose = onDismiss,
        onSave = {
            when {
                name.isBlank() -> error = "Give it a name."
                mode == 2 && from.isEmpty() -> error = "Choose the day the every-few-days count starts from."
                else -> {
                    x.saveSupplement(
                        SupplementEntity(
                            id = initial?.id ?: "u:" + java.util.UUID.randomUUID().toString().take(12), name = name.trim(), dose = dose.trim(),
                            atMinute = at, weekdays = if (mode == 1) weekdays else 0, everyDays = if (mode == 2) every else 1,
                            fromDate = from, untilDate = until, sortOrder = initial?.sortOrder ?: 0,
                        ),
                    )
                    onDismiss()
                }
            }
        },
    ) {
        PaperField(name, { name = it; error = null }, "Name", placeholder = "e.g. Vitamin D3", singleLine = true)
        PaperField(dose, { dose = it }, "Dose (optional)", placeholder = "e.g. 1000 IU", singleLine = true)
        MonoLabel("Time")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PaperPill("Any time", at < 0, { at = -1 })
            PaperButton(
                if (at >= 0) formatMinute(at) else "Set a time",
                { TimePickerDialog(context, { _, h, m -> at = h * 60 + m }, if (at >= 0) at / 60 else 8, if (at >= 0) at % 60 else 0, false).show() },
                style = PaperButtonStyle.Ghost, small = true,
            )
        }
        MonoLabel("How often")
        ChipFlow {
            PaperPill("Every day", mode == 0, { weekdays = 0; every = 1 })
            PaperPill("Some weekdays", mode == 1, { every = 1; if (weekdays == 0) weekdays = SupplementRules.bit(1) })
            PaperPill("Every few days", mode == 2, { weekdays = 0; if (every < 2) every = 2; if (from.isEmpty()) from = LocalDate.now().toString() })
        }
        if (mode == 1) {
            ChipFlow {
                dayNames.forEachIndexed { i, label ->
                    val bit = SupplementRules.bit(i + 1)
                    PaperPill(label, weekdays and bit != 0, { val next = weekdays xor bit; weekdays = if (next == 0) bit else next })
                }
            }
        }
        if (mode == 2) {
            PaperField(every.toString(), { every = (it.toIntOrNull() ?: 2).coerceIn(2, 90) }, "Every how many days", singleLine = true, keyboardOptions = DecimalKeys)
        }
        MonoLabel("From and until (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton(
                if (from.isEmpty()) "From: today on" else "From $from",
                { pickDay(context, runCatching { LocalDate.parse(from) }.getOrDefault(LocalDate.now())) { from = it.toString() } },
                style = PaperButtonStyle.Ghost, small = true,
            )
            PaperButton(
                if (until.isEmpty()) "Until: no end" else "Until $until",
                { pickDay(context, runCatching { LocalDate.parse(until) }.getOrDefault(LocalDate.now())) { until = it.toString() } },
                style = PaperButtonStyle.Ghost, small = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (from.isNotEmpty() && mode != 2) PaperPill("Clear from", false, { from = "" })
            if (until.isNotEmpty()) PaperPill("Clear until", false, { until = "" })
        }
        Text("Reminders for supplements come in a later step. For now the card shows what is due and you tick it off.", fontSize = 12.sp, color = p.ink2)
        if (error != null) Text(error!!, color = p.bad, fontSize = 13.sp)
        if (initial != null) PaperButton("Delete this supplement", { confirmDelete = true }, style = PaperButtonStyle.Danger, small = true)
    }
    if (confirmDelete && initial != null) {
        PaperDialog(
            title = "Delete ${initial.name}?", onDismiss = { confirmDelete = false }, confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger, dismissText = "Keep",
            onConfirm = { confirmDelete = false; x.deleteSupplement(initial); onDismiss() },
        ) { Text("Its ticks are deleted too.", color = p.ink) }
    }
}

// ---- body weight ----

@Composable
fun WeightCard(x: FoodExtrasViewModel, date: LocalDate) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    val weights by x.weights.collectAsState()
    val goal = remember(tick) { FoodSettings.weightGoalKg(context) }
    var logging by remember { mutableStateOf(false) }
    val latest = weights.lastOrNull()
    val pace = WeightMath.kgPerWeek(weights, LocalDate.now())

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Body weight", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    if (latest == null) "No weigh-in yet." else foodNum(latest.kg) + " kg on " + latest.date,
                    fontSize = 12.sp, color = p.ink2,
                )
            }
            PaperPill("Weigh in", false, { logging = true })
        }
        if (latest != null) {
            val lines = listOfNotNull(
                if (goal > 0) WeightMath.toGoal(latest.kg, goal)?.let { "Goal " + foodNum(goal) + " kg: " + it } else null,
                pace?.let { (if (it > 0) "+" else "") + (Math.round(it * 100) / 100.0) + " kg a week lately" },
            )
            lines.forEach { Text(it, fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 4.dp)) }
            val recent = weights.takeLast(30)
            if (recent.size >= 2) {
                val min = recent.minOf { it.kg }
                val max = recent.maxOf { it.kg }
                val line = p.ink
                Canvas(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(56.dp)) {
                    val span = (max - min).coerceAtLeast(0.5)
                    val path = Path()
                    recent.forEachIndexed { i, w ->
                        val px = size.width * i / (recent.size - 1)
                        val py = size.height - ((w.kg - min) / span * (size.height - 8.dp.toPx())).toFloat() - 4.dp.toPx()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
    if (logging) {
        val onDay = weights.firstOrNull { it.date == date.toString() }?.kg ?: latest?.kg ?: 0.0
        var kg by remember { mutableStateOf(foodNum(onDay)) }
        var goalText by remember { mutableStateOf(foodNum(goal)) }
        PaperDialog(
            title = "Weight on ${TimelineTop.title(date, LocalDate.now())}", onDismiss = { logging = false }, confirmText = "Save",
            onConfirm = {
                foodParse(kg)?.takeIf { it > 0 }?.let { x.setWeight(date, it) }
                FoodSettings.setWeightGoal(context, foodParse(goalText) ?: 0.0)
                logging = false
            },
        ) {
            PaperField(kg, { kg = it }, "Weight (kg)", singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(goalText, { goalText = it }, "Goal weight (kg), empty for none", Modifier.padding(top = 8.dp), singleLine = true, keyboardOptions = DecimalKeys)
            if (weights.any { it.date == date.toString() }) {
                PaperButton("Delete this weigh-in", { x.deleteWeight(date.toString()); logging = false }, Modifier.padding(top = 8.dp), style = PaperButtonStyle.Danger, small = true)
            }
        }
    }
}

// ---- the week ----

@Composable
fun WeekCard(x: FoodExtrasViewModel, date: LocalDate) {
    val p = LocalPaper.current
    val meals by x.weekMeals.collectAsState()
    val goals by x.goals.collectAsState()
    val week = FoodLogic.weekOf(date)
    val summary = WeekMath.summary(meals) { day -> FoodStats.goalFor(goals, LocalDate.parse(day)) }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Text("This week", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
        Text("${week.first().dayOfMonth}/${week.first().monthValue} to ${week.last().dayOfMonth}/${week.last().monthValue}", fontSize = 12.sp, color = p.ink2)
        if (summary.loggedDays == 0) {
            Text("Nothing logged yet.", fontSize = 13.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeekStat("Calories a day", kc(summary.avgKcal), Modifier.weight(1f))
                WeekStat("Protein a day", kc(summary.avgProtein) + " g", Modifier.weight(1f))
                WeekStat("On target", "${summary.daysOnTarget} of ${summary.loggedDays}", Modifier.weight(1f))
            }
            Text(
                "On target: calories within 10% of the goal and at least 90% of the protein. Counted over days with food.",
                fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun WeekStat(label: String, value: String, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1)
        Text(label, fontSize = 12.sp, color = p.ink2)
    }
}

// ---- fasting ----

@Composable
fun FastingCard(x: FoodExtrasViewModel) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    val fasts by x.fasts.collectAsState()
    val start = remember(tick) { FoodSettings.fastStartMs(context) }
    val goalHours = remember(tick) { FoodSettings.fastGoalHours(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) {
        while (start > 0) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val running = start > 0
    val progress = FastMath.progress(start, now, goalHours)
    val week = FastMath.week(fasts, System.currentTimeMillis())
    val streak = FastMath.streak(fasts)

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Text("Fasting", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
        if (running) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    val track = p.surface2
                    val ink = if (progress >= 1.0) p.accent else p.ink
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 9.dp.toPx()
                        val inset = stroke / 2
                        val arc = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(ink, -90f, (360f * progress.toFloat()).coerceIn(2f, 360f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Text(kc(progress * 100) + "%", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(FastMath.clock(now - start), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                    Text(
                        if (progress >= 1.0) "Goal of $goalHours h reached" else "of $goalHours h, ends " + formatMinute(java.time.Instant.ofEpochMilli(start + goalHours * 3_600_000L).atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }),
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton("End fast", { x.endFast() }, small = true)
                PaperButton("Cancel", { x.cancelFast() }, style = PaperButtonStyle.Ghost, small = true)
            }
        } else {
            Text("Choose how long, then start the timer.", fontSize = 12.sp, color = p.ink2)
            ChipFlow(modifier = Modifier.padding(top = 8.dp)) {
                listOf(12, 14, 16, 18, 20, 23).forEach { h ->
                    PaperPill("$h h", goalHours == h, { FoodSettings.setFast(context, 0L, h) })
                }
            }
            PaperButton("Start fasting", { x.startFast(goalHours) }, Modifier.padding(top = 10.dp), small = true, icon = R.drawable.ic_p_bolt)
        }
        if (week != null) {
            Text(
                "Last 7 days: ${week.count} " + (if (week.count == 1) "fast" else "fasts") + ", ${week.reached} reached the goal, average " + FastMath.clock(week.avgMinutes * 60_000),
                fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (streak > 1) Text("$streak fasts in a row reached their goal", fontSize = 12.sp, color = p.ink2)
    }
}

// ---- ingredients (meal prep and plans) ----

private fun itemOf(food: Loggable, grams: Double): SavedMealItem =
    SavedMealItem.of(FoodLogic.toMeal(food, grams, "", 0, FoodLogic.SNACK, ""))

@Composable
private fun IngredientRows(vm: FoodViewModel, items: List<SavedMealItem>, onChange: (List<SavedMealItem>) -> Unit) {
    val p = LocalPaper.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    items.forEachIndexed { i, item ->
        var text by remember(i, item.foodId, item.name) { mutableStateOf(foodNum(item.grams)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 2)
                Text(kc(item.kcal) + " kcal", fontSize = 12.sp, color = p.ink2)
            }
            PaperField(
                text,
                { value ->
                    text = value
                    foodParse(value)?.takeIf { it > 0 }?.let { g -> onChange(items.mapIndexed { j, it2 -> if (j == i) it2.copy(grams = g) else it2 }) }
                },
                "grams", Modifier.width(96.dp), singleLine = true, keyboardOptions = DecimalKeys,
            )
            PaperIconButton(R.drawable.ic_p_trash, "Remove ${item.name}", { onChange(items.filterIndexed { j, _ -> j != i }) })
        }
    }
    PaperButton("Add a food", { picking = true }, style = PaperButtonStyle.Ghost, small = true, icon = R.drawable.ic_p_plus)
    if (picking) {
        FoodPickerSheet(
            vm, "Add a food",
            onPick = { food -> scope.launch { val full = vm.withYourNumbers(food); onChange(items + itemOf(full, full.startAmount)) } },
            onDismiss = { picking = false },
        )
    }
}

// ---- meal prep ----

@Composable
fun PrepCard(vm: FoodViewModel, x: FoodExtrasViewModel, mealType: String) {
    val p = LocalPaper.current
    val batches by x.batches.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<BatchEntity?>(null) }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Meal prep", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    if (batches.isEmpty()) "Cooked a big pot? Log it once, then say how much you ate." else "Tap a pot to eat from it",
                    fontSize = 12.sp, color = p.ink2,
                )
            }
            PaperPill("New batch", false, { creating = true })
        }
        batches.forEach { b ->
            val share = BatchMath.leftShare(b)
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { opened = b },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row {
                    Text(b.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        if (BatchMath.finished(b)) "Finished" else kc(b.leftGrams) + " of " + kc(BatchMath.totalGrams(b)) + " g left",
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2)) {
                    if (share > 0) Box(modifier = Modifier.fillMaxWidth(share.toFloat()).height(6.dp).background(p.accent))
                }
            }
        }
    }
    if (creating) BatchEditor(vm, x, null, onDismiss = { creating = false })
    opened?.let { b -> BatchSheet(vm, x, b, mealType, onDismiss = { opened = null }) }
}

@Composable
private fun BatchEditor(vm: FoodViewModel, x: FoodExtrasViewModel, initial: BatchEntity?, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var items by remember { mutableStateOf(com.lifetracker.app.data.SavedMealItems.decode(initial?.items.orEmpty())) }
    var cooked by remember { mutableStateOf(foodNum(initial?.cookedGrams ?: 0.0)) }
    var portions by remember { mutableStateOf((initial?.portions ?: 4).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val raw = BatchMath.rawGrams(items)

    EditWindow(
        title = if (initial == null) "New batch" else name.ifBlank { "Batch" },
        sub = "Log the whole pot once, then eat it bit by bit",
        onClose = onDismiss,
        onSave = {
            val cookedGrams = foodParse(cooked) ?: 0.0
            when {
                name.isBlank() -> error = "Give the batch a name."
                items.isEmpty() -> error = "Add at least one ingredient."
                else -> {
                    val encoded = com.lifetracker.app.data.SavedMealItems.encode(items)
                    val total = if (cookedGrams > 0) cookedGrams else raw
                    val oldTotal = initial?.let { BatchMath.totalGrams(it) } ?: 0.0
                    // A new batch is full. Changing the weight of one that was started keeps the share that is left.
                    val left = if (initial == null || oldTotal <= 0) total else (initial.leftGrams / oldTotal * total)
                    x.saveBatch(
                        BatchEntity(
                            id = initial?.id ?: BatchMath.newId(), name = name.trim(), items = encoded,
                            createdMs = initial?.createdMs ?: System.currentTimeMillis(), cookedGrams = cookedGrams,
                            leftGrams = left, portions = (portions.toIntOrNull() ?: 4).coerceIn(2, 12),
                        ),
                    )
                    onDismiss()
                }
            }
        },
    ) {
        PaperField(name, { name = it; error = null }, "Name", placeholder = "e.g. Chicken and rice", singleLine = true)
        MonoLabel("Ingredients")
        IngredientRows(vm, items) { items = it; error = null }
        PaperField(
            cooked, { cooked = it }, "Cooked weight of the pot (g)", singleLine = true, keyboardOptions = DecimalKeys,
            placeholder = if (raw > 0) "empty uses the raw total, ${kc(raw)} g" else null,
        )
        PaperField(portions, { portions = it }, "Portions (for the one-tap button)", singleLine = true, keyboardOptions = DecimalKeys)
        if (error != null) Text(error!!, color = p.bad, fontSize = 13.sp)
        if (initial != null) PaperButton("Delete this batch", { confirmDelete = true }, style = PaperButtonStyle.Danger, small = true)
    }
    if (confirmDelete && initial != null) {
        PaperDialog(
            title = "Delete ${initial.name}?", onDismiss = { confirmDelete = false }, confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger, dismissText = "Keep",
            onConfirm = { confirmDelete = false; x.deleteBatch(initial); onDismiss() },
        ) { Text("Meals you already logged from it stay.", color = p.ink) }
    }
}

@Composable
private fun BatchSheet(vm: FoodViewModel, x: FoodExtrasViewModel, batch: BatchEntity, startMeal: String, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val date by vm.date.collectAsState()
    val food = remember(batch) { BatchMath.asFood(batch) }
    var meal by remember { mutableStateOf(startMeal) }
    var amount by remember { mutableStateOf(foodNum(food.portionGrams.coerceAtMost(maxOf(batch.leftGrams, 1.0)))) }
    var left by remember { mutableStateOf(foodNum(batch.leftGrams)) }
    var editing by remember { mutableStateOf(false) }
    val grams = foodParse(amount) ?: 0.0
    val k = grams / 100

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle(batch.name, if (BatchMath.finished(batch)) "This batch is finished." else kc(batch.leftGrams) + " g left of " + kc(BatchMath.totalGrams(batch)) + " g")
            ChipFlow {
                listOf(25, 50, 100).forEach { pct ->
                    PaperPill("$pct% of what is left", false, { amount = foodNum(batch.leftGrams * pct / 100) })
                }
                PaperPill("1 portion", false, { amount = foodNum(food.portionGrams) })
            }
            PaperField(amount, { amount = it }, "Amount you ate (g)", singleLine = true, keyboardOptions = DecimalKeys)
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
                Text(kc(food.per100.kcal * k) + " kcal", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    MacroCell("Protein", food.per100.protein * k, PROTEIN_COLOR, Modifier.weight(1f))
                    MacroCell("Carbs", food.per100.carbs * k, CARB_COLOR, Modifier.weight(1f))
                    MacroCell("Fat", food.per100.fat * k, FAT_COLOR, Modifier.weight(1f))
                }
            }
            MealPills(meal) { meal = it }
            PaperButton(
                "Log it", {
                    x.eatFromBatch(batch, grams, meal, date, FoodLogic.minuteFor(meal, date == LocalDate.now(), minuteNow()))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(), enabled = grams > 0 && !BatchMath.finished(batch), icon = R.drawable.ic_p_plus,
            )
            PaperField(left, { left = it }, "Weigh what is left (g)", singleLine = true, keyboardOptions = DecimalKeys)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton(
                    "Save what is left",
                    { foodParse(left)?.let { x.saveBatch(batch.copy(leftGrams = it.coerceIn(0.0, BatchMath.totalGrams(batch)))) }; onDismiss() },
                    style = PaperButtonStyle.Ghost, small = true,
                )
                PaperButton("Edit batch", { editing = true }, style = PaperButtonStyle.Ghost, small = true)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (editing) BatchEditor(vm, x, batch, onDismiss = { editing = false; onDismiss() })
}

// ---- meal plans ----

@Composable
fun PlansCard(vm: FoodViewModel, x: FoodExtrasViewModel, date: LocalDate, dayMeals: List<MealEntity>) {
    val p = LocalPaper.current
    val plans by x.plans.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<MealPlanEntity?>(null) }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Meal plans", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    if (plans.isEmpty()) "Plan a day of meals, then log it with one tap." else "Tap a plan to log it on this day",
                    fontSize = 12.sp, color = p.ink2,
                )
            }
            PaperPill("New plan", false, { creating = true })
        }
        plans.forEach { plan ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { opened = plan },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(plan.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text(kc(PlanMath.kcal(plan)) + " kcal", fontSize = 12.sp, color = p.ink2)
            }
        }
    }
    if (creating) PlanEditor(vm, x, null, dayMeals, onDismiss = { creating = false })
    opened?.let { plan -> PlanSheet(vm, x, plan, date, dayMeals, onDismiss = { opened = null }) }
}

@Composable
private fun PlanEditor(vm: FoodViewModel, x: FoodExtrasViewModel, initial: MealPlanEntity?, dayMeals: List<MealEntity>, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var plan by remember { mutableStateOf(initial ?: MealPlanEntity(PlanMath.newId(), "")) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    EditWindow(
        title = if (initial == null) "New plan" else plan.name.ifBlank { "Plan" },
        sub = kc(PlanMath.kcal(plan)) + " kcal in all",
        onClose = onDismiss,
        onSave = {
            when {
                plan.name.isBlank() -> error = "Give the plan a name."
                plan.allItems.isEmpty() -> error = "Add at least one food."
                else -> { x.savePlan(plan.copy(name = plan.name.trim())); onDismiss() }
            }
        },
    ) {
        PaperField(plan.name, { plan = plan.copy(name = it); error = null }, "Name", placeholder = "e.g. Study day", singleLine = true)
        if (dayMeals.isNotEmpty()) {
            PaperButton(
                "Copy the day I am looking at",
                {
                    var next = plan
                    FoodLogic.MEAL_TYPES.forEach { t -> next = next.withItems(t, dayMeals.filter { it.mealType == t }.map { SavedMealItem.of(it) }) }
                    plan = next
                },
                style = PaperButtonStyle.Ghost, small = true,
            )
        }
        FoodLogic.MEAL_TYPES.forEach { type ->
            MonoLabel(mealTypeLabel(type))
            IngredientRows(vm, plan.itemsOf(type)) { plan = plan.withItems(type, it); error = null }
        }
        if (error != null) Text(error!!, color = p.bad, fontSize = 13.sp)
        if (initial != null) PaperButton("Delete this plan", { confirmDelete = true }, style = PaperButtonStyle.Danger, small = true)
    }
    if (confirmDelete && initial != null) {
        PaperDialog(
            title = "Delete ${initial.name}?", onDismiss = { confirmDelete = false }, confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger, dismissText = "Keep",
            onConfirm = { confirmDelete = false; x.deletePlan(initial); onDismiss() },
        ) { Text("Days where you logged it are not changed.", color = p.ink) }
    }
}

@Composable
private fun PlanSheet(vm: FoodViewModel, x: FoodExtrasViewModel, plan: MealPlanEntity, date: LocalDate, dayMeals: List<MealEntity>, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle(plan.name, kc(PlanMath.kcal(plan)) + " kcal for the day")
            FoodLogic.MEAL_TYPES.forEach { type ->
                val items = plan.itemsOf(type)
                if (items.isNotEmpty()) {
                    MonoLabel(mealTypeLabel(type))
                    items.forEach { Text(it.name + "  ·  " + kc(it.grams) + " g  ·  " + kc(it.kcal) + " kcal", fontSize = 13.sp, color = p.ink) }
                }
            }
            message?.let { Text(it, fontSize = 13.sp, color = p.ink2) }
            PaperButton(
                "Log the empty meals on ${TimelineTop.title(date, LocalDate.now())}",
                {
                    x.applyPlan(plan, date, minuteNow()) { n ->
                        message = if (n == 0) "Every meal of that day already has food, so nothing was added." else "$n " + (if (n == 1) "food" else "foods") + " logged."
                    }
                },
                modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_p_plus,
            )
            PaperButton("Edit plan", { editing = true }, style = PaperButtonStyle.Ghost, small = true)
            Spacer(Modifier.height(12.dp))
        }
    }
    if (editing) PlanEditor(vm, x, plan, dayMeals, onDismiss = { editing = false; onDismiss() })
}

// ---- protein quality and fat types ----

@Composable
fun QualityCard(meals: List<MealEntity>, bodyKg: Double) {
    val p = LocalPaper.current
    val eaten = QualityMath.aminoEaten(meals)
    val shares = QualityMath.aminoShares(eaten, bodyKg)
    val animal = QualityMath.animalShare(meals)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Text("Protein quality", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
        if (animal == null) {
            Text("Log some food with protein to see it.", fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 4.dp))
        } else {
            Text(
                kc(animal * 100) + "% animal protein, " + kc((1 - animal) * 100) + "% plant",
                fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 4.dp),
            )
            if (eaten.any { it > 0 }) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    QualityMath.AMINO_NAMES.forEachIndexed { i, label ->
                        val share = shares[i]
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, fontSize = 12.sp, color = p.ink)
                                Text(kc(share * 100) + "% of the daily need", fontSize = 11.sp, color = p.ink2)
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2)) {
                                val f = share.toFloat().coerceIn(0f, 1f)
                                if (f > 0f) Box(modifier = Modifier.fillMaxWidth(f).height(5.dp).background(if (share >= 1.0) p.accent else p.sun))
                            }
                        }
                    }
                    Text(
                        "Against what the WHO lists for adults, for ${kc(bodyKg)} kg (your latest weigh-in, or 70 kg).",
                        fontSize = 11.sp, color = p.ink2,
                    )
                }
            } else {
                Text("The foods you logged have no amino acid numbers.", fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun FatsCard(meals: List<MealEntity>, goal: FoodGoalEntity?) {
    val p = LocalPaper.current
    val fats = QualityMath.fats(meals)
    val known = fats.sat + fats.mono + fats.poly
    // Health bodies advise keeping saturated fat under about a tenth of the day's calories.
    val satLimit = goal?.kcal?.takeIf { it > 0 }?.let { it * 0.1 / 9 }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Text("Types of fat", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
        if (known <= 0) {
            Text("The foods you logged have no fat type numbers yet.", fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 4.dp))
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeekStat("Saturated", kc(fats.sat) + " g", Modifier.weight(1f))
                WeekStat("Mono", kc(fats.mono) + " g", Modifier.weight(1f))
                WeekStat("Poly", kc(fats.poly) + " g", Modifier.weight(1f))
            }
            if (satLimit != null) {
                Text(
                    "Saturated fat: " + kc(fats.sat) + " of about " + kc(satLimit) + " g (a tenth of your calorie goal).",
                    fontSize = 12.sp, color = if (fats.sat > satLimit) p.bad else p.ink2, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
