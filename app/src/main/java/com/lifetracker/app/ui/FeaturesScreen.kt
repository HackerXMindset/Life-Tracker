package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.Feature
import com.lifetracker.app.data.Features
import com.lifetracker.app.ui.theme.LocalPaper

/**
 * One switch for each part of the app. Off means it disappears from the tabs, Settings, the Timeline and Stats, and nothing
 * new is collected for it until you switch it on again. What was collected before is kept, and comes back with the switch.
 */
@Composable
fun FeaturesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val changed by Features.changes.collectAsState()
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("Features", "Switch parts of the app off", onBack) }
        item {
            Text(
                "Switch off what you do not use. It disappears from the tabs, Settings, the Timeline and Stats, and the app stops collecting for it " +
                    "until you switch it on again. Nothing is deleted: what was collected before is kept and comes back with the switch.",
                fontSize = 13.sp,
                color = paper.ink2,
            )
        }
        item { PaperSection("Trackers you type into") }
        item {
            SetCard {
                featureRow(context, Feature.FOOD, changed, first = true)
                featureRow(context, Feature.HABITS, changed)
                featureRow(context, Feature.MONEY, changed)
                featureRow(context, Feature.STATS, changed)
            }
        }
        item { PaperSection("Collected from your phone") }
        item {
            SetCard {
                featureRow(context, Feature.PHONE_USAGE, changed, first = true)
                featureRow(context, Feature.STEPS_SLEEP, changed)
                featureRow(context, Feature.CALLS, changed)
                featureRow(context, Feature.CHARGING, changed)
                featureRow(context, Feature.PLACES, changed)
                featureRow(context, Feature.MAPS, changed)
            }
        }
        item { PaperSection("Automation") }
        item {
            SetCard {
                featureRow(context, Feature.RULES, changed, first = true)
                featureRow(context, Feature.GOALS, changed)
            }
        }
        item {
            Text(
                "Switching off Places and trips stops the position readings and the live map. Switching off Maps only stops map pictures and offline downloads: trips are then drawn on plain paper.",
                fontSize = 12.sp,
                color = paper.ink2,
            )
        }
    }
}

@Composable
private fun featureRow(context: android.content.Context, feature: Feature, changed: Int, first: Boolean = false) {
    ToggleRow(
        label = feature.title,
        checked = changed >= 0 && Features.isOn(context, feature),
        onChange = { Features.set(context, feature, it) },
        hint = feature.hint,
        first = first,
    )
}
