package com.lifetracker.app.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.FocusCredit
import com.lifetracker.app.data.FocusEngine
import com.lifetracker.app.data.FocusNotifier
import com.lifetracker.app.data.FocusSettings
import com.lifetracker.app.data.FocusTimer
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The focus timer's whole page (Habits H13): set-up when nothing runs, the running page when a session does. The timer
 * itself lives in [FocusEngine], so it keeps going (and rings) when this page is closed or the screen is off.
 */
@Composable
fun FocusPage(habits: List<HabitEntity>, startHabitId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by remember { FocusEngine.state(context) }.collectAsState()
    val session = state
    if (session == null) FocusSetup(habits, startHabitId, onBack) else FocusRunning(habits, onBack)
}

private const val COUNTDOWN = 0
private const val STOPWATCH = 1
private const val POMODORO = 2

@Composable
private fun FocusSetup(habits: List<HabitEntity>, startHabitId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val settings = remember { FocusSettings(context) }
    val first = habits.firstOrNull { it.id == startHabitId }

    var habitId by remember { mutableStateOf(first?.id ?: "") }
    var minutes by remember { mutableStateOf((first?.let { FocusCredit.minutesOf(it) } ?: settings.minutes).takeIf { it > 0 }?.toString() ?: "25") }
    var breakMinutes by remember { mutableStateOf((first?.let { FocusCredit.breakOf(it) } ?: settings.breakMinutes).let { if (it > 0) it.toString() else "5" }) }
    var mode by remember {
        mutableStateOf(
            when {
                first != null && FocusCredit.minutesOf(first) <= 0 -> STOPWATCH
                first != null && FocusCredit.breakOf(first) > 0 -> POMODORO
                first == null && settings.breakMinutes > 0 -> POMODORO
                else -> COUNTDOWN
            },
        )
    }
    var label by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf(emptyList<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val notificationsOn = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }

    LaunchedEffect(habitId) {
        labels = runCatching { AppDatabase.get(context).focusDao().labelsOf(habitId).take(8) }.getOrDefault(emptyList())
    }

    fun pick(h: HabitEntity?) {
        habitId = h?.id ?: ""
        label = ""
        error = null
        if (h != null) {
            val m = FocusCredit.minutesOf(h)
            val b = FocusCredit.breakOf(h)
            minutes = if (m > 0) m.toString() else "25"
            breakMinutes = if (b > 0) b.toString() else "5"
            mode = if (m <= 0) STOPWATCH else if (b > 0) POMODORO else COUNTDOWN
        }
    }

    HabitPage(
        title = "Focus",
        onBack = onBack,
        confirmText = "Start",
        onConfirm = {
            val m = minutes.trim().toIntOrNull() ?: 0
            val b = breakMinutes.trim().toIntOrNull() ?: 0
            when {
                mode != STOPWATCH && m !in 1..600 -> error = "Minutes must be 1 to 600."
                mode == POMODORO && b !in 1..120 -> error = "The break must be 1 to 120 minutes."
                else -> scope.launch {
                    FocusEngine.start(context, habitId, label, if (mode == STOPWATCH) 0 else m, if (mode == POMODORO) b else 0)
                }
            }
        },
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { Text(it, fontSize = 13.sp, color = paper.bad, fontWeight = FontWeight.Bold) }
            if (!notificationsOn) {
                Text(
                    "Notifications are off, so the banner and the ring at the end will not show. Allow them in Settings > Permissions.",
                    fontSize = 12.sp, color = paper.bad,
                )
            }
            MonoLabel("Habit")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("No habit", habitId.isEmpty(), { pick(null) })
                habits.forEach { h -> PaperPill(h.name, habitId == h.id, { pick(h) }) }
            }
            MonoLabel("Kind")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Countdown", mode == COUNTDOWN, { mode = COUNTDOWN; error = null })
                PaperPill("Stopwatch", mode == STOPWATCH, { mode = STOPWATCH; error = null })
                PaperPill("Pomodoro", mode == POMODORO, { mode = POMODORO; error = null })
            }
            if (mode != STOPWATCH) {
                PaperField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(3); error = null },
                    label = if (mode == POMODORO) "Minutes of each focus round" else "Minutes",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { m -> PaperPill("$m min", minutes == m.toString(), { minutes = m.toString(); error = null }) }
                }
            } else {
                Text("The stopwatch has no end: it counts up until you end it.", fontSize = 12.sp, color = paper.ink2)
            }
            if (mode == POMODORO) {
                PaperField(
                    value = breakMinutes,
                    onValueChange = { breakMinutes = it.filter(Char::isDigit).take(3); error = null },
                    label = "Minutes of each break",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                val long = settings.longBreakMinutes
                Text(
                    if (long > 0) "Every 4th break is a long one of $long minutes (change it in Settings > Habits > Focus)."
                    else "No long break. Set one in Settings > Habits > Focus.",
                    fontSize = 12.sp, color = paper.ink2,
                )
            }
            PaperField(
                value = label,
                onValueChange = { label = it.take(60) },
                label = "What are you working on? (optional)",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (labels.isNotEmpty()) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { l -> PaperPill(l, label == l, { label = l }) }
                }
            }
            Text(
                "A finished round ticks the habit (a habit that counts minutes gets the minutes) and puts a block on the Timeline. " +
                    "The timer keeps running and rings with the screen off.",
                fontSize = 11.5.sp, color = paper.ink2,
            )
        }
    }
}

