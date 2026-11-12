package com.lifetracker.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.BurnedMath
import com.lifetracker.app.data.HealthConnectSync
import com.lifetracker.app.ui.theme.PaperToggle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodPart
import com.lifetracker.app.data.FoodPartOrder
import com.lifetracker.app.data.FoodSettings
import com.lifetracker.app.data.FoodRef
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.Loggable
import com.lifetracker.app.data.FoodGoalEntity
import com.lifetracker.app.data.FoodView
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.MonthMath
import com.lifetracker.app.data.NutrientMath
import com.lifetracker.app.data.QualityMath
import com.lifetracker.app.data.SavedMealItems
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private fun n(value: Double): String = Math.round(value).toString()

val MEAL_TYPES = FoodLogic.MEAL_TYPES

fun mealTypeLabel(type: String): String = type.replaceFirstChar { it.uppercase() }

/** The colour of each meal type, as in the paper demo. */
fun mealColor(type: String): Color = when (type) {
    "breakfast" -> Color(0xFFE0A100)
    "lunch" -> Color(0xFF17785A)
    "dinner" -> Color(0xFF5B5FC7)
    else -> Color(0xFFD6457F)
}

private val CARB = Color(0xFFE0A100)
private val PROTEIN = Color(0xFF5B5FC7)
private val FAT = Color(0xFFD6457F)

/** What is open on top of the Food screen. */
private sealed interface Sheet {
    data class Add(val meal: String, val focus: Boolean) : Sheet
    data class Log(val food: Loggable, val meal: String) : Sheet
    data class Quick(val meal: String) : Sheet
    data class Entry(val meal: MealEntity) : Sheet
    data class MealMenu(val meal: String) : Sheet
    data object Favourites : Sheet
    data object SavedMeals : Sheet
    data object Goals : Sheet
    data object Trackers : Sheet
    data object Habits : Sheet
    data object Week : Sheet
}

