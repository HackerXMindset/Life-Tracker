package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.FoodGoalEntity
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.MealEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun n(value: Double): String = Math.round(value).toString()

@Composable
fun FoodScreen(vm: FoodViewModel = viewModel()) {
    val date by vm.date.collectAsState()
    val meals by vm.meals.collectAsState()
    val goal by vm.goal.collectAsState()
    val water by vm.waterMl.collectAsState()
    val recent by vm.recent.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<MealEntity?>(null) }

    val totals = FoodStats.totals(meals)
    val title = remember(date) { date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenHeader(label = "Food", badge = "Saved on this phone", title = title) }
            item { DayNav(date, onPrev = { vm.shift(-1) }, onNext = { vm.shift(1) }, onToday = vm::goToday) }
            item { CalorieCard(totals.kcal, totals.carbs, totals.fat, totals.protein, goal) }
            item { WaterCard(water, onAdd = vm::addWater) }

            if (meals.isEmpty()) {
                item {
                    Text(
                        text = "No meals logged for this day. Tap Add meal.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MEAL_TYPES.forEach { type ->
                val group = meals.filter { it.mealType == type }
                if (group.isNotEmpty()) {
                    item(key = "header-$type") {
                        MonoLabel("${mealTypeLabel(type)} · ${n(group.sumOf { it.kcal })} kcal")
                    }
                    items(group, key = { it.id }) { meal -> MealRow(meal, onClick = { toDelete = meal }) }
                }
            }
            if (meals.isNotEmpty()) {
                item {
                    Text(
                        text = "Tap a meal to delete it. Amounts are in grams, as in OpenNutriTracker.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text("Add meal") },
        )
    }

    if (showAdd) {
        AddMealSheet(
            date = date,
            recent = recent,
            onDismiss = { showAdd = false },
            onSave = {
                vm.addMeal(it)
                showAdd = false
            },
        )
    }

    toDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this meal?") },
            text = { Text(meal.name) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMeal(meal)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun DayNav(date: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPrev) { Text("‹ Previous") }
        if (date != LocalDate.now()) {
            TextButton(onClick = onToday) { Text("Today") }
        }
        OutlinedButton(onClick = onNext) { Text("Next ›") }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun CalorieCard(kcal: Double, carbs: Double, fat: Double, protein: Double, goal: FoodGoalEntity?) {
    Card {
        MonoLabel("Calories")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = n(kcal),
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (goal != null) "of ${n(goal.kcal)} kcal" else "kcal",
                modifier = Modifier.padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (goal != null && goal.kcal > 0) {
            Bar((kcal / goal.kcal).toFloat(), MaterialTheme.colorScheme.primary)
            val left = goal.kcal - kcal
            Text(
                text = if (left >= 0) "${n(left)} kcal left" else "${n(-left)} kcal over",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Macro("Carbs", carbs, goal?.carbs)
        Macro("Fat", fat, goal?.fat)
        Macro("Protein", protein, goal?.protein)
    }
}

@Composable
private fun Macro(label: String, value: Double, goal: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel(label)
            Text(
                text = if (goal != null) "${n(value)} / ${n(goal)} g" else "${n(value)} g",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (goal != null && goal > 0) Bar((value / goal).toFloat(), MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun WaterCard(ml: Int, onAdd: (Int) -> Unit) {
    val goal = FoodStats.DEFAULT_WATER_GOAL_ML
    Card {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel("Water")
            Text(
                text = "$ml / $goal ml",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Bar(ml.toFloat() / goal, MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAdd(250) }) { Text("+250 ml") }
            OutlinedButton(onClick = { onAdd(500) }) { Text("+500 ml") }
            TextButton(onClick = { onAdd(-250) }, enabled = ml > 0) { Text("Undo") }
        }
    }
}

@Composable
private fun MealRow(meal: MealEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = formatMinute(meal.minuteOfDay),
            modifier = Modifier
                .width(68.dp)
                .padding(top = 3.dp),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = meal.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${n(meal.kcal)} kcal · ${n(meal.grams)} g",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "C ${n(meal.carbs)} · F ${n(meal.fat)} · P ${n(meal.protein)} g",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
