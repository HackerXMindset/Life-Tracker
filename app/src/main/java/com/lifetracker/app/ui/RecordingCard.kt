package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AccuracyMode
import com.lifetracker.app.data.RecordingMode
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperSegmented

/** The Recording card on Settings > Places and trips: Gentle or GPS all day, and the GPS-all-day settings. */
fun LazyListScope.recordingItems(r: RecordingUi, vm: PlacesViewModel) {
    val allDay = r.mode == RecordingMode.ALL_DAY
    item { PaperSection("Recording", "how places and trips are followed") }
    item {
        PaperSegmented(
            options = listOf("Gentle", "GPS all day"),
            selected = if (allDay) 1 else 0,
            onSelect = { vm.setMode(if (it == 1) RecordingMode.ALL_DAY else RecordingMode.GENTLE) },
        )
    }
    item {
        Text(
            if (allDay) {
                "The app reads your position all day at the pace you set below, with a notification that stays. It keeps every good reading, so the path is exact. Uses more battery. Needs Watch my places to be on."
            } else {
                "Android says when you arrive somewhere or start moving, and only then the app reads your position. Gentle on the battery, but the path can have gaps."
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!allDay) return
    item {
        SetCard {
            KvRow("Now", r.status.ifEmpty { "Starting" }, first = true)
            KvRow("Kept / refused since you switched on", "${r.kept} / ${r.refused}")
        }
    }
    item {
        PaperButton(
            text = if (r.paused) "Resume recording" else "Pause recording",
            onClick = { vm.setTrackPaused(!r.paused) },
            style = if (r.paused) PaperButtonStyle.Dark else PaperButtonStyle.Ghost,
        )
    }
    item { NumberEntry("Seconds between readings", r.intervalSec, "s", min = 1, onSave = vm::setTrackIntervalSec) }
    item { NumberEntry("Keep a reading only if it moved at least", r.minMoveM, "m (0 keeps every reading)", min = 0, onSave = vm::setTrackMinMoveM) }
    item { NumberEntry("Refuse readings worse than", r.accuracyLimitM, "m (0 refuses none)", min = 0, onSave = vm::setTrackAccuracyLimitM) }
    item {
        Text("How exact the phone is asked to be", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
        PaperSegmented(
            options = listOf("High", "Balanced", "Low power"),
            selected = AccuracyMode.all.indexOf(r.accuracyMode).coerceAtLeast(0),
            onSelect = { vm.setTrackAccuracyMode(AccuracyMode.all[it]) },
        )
    }
}

/** A whole number you type and set. Nothing is capped except what makes no sense (below [min]). */
@Composable
private fun NumberEntry(label: String, value: Int, unit: String, min: Int, onSave: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { t -> text = t.filter { it.isDigit() }.take(6) },
            label = { Text(label) },
            suffix = { Text(unit, fontSize = 12.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        PaperButton(
            text = "Set",
            small = true,
            enabled = (text.toIntOrNull() ?: -1) >= min && text != value.toString(),
            onClick = { text.toIntOrNull()?.let { onSave(it.coerceAtLeast(min)) } },
        )
    }
}