@Composable
fun FoodScreen(vm: FoodViewModel = viewModel(), x: FoodExtrasViewModel = viewModel()) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val date by vm.date.collectAsState()
    val meals by vm.meals.collectAsState()
    val goal by vm.goal.collectAsState()
    val water by vm.waterMl.collectAsState()
    val recent by vm.recent.collectAsState()
    val library by vm.library.collectAsState()
    val favourites by vm.favourites.collectAsState()
    val savedMeals by vm.savedMeals.collectAsState()
    val nutrients by vm.nutrients.collectAsState()
    val tick by FoodSettings.changes.collectAsState()
    val weights by x.weights.collectAsState()
    val eventDays by x.eventDays.collectAsState()
    val weekMeals by x.weekMeals.collectAsState()
    val allGoals by x.goals.collectAsState()
    val shown = remember(tick) { FoodPart.entries.associateWith { FoodSettings.isOn(context, it) } }
    val order = remember(tick) { FoodSettings.order(context) }
    LaunchedEffect(date) { x.setDay(date) }
    val tapped = eventDays
    val pickedHabits by x.trackers.collectAsState()
    val checks by x.checks.collectAsState()
    val monthMeals by x.monthMeals.collectAsState()
    val diary by x.diary.collectAsState()
    val diarySpan by x.diarySpanDays.collectAsState()
    // The view you used last is the one the tab opens on. Opening a day from the timeline goes to the week without remembering that.
    var view by remember { mutableStateOf(FoodSettings.foodView(context)) }
    var month by remember { mutableStateOf(YearMonth.from(date)) }
    var offHabits by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(date) { month = YearMonth.from(date) }
    LaunchedEffect(month) { x.setMonth(month) }

    val today = LocalDate.now()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var openMeal by remember { mutableStateOf(FoodLogic.mealTypeFor(java.time.LocalTime.now().let { it.hour * 60 + it.minute })) }
    val totals = FoodStats.totals(meals)
    val extras = FoodLogic.extras(meals)
    // Calories burned: what you typed, else (when switched on) what the phone measured.
    val weekDays = remember(date) { FoodLogic.weekOf(date) }
    val stepRows by remember(date) { AppDatabase.get(context).stepDao().daysBetween(weekDays.first().toString(), weekDays.last().toString()) }
        .collectAsState(emptyList())
    val stepsByDay = remember(stepRows) { stepRows.associate { it.date to it.steps } }
    val bodyKg = weights.lastOrNull()?.kg ?: 70.0
    val burned = remember(tick, date, stepsByDay, bodyKg) {
        FoodSettings.burnedFor(context, date.toString(), stepsByDay[date.toString()] ?: 0, bodyKg).kcal
    }
    var askBurned by remember { mutableStateOf(false) }
    val ringPct = remember(weekMeals, allGoals, tick, stepsByDay, bodyKg) { ringOf(context, weekMeals, allGoals, stepsByDay, bodyKg) }
    val monthSpan = remember(month) { MonthMath.span(month) }
    val monthStepRows by remember(month) { AppDatabase.get(context).stepDao().daysBetween(monthSpan.first, monthSpan.second) }
        .collectAsState(emptyList())
    val monthSteps = remember(monthStepRows) { monthStepRows.associate { it.date to it.steps } }
    val monthRing = remember(monthMeals, allGoals, tick, monthSteps, bodyKg) { ringOf(context, monthMeals, allGoals, monthSteps, bodyKg) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenHeader(
                    label = "Food",
                    badge = "Saved on this phone",
                    title = TimelineTop.title(date, today),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaperIconButton(R.drawable.ic_p_cal, "Pick a day", onClick = {
                                DatePickerDialog(
                                    context, { _, y, m, d -> vm.select(LocalDate.of(y, m + 1, d)) },
                                    date.year, date.monthValue - 1, date.dayOfMonth,
                                ).show()
                            })
                            PaperIconButton(R.drawable.ic_p_list, "What to show", onClick = { sheet = Sheet.Trackers })
                            PaperIconButton(R.drawable.ic_p_gear, "Goals", onClick = { sheet = Sheet.Goals })
                        }
                    },
                )
            }
            item {
                FoodViewSwitch(view) { picked -> view = picked; FoodSettings.setFoodView(context, picked) }
            }
            when (view) {
                FoodView.WEEK -> item { WeekStrip(date, today, ringPct, tapped, onPick = vm::select, onShift = { vm.shift(it * 7L) }) }
                FoodView.MONTH -> item {
                    MonthView(
                        month = month, date = date, today = today, ringPct = monthRing,
                        trackers = pickedHabits, checks = checks, off = offHabits,
                        onToggleHabit = { id -> offHabits = if (id in offHabits) offHabits - id else offHabits + id },
                        onMonth = { month = it }, onPick = vm::select, onPickHabits = { sheet = Sheet.Habits },
                    )
                }
                FoodView.TIMELINE -> foodTimeline(
                    days = diary, trackers = pickedHabits, today = today, spanDays = diarySpan,
                    onDay = { vm.select(it); view = FoodView.WEEK }, onEarlier = x::showEarlier, onPickHabits = { sheet = Sheet.Habits },
                )
            }
            if (view != FoodView.TIMELINE) {
            item {
                HeroCard(totals.kcal, totals.carbs, totals.protein, totals.fat, goal?.kcal, goal?.carbs, goal?.protein, goal?.fat, burned, onBurned = { askBurned = true })
            }
            item {
                SearchRow(
                    onSearch = { sheet = Sheet.Add(openMeal, focus = true) },
                    onNew = { sheet = Sheet.Add(openMeal, focus = false) },
                )
            }
            item {
                FoodShelf(recent, library, onPick = { sheet = Sheet.Log(it, openMeal) })
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FolderCard(
                        title = "Favourites",
                        detail = if (favourites.isEmpty()) "Star a food to keep it here" else "${favourites.size} " + if (favourites.size == 1) "food" else "foods",
                        photos = favourites.take(3).map { f ->
                            val food = vm.loggable(f.foodId)
                            (food?.photoAsset) to (food?.name ?: "?")
                        },
                        modifier = Modifier.weight(1f),
                        onClick = { sheet = Sheet.Favourites },
                    )
                    FolderCard(
                        title = "Saved meals",
                        detail = if (savedMeals.isEmpty()) "Save a meal from its menu" else "${savedMeals.size} " + if (savedMeals.size == 1) "meal" else "meals",
                        photos = savedMeals.take(3).map { m ->
                            val first = SavedMealItems.decode(m.items).firstOrNull()
                            (first?.let { library?.byRef(it.foodId)?.photoAsset }) to m.name
                        },
                        modifier = Modifier.weight(1f),
                        onClick = { sheet = Sheet.SavedMeals },
                    )
                }
            }
            item {
                MealTabs(
                    selected = openMeal,
                    counts = MEAL_TYPES.associateWith { t -> meals.count { it.mealType == t } },
                    onSelect = { openMeal = it },
                )
            }
            item {
                val shown = meals.filter { it.mealType == openMeal }
                MealCard(
                    type = openMeal,
                    meals = shown,
                    library = library,
                    onAdd = { sheet = Sheet.Add(openMeal, focus = false) },
                    onQuick = { sheet = Sheet.Quick(openMeal) },
                    onMenu = { sheet = Sheet.MealMenu(openMeal) },
                    onEntry = { sheet = Sheet.Entry(it) },
                )
            }
            var pairDone = false
            FoodPartOrder.sorted(order, FoodPartOrder.TOP).forEach { part ->
                when (part) {
                    FoodPart.FASTING -> if (shown[part] == true) item { FastingCard(x) }
                    // Water and the shake share one row, which sits where the higher of the two is.
                    FoodPart.WATER, FoodPart.SHAKE -> if (!pairDone && (shown[FoodPart.WATER] == true || shown[FoodPart.SHAKE] == true)) {
                        pairDone = true
                        item {
                            val waterTile: (@Composable (Modifier) -> Unit)? =
                                if (shown[FoodPart.WATER] == true) { m -> WaterTile(water, onAdd = vm::addWater, modifier = m) } else null
                            val shakeTile: (@Composable (Modifier) -> Unit)? =
                                if (shown[FoodPart.SHAKE] == true) { m -> ShakeCard(vm, openMeal, totals.protein, goal?.protein, modifier = m) } else null
                            TilePair(waterTile, shakeTile)
                        }
                    }
                    FoodPart.SUPPLEMENTS -> if (shown[part] == true) item { SupplementsCard(x, date) }
                    FoodPart.TRACKERS -> if (shown[part] == true) item { TrackersCard(x, date) }
                    FoodPart.PREP -> if (shown[part] == true) item { Spacer(Modifier.height(6.dp)); PrepCard(vm, x, openMeal) }
                    FoodPart.PLANS -> if (shown[part] == true) item { Spacer(Modifier.height(6.dp)); PlansCard(vm, x, date, meals) }
                    else -> Unit
                }
            }

            item { Spacer(Modifier.height(6.dp)); SectionHeading("Details") }
            FoodPartOrder.sorted(order, FoodPartOrder.DETAILS).forEach { part ->
                when (part) {
                    FoodPart.EXTRAS -> if (shown[part] == true) item { DetailGrid(totals.protein, QualityMath.animalShare(meals), extras.fiber, extras.sugar, extras.sodiumMg) }
                    FoodPart.VITAMINS -> if (shown[part] == true) item {
                        val dayTotals = NutrientMath.dayTotals(meals)
                        VitaminsCard(
                            nutrients = nutrients.filter { !it.hidden },
                            totals = dayTotals,
                            anyMeals = meals.isNotEmpty(),
                            anyMissing = meals.any { NutrientMath.isMissing(it) },
                        )
                    }
                    FoodPart.WATCH -> if (shown[part] == true) item { NutrientWatchCard(nutrients.filter { !it.hidden }, library) }
                    FoodPart.QUALITY -> if (shown[part] == true) item { QualityCard(meals, weights.lastOrNull()?.kg ?: 70.0) }
                    FoodPart.FATS -> if (shown[part] == true) item { FatsCard(meals, goal) }
                    FoodPart.WEEK -> if (shown[part] == true) item { WeekCard(x, date, onOpen = { sheet = Sheet.Week }) }
                    FoodPart.WEIGHT -> if (shown[part] == true) item { WeightCard(x, date) }
                    else -> Unit
                }
            }
            }
        }
    }

    if (askBurned) {
        val day = date.toString()
        var text by remember { mutableStateOf(FoodSettings.burnedKcal(context, day).let { if (it > 0) it.toString() else "" }) }
        var auto by remember { mutableStateOf(FoodSettings.autoBurned(context)) }
        var allowed by remember { mutableStateOf<Boolean?>(null) }
        val scope = rememberCoroutineScope()
        val hcAvailable = remember { HealthConnectSync.status(context) == HealthConnectClient.SDK_AVAILABLE }
        val ask = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            allowed = HealthConnectSync.CALORIES_PERMISSION in granted
            if (allowed == true) scope.launch { HealthConnectSync.sync(context) }
        }
        LaunchedEffect(Unit) { allowed = hcAvailable && HealthConnectSync.CALORIES_PERMISSION in HealthConnectSync.granted(context) }
        val ink2 = LocalPaper.current.ink2
        PaperDialog(
            title = "Calories burned",
            onDismiss = { askBurned = false },
            confirmText = "Save",
            onConfirm = { FoodSettings.setBurnedKcal(context, day, text.toIntOrNull() ?: 0); askBurned = false },
        ) {
            Text("Type what you burned on this day (a watch or an app tells you). It is added to that day's calories, and a number you type always wins.", fontSize = 13.sp, color = ink2)
            PaperField(text, { text = it }, "Calories burned (kcal)", Modifier.padding(top = 8.dp), singleLine = true, keyboardOptions = DecimalKeys)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Count what my phone measured", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LocalPaper.current.ink)
                    Text("Health Connect's active calories, or an estimate from your steps and weight. Applies at once.", fontSize = 12.sp, color = ink2)
                }
                PaperToggle(auto, { auto = it; FoodSettings.setAutoBurned(context, it) })
            }
            if (auto) {
                val phone = BurnedMath.pick(0, FoodSettings.healthKcal(context, day), stepsByDay[day] ?: 0, bodyKg)
                Text(
                    when (phone.source) {
                        BurnedMath.Source.HEALTH_CONNECT -> "Health Connect says ${phone.kcal} kcal for this day."
                        BurnedMath.Source.STEPS -> "Estimated at ${phone.kcal} kcal from ${stepsByDay[day]} steps at ${Math.round(bodyKg)} kg."
                        else -> "Nothing from your phone for this day yet."
                    },
                    fontSize = 12.sp, color = ink2, modifier = Modifier.padding(top = 6.dp),
                )
                if (hcAvailable && allowed == false) {
                    PaperButton(
                        "Allow Health Connect to share calories", { ask.launch(setOf(HealthConnectSync.CALORIES_PERMISSION)) },
                        Modifier.padding(top = 6.dp), style = PaperButtonStyle.Ghost, small = true,
                    )
                }
            }
        }
    }

    when (val s = sheet) {
        null -> Unit
        is Sheet.Add -> AddFoodWindow(vm = vm, startMeal = s.meal, focus = s.focus, onClose = { sheet = null })
        is Sheet.Log -> LogFoodSheet(
            vm = vm, food = s.food, startMeal = s.meal, onDone = { sheet = null }, onDismiss = { sheet = null },
        )
        is Sheet.Quick -> QuickAddSheet(vm = vm, startMeal = s.meal, onDone = { sheet = null }, onDismiss = { sheet = null })
        is Sheet.Entry -> EntrySheet(vm = vm, meal = s.meal, library = library, onDismiss = { sheet = null })
        is Sheet.MealMenu -> MealMenuSheet(
            vm = vm, mealType = s.meal, meals = meals.filter { it.mealType == s.meal }, onDismiss = { sheet = null },
        )
        Sheet.Favourites -> FavouritesSheet(
            vm = vm, onPick = { sheet = Sheet.Log(it, openMeal) }, onDismiss = { sheet = null },
        )
        Sheet.SavedMeals -> SavedMealsSheet(vm = vm, mealType = openMeal, onDone = { sheet = null }, onDismiss = { sheet = null })
        Sheet.Goals -> GoalsSheet(vm = vm, onDismiss = { sheet = null })
        Sheet.Trackers -> TrackersSheet(onDismiss = { sheet = null })
        Sheet.Habits -> HabitPickerWindow(x, onClose = { sheet = null })
        Sheet.Week -> WeekWindow(x, date, onShiftWeek = { vm.shift(it.toLong()) }, onPickDay = { vm.select(it) }, onClose = { sheet = null })
    }
}

