package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.ui.theme.AppTheme
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.ThemeMode
import com.lifetracker.app.data.CallsCollector
import com.lifetracker.app.data.ChargeSettings
import com.lifetracker.app.data.HabitSource
import com.lifetracker.app.data.HabitsSettings
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.UsageCollector

/** Everything that can be opened from the Settings tab. The numbers for each live under Stats. */
enum class Page { Phone, Steps, Places, Calls, Charging, Activities, Habits, Backup }

/**
 * The Settings tab, laid out like the demo: a big title, then four groups of rows. Each row has an icon, a name, one small
 * line and a chevron, and opens the settings half of that part of the app. Where a permission is missing the small line says so.
 */
@Composable
fun SettingsScreen(onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val places = PlacesSettings(context)
    val usageOn = UsageCollector.hasAccess(context)
    val callsOn = CallsCollector.hasAccess(context)
    val chargingOn = ChargeSettings(context).track
    val fromStreak = HabitsSettings(context).source == HabitSource.Streak

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                Text(text = "Everything that is not data", fontSize = 12.sp, color = paper.ink2)
            }
        }

        item { PaperSection("Timeline") }
        item {
            SettingsRow(R.drawable.ic_p_list, "Activities", "The kinds of activity you sort things into") { onOpen(Page.Activities) }
        }

        item { PaperSection("Collected from your phone") }
        item {
            SettingsRow(
                R.drawable.ic_p_phone, "Phone and usage",
                (if (usageOn) "" else "Not allowed yet · ") + "Short stretches and hidden apps",
            ) { onOpen(Page.Phone) }
        }
        item { SettingsRow(R.drawable.ic_p_moon, "Sleep and steps", "Health Connect and step counter") { onOpen(Page.Steps) } }
        item {
            SettingsRow(
                R.drawable.ic_p_pin, "Places and trips",
                (if (places.enabled) "" else "Switched off · ") + "Permissions, battery and recording",
            ) { onOpen(Page.Places) }
        }
        item {
            SettingsRow(
                R.drawable.ic_p_call, "Calls",
                (if (callsOn) "" else "Not allowed yet · ") + "Shortest call to show",
            ) { onOpen(Page.Calls) }
        }
        item {
            SettingsRow(
                R.drawable.ic_p_bolt, "Charging",
                (if (chargingOn) "" else "Switched off · ") + "Recording and the banner",
            ) { onOpen(Page.Charging) }
        }

        item { PaperSection("Trackers") }
        item {
            SettingsRow(
                R.drawable.ic_p_habits, "Habits",
                if (fromStreak) "Imported from Streak" else "Tracked in this app",
            ) { onOpen(Page.Habits) }
        }

        item { PaperSection("This app") }
        item {
            SettingsRow(R.drawable.ic_p_cloud, "Appearance and backups", "Light or dark, backups, import and export") { onOpen(Page.Backup) }
        }

        // Until Appearance moves into that page (part P7h-g), the Look choice stays here so it is not lost.
        item { PaperSection("Look") }
        item {
            val mode = AppTheme.mode
            PaperSegmented(
                options = ThemeMode.entries.map { it.label },
                selected = ThemeMode.entries.indexOf(mode),
                onSelect = { AppTheme.set(context, ThemeMode.entries[it]) },
            )
        }
    }
}
