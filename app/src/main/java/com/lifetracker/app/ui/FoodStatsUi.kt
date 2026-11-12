package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.AgoText
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodSettings
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.PeriodStats
import com.lifetracker.app.data.StatPeriod
import com.lifetracker.app.data.StatSpans
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperSegmented
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/*
 * The stats window: how you ate over a week, a month, 90 days or all time. Swipe sideways (or use the arrows) to look at the period
 * before or after. Days with nothing logged are left out of every average and left empty in the chart, never counted as zero.
 */

@Composable
fun FoodStatsWindow(
    x: FoodExtrasViewModel,
    startDate: LocalDate,
    onPickDay: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    var period by remember { mutableStateOf(FoodSettings.statPeriod(context)) }
    var anchor by remember { mutableStateOf(if (period == StatPeriod.DAYS90 || period == StatPeriod.ALL) today else startDate) }
    val earliest by x.firstMealDate.collectAsState()
    val meals by x.statMeals.collectAsState()
    val goals by x.goals.collectAsState()
    val weights by x.weights.collectAsState()
    val checks by x.checks.collectAsState()
    val habits by x.trackers.collectAsState()

    val span = StatSpans.span(period, anchor, today, earliest)
    LaunchedEffect(span) { x.setStatSpan(span) }
    val result = remember(span, meals, goals, weights, checks, habits) {
        PeriodStats.build(span, today, meals, { day -> FoodStats.goalFor(goals, LocalDate.parse(day)) }, weights, checks, habits.map { it.id })
    }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val canEarlier = StatSpans.canGoEarlier(period, anchor, today, earliest)
    val canLater = StatSpans.canGoLater(period, anchor, today)
    val title = StatSpans.title(period, span, today)

    fun go(by: Int) {
        if (by < 0 && !canEarlier) return
        if (by > 0 && !canLater) return
        anchor = StatSpans.shift(period, anchor, by)
    }

    EditWindow(title = "Stats", sub = title, onClose = onClose) {
        Column(
            modifier = Modifier.fillMaxWidth().pointerInput(period, anchor, canEarlier, canLater) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 120f) go(-1) else if (total < -120f) go(1) },
                ) { _, delta -> total += delta }
            },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaperSegmented(
                options = StatPeriod.entries.map { it.label },
                selected = period.ordinal,
                onSelect = {
                    val next = StatPeriod.entries[it]
                    period = next
                    anchor = if (next == StatPeriod.WEEK || next == StatPeriod.MONTH) (if (span.to.isAfter(today)) today else span.to) else today
                    FoodSettings.setStatPeriod(context, next)
                },
            )
            if (period != StatPeriod.ALL) {
                PeriodNav(title, period, canEarlier, canLater, onEarlier = { go(-1) }, onLater = { go(1) }, onNow = { anchor = today })
            }

            if (result.loggedDays == 0) {
                EmptyStats(period)
            } else {
                StatsHero(result)
                CalorieChartCard(result)
                MacroCard(result)
                ExtremesCard(result, today, onPickDay = { onPickDay(it); onClose() })
                WeightChangeCard(result)
            }
            HabitStatsCard(result, habits.associateBy { it.id }, onPickHabits = null)
            if (result.topFoods.isNotEmpty()) TopFoodsCard(result, today, onOpen = { id, name -> info = id to name })
            if (period == StatPeriod.WEEK && result.loggedDays > 0) DayList(result, today, onPickDay = { onPickDay(it); onClose() })
        }
    }
    info?.let { (id, name) -> FoodInfoWindow(name, id, load = { x.allMeals() }, onClose = { info = null }) }
}

@Composable
private fun PeriodNav(
    title: String, period: StatPeriod, canEarlier: Boolean, canLater: Boolean,
    onEarlier: () -> Unit, onLater: () -> Unit, onNow: () -> Unit,
) {
    val p = LocalPaper.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (canEarlier) ArrowButton(R.drawable.ic_p_back, "Earlier", onEarlier)
        }
        Column(
            modifier = Modifier.weight(1f).clickable(role = Role.Button) { onNow() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            Text("Swipe sideways for the " + (if (period == StatPeriod.DAYS90) "90 days" else period.label.lowercase()) + " before or after", fontSize = 11.sp, color = p.ink2)
        }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (canLater) ArrowButton(R.drawable.ic_p_chev, "Later", onLater)
        }
    }
}