/** The share of each day's calorie budget that was eaten, for the rings. The budget is the goal plus what was burned. */
private fun ringOf(
    context: android.content.Context, meals: List<MealEntity>, goals: List<FoodGoalEntity>, steps: Map<String, Int>, bodyKg: Double,
): (LocalDate) -> Float {
    val byDay = meals.groupBy { it.date }
    return { d: LocalDate ->
        val budget = (FoodStats.goalFor(goals, d)?.kcal ?: 0.0) + FoodSettings.burnedFor(context, d.toString(), steps[d.toString()] ?: 0, bodyKg).kcal
        if (budget <= 0) 0f else ((byDay[d.toString()]?.sumOf { it.kcal } ?: 0.0) / budget).toFloat()
    }
}

// ---- the week ----

@Composable
private fun WeekStrip(date: LocalDate, today: LocalDate, ringPct: (LocalDate) -> Float, tapped: Set<String>, onPick: (LocalDate) -> Unit, onShift: (Int) -> Unit) {
    val p = LocalPaper.current
    val week = FoodLogic.weekOf(date)
    Row(
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total > 120f) onShift(-1) else if (total < -120f) onShift(1) },
            ) { _, delta -> total += delta }
        },
    ) {
        week.forEach { day ->
            val isToday = day == today
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { onPick(day) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (isToday) "Today" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1).uppercase(),
                    fontSize = if (isToday) 12.5.sp else 10.5.sp,
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isToday) p.ink else p.ink2,
                    maxLines = 1,
                )
                DayRing(day.dayOfMonth, ringPct(day), selected = day == date, future = day.isAfter(today))
                Box(modifier = Modifier.size(5.dp).background(if (day.toString() in tapped) p.sun else Color.Transparent, CircleShape))
            }
        }
    }
}

