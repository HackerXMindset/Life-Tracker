package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.data.TripsRange
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperSegmented
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TABS = listOf("Map", "Trips", "Data")
private val SPANS = TripsRange.Span.values().toList()
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

private fun clockOf(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

private fun modeText(t: TripEntity) = TripModes.label(t.mode) + (if (t.modeSource == "you") "" else " (guess)")

/**
 * Whole trips for any stretch of days, with three tabs like Colota's History: Map (every trip of the stretch, each in its own
 * colour), Trips (a list; tap one to see only that trip) and Data (the totals). It only reads; nothing here changes a trip.
 */
@Composable
fun TripsScreen(
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenReplay: (String, String, String) -> Unit = { _, _, _ -> },
    vm: TripsViewModel = viewModel(),
    tripVm: TripViewModel = viewModel(),
) {
    val paper = LocalPaper.current
    val state by vm.state.collectAsState()
    val span by vm.span.collectAsState()
    val custom by vm.custom.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var popup by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    val s = state
    val lines = remember(s) { s?.items.orEmpty().map { TripLine(it.trip.id, it.color, it.path) } }
    val pickedItem = s?.items?.firstOrNull { it.trip.id == selected }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SettingsTop("Trips", "Whole trips for a day, a week, a month, a year or any dates", onBack) }
            item {
                PaperSegmented(SPANS.map { it.label }, SPANS.indexOf(span), { vm.setSpan(SPANS[it]); selected = null })
            }
            item {
                if (span == TripsRange.Span.CUSTOM) {
                    RangePicker(RangeChoice.custom(custom)) { vm.setCustom(it.span); selected = null }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PaperButton("<", { vm.step(-1); selected = null }, style = PaperButtonStyle.Ghost, small = true)
                        Text(
                            s?.title.orEmpty(),
                            modifier = Modifier.weight(1f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = paper.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PaperButton(">", { vm.step(1); selected = null }, style = PaperButtonStyle.Ghost, small = true, enabled = s?.atEnd == false)
                    }
                }
            }
            item {
                PaperButton(
                    "Replay this stretch",
                    { s?.let { onOpenReplay(it.window.from.toString(), it.window.to.toString(), it.title) } },
                    style = PaperButtonStyle.Ghost,
                    small = true,
                    enabled = s != null,
                )
            }
            item { PaperSegmented(TABS, tab, { tab = it }) }

            if (s == null) {
                item { Text("Loading...", fontSize = 14.sp, color = paper.ink2) }
            } else if (s.items.isEmpty()) {
                item {
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Text("No trips in this stretch.", fontSize = 14.sp, color = paper.ink)
                        Text("Hidden trips are not counted. You can bring them back under Changes.", fontSize = 12.sp, color = paper.ink2)
                    }
                }
            } else when (tab) {
                0 -> {
                    item {
                        // While the popup is open its own map is the only one on screen, so the two never fight over the screen.
                        if (popup == null) {
                            TripsMap(
                                lines = lines,
                                selectedId = selected,
                                onPick = { selected = it },
                                description = "Map of " + s.items.size + " trips",
                            )
                        }
                    }
                    item {
                        if (pickedItem == null) {
                            Text("Tap a path to pick a trip. " + s.items.size + " trips, " + TripRules.distanceText(s.summary.distanceM.toInt()) + " in all.", fontSize = 12.sp, color = paper.ink2)
                        } else {
                            TripRow(pickedItem, onClick = { popup = pickedItem.trip.id })
                        }
                    }
                }
                1 -> {
                    s.days.forEach { (day, list) ->
                        item(key = "day-$day") { MonoLabel(day.format(DAY_FORMAT)) }
                        items(list, key = { it.trip.id }) { item -> TripRow(item, onClick = { popup = item.trip.id }) }
                    }
                }
                else -> {
                    item { DataTab(s) }
                }
            }
        }
        val open = popup
        if (open != null) {
            TripPopup(
                tripId = open,
                onDismiss = { popup = null },
                onOpenTrip = { popup = null; onOpenTrip(open) },
                onMode = null,
                vm = tripVm,
            )
        }
    }
}

@Composable
private fun TripRow(item: TripsItem, onClick: () -> Unit) {
    val paper = LocalPaper.current
    val t = item.trip
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Color(0xFF000000.toInt() or item.color), CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modeText(t) + " · " + TripRules.distanceText(t.distanceM) + " · " + PlaceRules.durationText(t.endMs - t.startMs),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = paper.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    clockOf(t.startMs) + " to " + clockOf(t.endMs) + " · " + item.fromName.ifEmpty { "unnamed spot" } + " to " + item.toName.ifEmpty { "unnamed spot" },
                    fontSize = 12.sp,
                    color = paper.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DataTab(s: TripsState) {
    val sum = s.summary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetCard {
            KvRow("Trips", sum.trips.toString(), first = true)
            KvRow("Distance", TripRules.distanceText(sum.distanceM.toInt()))
            KvRow("Time on the move", PlaceRules.durationText(sum.movingMs))
            KvRow("Average speed", sum.avgKmh.toString() + " km/h")
            sum.longest?.let { KvRow("Longest trip", TripRules.distanceText(it.distanceM) + " on " + formatDateTime(it.startMs)) }
            sum.fastest?.let { KvRow("Fastest trip", it.topKmh.toString() + " km/h on " + formatDateTime(it.startMs)) }
        }
        MonoLabel("By way of travel")
        SetCard {
            sum.byMode.forEachIndexed { i, m ->
                KvRow(
                    TripModes.label(m.mode),
                    TripRules.distanceText(m.distanceM.toInt()) + " · " + PlaceRules.durationText(m.ms) + " · " + m.count + (if (m.count == 1) " trip" else " trips"),
                    first = i == 0,
                )
            }
        }
        if (s.days.size > 1) {
            MonoLabel("By day")
            SetCard {
                s.days.forEachIndexed { i, (day, list) ->
                    KvRow(
                        day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                        list.sumOf { it.trip.distanceM.toLong() }.let { TripRules.distanceText(it.toInt()) } + " · " + list.size + (if (list.size == 1) " trip" else " trips"),
                        first = i == 0,
                    )
                }
            }
        }
    }
}
