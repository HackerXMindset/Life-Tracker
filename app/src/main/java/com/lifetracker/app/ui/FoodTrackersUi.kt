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
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperPillBox
import com.lifetracker.app.ui.theme.PaperSheet
import com.lifetracker.app.ui.theme.PaperToggle
import java.time.LocalDate

/*
 * Custom trackers (OpenNutriTracker issue 1304): count anything with one tap, see the time of each tap and a count per day,
 * and read it all next to the food in the Diary timeline. They never touch calories.
 */

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
                Text("Trackers", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(
                    when {
                        active.isEmpty() -> "Count anything with one tap, like a headache or a cup of tea."
                        today -> "Tap one to log it now"
                        else -> "Tap one, then choose the time"
                    },
                    fontSize = 12.sp, color = p.ink2,
                )
            }
            PaperPill(if (trackers.isEmpty()) "Add" else "Manage", false, { managing = true })
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
                        Text(t?.name ?: "Removed tracker", fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                        if (e.note.isNotBlank()) Text(e.note, fontSize = 12.sp, color = p.ink2, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(formatMinute(e.minuteOfDay), fontSize = 13.sp, color = p.ink2)
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

    if (managing) TrackersWindow(x, trackers, onClose = { managing = false })
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

@Composable
private fun TrackersWindow(x: FoodExtrasViewModel, trackers: List<FoodTrackerEntity>, onClose: () -> Unit) {
    val p = LocalPaper.current
    var editing by remember { mutableStateOf<FoodTrackerEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    EditWindow(title = "Trackers", sub = "Tap one to change it", onClose = onClose) {
        trackers.forEach { t ->
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = { editing = t }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(argbColor(t.color).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        IconGlyph(icon = t.icon, tint = argbColor(t.color), size = 22.dp, fallback = t.name)
                    }
                    Text(t.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f))
                    if (t.archived) Text("Hidden", fontSize = 12.sp, color = p.ink2)
                }
            }
        }
        PaperButton("New tracker", { creating = true }, icon = R.drawable.ic_p_plus)
        Text(
            "Examples: headache, reflux, bloating, tea or coffee, a medicine dose, a craving, a nap. Trackers never change your calories.",
            fontSize = 12.sp, color = p.ink2,
        )
    }
    if (creating) TrackerEditor(x, null, onDismiss = { creating = false })
    editing?.let { TrackerEditor(x, it, onDismiss = { editing = null }) }
}

@Composable
private fun TrackerEditor(x: FoodExtrasViewModel, initial: FoodTrackerEntity?, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember { mutableStateOf(initial?.icon.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: 0xFF5B5FC7L) }
    var hidden by remember { mutableStateOf(initial?.archived ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    EditWindow(
        title = if (initial == null) "New tracker" else name.ifBlank { "Tracker" },
        sub = "A name and an icon. One tap logs it with the time.",
        onClose = onDismiss,
        onSave = {
            if (name.isBlank()) {
                error = "Give it a name."
            } else {
                x.saveTracker(
                    FoodTrackerEntity(
                        id = initial?.id ?: TrackerMath.newId(), name = name.trim(), icon = icon, color = color,
                        sortOrder = initial?.sortOrder ?: -1, archived = hidden,
                    ),
                )
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
        if (initial != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hide it", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("It is not offered for logging, but its history stays.", fontSize = 12.sp, color = p.ink2)
                }
                PaperToggle(hidden, { hidden = it })
            }
        }
        if (error != null) Text(error!!, color = p.bad, fontSize = 13.sp)
        if (initial != null) PaperButton("Delete with its history", { confirmDelete = true }, style = PaperButtonStyle.Danger, small = true)
    }
    if (confirmDelete && initial != null) {
        PaperDialog(
            title = "Delete ${initial.name}?", onDismiss = { confirmDelete = false }, confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger, dismissText = "Keep",
            onConfirm = { confirmDelete = false; x.deleteTracker(initial); onDismiss() },
        ) { Text("Every tap of it is deleted too. Hide it instead to keep the history.", color = p.ink) }
    }
}

@Composable
private fun EventSheet(x: FoodExtrasViewModel, event: TrackerEventEntity, tracker: FoodTrackerEntity?, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var minute by remember { mutableIntStateOf(event.minuteOfDay) }
    var note by remember { mutableStateOf(event.note) }
    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle(tracker?.name ?: "Tracker", event.date)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Time", fontSize = 13.sp, color = p.ink2)
                TimeButton(minute, { minute = it })
            }
            PaperField(note, { note = it }, "Note (optional)", placeholder = "e.g. after the biryani", singleLine = true)
            PaperButton(
                "Save", { x.saveEvent(event.copy(minuteOfDay = minute, note = note.trim())); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )
            PaperButton("Delete this one", { x.deleteEvent(event); onDismiss() }, style = PaperButtonStyle.Danger, small = true)
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- the Diary timeline ----

/** Days newest first: the date, the day's calories, the foods with their times and the taps as chips. */
@Composable
fun FoodDiaryWindow(x: FoodExtrasViewModel, onPickDay: (LocalDate) -> Unit, onClose: () -> Unit) {
    val p = LocalPaper.current
    val days by x.diary.collectAsState()
    val trackers by x.trackers.collectAsState()
    val span by x.diarySpanDays.collectAsState()
    val today = LocalDate.now()

    EditWindow(title = "Diary", sub = "Newest first. Tap a day to open it. The last $span days.", onClose = onClose) {
        if (days.isEmpty()) Text("Nothing logged in this time.", color = p.ink2, fontSize = 13.sp)
        days.forEach { day -> DiaryDayCard(day, trackers, today, onClick = { onPickDay(day.date); onClose() }) }
        PaperButton("Show 30 days earlier", { x.showEarlier() }, style = PaperButtonStyle.Ghost, small = true)
    }
}

@Composable
private fun DiaryDayCard(day: DiaryDay, trackers: List<FoodTrackerEntity>, today: LocalDate, onClick: () -> Unit) {
    val p = LocalPaper.current
    val chips = TrackerMath.chips(day.events, trackers)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(if (day.hasEvents) p.sun else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (day.hasEvents) p.sun else p.pen, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(TimelineTop.title(day.date, today), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(day.date.toString(), fontSize = 11.sp, color = p.ink2)
            }
            Text(kc(day.kcal) + " kcal", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        }
        if (day.meals.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                day.meals.forEach { m ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatMinute(m.minuteOfDay), fontSize = 12.sp, color = p.ink2, modifier = Modifier.width(44.dp))
                        Text(m.name, fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(kc(m.grams) + " g", fontSize = 12.sp, color = p.ink2)
                    }
                }
            }
        }
        if (chips.isNotEmpty()) {
            ChipFlow(modifier = Modifier.padding(top = 8.dp)) {
                chips.forEach { c ->
                    Row(
                        modifier = Modifier
                            .background(argbColor(c.color).copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        IconGlyph(icon = c.icon, tint = argbColor(c.color), size = 14.dp, fallback = c.name)
                        Text(c.text + "  " + c.minutes.joinToString(" ") { formatMinute(it) }, fontSize = 12.sp, color = p.ink)
                    }
                }
            }
        }
    }
}