// ---- the day at a glance ----

@Composable
private fun HeroCard(
    kcal: Double, carbs: Double, protein: Double, fat: Double,
    goalKcal: Double?, goalCarbs: Double?, goalProtein: Double?, goalFat: Double?,
    burned: Int, onBurned: () -> Unit,
) {
    val p = LocalPaper.current
    val hasGoal = goalKcal != null && goalKcal > 0
    val budget = (goalKcal ?: 0.0) + burned
    val left = budget - kcal
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SideStat(R.drawable.ic_p_fork, p.accent, n(kcal), "Eaten", Modifier.weight(1f))
            Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                val track = p.surface2
                val ink = if (hasGoal && left < 0) p.bad else p.accent
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 13.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (hasGoal && kcal > 0) {
                        val sweep = (360f * (kcal / budget).toFloat()).coerceIn(2f, 360f)
                        drawArc(ink, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hasGoal) {
                        Text(if (left >= 0) "Remaining" else "Over", fontSize = 12.sp, color = if (left >= 0) p.ink2 else p.bad)
                        Text(n(Math.abs(left)), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                        Text("of ${n(budget)} kcal", fontSize = 11.5.sp, color = p.ink2)
                    } else {
                        Text(n(kcal), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                        Text("kcal eaten", fontSize = 11.5.sp, color = p.ink2)
                        Text("No goal yet", fontSize = 11.sp, color = p.ink2)
                    }
                }
            }
            SideStat(R.drawable.ic_p_fire, p.sun, n(burned.toDouble()), "Burned", Modifier.weight(1f), onClick = onBurned)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MacroBar("Carbs", carbs, goalCarbs, CARB, Modifier.weight(1f))
            MacroBar("Protein", protein, goalProtein, PROTEIN, Modifier.weight(1f))
            MacroBar("Fat", fat, goalFat, FAT, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SideStat(icon: Int, tint: Color, value: String, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPaper.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PaperIcon(icon, size = 16.dp, tint = tint)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MacroBar(label: String, value: Double, goal: Double?, color: Color, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = n(value) + if (goal != null && goal > 0) "/${n(goal)} g" else " g",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1,
        )
        Box(modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)).background(p.surface2)) {
            val fraction = if (goal != null && goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f
            if (fraction > 0f) Box(modifier = Modifier.fillMaxWidth(fraction).height(7.dp).background(color))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink)
        }
    }
}

