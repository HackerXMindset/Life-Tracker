package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.CallsCollector
import com.lifetracker.app.data.ChargeSettings
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.UsageCollector

/** Everything that can be opened from the Settings tab. The numbers for each live under Stats. */
enum class Page { Phone, Steps, Places, Calls, Charging, Activities, Backup }

/**
 * The Settings tab: one place for every permission and switch. Each row opens the settings half of that part of the app
 * (what to allow, what to show on the Timeline, how closely to watch) and says whether it is switched on.
 */
@Composable
fun SettingsScreen(onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    val places = PlacesSettings(context)
    val usageOn = UsageCollector.hasAccess(context)
    val callsOn = CallsCollector.hasAccess(context)
    val chargingOn = ChargeSettings(context).track

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(label = "Settings", badge = "On this phone", title = "Settings") }
        item {
            Text(
                text = "Allow what the phone can record, choose what shows on the Timeline, and look after your data. Everything stays on this phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { MonoLabel("What the phone records") }
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

        item { MonoLabel("Your data") }
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

@Composable
private fun SettingsRow(title: String, status: String?, good: Boolean, hint: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (status != null) {
                    Text(
                        text = status,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(text = hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
