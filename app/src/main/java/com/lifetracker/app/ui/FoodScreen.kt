package com.lifetracker.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodRef
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.Loggable
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.NutrientMath
import com.lifetracker.app.data.SavedMealItems
import com.lifetracker.app.ui.theme.HighlightedHeading
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill
import java.time.LocalDate
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
}

@Composable
fun FoodScreen(vm: FoodViewModel = viewModel()) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val date by vm.date.collectAsState()
    val meals by vm.meals.collectAsState()
    val goal by vm.goal.collectAsState()
    val water by vm.waterMl.collectAsState()
    val recent by vm.recent.collectAsState()
    val loggedDays by vm.datesWithMeals.collectAsState()
    val library by vm.library.collectAsState()
    val favourites by vm.favourites.collectAsState()
    val savedMeals by vm.savedMeals.collectAsState()
    val nutrients by vm.nutrients.collectAsState()

    val today = LocalDate.now()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var openMeal by remember { mutableStateOf(FoodLogic.mealTypeFor(java.time.LocalTime.now().let { it.hour * 60 + it.minute })) }
    val totals = FoodStats.totals(meals)
    val extras = FoodLogic.extras(meals)

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
                            PaperIconButton(R.drawable.ic_p_gear, "Goals", onClick = { sheet = Sheet.Goals })
                        }
                    },
                )
            }
            item { WeekStrip(date, today, loggedDays, onPick = vm::select, onShift = { vm.shift(it * 7L) }) }
            item {
                HeroCard(totals.kcal, totals.carbs, totals.protein, totals.fat, goal?.kcal, goal?.carbs, goal?.protein, goal?.fat)
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
            item { WaterCard(water, onAdd = vm::addWater) }

            item { HighlightedHeading(text = "Details") }
            item { ExtrasCard(extras.fiber, extras.sugar, extras.sodiumMg) }
            item {
                val dayTotals = NutrientMath.dayTotals(meals)
                VitaminsCard(
                    nutrients = nutrients.filter { !it.hidden },
                    totals = dayTotals,
                    anyMeals = meals.isNotEmpty(),
                    anyMissing = meals.any { NutrientMath.isMissing(it) },
                )
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
    }
}

// ---- the week ----

@Composable
private fun WeekStrip(date: LocalDate, today: LocalDate, logged: Set<String>, onPick: (LocalDate) -> Unit, onShift: (Int) -> Unit) {
    val p = LocalPaper.current
    val week = FoodLogic.weekOf(date)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PaperIconButton(R.drawable.ic_p_back, "Last week", onClick = { onShift(-1) }, modifier = Modifier.size(32.dp))
        week.forEach { day ->
            val on = day == date
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { onPick(day) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), fontSize = 11.sp, color = p.ink2)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .then(if (on) Modifier.background(p.ink, CircleShape) else if (day == today) Modifier.border(1.5.dp, p.ink, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(day.dayOfMonth.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) p.bg else p.ink)
                }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(if (day.toString() in logged) p.accent else Color.Transparent, CircleShape),
                )
            }
        }
        PaperIconButton(R.drawable.ic_p_chev, "Next week", onClick = { onShift(1) }, modifier = Modifier.size(32.dp))
    }
}

// ---- the day at a glance ----

@Composable
private fun HeroCard(
    kcal: Double, carbs: Double, protein: Double, fat: Double,
    goalKcal: Double?, goalCarbs: Double?, goalProtein: Double?, goalFat: Double?,
) {
    val p = LocalPaper.current
    val hasGoal = goalKcal != null && goalKcal > 0
    val left = (goalKcal ?: 0.0) - kcal
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SideStat("Eaten", n(kcal), "kcal", Modifier.weight(1f))
            Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                val track = p.surface2
                val ink = if (hasGoal && left < 0) p.bad else p.accent
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 13.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (hasGoal && kcal > 0) {
                        val sweep = (360f * (kcal / goalKcal!!).toFloat()).coerceIn(2f, 360f)
                        drawArc(ink, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hasGoal) {
                        Text(if (left >= 0) "left" else "over", fontSize = 12.sp, color = if (left >= 0) p.ink2 else p.bad)
                        Text(n(Math.abs(left)), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                        Text("of ${n(goalKcal!!)} kcal", fontSize = 11.sp, color = p.ink2)
                    } else {
                        Text(n(kcal), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                        Text("kcal eaten", fontSize = 11.sp, color = p.ink2)
                        Text("No goal yet", fontSize = 11.sp, color = p.ink2)
                    }
                }
            }
            SideStat("Goal", if (hasGoal) n(goalKcal!!) else "-", "kcal", Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MacroBar("Carbs", carbs, goalCarbs, CARB, Modifier.weight(1f))
            MacroBar("Protein", protein, goalProtein, PROTEIN, Modifier.weight(1f))
            MacroBar("Fat", fat, goalFat, FAT, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SideStat(label: String, value: String, unit: String, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1)
        Text(unit, fontSize = 11.sp, color = p.ink2)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.ink2)
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
        FoodPhoto(photo, meal.name, 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(meal.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
            Text(n(meal.grams) + " g", fontSize = 12.sp, color = p.ink2)
        }
        Text(n(meal.kcal) + " kcal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

// ---- water ----

@Composable
private fun WaterCard(ml: Int, onAdd: (Int) -> Unit) {
    val p = LocalPaper.current
    val goal = FoodStats.DEFAULT_WATER_GOAL_ML
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperIcon(R.drawable.ic_p_drop, size = 22.dp, tint = Color(0xFF2878B4))
            Column(modifier = Modifier.weight(1f)) {
                Text("Water", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text("$ml of $goal ml", fontSize = 12.sp, color = p.ink2)
            }
            PaperPill("-250", false, { onAdd(-250) })
            PaperPill("+250", false, { onAdd(250) })
            PaperPill("+500", false, { onAdd(500) })
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(p.surface2)) {
            val f = (ml.toFloat() / goal).coerceIn(0f, 1f)
            if (f > 0f) Box(modifier = Modifier.fillMaxWidth(f).height(7.dp).background(Color(0xFF2878B4)))
        }
    }
}

// ---- details ----

@Composable
private fun ExtrasCard(fiber: Double, sugar: Double, sodiumMg: Double) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExtraStat("Fibre", n(fiber), "g", Modifier.weight(1f))
            ExtraStat("Sugar", n(sugar), "g", Modifier.weight(1f))
            ExtraStat("Sodium", n(sodiumMg), "mg", Modifier.weight(1f))
        }
        Text("From the foods you logged. Foods without these numbers add nothing.", fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ExtraStat(label: String, value: String, unit: String, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        MonoLabel(label)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            Text(unit, fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
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

private fun amountShort(v: Double): String = when {
    v >= 100 -> Math.round(v).toString()
    v >= 10 -> (Math.round(v * 10) / 10.0).toString().removeSuffix(".0")
    else -> (Math.round(v * 100) / 100.0).toString().removeSuffix(".0")
}
