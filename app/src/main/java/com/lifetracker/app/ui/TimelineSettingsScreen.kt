package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.TimelineSettings

/**
 * What the Timeline shows and asks. Every switch only changes how a day is drawn or what is asked: nothing saved is
 * ever changed or removed, and turning a switch back on brings everything back.
 */
@Composable
fun TimelineSettingsScreen(onBack: () -> Unit) {
    val settings = TimelineSettings(LocalContext.current)
    var showGaps by remember { mutableStateOf(settings.showGaps) }
    var group by remember { mutableStateOf(settings.groupRepeatedOpens) }
    var ask by remember { mutableStateOf(settings.askHowTravelled) }
    var keep by remember { mutableStateOf(settings.rememberCategoryChanges) }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("Timeline", "What a day shows and asks", onBack) }

        item { PaperSection("What the day shows") }
        item {
            SetCard {
                ToggleRow(
                    "Show gaps", showGaps, { showGaps = it; settings.showGaps = it },
                    hint = "No data, Offline and Phone off stretches", first = true,
                )
                ToggleRow(
                    "Group repeated opens", group, { group = it; settings.groupRepeatedOpens = it },
                    hint = "Apps used again within 5 minutes make one row",
                )
            }
        }

        item { PaperSection("What it asks and remembers") }
        item {
            SetCard {
                ToggleRow(
                    "Ask how I travelled", ask, { ask = it; settings.askHowTravelled = it },
                    hint = "Quick buttons under a journey with an unknown way", first = true,
                )
                ToggleRow(
                    "Remember my category changes", keep, { keep = it; settings.rememberCategoryChanges = it },
                    hint = "A category you give one app period also applies to that app on every day",
                )
            }
        }
        item {
            Text(
                "These only change how a day is drawn and what is asked. Nothing saved is changed or removed, and switching one back on brings everything back.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
