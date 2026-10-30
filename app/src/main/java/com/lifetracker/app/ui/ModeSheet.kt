package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.Wobble

/** The picture for each way of travelling. */
internal fun modeIcon(id: String): String = when (id) {
    "walk" -> "🚶"
    "run" -> "🏃"
    "cycle" -> "🚴"
    "bike" -> "🏍️"
    "auto" -> "🛺"
    "car" -> "🚗"
    "bus" -> "🚌"
    "train" -> "🚆"
    "flight" -> "✈️"
    "boat" -> "⛴️"
    "scooter" -> "🛴"
    "skateboard" -> "🛹"
    "other" -> "➕"
    else -> "🚘"
}

/**
 * "How did you travel?": big pictures for each way of travelling, above the trip's From, To, Distance and Average speed.
 * Tapping a picture saves your answer (it also teaches the guess for later trips).
 */
@Composable
fun ModeSheet(
    trip: TripEntity?,
    fromName: String,
    toName: String,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val paper = LocalPaper.current
    PaperDialog(
        title = "How did you travel?",
        onDismiss = onDismiss,
        confirmText = if (onEdit != null) "Edit time and category" else null,
        onConfirm = onEdit,
        dismissText = "Close",
    ) {
        Column(
            modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (trip != null) {
                SetCard {
                    KvRow("From", fromName.ifEmpty { "an unnamed spot" }, first = true)
                    KvRow("To", toName.ifEmpty { "an unnamed spot" })
                    KvRow("Distance", TripRules.distanceText(trip.distanceM))
                    KvRow("Average speed", TripRules.avgKmh(trip.distanceM, trip.endMs - trip.startMs).toString() + " km/h (" + PlaceRules.durationText(trip.endMs - trip.startMs) + ")")
                }
            }
            val modes = TripModes.list.filter { it.id != "vehicle" }
            modes.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { m ->
                        val on = m.id == current
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(Wobble.Small)
                                .background(if (on) paper.ink else paper.surface, Wobble.Small)
                                .border(1.25.dp, if (on) paper.ink else paper.line, Wobble.Small)
                                .clickable { onPick(m.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(modeIcon(m.id), fontSize = 30.sp)
                            Text(
                                m.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (on) paper.bg else paper.ink,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                    repeat(3 - row.size) { Column(modifier = Modifier.weight(1f)) {} }
                }
            }
            Text(
                "Your answer is kept, and the app learns from it for the next trips.",
                fontSize = 11.5.sp,
                color = paper.ink2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