@Composable
private fun FocusRunning(habits: List<HabitEntity>, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    val flow = remember { FocusEngine.state(context) }
    val state by flow.collectAsState()
    val latest = rememberUpdatedState(state)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ask by remember { mutableStateOf(false) }

    // Ticks the clock, and applies a phase that reached its end while this page is open.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val s = latest.value
            if (s != null && FocusTimer.phaseDue(s, now)) FocusEngine.checkEnd(context)
            delay(500)
        }
    }

    val s = state ?: return
    val name = habits.firstOrNull { it.id == s.habitId }?.name
    val seconds = FocusTimer.displaySeconds(s, now)
    val live = FocusTimer.isRunning(s)

    fun end(completed: Boolean?) {
        scope.launch {
            val kept = FocusEngine.stop(context, completed)
            Toast.makeText(
                context,
                if (kept != null) "Saved ${kept.minutes} min" else "Nothing saved (under 30 seconds, or a break)",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    HabitPage(title = name ?: "Focus", onBack = onBack) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = FocusNotifier.phaseText(s),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = paper.ink2, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = FocusTimer.clock(seconds),
                fontSize = 64.sp, lineHeight = 70.sp, fontWeight = FontWeight.ExtraBold, color = paper.ink, textAlign = TextAlign.Center,
            )
            if (s.label.isNotBlank()) Text(s.label, fontSize = 15.sp, color = paper.ink, textAlign = TextAlign.Center)
            if (FocusTimer.isPomodoro(s)) {
                Text("Round ${s.round}", fontSize = 12.sp, color = paper.ink2)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    s.finished -> Unit
                    s.awaiting -> PaperButton("Continue", { scope.launch { FocusEngine.next(context) } })
                    live -> PaperButton("Pause", { scope.launch { FocusEngine.pause(context) } })
                    else -> PaperButton("Resume", { scope.launch { FocusEngine.resume(context) } })
                }
                if (s.isBreak && !s.awaiting) PaperButton("Skip break", { scope.launch { FocusEngine.next(context) } }, style = PaperButtonStyle.Ghost)
                if (!s.isBreak && !FocusTimer.isFlow(s) && !s.finished && !s.awaiting) {
                    PaperButton("+1 min", { scope.launch { FocusEngine.addMinute(context) } }, style = PaperButtonStyle.Ghost)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!s.finished && !s.awaiting) PaperButton("Start over", { scope.launch { FocusEngine.reset(context) } }, style = PaperButtonStyle.Ghost, small = true)
                PaperButton(
                    if (s.finished) "Close" else "End",
                    { if (s.finished || FocusTimer.metGoal(s, now) || s.isBreak) end(null) else ask = true },
                    style = PaperButtonStyle.Ghost,
                    small = true,
                )
            }
            if (s.finished) {
                Text("Done. This session is saved and on your Timeline.", fontSize = 12.sp, color = paper.ink2, textAlign = TextAlign.Center)
            }
            Text(
                "You can close this page: the timer keeps going, shows a banner and rings when it is over, even with the screen off.",
                fontSize = 11.5.sp, color = paper.ink2, textAlign = TextAlign.Center,
            )
        }
    }

    if (ask) {
        PaperDialog(title = "End this session?", onDismiss = { ask = false }, dismissText = "Keep going") {
            Text("${FocusTimer.clock(FocusTimer.elapsedSeconds(s, now))} done of ${s.targetMinutes} minutes.", fontSize = 13.sp, color = paper.ink2)
            PaperButton("Mark it done", { ask = false; end(true) }, modifier = Modifier.fillMaxWidth())
            PaperButton("End anyway", { ask = false; end(false) }, modifier = Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
        }
    }
}
