package com.lifetracker.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.lifetracker.app.ui.theme.PaperCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.ChargeStats

/** Charging: settings, the banner, and every time your phone was on a charger. */
@Composable
fun ChargingScreen(onBack: () -> Unit, part: Part = Part.Data, vm: ChargingViewModel = viewModel()) {
    val context = LocalContext.current
    val rows by vm.rows.collectAsState()
    val range by vm.range.collectAsState()
    val track by vm.track.collectAsState()
    val showBanner by vm.showBanner.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    val notificationsOn by vm.notificationsOn.collectAsState()
    var editing by remember { mutableStateOf<ChargeRow?>(null) }
    BackHandler(onBack = onBack)
    StartOnToday(vm::setRange)

    DisposableEffect(context) {
        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshNotifications()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val totals = ChargeStats.totals(rows.map { it.session })
    val usedMs = rows.sumOf { it.phoneUseMs }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (part == Part.Settings) {
            item { SettingsTop("Charging", "Recording and the banner", onBack) }
        } else {
            item { TextButton(onClick = onBack) { Text("Back") } }
            item { ScreenHeader(label = "Stats", badge = "On this phone", title = "Charging") }
        }

        if (part == Part.Settings) {
            item {
                SetCard {
                    ToggleRow("Record charging", track, vm::setTrack, hint = "Start, end, battery level and speed", first = true)
                    ToggleRow("Banner when I plug in", showBanner, vm::setShowBanner, hint = "Asks what you are charging with")
                    ToggleRow("Show charging on the Timeline", showOnTimeline, vm::setShowOnTimeline)
                }
            }
            if (showBanner && !notificationsOn) {
                item { SetupNote("The banner needs notifications. Allow them in Settings > Permissions.") }
            }

        }
        if (part == Part.Data && !track) {
            item { SetupNote("Charging is not being recorded. Turn it on in the Settings tab, under Charging.") }
        }
        if (part == Part.Data) {
            item { RangePicker(choice = range, onChange = vm::setRange) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChargeStat("Sessions", totals.sessions.toString(), Modifier.weight(1f))
                    ChargeStat("Charging", ChargeStats.durationText(totals.chargingMs), Modifier.weight(1f))
                    ChargeStat("Used phone", ChargeStats.durationText(usedMs), Modifier.weight(1f))
                }
            }

            if (rows.isEmpty()) {
                item {
                    Text(
                        "No charging on these days. Plug in your phone and it appears here. Days from before this step cannot be filled in.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { PaperSection("Sessions · tap to say what you used") }
                items(rows.take(100), key = { it.session.id }) { row ->
                    val s = row.session
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = row },
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            ChargeStats.title(s, row.guess),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            formatDateTime(s.startMs) + " · " + ChargeStats.detail(s),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (row.phoneUseMs >= 60_000L) {
                            Text(
                                "Used the phone for " + ChargeStats.durationText(row.phoneUseMs) + " of this",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

        }
        if (part == Part.Settings) {
            item { PaperSection("How it works") }
            item {
                SetCard {
                    AccordionRow(
                        "How it works",
                        "Android cannot tell a power bank from a wall charger, so you tell the app once from the banner and it learns: " +
                            "the same kind of plug and about the same speed, tagged the same way at least 3 times, shows up as \"(guess)\". " +
                            "The app does not run in the background while the phone is not charging. While charging, Android wakes it about every 15 minutes " +
                            "to note the battery level and speed, so a session's end time can be up to 15 minutes early, unless the app was open when you unplugged. " +
                            "The speed shown is what goes into the battery, a little less than the charger's label, and lower if you were using the phone.",
                        first = true,
                    )
                    AccordionRow(
                        "If the banner never appears",
                        "Open Android Settings > Apps > this app, set Battery to No restrictions, " +
                            "and allow Notifications including pop-ups. Android otherwise stops background work to save battery.",
                    )
                }
            }
            item {
                Text(
                    "Backups include your charging history. Nothing leaves the phone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    editing?.let { row ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("What did you charge with?") },
            text = {
                Column {
                    Text(
                        formatDateTime(row.session.startMs) + " · " + ChargeStats.detail(row.session),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChargeStats.SOURCES.forEach { source ->
                        TextButton(onClick = {
                            vm.setSource(row.session.id, source)
                            editing = null
                        }) { Text(ChargeStats.sourceLabel(source) + if (row.session.source == source) "  ✓" else "") }
                    }
                    TextButton(onClick = {
                        vm.setSource(row.session.id, "")
                        editing = null
                    }) { Text("Not sure") }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun ChargeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MonoLabel(label)
        Text(
            text = value,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
