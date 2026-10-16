package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.ui.theme.AppTheme
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.ThemeMode
import com.lifetracker.app.ui.theme.Wobble
import com.lifetracker.app.data.CallsCollector
import com.lifetracker.app.data.ChargeSettings
import com.lifetracker.app.data.HabitSource
import com.lifetracker.app.data.HabitsSettings
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.UsageCollector

/** Everything that can be opened from the Settings tab. The numbers for each live under Stats. */
enum class Page { Phone, Steps, Places, Calls, Charging, Activities, Habits, Backup }

/**
 * The Settings tab: one place for every permission and switch. Each row opens the settings half of that part of the app
 * (what to allow, what to show on the Timeline, how closely to watch) and says whether it is switched on.
 * Paper look: a big title, orange-swipe headings, and one paper card per row with its state as a small chip.
 */
@Composable
fun SettingsScreen(onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val places = PlacesSettings(context)
    val usageOn = UsageCollector.hasAccess(context)
    val callsOn = CallsCollector.hasAccess(context)
    val chargingOn = ChargeSettings(context).track

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Settings",
                    fontSize = 38.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = paper.ink,
                    maxLines = 1,
                )
                Text(
                    text = "Allow what the phone can record and look after your data. Everything stays on this phone.",
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
            }
        }

        item { PaperSection("Look") }
        item {
            val mode = AppTheme.mode
            PaperSegmented(
                options = ThemeMode.entries.map { it.label },
                selected = ThemeMode.entries.indexOf(mode),
                onSelect = { AppTheme.set(context, ThemeMode.entries[it]) },
            )
        }

        item { PaperSection("What the phone records") }
        item {
            SettingsRow(
                title = "Phone usage",
                status = if (usageOn) "Allowed" else "Not allowed yet",
                good = usageOn,
                hint = "Usage access, what shows on the Timeline, apps linked to an activity, hidden apps",
                onClick = { onOpen(Page.Phone) },
            )
        }
        item {
            SettingsRow(
                title = "Steps and sleep",
                status = null,
                good = true,
                hint = "Health Connect, the phone's step counter, how sleep is worked out",
                onClick = { onOpen(Page.Steps) },
            )
        }
        item {
            SettingsRow(
                title = "Places and trips",
                status = if (places.enabled) "Watching" else "Switched off",
                good = places.enabled,
                hint = "Location permissions, your places, trips, keeping the app awake, how closely to watch, status, GPX and CSV files",
                onClick = { onOpen(Page.Places) },
            )
        }
        item {
            SettingsRow(
                title = "Calls",
                status = if (callsOn) "Allowed" else "Not allowed yet",
                good = callsOn,
                hint = "Call log access, copying older calls, what shows on the Timeline",
                onClick = { onOpen(Page.Calls) },
            )
        }
        item {
            SettingsRow(
                title = "Charging",
                status = if (chargingOn) "Recording" else "Switched off",
                good = chargingOn,
                hint = "Recording, the banner when you plug in, notifications",
                onClick = { onOpen(Page.Charging) },
            )
        }

        item { PaperSection("Your data") }
        item {
            val fromStreak = HabitsSettings(context).source == HabitSource.Streak
            SettingsRow(
                title = "Habits",
                status = if (fromStreak) "From Streak" else "Own data",
                good = true,
                hint = "Track habits in this app, or import them from Streak",
                onClick = { onOpen(Page.Habits) },
            )
        }
        item {
            SettingsRow(
                title = "Activities",
                status = null,
                good = true,
                hint = "The choices in the Log pop-up: names, colours, hide the ones you never use",
                onClick = { onOpen(Page.Activities) },
            )
        }
        item {
            SettingsRow(
                title = "Backup and import",
                status = null,
                good = true,
                hint = "Back up and restore everything, bring in Streak and OpenNutriTracker data",
                onClick = { onOpen(Page.Backup) },
            )
        }
    }
}

/** One paper card: the name and a quiet line on the left, the real state as a small chip, and a chevron. */
@Composable
private fun SettingsRow(title: String, status: String?, good: Boolean, hint: String, onClick: () -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                Text(text = hint, fontSize = 12.sp, color = paper.ink2)
            }
            if (status != null) StateChip(status, good)
            Chevron(paper.ink2)
        }
    }
}

/** "Allowed" in green, "Not allowed yet" in red: the same words as before, now as a small paper chip. */
@Composable
private fun StateChip(text: String, good: Boolean) {
    val paper = LocalPaper.current
    val color = if (good) paper.accent else paper.bad
    Text(
        text = text,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), Wobble.Pill)
            .border(1.25.dp, color.copy(alpha = 0.6f), Wobble.Pill)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun Chevron(color: Color) {
    Canvas(modifier = Modifier.size(width = 8.dp, height = 14.dp)) {
        val w = 1.75.dp.toPx()
        drawLine(color, Offset(1.dp.toPx(), 1.dp.toPx()), Offset(size.width - 1.dp.toPx(), size.height / 2), w, StrokeCap.Round)
        drawLine(color, Offset(size.width - 1.dp.toPx(), size.height / 2), Offset(1.dp.toPx(), size.height - 1.dp.toPx()), w, StrokeCap.Round)
    }
}
