package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val SECONDS_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun clockSeconds(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(SECONDS_FORMAT)

private fun clockOf(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

/** "Home" or "an unnamed spot". */
private fun spot(name: String) = name.ifEmpty { "an unnamed spot" }

/**
 * The page of one trip: its path, numbers, the way you travelled, and the things you can do to it: join it to the trip before
 * or after, cut it in two at a reading you pick, write a note on a reading, hide it. Joined or cut trips can be undone.
 */
@Composable
fun TripScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenPlace: (String) -> Unit,
    vm: TripViewModel = viewModel(),
) {
    val paper = LocalPaper.current
    val page by vm.page.collectAsState()
    val message by vm.message.collectAsState()
    LaunchedEffect(tripId) { vm.open(tripId) }
    BackHandler(onBack = onBack)

    val p = page
    val fixes = p?.points.orEmpty()
    var selected by remember(p?.trip?.id) { mutableIntStateOf(0) }
    val sel = selected.coerceIn(0, (fixes.size - 1).coerceAtLeast(0))
    val path = remember(fixes) { fixes.map { it.lat to it.lng } }
    val times = remember(fixes) { fixes.map { it.ms } }
    val noted = remember(fixes, p?.notes) { fixes.indices.filter { p?.notes?.containsKey(fixes[it].ms) == true }.toSet() }
    var noting by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (p == null) {
            item { SettingsTop("Trip", "This trip is not here any more", onBack) }
        } else {
            val trip = p.trip
            item {
                SettingsTop(
                    "Trip",
                    formatDateTime(trip.startMs) + " to " + clockOf(trip.endMs),
                    onBack,
                )
            }
            message?.let { text ->
                item {
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Text(text, fontSize = 14.sp, color = paper.ink)
                        PaperButton("OK", vm::clearMessage, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
            }
            val joinedInto = p.joinedInto
            if (joinedInto != null) {
                item {
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Text("This trip was joined to the one before it, so it is shown there.", fontSize = 14.sp, color = paper.ink)
                        PaperButton("Open the joined trip", { onOpenTrip(joinedInto) }, small = true)
                    }
                }
            } else {
                if (p.hidden) {
                    item {
                        PaperCard(modifier = Modifier.fillMaxWidth()) {
                            Text("You hid this trip. It is kept, and it does not show on the Timeline or in the day.", fontSize = 14.sp, color = paper.ink)
                            PaperButton("Show again", { vm.setHidden(trip, false) }, small = true)
                        }
                    }
                }
                item {
                    MapPanel(
                        path = path,
                        description = "The path of the trip, " + TripRules.distanceText(trip.distanceM),
                        times = times,
                        selected = if (fixes.isEmpty()) -1 else sel,
                        marked = noted,
                        onPick = { selected = it },
                    )
                }
                item {
                    SetCard {
                        KvRow("Distance", TripRules.distanceText(trip.distanceM), first = true)
                        KvRow("Time", PlaceRules.durationText(trip.endMs - trip.startMs))
                        KvRow("Usual speed", "${trip.medianKmh} km/h")
                        KvRow("Top speed", "${trip.topKmh} km/h")
                        KvRow("From", spot(p.fromName))
                        KvRow("To", spot(p.toName))
                        KvRow("Readings", fixes.size.toString())
                    }
                }
                if (trip.fromPlaceId.isNotEmpty() || trip.toPlaceId.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (trip.fromPlaceId.isNotEmpty()) PaperButton("Open " + spot(p.fromName), { onOpenPlace(trip.fromPlaceId) }, style = PaperButtonStyle.Ghost, small = true)
                            if (trip.toPlaceId.isNotEmpty() && trip.toPlaceId != trip.fromPlaceId) {
                                PaperButton("Open " + spot(p.toName), { onOpenPlace(trip.toPlaceId) }, style = PaperButtonStyle.Ghost, small = true)
                            }
                        }
                    }
                }

                item { PaperSection("How did you travel?", if (trip.modeSource == "you") "your answer" else "the app's guess") }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(TripModes.label(trip.mode) + if (trip.modeSource == "you") "" else " (guess)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TripModes.list.filter { it.id != "vehicle" }.forEach { m ->
                                PaperPill(m.label, selected = trip.mode == m.id && trip.modeSource == "you", onClick = { vm.setMode(trip, m.id) })
                            }
                        }
                    }
                }

                item { PaperSection("A point on the path", "tap the path or slide") }
                item {
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        if (fixes.isEmpty()) {
                            Text("This trip has no readings stored, so there is no path to pick a point on.", fontSize = 13.sp, color = paper.ink2)
                        } else {
                            val fix = fixes[sel]
                            Text(
                                "Reading ${sel + 1} of ${fixes.size} · ${clockSeconds(fix.ms)} · within ${fix.acc.roundToInt()} m",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = paper.ink,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PaperButton("‹", { selected = (sel - 1).coerceAtLeast(0) }, style = PaperButtonStyle.Ghost, small = true, enabled = sel > 0)
                                if (fixes.size > 1) {
                                    Slider(
                                        value = sel.toFloat(),
                                        onValueChange = { selected = it.roundToInt() },
                                        valueRange = 0f..(fixes.size - 1).toFloat(),
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = paper.ink,
                                            activeTrackColor = paper.ink,
                                            inactiveTrackColor = paper.line,
                                        ),
                                    )
                                }
                                PaperButton("›", { selected = (sel + 1).coerceAtMost(fixes.size - 1) }, style = PaperButtonStyle.Ghost, small = true, enabled = sel < fixes.size - 1)
                            }
                            p.notes[fix.ms]?.let { Text("Note: $it", fontSize = 13.sp, color = paper.ink) }
                            val canCut = vm.canSplit(p, fix.ms)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaperButton("Split here", { vm.split(p, fix.ms) }, small = true, enabled = canCut)
                                PaperButton(if (p.notes.containsKey(fix.ms)) "Edit note" else "Add a note", { noting = true }, style = PaperButtonStyle.Ghost, small = true)
                            }
                            Text(
                                "Split here cuts the trip in two at this reading. Each part needs at least two readings, so the first and last cannot be used.",
                                fontSize = 12.sp,
                                color = paper.ink2,
                            )
                        }
                    }
                }

                if (p.changes.isNotEmpty()) {
                    item { PaperSection("Changes", "kept, so they can be undone") }
                    item {
                        SetCard {
                            p.changes.forEachIndexed { i, row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier else Modifier.dashedTop(paper.line)).padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(row.text, modifier = Modifier.weight(1f), fontSize = 14.sp, color = paper.ink)
                                    PaperButton("Undo", { vm.undo(row) }, style = PaperButtonStyle.Ghost, small = true)
                                }
                            }
                        }
                    }
                }

                if (p.notes.isNotEmpty()) {
                    item { PaperSection("Notes on this trip") }
                    item {
                        SetCard {
                            p.notes.entries.sortedBy { it.key }.forEachIndexed { i, (ms, text) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier else Modifier.dashedTop(paper.line)).padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(clockSeconds(ms), fontSize = 13.sp, color = paper.ink2)
                                    Text(text, modifier = Modifier.weight(1f), fontSize = 14.sp, color = paper.ink)
                                    PaperButton(
                                        "Show",
                                        { fixes.indexOfFirst { it.ms == ms }.takeIf { it >= 0 }?.let { selected = it } },
                                        style = PaperButtonStyle.Ghost,
                                        small = true,
                                    )
                                }
                            }
                        }
                    }
                }

                if (!p.hidden) {
                    item {
                        PaperButton("Hide this trip", { vm.setHidden(trip, true) }, modifier = Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                    }
                    item {
                        Text(
                            "Hiding keeps the trip and everything recorded for it. It only leaves the Timeline and the day list, and you can show it again here.",
                            fontSize = 12.sp,
                            color = paper.ink2,
                        )
                    }
                }
            }
        }
    }

    if (noting && p != null && fixes.isNotEmpty()) {
        val fix = fixes[sel]
        var text by remember(fix.ms) { mutableStateOf(p.notes[fix.ms].orEmpty()) }
        PaperDialog(
            title = "Note at ${clockSeconds(fix.ms)}",
            onDismiss = { noting = false },
            confirmText = "Save",
            onConfirm = {
                vm.saveNote(fix.ms, text)
                noting = false
            },
        ) {
            PaperField(value = text, onValueChange = { text = it }, label = "What was here? (empty clears the note)", minLines = 2)
        }
    }
}
