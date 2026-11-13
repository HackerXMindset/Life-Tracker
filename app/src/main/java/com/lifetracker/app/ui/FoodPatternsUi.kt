package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.FoodPatterns
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import java.time.LocalDate

/**
 * "What came before": the foods eaten in the hours before a habit you watch, compared with how often you eat them at other times.
 * It points at patterns to try changing. It cannot say what caused anything.
 */
@Composable
fun PatternsWindow(x: FoodExtrasViewModel, startHabit: String?, onClose: () -> Unit) {
    val p = LocalPaper.current
    val today = LocalDate.now()
    val habits by x.trackers.collectAsState()
    val checks by x.checks.collectAsState()
    val meals by produceState<List<MealEntity>?>(null) { value = x.allMeals() }

    var habitId by remember { mutableStateOf(startHabit) }
    var hours by remember { mutableStateOf(6) }
    var days by remember { mutableStateOf(90) } // 0 is all time

    val chosen = habits.firstOrNull { it.id == habitId } ?: habits.firstOrNull()
    val result = remember(meals, checks, chosen?.id, hours, days) {
        val all = meals
        if (chosen == null || all == null) {
            null
        } else {
            val mine = checks.filter { it.trackerId == chosen.id }
            val from = if (days == 0) (mine.minOfOrNull { it.date }?.let { LocalDate.parse(it) } ?: today) else today.minusDays(days - 1L)
            FoodPatterns.build(all, mine, hours, from, today)
        }
    }

    EditWindow(title = "What came before", sub = "Foods eaten in the hours before a habit you watch", onClose = onClose) {
        if (habits.isEmpty()) {
            Text("Pick a habit to watch first, such as a headache or reflux: on the Food tab open Habits and tap Change.", fontSize = 13.sp, color = p.ink2)
            return@EditWindow
        }
        MonoLabel("Habit")
        ChipFlow { habits.forEach { h -> PaperPill(h.name, h.id == chosen?.id, { habitId = h.id }, dot = argbColor(h.color)) } }
        MonoLabel("Hours before")
        ChipFlow { FoodPatterns.WINDOWS.forEach { h -> PaperPill("$h h", h == hours, { hours = h }) } }
        MonoLabel("Look back")
        ChipFlow {
            listOf(30 to "30 days", 90 to "90 days", 0 to "All time").forEach { (d, label) -> PaperPill(label, d == days, { days = d }) }
        }

        val r = result
        if (r == null) {
            Text("Looking through your log…", fontSize = 13.sp, color = p.ink2)
            return@EditWindow
        }
        if (r.checks == 0) {
            Text("No ticks of " + chosen!!.name + " with a time in this stretch yet.", fontSize = 13.sp, color = p.ink2)
        } else {
            Text(
                r.checks.toString() + (if (r.checks == 1) " tick" else " ticks") + ", " + r.episodes + (if (r.episodes == 1) " separate time" else " separate times") +
                    ". " + r.withFood + " had food logged in the " + hours + " hours before.",
                fontSize = 12.5.sp, color = p.ink2,
            )
        }
        when {
            r.checks == 0 -> Unit
            !r.enough -> NoteCard(
                "Not enough yet",
                "A pattern needs at least " + FoodPatterns.MIN_EPISODES + " times with food logged before them. Keep logging your food and ticking the habit, and it appears here.",
            )
            r.rows.isEmpty() -> NoteCard(
                "Nothing stands out",
                "No food came before " + chosen!!.name + " more often than it usually shows up. Try another number of hours.",
            )
            else -> r.rows.forEach { row -> PatternRow(row, r.withFood, hours) }
        }
        Text(
            "A pattern is not a cause. A food you eat often shows up a lot, which is why each one is compared with how often you eat it at other times. " +
                "If one food stands out, try leaving it out for two weeks and see.",
            fontSize = 11.5.sp, color = p.ink2,
        )
    }
}

@Composable
private fun NoteCard(title: String, text: String) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, shadow = false) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text(text, fontSize = 12.5.sp, color = p.ink2)
    }
}

@Composable
private fun PatternRow(row: FoodPatterns.Row, episodes: Int, hours: Int) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(Modifier.fillMaxWidth()) {
            Text(row.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
            Text(row.before.toString() + " of " + episodes, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        }
        Spacer(Modifier.height(8.dp))
        ShareBar("Before this habit", row.share, p.accent)
        Spacer(Modifier.height(6.dp))
        ShareBar("At other times", row.baseline, p.ink2)
        val gap = Math.round(row.medianGapHours * 2) / 2.0
        Text(
            "Usually about " + (if (gap < 1) "under an hour" else foodNum(gap) + (if (gap == 1.0) " hour" else " hours")) + " earlier, within the " + hours + " hours.",
            fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ShareBar(label: String, share: Double, colour: androidx.compose.ui.graphics.Color) {
    val p = LocalPaper.current
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.weight(1.1f))
        Box(Modifier.weight(1.6f).height(8.dp).background(p.surface2, RoundedCornerShape(4.dp))) {
            if (share > 0.005) Box(Modifier.fillMaxWidth(share.toFloat().coerceIn(0f, 1f)).height(8.dp).background(colour, RoundedCornerShape(4.dp)))
        }
        Text(Math.round(share * 100).toString() + "%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(0.5f).padding(start = 8.dp))
    }
}
