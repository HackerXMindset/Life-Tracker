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
import com.lifetracker.app.data.FoodPhotoAi
import com.lifetracker.app.data.IngredientText
import com.lifetracker.app.data.PhotoAiException
import com.lifetracker.app.data.FoodPart
import com.lifetracker.app.data.FoodSettings
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.Loggable
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.MealPlanEntity
import com.lifetracker.app.data.NutrientValues
import com.lifetracker.app.data.PlanMath
import com.lifetracker.app.data.FoodPartOrder
import com.lifetracker.app.data.FoodReminderRules
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
            SheetTitle("What to show", "Switch parts of the Food tab on or off, and move them up or down. Nothing you logged is deleted.")
            val order = remember(tick) { FoodSettings.order(context) }
            listOf("Above the details" to FoodPartOrder.TOP, "Details" to FoodPartOrder.DETAILS).forEach { (heading, group) ->
                MonoLabel(heading)
                val sorted = FoodPartOrder.sorted(order, group)
                sorted.forEachIndexed { index, part ->
                    val on = remember(tick) { FoodSettings.isOn(context, part) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(part.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                            Text(part.hint, fontSize = 12.sp, color = p.ink2)
                        }
                        PaperButton("Up", { FoodSettings.move(context, group, part, -1) }, style = PaperButtonStyle.Ghost, small = true, enabled = index > 0)
                        PaperButton("Down", { FoodSettings.move(context, group, part, 1) }, style = PaperButtonStyle.Ghost, small = true, enabled = index < sorted.lastIndex)
                        PaperToggle(on, { FoodSettings.setOn(context, part, it) })
                    }
                }
            }
            Text("Water and Protein shake share one row, which sits where the higher of the two is.", fontSize = 12.sp, color = p.ink2)

            MonoLabel("Photo reading")
            val animOn = remember(tick) { FoodSettings.readingAnimation(context) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reading animation", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("A pen, a marker and a stamp play while a photo or your words are read, and when the answer comes back. Android's \"remove animations\" also turns it off.", fontSize = 12.sp, color = p.ink2)
                }
                PaperToggle(animOn, { FoodSettings.setReadingAnimation(context, it) })
            }

            MonoLabel("Habits")
            val linkOn = remember(tick) { FoodSettings.waterLinked(context) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Link water to Habits", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("Water drunk here counts on your Water habit, and the other way round. A habit called Water is made if you have none.", fontSize = 12.sp, color = p.ink2)
                }
                PaperToggle(linkOn, { FoodSettings.setWaterLinked(context, it) })
            }

            MonoLabel("Reminders")
            val mealOn = remember(tick) { FoodSettings.mealReminders(context) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Meal reminders", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("At each meal time, only if nothing is logged for that meal yet. Off until you switch it on.", fontSize = 12.sp, color = p.ink2)
                }
                PaperToggle(mealOn, { FoodSettings.setMealReminders(context, it) })
            }
            if (mealOn) {
                FoodReminderRules.MEALS.forEach { meal ->
                    val remind = remember(tick) { FoodSettings.mealRemindOn(context, meal) }
                    val minute = remember(tick) { FoodSettings.mealMinute(context, meal) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(mealTypeLabel(meal), fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f))
                        TimeButton(minute, { FoodSettings.setMealMinute(context, meal, it) })
                        PaperToggle(remind, { FoodSettings.setMealRemindOn(context, meal, it) })
                    }
                }
            }
            val waterOn = remember(tick) { FoodSettings.waterReminders(context) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Water reminders", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("Through the day, only when you are behind on your water goal. Off until you switch it on.", fontSize = 12.sp, color = p.ink2)
                }
                PaperToggle(waterOn, { FoodSettings.setWaterReminders(context, it) })
            }
            if (waterOn) {
                val w = remember(tick) { FoodSettings.waterWindow(context) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60 to "Every hour", 120 to "Every 2 h", 180 to "Every 3 h").forEach { (minutes, label) ->
                        PaperPill(label, w.interval == minutes, { FoodSettings.setWaterWindow(context, w.start, w.end, minutes) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("From", fontSize = 13.sp, color = p.ink2)
                    TimeButton(w.start, { FoodSettings.setWaterWindow(context, it, w.end, w.interval) })
                    Text("to", fontSize = 13.sp, color = p.ink2)
                    TimeButton(w.end, { FoodSettings.setWaterWindow(context, w.start, it, w.interval) })
                }
            }
            val weekOn = remember(tick) { FoodSettings.weeklyReport(context) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sunday week report", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("A short summary of the week, on Sundays, only if you logged something. Off until you switch it on.", fontSize = 12.sp, color = p.ink2)
                }
                PaperToggle(weekOn, { FoodSettings.setWeeklyReport(context, it) })
            }
            if (weekOn) {
                val minute = remember(tick) { FoodSettings.weeklyMinute(context) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sunday at", fontSize = 13.sp, color = p.ink2)
                    TimeButton(minute, { FoodSettings.setWeeklyMinute(context, it) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- water ----

@Composable
fun WaterTile(ml: Int, onAdd: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    val goal = remember(tick) { FoodSettings.waterGoalMl(context) }
    val glass = remember(tick) { FoodSettings.glassMl(context) }
    var editing by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val done = ml >= goal
    VesselTile(
        title = "Water",
        onMenu = { editing = true },
        onTap = { onAdd(glass) },
        onHold = { custom = true },
        value = ml.toString(),
        unit = "/ $goal ml",
        hint = if (done) "Goal reached" else "Tap the glass: +$glass ml",
        hintColor = if (done) p.accent else WATER_COLOR,
        glow = WATER_COLOR,
        modifier = modifier,
    ) {
        PhotoVessel(
            asset = "food/vessel/vessel-water.webp", mask = "food/vessel/vessel-water-mask.webp", ratio = 202f / 640f,
            level = if (goal <= 0) 0f else ml / goal.toFloat(), height = 158.dp,
            top = 0.269f, bottom = 0.989f, curve = 0.08f, liquid = Color(0xFF4FB4F2),
        )
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
            PaperButton("Take off one glass", { onAdd(-glass); editing = false }, Modifier.padding(top = 8.dp), style = PaperButtonStyle.Ghost, small = true)
        }
    }
    if (custom) {
        var amount by remember { mutableStateOf("") }
        PaperDialog(
            title = "Add water", onDismiss = { custom = false }, confirmText = "Add",
            onConfirm = { amount.toIntOrNull()?.takeIf { it > 0 }?.let { onAdd(it) }; custom = false },
        ) {
            PaperField(amount, { amount = it }, "Amount (ml)", singleLine = true, keyboardOptions = DecimalKeys)
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
fun ShakeCard(vm: FoodViewModel, mealType: String, protein: Double, proteinGoal: Double?, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    val library by vm.library.collectAsState()
    val own by vm.ownFoods.collectAsState()
    val date by vm.date.collectAsState()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val refs = remember(tick) { FoodSettings.shakeRefs(context).toList() }
    val current = remember(tick) { FoodSettings.shakeRef(context) }
    val food = if (library != null && own.size >= 0) vm.loggable(current) else null
    val goal = proteinGoal ?: 0.0

    fun logIt() {
        val f = food
        if (f == null) { picking = true; return }
        scope.launch {
            val full = vm.withYourNumbers(f)
            vm.logFood(full, full.startAmount, mealType, date, FoodLogic.minuteFor(mealType, date == LocalDate.now(), minuteNow()))
        }
    }

    VesselTile(
        title = "Protein",
        onMenu = { menu = true },
        onTap = { logIt() },
        onHold = { picking = true },
        value = kc(protein),
        unit = if (goal > 0) "/ ${kc(goal)} g" else "g",
        hint = if (food == null) "Tap the shaker to pick your shake" else "Tap the shaker: ${food.name}",
        hintColor = PROTEIN_COLOR,
        glow = PROTEIN_COLOR,
        modifier = modifier,
    ) {
        PhotoVessel(
            asset = "food/vessel/vessel-shake.webp", mask = "food/vessel/vessel-shake-mask.webp", ratio = 241f / 640f,
            level = if (goal <= 0) 0f else (protein / goal).toFloat(), height = 158.dp,
            top = 0.407f, bottom = 0.975f, curve = 0.066f, liquid = shakeLiquid(food?.name),
            texture = "food/vessel/shake-texture.webp",
        )
    }
    if (menu) {
        PaperDialog(title = "Protein shake", onDismiss = { menu = false }, dismissText = "Close") {
            Text(
                if (food == null) "Pick the shake you drink, then one tap on the shaker logs it." else food.name + ", " + kc(food.per100.protein * food.startAmount / 100) + " g protein",
                fontSize = 13.sp, color = p.ink2,
            )
            PaperButton(if (food == null) "Pick your shake" else "Change", { menu = false; picking = true }, Modifier.padding(top = 8.dp), style = PaperButtonStyle.Ghost, small = true)
            if (food != null) PaperButton("Not a shake", { FoodSettings.removeShake(context, current); menu = false }, Modifier.padding(top = 6.dp), style = PaperButtonStyle.Ghost, small = true)
            if (refs.size > 1) {
                ChipFlow(modifier = Modifier.padding(top = 8.dp)) {
                    refs.forEach { r ->
                        val f = vm.loggable(r)
                        if (f != null) PaperPill(f.name, r == current, { FoodSettings.setShake(context, r) })
                    }
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
    var preset by remember { mutableStateOf<String?>(null) }
    val due = all.filter { SupplementRules.dueOn(it, date) }
    val doneIds = taken.map { it.supplementId }.toSet()

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeekImage("peek-protein", 22.dp)
            Kicker("Supplements", Modifier.weight(1f))
            if (due.isNotEmpty()) {
                val done = due.count { it.id in doneIds }
                Text("$done/${due.size}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (done == due.size) p.accent else p.ink2)
            }
        }
        if (all.isEmpty()) {
            ChipFlow(modifier = Modifier.padding(top = 10.dp)) {
                SUGGESTED_SUPPLEMENTS.forEach { PaperPill("+ $it", false, { preset = it }) }
            }
            Text("Add what you take, then tick it off each day.", fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.padding(top = 10.dp))
        } else if (due.isEmpty()) {
            Text("Nothing due this day.", fontSize = 12.5.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
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
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "+ Add supplement", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = p.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { preset = "" }.padding(vertical = 6.dp, horizontal = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            if (all.isNotEmpty()) {
                Text(
                    "Manage", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = p.ink2,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { managing = true }.padding(vertical = 6.dp, horizontal = 2.dp),
                )
            }
        }
    }
    if (managing) SupplementsWindow(x, all, onClose = { managing = false })
    preset?.let { SupplementEditor(x, null, onDismiss = { preset = null }, preset = it) }
}

private val SUGGESTED_SUPPLEMENTS = listOf("Creatine", "Vitamin D", "Omega-3", "Magnesium", "Multivitamin", "Caffeine")

@Composable
private fun SupplementsWindow(x: FoodExtrasViewModel, all: List<SupplementEntity>, onClose: () -> Unit) {
    val p = LocalPaper.current
    var editing by remember { mutableStateOf<SupplementEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var remind by remember { mutableStateOf(FoodSettings.suppReminders(context)) }
    EditWindow(title = "Supplements", sub = "Tap one to change it", onClose = onClose) {
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Remind me", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(
                        "A notification at each supplement's time, on the days it is due, with Taken and Snooze buttons. " +
                            "It stays quiet if you already ticked it. Supplements set to \"Any time\" do not ring. " +
                            "Notifications and \"Alarms and reminders\" must be allowed in Settings > Permissions.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
                PaperToggle(remind, { remind = it; FoodSettings.setSuppReminders(context, it) })
            }
        }
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
private fun SupplementEditor(x: FoodExtrasViewModel, initial: SupplementEntity?, onDismiss: () -> Unit, preset: String = "") {
    val p = LocalPaper.current
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: preset) }
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
    val recent = weights.takeLast(7)

    ChartCard(
        icon = R.drawable.ic_p_scale,
        color = WATER_COLOR,
        title = "Body weight",
        value = if (latest == null) "-" else foodNum(latest.kg),
        unit = if (latest == null) "" else "kg",
        lines = if (latest == null) {
            listOf("No weigh-in yet.", "Tap to weigh in")
        } else {
            listOfNotNull(
                if (goal > 0) "Goal " + foodNum(goal) + " kg" else "No goal set",
                if (goal > 0) WeightMath.toGoal(latest.kg, goal) else null,
                pace?.let { (if (it > 0) "+" else "") + (Math.round(it * 100) / 100.0) + " kg a week lately" },
            )
        },
        onClick = { logging = true },
        chart = if (recent.size < 2) null else {
            {
                ValueLineChart(
                    values = recent.map { it.kg },
                    labels = recent.map { w -> runCatching { LocalDate.parse(w.date).let { "${it.dayOfMonth}/${it.monthValue}" } }.getOrDefault("") },
                    color = WATER_COLOR,
                    goal = if (goal > 0) goal else null,
                    highlight = recent.size - 1,
                    fromZero = false,
                    height = 130.dp,
                )
            }
        },
    )
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
fun WeekCard(x: FoodExtrasViewModel, date: LocalDate, onOpen: (() -> Unit)? = null) {
    val p = LocalPaper.current
    val meals by x.weekMeals.collectAsState()
    val goals by x.goals.collectAsState()
    val week = FoodLogic.weekOf(date)
    val today = LocalDate.now()
    val summary = WeekMath.summary(meals) { day -> FoodStats.goalFor(goals, LocalDate.parse(day)) }
    val byDay = meals.groupBy { it.date }
    val goalKcal = FoodStats.goalFor(goals, date)?.kcal?.takeIf { it > 0 }
    val none = summary.loggedDays == 0
    ChartCard(
        icon = R.drawable.ic_p_chart,
        color = p.accent,
        title = "This week",
        value = if (none) "-" else kc(summary.avgKcal),
        unit = if (none) "" else "kcal a day",
        lines = if (none) listOf("Nothing logged yet.") else listOf("${kc(summary.avgProtein)} g protein a day", "${summary.daysOnTarget} of ${summary.loggedDays} days on target"),
        chart = if (none) null else {
            {
                ValueLineChart(
                    values = week.map { d -> if (d.isAfter(today)) null else byDay[d.toString()]?.sumOf { it.kcal } },
                    labels = week.map { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) },
                    color = p.accent,
                    goal = goalKcal,
                    highlight = week.indexOf(date),
                    height = 130.dp,
                    tone = { v -> if (goalKcal == null) p.accent else if (v > goalKcal * 1.1) p.bad else if (v >= goalKcal * 0.9) p.accent else p.sun },
                )
            }
        },
        onClick = onOpen,
    )
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by vm.library.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var describe by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    val speak = rememberSpeak { describe = it }
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
    PaperField(describe, { describe = it; report = null }, "Or say what is in it", placeholder = "200 g rice, 150 g chicken", singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaperButton("Speak", { speak() }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true)
        PaperButton(
            if (working) "Adding..." else "Add them",
            {
                scope.launch {
                    working = true
                    report = null
                    val lib = library
                    val found = ArrayList<SavedMealItem>()
                    val rest = ArrayList<IngredientText.Part>()
                    // An ingredient with an amount is looked up in your foods first; only the rest is sent to the AI.
                    for (part in IngredientText.parse(describe)) {
                        val grams = part.grams
                        val food = if (grams != null && lib != null) lib.search(part.name, null, 3).firstOrNull { IngredientText.accepts(it.name, part.name) } else null
                        if (food != null && grams != null && lib != null) {
                            found += itemOf(vm.withYourNumbers(FoodLogic.of(food, lib.microOf(food.id))), grams)
                        } else {
                            rest += part
                        }
                    }
                    var estimated = 0
                    var problem: String? = null
                    if (rest.isNotEmpty()) {
                        try {
                            val answer = FoodPhotoAi.analyzeText(
                                context, rest.joinToString(", ") { it.original },
                                "These are the ingredients of one recipe. Give one item for each ingredient and keep the weights I wrote.", false,
                            )
                            val fromAi = IngredientText.itemsFrom(answer)
                            found += fromAi
                            estimated = fromAi.size
                        } catch (e: PhotoAiException) {
                            problem = "Could not estimate " + rest.joinToString(", ") { it.original } + ": " + e.message
                        }
                    }
                    if (found.isNotEmpty()) {
                        onChange(items + found)
                        describe = ""
                    }
                    val fromFoods = found.size - estimated
                    report = listOfNotNull(
                        if (found.isEmpty() && problem == null) "Nothing to add. Write what is in it, for example 200 g rice, 150 g chicken." else null,
                        if (fromFoods > 0) "$fromFoods from your foods." else null,
                        if (estimated > 0) "$estimated estimated by the AI, so check their grams and numbers." else null,
                        problem,
                    ).joinToString(" ")
                    working = false
                }
            },
            Modifier.weight(1f), small = true, enabled = describe.isNotBlank() && !working,
        )
    }
    report?.let { Text(it, fontSize = 12.sp, color = p.ink2) }
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Meal prep", action = "New batch", onAction = { creating = true })
        if (batches.isEmpty()) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Text("Cooked a big pot? Log it once, then say how much you ate.", fontSize = 12.5.sp, color = p.ink2)
            }
        }
        batches.forEach { b ->
            val share = BatchMath.leftShare(b)
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false, onClick = { opened = b }) {
                Row {
                    Text(b.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(
                        if (BatchMath.finished(b)) "Finished" else kc(b.leftGrams) + " of " + kc(BatchMath.totalGrams(b)) + " g left",
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2)) {
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
    var savedAsFood by remember { mutableStateOf(false) }
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
            PaperButton(
                if (savedAsFood) "Saved in My foods" else "Save as a food",
                { val (entity, micro) = BatchMath.asOwnFood(batch); vm.saveFood(entity, micro); savedAsFood = true },
                Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost, small = true, enabled = !savedAsFood,
            )
            Text(
                "Makes this recipe a food of your own, so you can find it by name and log a serving (" + kc(food.portionGrams) + " g) any time.",
                fontSize = 12.sp, color = p.ink2,
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Meal plans", action = "New plan", onAction = { creating = true })
        if (plans.isEmpty()) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Text("Plan a day of meals, then log it with one tap.", fontSize = 12.5.sp, color = p.ink2)
            }
        }
        plans.forEach { plan ->
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false, onClick = { opened = plan }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(kc(PlanMath.kcal(plan)) + " kcal", fontSize = 12.sp, color = p.ink2)
                }
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
