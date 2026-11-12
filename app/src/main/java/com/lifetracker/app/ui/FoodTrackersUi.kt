package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.DiaryDay
import com.lifetracker.app.data.FoodTrackerEntity
import com.lifetracker.app.data.TrackerEventEntity
import com.lifetracker.app.data.TrackerMath
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperPillBox
import com.lifetracker.app.ui.theme.PaperSheet
import com.lifetracker.app.ui.theme.PaperToggle
import java.time.LocalDate

/*
 * Habits in Food (OpenNutriTracker issue 1304): the habits you pick from the Habits tab, tapped here with one tap, with the time
 * of each check and a count per day, read next to the food in the Diary timeline. They are the same habits as in the Habits
 * tab, so a check made in either place shows in both. They never touch calories.
 */

/** The time of a check, or nothing when the check has no time. */
private fun checkTime(minute: Int): String = if (minute < 0) "" else formatMinute(minute)

@Composable
fun TrackersCard(x: FoodExtrasViewModel, date: LocalDate) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val trackers by x.trackers.collectAsState()
    val week by x.weekEvents.collectAsState()
    val active = trackers.filter { !it.archived }
    val dayEvents = week.filter { it.date == date.toString() }.sortedBy { it.minuteOfDay }
    var managing by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<TrackerEventEntity?>(null) }
    var chartId by remember { mutableStateOf("") }
    val chartTracker = active.firstOrNull { it.id == chartId } ?: active.firstOrNull()
    val today = date == LocalDate.now()

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Habits", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    when {
                        active.isEmpty() -> "Pick habits from the Habits tab to see their ticks next to your food."
                        today -> "Tap one to log it now"
                        else -> "Tap one, then choose the time"
                    },
                    fontSize = 12.sp, color = p.ink2,
                )
            }
            PaperPill(if (trackers.isEmpty()) "Pick" else "Change", false, { managing = true })
        }

        if (active.isNotEmpty()) {
            ChipFlow(modifier = Modifier.padding(top = 10.dp)) {
                active.forEach { t ->
                    val n = dayEvents.count { it.trackerId == t.id }
                    PaperPillBox(
                        selected = false,
                        onClick = {
                            if (today) {
                                x.tap(t, date, minuteNow())
                            } else {
                                TimePickerDialog(context, { _, h, m -> x.tap(t, date, h * 60 + m) }, 12, 0, false).show()
                            }
                        },
                    ) {
                        IconGlyph(icon = t.icon, tint = argbColor(t.color), size = 18.dp, fallback = t.name)
                        Text(if (n > 0) "${t.name}  ×$n" else t.name)
                    }
                }
            }
        }

        if (dayEvents.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                dayEvents.forEach { e ->
                    val t = trackers.firstOrNull { it.id == e.trackerId }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { editingEvent = e }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (t != null) IconGlyph(icon = t.icon, tint = argbColor(t.color), size = 18.dp, fallback = t.name)
                        Text(t?.name ?: "Removed habit", fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(checkTime(e.minuteOfDay), fontSize = 13.sp, color = p.ink2)
                    }
                }
            }
        }

        if (chartTracker != null) {
            MonoLabel("This week")
            if (active.size > 1) {
                ChipFlow(modifier = Modifier.padding(bottom = 6.dp)) {
                    active.forEach { t -> PaperPill(t.name, t.id == chartTracker.id, { chartId = t.id }) }
                }
            }
            WeekBars(week, date, chartTracker)
        }
    }

    if (managing) HabitPickerWindow(x, onClose = { managing = false })
    editingEvent?.let { e ->
        EventSheet(x, e, trackers.firstOrNull { it.id == e.trackerId }, onDismiss = { editingEvent = null })
    }
}

