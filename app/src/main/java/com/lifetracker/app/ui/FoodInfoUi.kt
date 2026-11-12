package com.lifetracker.app.ui

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

    EditWindow(
        title = name,
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
                info.windows.forEach { w -> CountTile("last " + w.days + " days", w.times, w.daysEaten, Modifier.weight(1f)) }
                CountTile("all time", info.times, info.daysEaten, Modifier.weight(1f))
            }
        }
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Kicker("A usual helping")
            Spacer(Modifier.height(6.dp))
            Text(
                kc(info.avgGrams) + " g · " + kc(info.avgKcal) + " kcal · " + kc(info.avgProtein) + " g protein",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink,
            )
            val parts = ArrayList<String>()
            info.usualMeal?.let { parts += "Usually " + mealTypeLabel(it).lowercase() }
            info.first?.let { parts += "first logged " + dayText(it) }
            if (parts.isNotEmpty()) Text(parts.joinToString(", ").replaceFirstChar { it.uppercase() }, fontSize = 12.5.sp, color = p.ink2, modifier = Modifier.padding(top = 4.dp))
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
}

@Composable
private fun CountTile(label: String, times: Int, days: Int, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        Text(times.toString(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = if (times == 0) p.ink2 else p.ink)
        Text(if (times == 1) "time" else "times", fontSize = 11.5.sp, color = p.ink2)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, maxLines = 1)
        if (times > 0 && days != times) Text("on $days days", fontSize = 10.5.sp, color = p.ink2)
    }
}

private fun dayText(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth + " " + d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
        (if (d.year != LocalDate.now().year) " " + d.year else "")
