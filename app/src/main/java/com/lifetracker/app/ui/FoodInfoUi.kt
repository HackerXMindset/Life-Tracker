package com.lifetracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AgoText
import com.lifetracker.app.data.FoodHistory
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The page of one food: when you last ate it and how many times in the last 7, 30 and 90 days and in all. Opened from a food in
 * "Eaten most" and from a logged food. [load] gives every meal ever logged; the matching ones are picked here.
 */
@Composable
fun FoodInfoWindow(name: String, foodId: String, load: suspend () -> List<MealEntity>, onClose: () -> Unit) {
    val p = LocalPaper.current
    val today = LocalDate.now()
    val meals by produceState<List<MealEntity>?>(null, name, foodId) { value = load() }
    val info = remember(meals, name, foodId) { meals?.let { FoodHistory.build(it, foodId, name, today) } }
    // A similar food opened from this page ("Looks similar"), on top of this one.
    var other by remember { mutableStateOf<String?>(null) }

    EditWindow(
        title = info?.title ?: name,
        sub = when {
            info == null -> "Looking through your log…"
            info.last == null -> "Not logged yet"
            else -> "Last eaten " + AgoText.text(info.last, today)
        },
        onClose = onClose,
    ) {
        if (info == null) return@EditWindow
        if (info.times == 0) {
            Text("This food is not in your log yet.", fontSize = 13.sp, color = p.ink2)
            return@EditWindow
        }
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, shadow = false) {
            Text("Last eaten", fontSize = 11.5.sp, color = p.ink2)
            Text(AgoText.text(info.last!!, today).replaceFirstChar { it.uppercase() }, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            Text(dayText(info.last), fontSize = 12.5.sp, color = p.ink2)
            if (info.currentRun >= 2) {
                Spacer(Modifier.height(6.dp))
                Text(info.currentRun.toString() + " days in a row", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.accent)
            }
        }
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Kicker("How often")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                info.windows.forEach { w -> CountTile("last " + w.days + " days", w.times, w.daysEaten, if (info.totals != null) weightText(w.grams) else "", Modifier.weight(1f)) }
                CountTile("all time", info.times, info.daysEaten, info.totals?.let { weightText(it.grams) } ?: "", Modifier.weight(1f))
            }
        }
        val totals = info.totals
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Kicker("Total eaten")
            Spacer(Modifier.height(6.dp))
            if (totals == null) {
                Text("This food was only ever part of a longer line with other foods, so its own weight is not known.", fontSize = 13.sp, color = p.ink2)
            } else {
                Text(weightText(totals.grams), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                Text("over " + totals.times + (if (totals.times == 1) " time" else " times") + ", about " + kc(totals.kcal) + " kcal in all", fontSize = 12.5.sp, color = p.ink2)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    TotalTile("protein", kc(totals.protein) + " g", Modifier.weight(1f))
                    TotalTile("carbs", kc(totals.carbs) + " g", Modifier.weight(1f))
                    TotalTile("fat", kc(totals.fat) + " g", Modifier.weight(1f))
                }
            }
            if (info.mixedTimes > 0) {
                Text(
                    info.mixedTimes.toString() + (if (info.mixedTimes == 1) " time" else " times") + " it was part of a longer line, such as with roti, so those are counted above but not in the weight.",
                    fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Kicker("A usual helping")
            Spacer(Modifier.height(6.dp))
            Text(
                kc(info.avgGrams) + " g · " + kc(info.avgKcal) + " kcal · " + kc(info.avgProtein) + " g protein",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink,
            )
            val lines = ArrayList<String>()
            if (totals != null && totals.times > 1) {
                lines += "Smallest " + kc(totals.minGrams) + " g, largest " + kc(totals.maxGrams) + " g"
                if (Math.abs(totals.maxKcal100 - totals.minKcal100) >= 1.0) {
                    lines += "Calories per 100 g ranged from " + kc(totals.minKcal100) + " to " + kc(totals.maxKcal100) + ", so the dish was not the same every time"
                } else {
                    lines += kc(totals.minKcal100) + " kcal per 100 g every time"
                }
            }
            if (info.byMeal.isNotEmpty()) lines += info.byMeal.joinToString(", ") { (type, n) -> mealTypeLabel(type) + " " + n }
            info.usualMinute?.let { lines += "Usually around " + formatMinute(it) }
            info.first?.let { lines += "First logged " + dayText(it) }
            lines.forEach { Text(it, fontSize = 12.5.sp, color = p.ink2, modifier = Modifier.padding(top = 4.dp)) }
        }
        if (info.variants.isNotEmpty()) {
            MonoLabel("Looks similar, kept apart")
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "These share a name with " + info.title.lowercase() + " but are counted as different foods. Tap one for its own page.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                    info.variants.forEach { v ->
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { other = v.name }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(v.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
                                Text("last " + AgoText.text(v.last, today), fontSize = 11.5.sp, color = p.ink2)
                            }
                            Text(if (v.times == 1) "once" else v.times.toString() + " times", fontSize = 12.5.sp, color = p.ink2)
                        }
                    }
                }
            }
        }
        MonoLabel("Recent")
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                info.entries.take(10).forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dayText(LocalDate.parse(m.date)), fontSize = 13.sp, color = p.ink)
                            Text(mealTypeLabel(m.mealType) + " · " + formatMinute(m.minuteOfDay), fontSize = 11.5.sp, color = p.ink2)
                        }
                        Text(kc(m.grams) + " g · " + kc(m.kcal) + " kcal", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink, textAlign = TextAlign.End)
                    }
                }
                if (info.entries.size > 10) Text("and " + (info.entries.size - 10) + " earlier", fontSize = 12.sp, color = p.ink2)
            }
        }
    }
    other?.let { n -> FoodInfoWindow(n, "", load = load, onClose = { other = null }) }
}

@Composable
private fun CountTile(label: String, times: Int, days: Int, weight: String, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        Text(times.toString(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = if (times == 0) p.ink2 else p.ink)
        Text(if (times == 1) "time" else "times", fontSize = 11.5.sp, color = p.ink2)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, maxLines = 1)
        if (times > 0 && days != times) Text("on $days days", fontSize = 10.5.sp, color = p.ink2)
        if (times > 0 && weight.isNotEmpty()) Text(weight, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

@Composable
private fun TotalTile(label: String, value: String, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1)
        Text(label, fontSize = 12.sp, color = p.ink2)
    }
}

/** A weight in grams, or in kilos from 1 kg up. */
private fun weightText(grams: Double): String =
    if (grams >= 1000) String.format(Locale.US, "%.1f", grams / 1000) + " kg" else kc(grams) + " g"

private fun dayText(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth + " " + d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
        (if (d.year != LocalDate.now().year) " " + d.year else "")