@Composable
private fun WeekBars(events: List<TrackerEventEntity>, date: LocalDate, tracker: FoodTrackerEntity) {
    val p = LocalPaper.current
    val days = com.lifetracker.app.data.FoodLogic.weekOf(date)
    val counts = TrackerMath.counts(events, days, tracker.id)
    val top = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, d ->
            val n = counts[i]
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (n > 0) n.toString() else "", fontSize = 11.sp, color = p.ink2)
                Box(modifier = Modifier.height(54.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height((4 + 50 * n / top).dp.takeIf { n > 0 } ?: 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (n > 0) argbColor(tracker.color) else p.surface2),
                    )
                }
                Text(
                    names[i], fontSize = 11.sp, color = if (d == date) p.ink else p.ink2,
                    fontWeight = if (d == date) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The window that picks which habits show in Food. */
@Composable
fun HabitPickerWindow(x: FoodExtrasViewModel, onClose: () -> Unit) {
    val p = LocalPaper.current
    val habits by x.habits.collectAsState()
    val picked by x.picked.collectAsState()
    var creating by remember { mutableStateOf(false) }
    EditWindow(title = "Habits in Food", sub = "Switch on the habits whose ticks you want next to your food.", onClose = onClose) {
        if (habits.isEmpty()) Text("You have no habits yet. Make one below, or in the Habits tab.", fontSize = 13.sp, color = p.ink2)
        habits.forEach { h ->
            val on = h.id in picked
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = { x.setPicked(h.id, !on) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(argbColor(h.color).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        IconGlyph(icon = h.icon, tint = argbColor(h.color), size = 22.dp, fallback = h.name)
                    }
                    Text(h.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    PaperToggle(on, { x.setPicked(h.id, it) })
                }
            }
        }
        PaperButton("New habit", { creating = true }, icon = R.drawable.ic_p_plus)
        Text(
            "Examples: headache, reflux, bloating, tea or coffee, a medicine dose, a craving, a nap. A habit made here also appears in the Habits tab, with no streak. Habits never change your calories.",
            fontSize = 12.sp, color = p.ink2,
        )
    }
    if (creating) NewHabitWindow(x, onDismiss = { creating = false })
}

@Composable
private fun NewHabitWindow(x: FoodExtrasViewModel, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0xFF5B5FC7L) }
    var error by remember { mutableStateOf<String?>(null) }

    EditWindow(
        title = "New habit",
        sub = "It is added to the Habits tab, and one tap here checks it with the time.",
        onClose = onDismiss,
        onSave = {
            if (name.isBlank()) {
                error = "Give it a name."
            } else {
                x.createHabit(name, icon, color)
                onDismiss()
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(argbColor(color).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                IconGlyph(icon = icon, tint = argbColor(color), size = 26.dp, fallback = name.ifBlank { "?" })
            }
            Text(name.ifBlank { "Name" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
        }
        PaperField(name, { name = it; error = null }, "Name", placeholder = "e.g. Headache", singleLine = true)
        MonoLabel("Colour")
        ColorPickerPanel(selected = color, onSelected = { color = it })
        MonoLabel("Icon")
        IconPickerPanel(selected = icon, tint = argbColor(color), onSelected = { icon = it })
        if (error != null) Text(error!!, color = p.bad, fontSize = 13.sp)
    }
}

@Composable
private fun EventSheet(x: FoodExtrasViewModel, event: TrackerEventEntity, tracker: FoodTrackerEntity?, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var minute by remember { mutableIntStateOf(if (event.minuteOfDay < 0) minuteNow() else event.minuteOfDay) }
    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle(tracker?.name ?: "Habit", event.date)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Time", fontSize = 13.sp, color = p.ink2)
                TimeButton(minute, { minute = it })
            }
            if (event.minuteOfDay < 0) Text("This check was made without a time. Saving gives it one.", fontSize = 12.sp, color = p.ink2)
            PaperButton(
                "Save", { x.saveEvent(event.copy(minuteOfDay = minute)); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )
            PaperButton("Take this check away", { x.deleteEvent(event); onDismiss() }, style = PaperButtonStyle.Danger, small = true)
            Spacer(Modifier.height(12.dp))
        }
    }
}
