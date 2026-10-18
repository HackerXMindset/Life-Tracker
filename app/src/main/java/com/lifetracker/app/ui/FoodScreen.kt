package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.FoodCalendar
import com.lifetracker.app.data.FoodGoalEntity
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.ui.theme.HighlightedHeading
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperFab
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.TallyBar
import com.lifetracker.app.ui.theme.Wobble
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private fun n(value: Double): String = Math.round(value).toString()

/** The colour of each meal type, as in the paper demo. */
fun mealColor(type: String): Color = when (type) {
    "breakfast" -> Color(0xFFE0A100)
    "lunch" -> Color(0xFF17785A)
    "dinner" -> Color(0xFF5B5FC7)
    else -> Color(0xFFD6457F)
}

@Composable
fun FoodScreen(vm: FoodViewModel = viewModel()) {
    val date by vm.date.collectAsState()
    val meals by vm.meals.collectAsState()
    val goal by vm.goal.collectAsState()
    val water by vm.waterMl.collectAsState()
    val recent by vm.recent.collectAsState()
    val loggedDays by vm.datesWithMeals.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var addType by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<MealEntity?>(null) }
    var month by remember { mutableStateOf(YearMonth.from(date)) }

    val today = LocalDate.now()
    val totals = FoodStats.totals(meals)
    val p = LocalPaper.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { FoodHeader(date, today) }
            item {
                FoodCalendarCard(
                    month = month,
                    selected = date,
                    today = today,
                    loggedDays = loggedDays,
                    onMonth = { month = it },
                    onPick = { vm.select(it); month = YearMonth.from(it) },
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HighlightedHeading(text = TimelineTop.title(date, today), modifier = Modifier.weight(1f))
                    if (date != today) PaperPill(text = "Today", selected = false, onClick = { vm.goToday(); month = YearMonth.from(today) })
                    PaperIconButton(R.drawable.ic_p_back, "Previous day", onClick = { vm.shift(-1); month = YearMonth.from(date.minusDays(1)) })
                    PaperIconButton(R.drawable.ic_p_chev, "Next day", onClick = { vm.shift(1); month = YearMonth.from(date.plusDays(1)) })
                }
            }
            item { DayCard(meals, totals.kcal, totals.carbs, totals.fat, totals.protein, goal) }
            item { WaterCard(water, onAdd = vm::addWater) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HighlightedHeading(text = "What I ate")
                    Text(
                        text = "${meals.size} ${if (meals.size == 1) "item" else "items"}",
                        fontSize = 12.sp,
                        color = p.ink2,
                    )
                }
            }
            MEAL_TYPES.forEach { type ->
                val group = meals.filter { it.mealType == type }
                item(key = "meals-$type") {
                    MealCard(
                        type = type,
                        meals = group,
                        onAdd = { addType = type; showAdd = true },
                        onTap = { toDelete = it },
                    )
                }
            }
            if (meals.isNotEmpty()) {
                item {
                    Text(
                        text = "Tap a meal to delete it. Amounts are in grams, as in OpenNutriTracker.",
                        fontSize = 12.sp,
                        color = p.ink2,
                    )
                }
            }
        }

        PaperFab(
            text = "Add meal",
            icon = R.drawable.ic_p_plus,
            onClick = { addType = null; showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (showAdd) {
        AddMealSheet(
            date = date,
            recent = recent,
            startType = addType,
            onDismiss = { showAdd = false },
            onSave = {
                vm.addMeal(it)
                showAdd = false
            },
        )
    }

    toDelete?.let { meal ->
        PaperDialog(
            title = "Delete this meal?",
            onDismiss = { toDelete = null },
            confirmText = "Delete",
            onConfirm = {
                vm.deleteMeal(meal)
                toDelete = null
            },
            dismissText = "Keep",
            confirmStyle = PaperButtonStyle.Danger,
        ) {
            Text(meal.name, color = p.ink)
        }
    }
}

/** The big title and the full date under it. */
@Composable
private fun FoodHeader(date: LocalDate, today: LocalDate) {
    val p = LocalPaper.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Food",
            fontSize = 38.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            color = p.ink,
            maxLines = 1,
        )
        Text(text = TimelineTop.subtitle(date), fontSize = 12.sp, color = p.ink2)
    }
}

