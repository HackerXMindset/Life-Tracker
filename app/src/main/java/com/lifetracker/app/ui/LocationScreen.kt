package com.lifetracker.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.LocationDays
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceTrail
import com.lifetracker.app.data.StayConfidence
import com.lifetracker.app.data.TimelineEditEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.Wobble
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

/** "Home" or, for a spot with no name, "unnamed spot". */
internal fun tripEnd(name: String) = name.ifEmpty { "unnamed spot" }

private fun clockOf(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

/**
 * The Location page: a month with a dot on every day you moved, and below it the day you pick, with its numbers and its stays and
 * journeys in order. A journey opens its trip page, a stay at a named place opens the place page. Spots that were not named yet wait
 * at the top for you to name them.
 */
@Composable
fun LocationScreen(
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenPlace: (String) -> Unit,
    vm: LocationViewModel = viewModel(),
    places: PlacesViewModel = viewModel(),
) {
    val paper = LocalPaper.current
    val context = LocalContext.current
    val month by vm.month.collectAsState()
    val day by vm.day.collectAsState()
    val dots by vm.movementDays.collectAsState()
    val dayState by vm.dayState.collectAsState()
    val enabled by places.enabled.collectAsState()
    val clusters by places.clusters.collectAsState()
    val suggestions by places.suggestions.collectAsState()
    val categories by places.categories.collectAsState()
    var naming by remember { mutableStateOf<PlaceRules.Cluster?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(clusters) { places.lookUp(clusters) }

    val today = remember { LocalDate.now() }
    val now = System.currentTimeMillis()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("Location", "Where you were and how you got there, on this phone", onBack) }
        if (!enabled) {
            item { SetupNote("Place watching is switched off. Turn it on in the Settings tab, under Places and trips.") }
        }

        if (clusters.isNotEmpty()) {
            item { PaperSection("To name · ${clusters.size} spot${if (clusters.size == 1) "" else "s"}", "stops at spots with no name") }
            items(clusters, key = { it.ids.first() }) { cluster ->
                val hint = suggestions[places.suggestionKey(cluster)]
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${cluster.visits.size} stop${if (cluster.visits.size == 1) "" else "s"} · " + PlaceRules.durationText(cluster.totalMs),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = paper.ink,
                    )
                    Text(
                        (hint?.let { "Looks like: $it\n" } ?: "") + formatDateTime(cluster.firstMs) +
                            (if (cluster.visits.size > 1) "  to  " + formatDateTime(cluster.lastMs) else "") + "\n" +
                            PlaceRules.coordinatesText(cluster.lat, cluster.lng),
                        fontSize = 13.sp,
                        color = paper.ink2,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (hint != null) {
                            PaperButton("Use \"$hint\"", { places.addPlace(hint, "other", PlaceRules.DEFAULT_RADIUS, cluster.lat, cluster.lng) }, small = true)
                        }
                        PaperButton(if (hint != null) "Rename" else "Name it", { naming = cluster }, style = if (hint != null) PaperButtonStyle.Ghost else PaperButtonStyle.Dark, small = true)
                        PaperButton(
                            "Map",
                            {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:${cluster.lat},${cluster.lng}?q=${cluster.lat},${cluster.lng}(${Uri.encode("Stop")})"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                            style = PaperButtonStyle.Ghost,
                            small = true,
                        )
                        PaperButton("Not a place", { places.ignore(cluster) }, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
            }
        }

        item {
            MonthCard(
                month = month,
                selected = day,
                today = today,
                dots = dots,
                onPrevious = { vm.showMonth(month.minusMonths(1)) },
                onNext = { vm.showMonth(month.plusMonths(1)) },
                onPick = vm::pickDay,
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(day.format(DAY_TITLE), modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = paper.ink)
                if (day != today) PaperButton("Today", vm::goToday, style = PaperButtonStyle.Ghost, small = true)
            }
        }

        val state = dayState
        if (state == null) {
            item { Text("Loading the day…", fontSize = 13.sp, color = paper.ink2) }
        } else {
            item { Text(LocationDays.summaryText(state.summary), fontSize = 14.sp, color = paper.ink2) }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        "No places or journeys on this day. Stay somewhere for 3 minutes and it appears here.",
                        fontSize = 13.sp,
                        color = paper.ink2,
                    )
                }
            } else {
                items(state.rows, key = { it.key }) { row ->
                    when (row) {
                        is PlaceTrail.Stay -> StayRow(row, now, onOpenPlace)
                        is PlaceTrail.Journey -> JourneyRow(row, vm::setJourneyMode, onOpenTrip)
                    }
                }
            }
            if (state.hiddenTrips.isNotEmpty()) {
                item { PaperSection("Hidden by you · ${state.hiddenTrips.size}", "kept, nothing is lost") }
                items(state.hiddenTrips, key = { it.refId }) { edit -> HiddenTripRow(edit, onShow = { vm.showTrip(edit) }, onOpen = { onOpenTrip(edit.refId) }) }
            }
        }
    }

    naming?.let { cluster ->
        PlaceDialog(
            title = "Name this spot",
            initial = null,
            cluster = cluster,
            suggestion = suggestions[places.suggestionKey(cluster)],
            categories = categories,
            onFetchHere = places::fetchHere,
            onSave = { name, kind, radius, lat, lng, pause -> places.addPlace(name, kind, radius, lat, lng, pause) },
            onArchiveToggle = null,
            onDismiss = { naming = null },
        )
    }
}