@Composable
private fun EmptyStats(period: StatPeriod) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp, shadow = false) {
        Text("Nothing logged in this time", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            if (period == StatPeriod.ALL) "Log a food and your numbers start here."
            else "Swipe sideways to look at another " + (if (period == StatPeriod.DAYS90) "stretch" else period.label.lowercase()) + ", or pick a longer period above.",
            fontSize = 13.sp, color = p.ink2,
        )
    }
}

@Composable
private fun StatsHero(r: PeriodStats.Result) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, shadow = false) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(kc(r.avgKcal), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            Text(" kcal a day", fontSize = 13.sp, color = p.ink2, modifier = Modifier.padding(bottom = 6.dp))
        }
        val vs = r.avgVsGoal
        if (vs != null) {
            val pct = Math.round(abs(vs) * 100).toInt()
            Text(
                if (pct == 0) "Right on your goal of ${kc(r.goalKcal!!)} kcal" else "$pct% " + (if (vs > 0) "above" else "below") + " your goal of ${kc(r.goalKcal!!)} kcal",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (abs(vs) <= 0.1) p.accent else p.ink2,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            StatTile(r.loggedDays.toString() + " of " + r.elapsedDays, "days logged", Modifier.weight(1f))
            StatTile(kc(r.avgProtein) + " g", "protein a day", Modifier.weight(1f))
            if (r.goalDays > 0) StatTile(r.daysInBand.toString() + " of " + r.goalDays, "near your goal", Modifier.weight(1f))
        }
        if (r.emptyDays > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                r.emptyDays.toString() + (if (r.emptyDays == 1) " day" else " days") + " with nothing logged " + (if (r.emptyDays == 1) "is" else "are") + " left out, not counted as zero.",
                fontSize = 11.5.sp, color = p.ink2,
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1)
        Text(label, fontSize = 11.5.sp, color = p.ink2)
    }
}