// ---- search, shelf and folders ----

@Composable
private fun SearchRow(onSearch: () -> Unit, onNew: () -> Unit) {
    val p = LocalPaper.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .background(p.surface, RoundedCornerShape(25.dp))
                .border(1.25.dp, p.pen, RoundedCornerShape(25.dp))
                .clip(RoundedCornerShape(25.dp))
                .clickable(role = Role.Button, onClick = onSearch)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaperIcon(R.drawable.ic_p_search, size = 18.dp, tint = p.ink2)
            Text("Search 561 foods", color = p.ink2, fontSize = 14.sp, maxLines = 1)
        }
        PaperIconButton(R.drawable.ic_p_plus, "Add food", onClick = onNew, modifier = Modifier.size(50.dp))
    }
}

@Composable
private fun FoodShelf(recent: List<MealEntity>, library: com.lifetracker.app.data.FoodLibraryData?, onPick: (Loggable) -> Unit) {
    val p = LocalPaper.current
    if (recent.isEmpty()) {
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Text("Foods you log show up here, so you can add them again in one tap.", fontSize = 13.sp, color = p.ink2)
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        recent.forEach { meal ->
            val food = FoodLogic.ofMeal(meal, library)
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { onPick(food) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FoodPhoto(food.photoAsset, food.name, 62.dp)
                Text(food.name, fontSize = 12.sp, color = p.ink, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun FolderCard(title: String, detail: String, photos: List<Pair<String?, String>>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPaper.current
    PaperCard(modifier = modifier, padding = 12.dp, onClick = onClick) {
        Row(modifier = Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (photos.isEmpty()) {
                PaperIcon(R.drawable.ic_p_folder, size = 30.dp, tint = p.ink2)
            } else {
                FoodPlate(photos, 40.dp)
            }
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.padding(top = 8.dp))
        Text(detail, fontSize = 12.sp, color = p.ink2, maxLines = 2)
    }
}

// ---- meals ----

@Composable
private fun MealTabs(selected: String, counts: Map<String, Int>, onSelect: (String) -> Unit) {
    val p = LocalPaper.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        MEAL_TYPES.forEach { type ->
            val on = type == selected
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Tab) { onSelect(type) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    mealTypeLabel(type),
                    fontSize = if (on) 21.sp else 16.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (on) p.ink else p.ink2,
                )
                if (!on && (counts[type] ?: 0) > 0) {
                    Box(Modifier.padding(start = 3.dp, top = 2.dp).size(6.dp).background(mealColor(type), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun MealCard(
    type: String,
    meals: List<MealEntity>,
    library: com.lifetracker.app.data.FoodLibraryData?,
    onAdd: () -> Unit,
    onQuick: () -> Unit,
    onMenu: () -> Unit,
    onEntry: (MealEntity) -> Unit,
) {
    val p = LocalPaper.current
    val total = FoodStats.totals(meals)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onAdd)) {
                FoodPlate(meals.reversed().map { library?.byRef(it.foodId)?.photoAsset to it.name }, 56.dp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(n(total.kcal), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                    Text("kcal", fontSize = 13.sp, color = p.ink2, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    if (meals.isEmpty()) "Nothing logged" else "${meals.size} " + if (meals.size == 1) "item" else "items",
                    fontSize = 12.sp, color = p.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MacroPill("P", total.protein, PROTEIN)
                    MacroPill("C", total.carbs, CARB)
                    MacroPill("F", total.fat, FAT)
                }
            }
            PaperIconButton(R.drawable.ic_p_list, "Options for ${mealTypeLabel(type)}", onClick = onMenu, modifier = Modifier.size(36.dp))
        }
        if (meals.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                meals.forEach { meal -> EntryRow(meal, library?.byRef(meal.foodId)?.photoAsset, onClick = { onEntry(meal) }) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaperButton("Add to ${type}", onAdd, modifier = Modifier.weight(1f), icon = R.drawable.ic_p_plus)
            PaperIconButton(R.drawable.ic_p_bolt, "Quick add", onClick = onQuick, modifier = Modifier.size(46.dp))
        }
    }
}

@Composable
private fun MacroPill(letter: String, grams: Double, color: Color) {
    val p = LocalPaper.current
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(letter, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        Text(n(grams), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

@Composable
private fun EntryRow(meal: MealEntity, photo: String?, onClick: () -> Unit) {
    val p = LocalPaper.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MealThumb(meal.id, photo, meal.name, 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(meal.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
            Text(n(meal.grams) + " g", fontSize = 12.sp, color = p.ink2)
        }
        Text(n(meal.kcal) + " kcal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

// ---- details ----

@Composable
private fun DetailGrid(protein: Double, animalShare: Double?, fiber: Double, sugar: Double, sodiumMg: Double) {
    val p = LocalPaper.current
    val animal = (animalShare ?: 0.0) * protein
    val cards = listOf<@Composable (Modifier) -> Unit>(
        { m ->
            RingCard(
                R.drawable.ic_p_bolt, p.sun, "Animal protein", n(animal), "g",
                if (protein <= 0) "g" else n(animal / protein * 100) + " %", if (protein <= 0) 0f else (animal / protein).toFloat(), m,
            )
        },
        { m -> RingCard(R.drawable.ic_p_leaf, p.accent, "Fibre", n(fiber), "g", "/ 30 g", (fiber / 30).toFloat(), m) },
        { m -> RingCard(R.drawable.ic_p_cube, if (sugar > 50) p.bad else CARB, "Sugar", n(sugar), "g", "/ 50 g", (sugar / 50).toFloat(), m) },
        { m ->
            RingCard(
                R.drawable.ic_p_grain, if (sodiumMg > 2300) p.bad else Color(0xFF2878B4), "Sodium",
                (Math.round(sodiumMg / 100) / 10.0).toString(), "g", "/ 2.3 g", (sodiumMg / 2300).toFloat(), m,
            )
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { RingGrid(cards) }
}



@Composable
private fun VitaminsCard(
    nutrients: List<com.lifetracker.app.data.NutrientEntity>,
    totals: Map<String, Double>,
    anyMeals: Boolean,
    anyMissing: Boolean,
) {
    val p = LocalPaper.current
    var all by remember { mutableStateOf(false) }
    val rows = if (all) nutrients else NutrientMath.shown(nutrients, totals)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vitamins and minerals", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f))
            if (nutrients.isNotEmpty()) PaperPill(if (all) "Only eaten" else "Show all", all, { all = !all })
        }
        when {
            !anyMeals -> Text("Log some food to see them.", fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 6.dp))
            rows.isEmpty() -> Text(
                "No vitamin numbers for what you ate yet. Open a food and tap Vitamins and minerals to type them in. Settings, Trackers, Vitamins and minerals lets you change the list.",
                fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 6.dp),
            )
            else -> Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                rows.forEach { nu ->
                    val eaten = totals[nu.id] ?: 0.0
                    val share = NutrientMath.fraction(eaten, nu.target)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(nu.name, fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(
                                text = amountShort(eaten) + " " + nu.unit + (if (nu.target > 0) " of ${amountShort(nu.target)}" else ""),
                                fontSize = 12.sp, color = p.ink2,
                            )
                        }
                        if (share != null) {
                            Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2)) {
                                val f = share.toFloat().coerceIn(0f, 1f)
                                if (f > 0f) Box(modifier = Modifier.fillMaxWidth(f).height(6.dp).background(if (share >= 1.0) p.accent else p.sun))
                            }
                        }
                    }
                }
                if (anyMissing) Text("Some foods have no vitamin numbers, so these totals can be too low.", fontSize = 11.5.sp, color = p.ink2)
            }
        }
    }
}

internal fun amountShort(v: Double): String = when {
    v >= 100 -> Math.round(v).toString()
    v >= 10 -> (Math.round(v * 10) / 10.0).toString().removeSuffix(".0")
    else -> (Math.round(v * 100) / 100.0).toString().removeSuffix(".0")
}