/** The month as a calendar: a dot under each day you moved, the picked day filled in. */
@Composable
private fun MonthCard(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    dots: Set<LocalDate>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton("‹", onPrevious, style = PaperButtonStyle.Ghost, small = true)
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = paper.ink,
            )
            PaperButton("›", onNext, style = PaperButtonStyle.Ghost, small = true)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            LocationDays.weekdayLetters().forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = paper.ink2)
            }
        }
        LocationDays.monthGrid(month).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, date == selected, date == today, date in dots) { onPick(date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, today: Boolean, dot: Boolean, onClick: () -> Unit) {
    val paper = LocalPaper.current
    val shape = Wobble.Small
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(paper.ink, shape) else Modifier)
            .then(if (today && !selected) Modifier.border(1.5.dp, paper.ink, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 14.sp,
            fontWeight = if (selected || today) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) paper.bg else paper.ink,
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .then(if (dot) Modifier.background(if (selected) paper.bg else paper.accent, CircleShape) else Modifier),
        )
    }
}

/** A stay: where, when and for how long, with a way to open the place. */
@Composable
private fun StayRow(stay: PlaceTrail.Stay, nowMs: Long, onOpenPlace: (String) -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Text(stay.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = paper.ink)
        Text(
            formatDateTime(stay.startMs) + " to " + (if (stay.ongoing) "now" else clockOf(stay.endMs)) +
                " · " + PlaceRules.durationText(stay.endMs - stay.startMs) +
                (if (StayConfidence.isUnsure(stay.visit.confidence)) " · unsure" else ""),
            fontSize = 12.sp,
            color = paper.ink2,
        )
        if (stay.named) {
            PaperButton("Open place", { onOpenPlace(stay.visit.placeId) }, style = PaperButtonStyle.Ghost, small = true)
        } else {
            Text("Not named yet. Name it from the list at the top.", fontSize = 12.sp, color = paper.ink2)
        }
    }
}

/** The way from one stay to the next. When nobody knows how you went it offers quick answers. */
@Composable
private fun JourneyRow(journey: PlaceTrail.Journey, onMode: (PlaceTrail.Journey, String) -> Unit, onOpenTrip: (String) -> Unit) {
    val paper = LocalPaper.current
    val trip = journey.trip
    val unknown = journey.unknownMode
    val length = PlaceRules.durationText((journey.endMs - journey.startMs).coerceAtLeast(0L))
    val distance = TripRules.distanceText(journey.distanceM) + if (trip == null) " in a straight line" else ""
    val title = if (unknown || trip == null) "Unknown way of travel" else TripModes.label(trip.mode) + if (trip.modeSource == "you") "" else " (guess)"
    PaperCard(modifier = Modifier.fillMaxWidth().padding(start = 18.dp), shadow = false) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (unknown) paper.sun else paper.ink)
        Text(
            distance + " · " + length +
                (if (trip != null && trip.distanceM > 0) " · " + TripRules.avgKmh(trip.distanceM, trip.endMs - trip.startMs) + " km/h" else ""),
            fontSize = 13.sp,
            color = paper.ink2,
        )
        Text(
            clockOf(journey.startMs) + " to " + clockOf(journey.endMs) + " · " + tripEnd(journey.fromName) + " to " + tripEnd(journey.toName),
            fontSize = 12.sp,
            color = paper.ink2,
        )
        if (unknown) {
            Text(
                if (trip == null) "How did you go? The phone has no record of the way, so the distance is a straight line." else "How did you go?",
                fontSize = 12.sp,
                color = paper.ink2,
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_MODES.forEach { (label, mode) -> PaperPill(label, selected = false, onClick = { onMode(journey, mode) }) }
            }
        }
        if (trip != null) PaperButton("Open trip", { onOpenTrip(trip.id) }, style = PaperButtonStyle.Ghost, small = true)
    }
}

@Composable
private fun HiddenTripRow(edit: TimelineEditEntity, onShow: () -> Unit, onOpen: () -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Text(clockOf(edit.startMs) + "  " + edit.label.ifEmpty { "Trip" }, fontSize = 14.sp, color = paper.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton("Show again", onShow, small = true)
            PaperButton("Open trip", onOpen, style = PaperButtonStyle.Ghost, small = true)
        }
    }
}
