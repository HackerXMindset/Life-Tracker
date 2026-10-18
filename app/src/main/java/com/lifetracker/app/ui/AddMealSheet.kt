package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSheet
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

val MEAL_TYPES = listOf("breakfast", "lunch", "dinner", "snack")

fun mealTypeLabel(type: String): String = type.replaceFirstChar { it.uppercase() }

private fun amountText(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format("%.1f", value)

@Composable
fun AddMealSheet(
    date: LocalDate,
    recent: List<MealEntity>,
    onDismiss: () -> Unit,
    onSave: (MealEntity) -> Unit,
    startType: String? = null,
) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val now = remember { LocalTime.now().let { it.hour * 60 + it.minute } }
    val startMinute = if (date == LocalDate.now()) now else 12 * 60

    var type by remember {
        mutableStateOf(
            startType ?: when (startMinute / 60) {
                in 0..10 -> "breakfast"
                in 11..15 -> "lunch"
                in 16..18 -> "snack"
                else -> "dinner"
            },
        )
    }
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var minute by remember { mutableIntStateOf(startMinute) }
    var error by remember { mutableStateOf<String?>(null) }

    val g = grams.toDoubleOrNull() ?: 0.0
    val k100 = kcal.toDoubleOrNull() ?: 0.0
    val preview = "= ${Math.round(k100 * g / 100)} kcal  ·  " +
        "C ${Math.round((carbs.toDoubleOrNull() ?: 0.0) * g / 100)} g  ·  " +
        "F ${Math.round((fat.toDoubleOrNull() ?: 0.0) * g / 100)} g  ·  " +
        "P ${Math.round((protein.toDoubleOrNull() ?: 0.0) * g / 100)} g"

    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    PaperSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Add a meal",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
            )

            if (recent.isNotEmpty()) {
                MonoLabel("Eat again")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recent.forEach { meal ->
                        PaperPill(
                            text = meal.name,
                            selected = false,
                            onClick = {
                                name = meal.name
                                grams = amountText(meal.grams)
                                kcal = amountText(meal.kcal100)
                                carbs = amountText(meal.carbs100)
                                fat = amountText(meal.fat100)
                                protein = amountText(meal.protein100)
                                type = meal.mealType
                                error = null
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MEAL_TYPES.forEach { t ->
                    PaperPill(text = mealTypeLabel(t), selected = type == t, onClick = { type = t }, dot = mealColor(t))
                }
            }

            PaperField(
                value = name,
                onValueChange = { name = it; error = null },
                label = "What did you eat?",
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                PaperField(
                    value = grams,
                    onValueChange = { grams = it; error = null },
                    label = "Amount eaten (g)",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = decimal,
                    singleLine = true,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Time", fontSize = 12.sp, color = p.ink2)
                    PaperButton(
                        text = formatMinute(minute),
                        onClick = {
                            TimePickerDialog(context, { _, h, m -> minute = h * 60 + m }, minute / 60, minute % 60, false).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = PaperButtonStyle.Ghost,
                    )
                }
            }

            MonoLabel("Nutrition per 100 g")
            PaperField(
                value = kcal,
                onValueChange = { kcal = it; error = null },
                label = "Calories (kcal)",
                keyboardOptions = decimal,
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = "Carbs g",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = decimal,
                    singleLine = true,
                )
                PaperField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = "Fat g",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = decimal,
                    singleLine = true,
                )
                PaperField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = "Protein g",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = decimal,
                    singleLine = true,
                )
            }
            Text(text = preview, fontSize = 13.sp, color = p.ink2)

            error?.let { Text(text = it, color = p.bad, fontSize = 13.sp) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaperButton("Cancel", onClick = onDismiss, style = PaperButtonStyle.Ghost, small = true)
                PaperButton("Add meal", small = true, onClick = {
                    when {
                        name.isBlank() -> error = "Give the meal a name."
                        g <= 0.0 -> error = "Enter how many grams you ate."
                        kcal.toDoubleOrNull() == null -> error = "Enter the calories per 100 g."
                        else -> onSave(
                            MealEntity(
                                id = UUID.randomUUID().toString(),
                                date = date.toString(),
                                minuteOfDay = minute.coerceIn(0, 1439),
                                mealType = type,
                                name = name.trim(),
                                grams = g,
                                kcal100 = k100,
                                carbs100 = carbs.toDoubleOrNull() ?: 0.0,
                                fat100 = fat.toDoubleOrNull() ?: 0.0,
                                protein100 = protein.toDoubleOrNull() ?: 0.0,
                                source = "app",
                                raw = "{}",
                            ),
                        )
                    }
                })
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
