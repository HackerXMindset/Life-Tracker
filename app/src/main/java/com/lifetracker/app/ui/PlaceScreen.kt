package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.PathFrame
import com.lifetracker.app.data.PauseSettings
import com.lifetracker.app.data.PauseText
import com.lifetracker.app.data.PlaceKinds
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import java.time.Instant
import java.time.ZoneId

private fun clockOf(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

/**
 * The page of one place: its circle, how often and how long you were there, its pause zone in words, and every stay. Editing
 * the place (name, size, position, pause zone, stop watching) opens the same editor as in Settings.
 */
@Composable
fun PlaceScreen(
    placeId: String,
    onBack: () -> Unit,
    vm: PlaceViewModel = viewModel(),
    places: PlacesViewModel = viewModel(),
) {
    val paper = LocalPaper.current
    val page by vm.page.collectAsState()
    val categories by places.categories.collectAsState()
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(placeId) { vm.open(placeId) }
    BackHandler(onBack = onBack)

    val p = page
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (p == null) {
            item { SettingsTop("Place", "This place is not here any more", onBack) }
        } else {
            val place = p.place
            val kind = categories.firstOrNull { it.id == place.kind }?.name ?: PlaceKinds.label(place.kind)
            item { SettingsTop(place.name, kind + if (place.archived) " · not watched" else "", onBack) }
            item {
                MapPanel(
                    path = emptyList(),
                    description = "The circle of ${place.name}, ${place.radiusM} metres",
                    circle = PathFrame.Circle(place.lat, place.lng, place.radiusM.toDouble()),
                    spots = listOf(place.lat to place.lng),
                    height = 200.dp,
                )
            }
            item {
                SetCard {
                    KvRow("Circle", "${place.radiusM} m", first = true)
                    KvRow("Position", PlaceRules.coordinatesText(place.lat, place.lng))
                    KvRow("Stays", p.visits.size.toString())
                    KvRow("Time here", PlaceRules.durationText(p.totalMs))
                    p.visits.firstOrNull()?.let { KvRow("Last stay", formatDateTime(it.startMs)) }
                }
            }
            item { PaperSection("Pause zone") }
            item {
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Text(PauseText.describe(PauseSettings.of(place)), fontSize = 14.sp, color = paper.ink)
                }
            }
            item { PaperButton("Edit place", { editing = true }, modifier = Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost) }
            item { PaperSection("Stays", "newest first") }
            if (p.visits.isEmpty()) {
                item { Text("No stays here yet.", fontSize = 13.sp, color = paper.ink2) }
            } else {
                items(p.visits, key = { it.id }) { v ->
                    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
                        Text(
                            formatDateTime(v.startMs) + " to " + (if (v.ongoing) "now" else clockOf(v.endMs)),
                            fontSize = 14.sp,
                            color = paper.ink,
                        )
                        Text(
                            PlaceRules.durationText(PlaceRules.lengthMs(v, System.currentTimeMillis())),
                            fontSize = 12.sp,
                            color = paper.ink2,
                        )
                    }
                }
            }
        }
    }

    val place = p?.place
    if (editing && place != null) {
        PlaceDialog(
            title = "Edit place",
            initial = place,
            cluster = null,
            categories = categories,
            onFetchHere = places::fetchHere,
            onSave = { name, kind, radius, lat, lng, pause ->
                places.updatePlace(
                    place.copy(
                        name = name.trim(), kind = kind, radiusM = radius, lat = lat, lng = lng,
                        pauseHere = pause.pauseHere, pauseWifi = pause.wifi, pauseMotionless = pause.motionless,
                        heartbeatMin = pause.heartbeatMin, entryDelaySec = pause.entryDelaySec,
                    ),
                )
            },
            onArchiveToggle = { places.updatePlace(place.copy(archived = !place.archived)) },
            onDismiss = { editing = false },
        )
    }
}
