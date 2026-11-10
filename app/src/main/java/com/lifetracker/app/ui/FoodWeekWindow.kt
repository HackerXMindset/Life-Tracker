package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.WeekReport
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The week of [date]: how it went, what you ate most, and every day with its meals. Opened from the "This week" card. */
@Composable
fun WeekWindow(
    x: FoodExtrasViewModel,
    date: LocalDate,
    onShiftWeek: (Int) -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPaper.current
    val meals by x.weekMeals.collectAsState()
    val goals by x.goals.collectAsState()
    val weights by x.weights.collectAsState()
    val week = FoodLogic.weekOf(date)
    val report = WeekReport.build(week, meals, { day -> FoodStats.goalFor(goals, LocalDate.parse(day)) }, weights)
    val short = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth

    EditWindow(title = "This week", sub = week.first().format(short) + " to " + week.last().format(short), onClose = onClose) {
        if (report.loggedDays == 0) {
            Text("Nothing logged this week.", color = p.ink2, fontSize = 13.sp)
        } else {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        ReportStat("kcal a day", kc(report.avgKcal), Modifier.weight(1f))
                        ReportStat("g protein a day", kc(report.avgProtein), Modifier.weight(1f))
                        ReportStat("days on target", report.daysOnTarget.toString() + "/" + report.loggedDays, Modifier.weight(1f))
                    }
                    if (report.proteinGoalDays > 0) {
                        Text("Protein goal reached on ${report.proteinDays} of ${report.proteinGoalDays} days.", fontSize = 13.sp, color = p.ink)
                    }
                    report.closest?.let { Text("Closest to your goal: " + dayName(it.date) + ", " + kc(it.kcal) + " kcal.", fontSize = 13.sp, color = p.ink) }
                    report.furthest?.let { Text("Furthest from it: " + dayName(it.date) + ", " + kc(it.kcal) + " kcal.", fontSize = 13.sp, color = p.ink) }
                    report.weightChangeKg?.let {
                        Text("Weight this week: " + (if (it > 0) "+" else "") + (Math.round(it * 10) / 10.0) + " kg.", fontSize = 13.sp, color = p.ink)
                    }
                }
            }
            if (report.topFoods.isNotEmpty()) {
                MonoLabel("Eaten most")
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        report.topFoods.forEach { (name, times) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(if (times == 1) "once" else "$times times", fontSize = 12.sp, color = p.ink2)
                            }
                        }
                    }
                }
            }
        }

        MonoLabel("Days")
        report.days.forEach { day ->
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = { onPickDay(day.date); onClose() }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(dayName(day.date), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f))
                        if (day.logged) {
                            Text(kc(day.kcal) + " kcal · " + kc(day.protein) + " g protein", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                        } else {
                            Text(if (day.date.isAfter(LocalDate.now())) "" else "nothing logged", fontSize = 12.sp, color = p.ink2)
                        }
                    }
                    val order = listOf(FoodLogic.BREAKFAST, FoodLogic.LUNCH, FoodLogic.SNACK, FoodLogic.DINNER)
                    val byType = day.meals.groupBy { it.mealType }
                    (order + byType.keys.filter { it !in order }).forEach { type ->
                        val list = byType[type].orEmpty().sortedBy { it.minuteOfDay }
                        if (list.isNotEmpty()) {
                            Text(
                                mealTypeLabel(type) + ": " + list.joinToString(", ") { it.name },
                                fontSize = 12.sp, color = p.ink2, maxLines = 2,
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton("Earlier week", { onShiftWeek(-7) }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true)
            PaperButton(
                "Later week", { onShiftWeek(7) }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true,
                enabled = week.last().isBefore(LocalDate.now()),
            )
        }
    }
}

@Composable
private fun ReportStat(label: String, value: String, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1)
        Text(label, fontSize = 11.sp, color = p.ink2)
    }
}
