package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSheet
import java.time.LocalDate

/**
 * Copy foods you logged to another meal and/or another day: pick the meal (so breakfast can be copied to lunch), pick the day
 * (today, tomorrow or any day), and copy.
 */
@Composable
fun CopySheet(vm: FoodViewModel, meals: List<MealEntity>, fromType: String, startDay: LocalDate, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val tomorrow = today.plusDays(1)
    var type by remember { mutableStateOf(fromType) }
    var day by remember { mutableStateOf(startDay) }
    val custom = day != today && day != tomorrow

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle(
                if (meals.size == 1) "Copy " + meals[0].name else "Copy ${meals.size} foods",
                kc(FoodStats.totals(meals).kcal) + " kcal",
            )
            Kicker("To which meal")
            MealPills(type) { type = it }
            Kicker("On which day")
            ChipFlow {
                PaperPill("Today", day == today, { day = today })
                PaperPill("Tomorrow", day == tomorrow, { day = tomorrow })
                PaperPill(if (custom) TimelineTop.title(day, today) else "Another day", custom, { pickDay(context, day) { day = it } })
            }
            PaperButton(
                "Copy to " + mealTypeLabel(type) + ", " + TimelineTop.title(day, today),
                { vm.copyMealsTo(meals, type, day); onDone() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
