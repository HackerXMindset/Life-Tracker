package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.Wobble
import java.time.Instant
import java.time.ZoneId

private fun clockOf(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

/**
 * A card laid over the screen that shows one trip and nothing else: its path on the map (coloured by speed), the numbers, and
 * where it began and ended. From the Timeline it opens when you tap a trip, from the Trips view when you tap one in the list.
 * [onMode] (when given) lets you say how you travelled right here. It changes nothing unless you press something.
 */
@Composable
fun TripPopup(
    tripId: String,
    onDismiss: () -> Unit,
    onOpenTrip: () -> Unit,
    onMode: ((TripEntity, String) -> Unit)? = null,
    vm: TripViewModel = viewModel(),
) {
    val paper = LocalPaper.current
    val page by vm.page.collectAsState()
    LaunchedEffect(tripId) { vm.open(tripId) }
    BackHandler(onBack = onDismiss)

    // The page may still show the trip opened before this one for a moment, so wait until it is this trip's.
    val p = page?.takeIf { it.trip.id == tripId }
    val path = remember(p) { p?.points.orEmpty().map { it.lat to it.lng } }
    val times = remember(p) { p?.points.orEmpty().map { it.ms } }
    val shape = Wobble.Card
    var choosing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .background(paper.surface, shape)
                .border(1.5.dp, paper.pen, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (p == null) {
                Text("Loading the trip...", fontSize = 14.sp, color = paper.ink2)
                PaperButton("Close", onDismiss, style = PaperButtonStyle.Ghost, small = true)
            } else {
                val trip = p.trip
                val guess = trip.modeSource != "you"
                Text(
                    TripModes.label(trip.mode) + (if (guess) " (guess)" else ""),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = paper.ink,
                )
                Text(
                    formatDateTime(trip.startMs) + " to " + clockOf(trip.endMs),
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
                MapPanel(
                    path = path,
                    description = "The path of this trip, " + TripRules.distanceText(trip.distanceM),
                    times = times,
                    height = 300.dp,
                )
                Text(
                    TripRules.distanceText(trip.distanceM) + " · " + PlaceRules.durationText(trip.endMs - trip.startMs) +
                        " · usual " + trip.medianKmh + " km/h, top " + trip.topKmh + " km/h",
                    fontSize = 14.sp,
                    color = paper.ink,
                )
                Text(
                    (p.fromName.ifEmpty { "An unnamed spot" }) + " to " + (p.toName.ifEmpty { "an unnamed spot" }),
                    fontSize = 13.sp,
                    color = paper.ink2,
                )
                if (onMode != null) {
                    PaperButton("How did you travel?", { choosing = true }, style = PaperButtonStyle.Ghost, small = true)
                    if (choosing) {
                        ModeSheet(
                            trip = trip,
                            fromName = p.fromName,
                            toName = p.toName,
                            current = if (guess) "" else trip.mode,
                            onPick = { onMode(trip, it); choosing = false },
                            onDismiss = { choosing = false },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperButton("Open trip page", onOpenTrip, small = true)
                    PaperButton("Close", onDismiss, style = PaperButtonStyle.Ghost, small = true)
                }
            }
        }
    }
}