@Composable
private fun CalorieChartCard(r: PeriodStats.Result) {
    val p = LocalPaper.current
    val goal = r.goalKcal
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Kicker(
            when {
                r.span.days <= 31 -> "Calories each day"
                r.span.days <= 125 -> "Calories each week, average of logged days"
                else -> "Calories each month, average of logged days"
            },
        )
        Spacer(Modifier.height(8.dp))
        ValueLineChart(
            values = r.points.map { it.kcal },
            labels = r.points.map { it.label },
            color = p.accent,
            goal = goal,
            height = 150.dp,
            tone = { v -> if (goal == null) p.accent else if (v > goal * 1.1) p.bad else if (v >= goal * 0.9) p.accent else p.sun },
        )
        if (goal != null) Text("Dashed line is your goal. Green is near it, red over, yellow under.", fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MacroCard(r: PeriodStats.Result) {
    val p = LocalPaper.current
    val m = r.macros ?: return
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Kicker("Where the calories come from")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(14.dp).background(p.surface2, RoundedCornerShape(7.dp))) {
            listOf(m.carbs to CARB_COLOR, m.protein to PROTEIN_COLOR, m.fat to FAT_COLOR).forEach { (share, colour) ->
                if (share > 0.001) Box(Modifier.weight(share.toFloat()).height(14.dp).background(colour))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            MacroLegend("Carbs", m.carbs, r.avgCarbs, CARB_COLOR, Modifier.weight(1f))
            MacroLegend("Protein", m.protein, r.avgProtein, PROTEIN_COLOR, Modifier.weight(1f))
            MacroLegend("Fat", m.fat, r.avgFat, FAT_COLOR, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroLegend(name: String, share: Double, grams: Double, colour: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(9.dp).background(colour, CircleShape))
            Text(name, fontSize = 12.sp, color = p.ink2)
        }
        Text(Math.round(share * 100).toString() + "%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        Text(kc(grams) + " g a day", fontSize = 11.5.sp, color = p.ink2)
    }
}

@Composable
private fun ExtremesCard(r: PeriodStats.Result, today: LocalDate, onPickDay: (LocalDate) -> Unit) {
    val p = LocalPaper.current
    val hi = r.highest ?: return
    val lo = r.lowest ?: return
    fun name(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth + " " + d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Kicker("Highest and lowest day")
        Spacer(Modifier.height(8.dp))
        listOf("Highest" to hi, "Lowest" to lo).forEach { (label, day) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onPickDay(day.date) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 13.sp, color = p.ink2, modifier = Modifier.weight(0.8f))
                Text(name(day.date) + " (" + AgoText.text(day.date, today) + ")", fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(2f), maxLines = 1)
                Text(kc(day.kcal) + " kcal", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun WeightChangeCard(r: PeriodStats.Result) {
    val p = LocalPaper.current
    val change = r.weightChange ?: return
    val first = r.weightFirst ?: return
    val last = r.weightLast ?: return
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Kicker("Weight")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text((if (change > 0) "+" else "") + (Math.round(change * 10) / 10.0) + " kg", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            Text("   " + (Math.round(first * 10) / 10.0) + " to " + (Math.round(last * 10) / 10.0) + " kg", fontSize = 13.sp, color = p.ink2, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun HabitStatsCard(r: PeriodStats.Result, habits: Map<String, com.lifetracker.app.data.FoodTrackerEntity>, onPickHabits: (() -> Unit)?) {
    val p = LocalPaper.current
    val rows = r.habits.filter { habits.containsKey(it.habitId) }
    if (rows.isEmpty()) return
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Kicker("Habits you watch")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                val habit = habits.getValue(row.habitId)
                val colour = argbColor(habit.color)
                val share = if (r.elapsedDays > 0) (row.daysDone.toFloat() / r.elapsedDays).coerceIn(0f, 1f) else 0f
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(colour, CircleShape))
                        Spacer(Modifier.size(7.dp))
                        Text(habit.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            if (row.daysDone == 0) "none" else row.daysDone.toString() + " of " + r.elapsedDays + (if (r.elapsedDays == 1) " day" else " days"),
                            fontSize = 12.5.sp, color = p.ink2,
                        )
                    }
                    Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(6.dp).background(p.surface2, RoundedCornerShape(3.dp))) {
                        if (share > 0f) Box(Modifier.fillMaxWidth(share).height(6.dp).background(colour, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopFoodsCard(r: PeriodStats.Result, today: LocalDate, onOpen: (String, String) -> Unit) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Kicker("Eaten most (tap a food for its history)")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.topFoods.forEach { f ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(f.foodId, f.name) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontSize = 14.sp, color = p.ink, maxLines = 1)
                        Text("last " + AgoText.text(f.last, today), fontSize = 11.5.sp, color = p.ink2)
                    }
                    Text(if (f.times == 1) "once" else "${f.times} times", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink)
                }
            }
        }
    }
}

@Composable
private fun DayList(r: PeriodStats.Result, today: LocalDate, onPickDay: (LocalDate) -> Unit) {
    val p = LocalPaper.current
    MonoLabel("Days")
    r.days.forEach { day ->
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = { onPickDay(day.date) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + day.date.dayOfMonth,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f),
                    )
                    when {
                        day.logged -> Text(kc(day.kcal) + " kcal · " + kc(day.protein) + " g protein", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                        !day.date.isAfter(today) -> Text("nothing logged", fontSize = 12.sp, color = p.ink2)
                    }
                }
                val order = listOf(FoodLogic.BREAKFAST, FoodLogic.LUNCH, FoodLogic.SNACK, FoodLogic.DINNER)
                val byType = day.meals.groupBy { it.mealType }
                (order + byType.keys.filter { it !in order }).forEach { type ->
                    val list = byType[type].orEmpty().sortedBy { it.minuteOfDay }
                    if (list.isNotEmpty()) Text(mealTypeLabel(type) + ": " + list.joinToString(", ") { it.name }, fontSize = 12.sp, color = p.ink2, maxLines = 2)
                }
            }
        }
    }
}
