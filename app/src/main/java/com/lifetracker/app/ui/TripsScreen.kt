package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import java.time.Instant
import java.time.ZoneId

/** "Home" or, for a spot with no name, "an unnamed spot". */
internal fun tripEnd(name: String) = name.ifEmpty { "unnamed spot" }

/** Trips: how far and how fast you went, and how you travelled. Tap a trip to tell the app which way it was. */
@Composable
fun TripsScreen(onBack: () -> Unit, vm: TripsViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    val range by vm.range.collectAsState()
    val record by vm.recordTrips.collectAsState()
    val pace by vm.tripIntervalSec.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    var choosing by remember { mutableStateOf<TripEntity?>(null) }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { ScreenHeader(label = "Phone", badge = "On this phone", title = "Trips") }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Record my trips", color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = record, onCheckedChange = vm::setRecordTrips)
            }
        }
        item { MonoLabel("How closely to follow a trip") }
        item {
            Text(
                "While you are on the way, the app reads your position this often. Quicker is more exact for speed and route, and uses more battery. Trips need Places to be switched on.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15 s", 30 to "30 s", 60 to "1 min").forEach { (sec, label) ->
                    FilterChip(selected = pace == sec, onClick = { vm.setTripIntervalSec(sec) }, label = { Text(label) })
                }
            }
        }

        item { MonoLabel("Your trips") }
        item { RangePicker(choice = range, onChange = vm::setRange) }
        item {
            Text(
                TripRules.distanceText(data.distanceM.toInt()) + " in " + PlaceRules.durationText(data.ms) +
                    " · ${data.rows.size} trip${if (data.rows.size == 1) "" else "s"}",
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (data.modes.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.modes.forEach { m ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(TripModes.label(m.mode), color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    TripRules.distanceText(m.distanceM.toInt()) + " · " + PlaceRules.durationText(m.ms),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (data.rows.isEmpty()) {
            item {
                Text(
                    "No trips on these days. A trip is recorded when you leave a place or a stop and arrive at the next one, as long as it gets at least 200 m away.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(data.rows.take(300), key = { it.trip.id }) { row ->
                val t = row.trip
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { choosing = t },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        tripEnd(row.from) + " → " + tripEnd(row.to),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        TripModes.label(t.mode) + (if (t.modeSource == "you") "" else " (guess)") + " · " + TripRules.distanceText(t.distanceM) +
                            " · " + PlaceRules.durationText(t.endMs - t.startMs) + " · " + TripRules.avgKmh(t.distanceM, t.endMs - t.startMs) + " km/h",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        formatDateTime(t.startMs) + " → " + Instant.ofEpochMilli(t.endMs).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) } +
                            " · top ${t.topKmh} km/h",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { MonoLabel("On the Timeline") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show trips on the Timeline", color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = showOnTimeline, onCheckedChange = vm::setShowOnTimeline)
            }
        }
        item { MonoLabel("How it works") }
        item {
            Text(
                "A trip starts when you leave a place or stop and ends when you stay somewhere for 5 minutes. " +
                    "The route is the position readings in between; distance and speed come from them, with glitches skipped. " +
                    "Android's walking, cycling and vehicle signals and the speed give a guess at how you travelled. " +
                    "A guess that is not walking or cycling stays \"vehicle\" until you tell it which: tap a trip and pick Auto-rickshaw, Motorbike, Bus and so on. " +
                    "It learns from what you pick: the same two places, or trips with a similar speed, get the same answer next time. " +
                    "Trips and their readings are never deleted and are in your backups. Nothing leaves the phone.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    choosing?.let { trip ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("How did you travel?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TripModes.list.filter { it.id != "vehicle" }.forEach { m ->
                        TextButton(
                            onClick = {
                                vm.setMode(trip, m.id)
                                choosing = null
                            },
                        ) { Text(m.label + if (trip.mode == m.id) "  ✓" else "") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("Cancel") } },
        )
    }
}