/** A month of paper tiles. A small square mark shows a day with food logged. */
@Composable
private fun FoodCalendarCard(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    loggedDays: Set<String>,
    onMonth: (YearMonth) -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val p = LocalPaper.current
    val cells = remember(month) { FoodCalendar.cells(month) }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PaperIconButton(R.drawable.ic_p_back, "Previous month", onClick = { onMonth(month.minusMonths(1)) })
            Text(
                text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = p.ink,
            )
            PaperIconButton(R.drawable.ic_p_chev, "Next month", onClick = { onMonth(month.plusMonths(1)) })
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { letter ->
                Text(
                    text = letter,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = p.ink2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayTile(
                            day = day,
                            isSelected = day == selected,
                            isToday = day == today,
                            isFuture = day.isAfter(today),
                            logged = day.toString() in loggedDays,
                            onClick = { onPick(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(7.dp).border(1.25.dp, p.ink2, RoundedCornerShape(2.dp)))
            Text("Food logged", fontSize = 10.5.sp, color = p.ink2)
        }
    }
}

@Composable
private fun DayTile(
    day: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    logged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    val ink = if (isSelected) p.bg else p.ink
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(if (isSelected) Modifier.background(p.ink, shape) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, p.ink, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            fontSize = 13.sp,
            color = if (isFuture && !isSelected) ink.copy(alpha = 0.55f) else ink,
        )
        if (logged) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 4.dp)
                    .size(6.dp)
                    .border(1.25.dp, if (isSelected) p.bg else p.ink2, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Calories in big type, the bar split by meal, then carbs, fat and protein in tally strokes. */
@Composable
private fun DayCard(
    meals: List<MealEntity>,
    kcal: Double,
    carbs: Double,
    fat: Double,
    protein: Double,
    goal: FoodGoalEntity?,
) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(text = n(kcal), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(text = "kcal eaten", fontSize = 12.sp, color = p.ink2)
            }
            if (goal != null && goal.kcal > 0) {
                val left = goal.kcal - kcal
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (left >= 0) "${n(left)} left" else "${n(-left)} over",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (left >= 0) p.accent else p.bad,
                    )
                    Text(text = "of ${n(goal.kcal)}", fontSize = 12.sp, color = p.ink2)
                }
            }
        }
        FuelBar(meals, goal?.kcal)
        Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Macro("Carbs", carbs, goal?.carbs, Color(0xFFC28410))
            Macro("Fat", fat, goal?.fat, Color(0xFFCC5F22))
            Macro("Protein", protein, goal?.protein, Color(0xFF17785A))
        }
    }
}

/** One piece per meal, coloured by meal type and as wide as its calories against the goal. */
@Composable
private fun FuelBar(meals: List<MealEntity>, goalKcal: Double?) {
    val p = LocalPaper.current
    val shares = FoodStats.fuelShares(meals.map { it.kcal }, goalKcal)
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .height(30.dp)
            .clip(shape)
            .background(p.surface2, shape)
            .border(1.25.dp, p.line, shape),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        meals.forEachIndexed { i, meal ->
            val share = shares[i]
            if (share > 0.0) {
                Box(
                    modifier = Modifier
                        .weight(share.toFloat().coerceAtLeast(0.01f))
                        .height(30.dp)
                        .background(mealColor(meal.mealType)),
                )
            }
        }
        // The unused part of the goal.
        val used = shares.sum().toFloat()
        if (used < 0.995f) Box(modifier = Modifier.weight((1f - used).coerceAtLeast(0.01f)).height(30.dp))
    }
}

@Composable
private fun Macro(label: String, value: Double, goal: Double?, color: Color) {
    val p = LocalPaper.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(9.dp).background(color, CircleShape))
                MonoLabel(label)
            }
            Text(
                text = if (goal != null) "${n(value)} / ${n(goal)} g" else "${n(value)} g",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
            )
        }
        if (goal != null && goal > 0) {
            TallyBar(progress = (value / goal).toFloat().coerceIn(0f, 1f), color = color, height = 16.dp)
        }
    }
}

@Composable
private fun WaterCard(ml: Int, onAdd: (Int) -> Unit) {
    val p = LocalPaper.current
    val goal = FoodStats.DEFAULT_WATER_GOAL_ML
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("Water")
            Text(text = "$ml / $goal ml", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
        }
        Box(modifier = Modifier.padding(top = 8.dp)) {
            TallyBar(progress = (ml.toFloat() / goal).coerceIn(0f, 1f), color = Color(0xFF2878B4), height = 18.dp)
        }
        Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton("+250 ml", onClick = { onAdd(250) }, style = PaperButtonStyle.Ghost, small = true)
            PaperButton("+500 ml", onClick = { onAdd(500) }, style = PaperButtonStyle.Ghost, small = true)
            PaperButton("Undo", onClick = { onAdd(-250) }, style = PaperButtonStyle.Ghost, small = true, enabled = ml > 0)
        }
    }
}

/** One meal type: a coloured side bar, the kcal total, a small add button, and its meals. */
@Composable
private fun MealCard(type: String, meals: List<MealEntity>, onAdd: () -> Unit, onTap: (MealEntity) -> Unit) {
    val p = LocalPaper.current
    val color = mealColor(type)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp, shadow = false) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = mealTypeLabel(type),
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = p.ink,
                    )
                    Text(text = "${n(meals.sumOf { it.kcal })} kcal", fontSize = 12.sp, color = p.ink2)
                    PaperIconButton(R.drawable.ic_p_plus, "Add to ${mealTypeLabel(type)}", onClick = onAdd, modifier = Modifier.size(30.dp))
                }
                if (meals.isEmpty()) {
                    Text(text = "Nothing added yet", fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
                } else {
                    meals.forEach { meal -> MealRow(meal, onClick = { onTap(meal) }) }
                }
            }
        }
    }
}

@Composable
private fun MealRow(meal: MealEntity, onClick: () -> Unit) {
    val p = LocalPaper.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = meal.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
            Text(
                text = "${formatMinute(meal.minuteOfDay)} · ${n(meal.grams)} g · C ${n(meal.carbs)} · F ${n(meal.fat)} · P ${n(meal.protein)}",
                fontSize = 11.sp,
                color = p.ink2,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = n(meal.kcal), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Text(text = "kcal", fontSize = 10.sp, color = p.ink2)
        }
    }
}
