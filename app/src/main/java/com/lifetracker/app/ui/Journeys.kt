package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceTrail
import com.lifetracker.app.data.StayConfidence
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import java.time.Instant
import java.time.ZoneId

/** "Home" or, for a spot with no name, "an unnamed spot". */
internal fun tripEnd(name: String) = name.ifEmpty { "unnamed spot" }

private val UNKNOWN_GOLD = Color(0xFFD9A441)

private fun clockOf(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

/** A stay in the Places list: where, when and for how long. */
@Composable
fun StayCard(stay: PlaceTrail.Stay, nowMs: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stay.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                formatDateTime(stay.startMs) + " to " + (if (stay.ongoing) "now" else clockOf(stay.endMs)) +
                    " · " + PlaceRules.durationText(stay.endMs - stay.startMs) +
                    (if (StayConfidence.isUnsure(stay.visit.confidence)) " · unsure" else ""),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The way from one stay to the next: "Walked, 1.6 km, 15m". When nobody knows how you went it is gold and offers
 * quick choices; tapping a journey that has a trip record opens the full list of ways to travel.
 */
@Composable
fun JourneyCard(journey: PlaceTrail.Journey, onMode: (PlaceTrail.Journey, String) -> Unit) {
    val trip = journey.trip
    val unknown = journey.unknownMode
    var picking by remember { mutableStateOf(false) }
    val length = PlaceRules.durationText((journey.endMs - journey.startMs).coerceAtLeast(0L))
    val distance = TripRules.distanceText(journey.distanceM) + if (trip == null) " in a straight line" else ""
    val title = when {
        unknown -> "Unknown way of travel"
        else -> TripModes.label(trip!!.mode) + if (trip.modeSource == "you") "" else " (guess)"
    }
    val accent = if (unknown) UNKNOWN_GOLD else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp).clickable(enabled = trip != null) { picking = true },
        shape = RoundedCornerShape(12.dp),
        color = if (unknown) UNKNOWN_GOLD.copy(alpha = 0.12f) else Color.Transparent,
        border = BorderStroke(1.dp, if (unknown) UNKNOWN_GOLD else MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                distance + " · " + length + (if (trip != null && trip.distanceM > 0) " · " + TripRules.avgKmh(trip.distanceM, trip.endMs - trip.startMs) + " km/h" else ""),
                fontSize = 13.sp,
                color = accent,
            )
            Text(
                clockOf(journey.startMs) + " to " + clockOf(journey.endMs) + " · " + tripEnd(journey.fromName) + " to " + tripEnd(journey.toName),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (unknown) {
                Text(
                    if (trip == null) "How did you go? The phone has no record of the way, so the distance is a straight line."
                    else "How did you go?",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_MODES.forEach { (label, mode) ->
                        FilterChip(selected = false, onClick = { onMode(journey, mode) }, label = { Text(label) })
                    }
                }
            }
        }
    }
    if (picking && trip != null) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("How did you travel?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TripModes.list.filter { it.id != "vehicle" }.forEach { m ->
                        TextButton(onClick = { onMode(journey, m.id); picking = false }) {
                            Text(m.label + if (trip.mode == m.id && trip.modeSource == "you") "  ✓" else "")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}
