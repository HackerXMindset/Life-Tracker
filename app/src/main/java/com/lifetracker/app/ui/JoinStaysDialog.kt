package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.DayLayout
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.StayJoin
import com.lifetracker.app.data.PlaceVisitEntity
import com.lifetracker.app.ui.theme.LocalPaper

/** Asks to join two stays that are next to each other. When both have a name and the names differ, you pick which name the one stay keeps. */
@Composable
fun JoinStaysDialog(
    first: DayLayout.Place,
    second: DayLayout.Place,
    onJoin: (keep: PlaceVisitEntity, drop: PlaceVisitEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val paper = LocalPaper.current
    val earlier = if (first.startMs <= second.startMs) first else second
    val later = if (earlier === first) second else first
    val forced = StayJoin.forcedKeeper(earlier.visit, later.visit)
    var pick by remember { mutableStateOf(earlier) }
    val keep = when {
        forced == null -> pick
        forced.id == later.visit.id -> later
        else -> earlier
    }
    val drop = if (keep === earlier) later else earlier
    val total = PlaceRules.durationText(later.endMs - earlier.startMs)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join these two stays?", color = paper.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "They become one stay of $total, and any walk between them becomes part of it. Nothing is deleted: Changes lists it with a button to separate them again.",
                    fontSize = 13.sp, color = paper.ink2,
                )
                if (forced == null) {
                    Text("Which name should the stay keep?", fontSize = 13.sp, color = paper.ink)
                    OptionRow(earlier.name, "Earlier, " + PlaceRules.durationText(earlier.endMs - earlier.startMs), pick === earlier) { pick = earlier }
                    OptionRow(later.name, "Later, " + PlaceRules.durationText(later.endMs - later.startMs), pick === later) { pick = later }
                } else {
                    Text(
                        if (forced.ongoing) "The stay you are in now keeps its name: ${keep.name}."
                        else "The joined stay is called ${keep.name}.",
                        fontSize = 13.sp, color = paper.ink,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onJoin(keep.visit, drop.visit) }) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
