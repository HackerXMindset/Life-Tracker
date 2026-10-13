package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.TimelineEdits

/**
 * One thing on the Timeline that can be changed. [startMs] and [endMs] are what is shown now (your change, if you made one);
 * [endMs] is null for something with no length (a meal). [activityId] is the category you chose, or "" for the item's own.
 * [baseDayStartMs] is the start of the day the item begins on, which the clock times you pick are placed on.
 */
class EditTarget(
    val kind: String,
    val refId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long?,
    val activityId: String,
    val edited: Boolean,
    val baseDayStartMs: Long,
    val canChangeCategory: Boolean = true,
    /** An extra button for something only this kind of item has, such as the app's category on every day. */
    val extraLabel: String? = null,
    val onExtra: (() -> Unit)? = null,
    /** A button that opens the full screen for this kind of item. */
    val openLabel: String? = null,
    val onOpen: (() -> Unit)? = null,
)

/** Change when something happened and which category it counts as, hide it, or put it back the way the phone recorded it. */
@Composable
fun EditItemDialog(
    target: EditTarget,
    types: List<ActivityTypeEntity>,
    onSave: (startMs: Long, endMs: Long?, activityId: String) -> Unit,
    onHide: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var startMinute by remember(target) { mutableIntStateOf(TimelineEdits.minuteOf(target.startMs, target.baseDayStartMs)) }
    var endMinute by remember(target) { mutableIntStateOf(target.endMs?.let { TimelineEdits.minuteOf(it, target.baseDayStartMs) } ?: 0) }
    var activityId by remember(target) { mutableStateOf(target.activityId) }

    fun pick(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, false).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.title, maxLines = 2) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick(startMinute) { startMinute = it } }) { Text("Started  " + formatMinute(startMinute)) }
                    if (target.endMs != null) {
                        OutlinedButton(onClick = { pick(endMinute) { endMinute = it } }) { Text("Ended  " + formatMinute(endMinute)) }
                    }
                }
                if (target.canChangeCategory) {
                    MonoLabel("Category")
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = activityId.isEmpty(), onClick = { activityId = "" }, label = { Text("As recorded") })
                        types.filter { !it.archived }.forEach { t ->
                            FilterChip(selected = activityId == t.id, onClick = { activityId = t.id }, label = { Text(t.name) })
                        }
                    }
                }
                Text(
                    "The phone's own record is never changed. Your change sits on top of it, and \"Back to as recorded\" removes it.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (target.extraLabel != null && target.onExtra != null) {
                    TextButton(onClick = { target.onExtra.invoke(); onDismiss() }) { Text(target.extraLabel) }
                }
                if (target.openLabel != null && target.onOpen != null) {
                    TextButton(onClick = { target.onOpen.invoke(); onDismiss() }) { Text(target.openLabel) }
                }
                if (target.edited) {
                    TextButton(onClick = { onReset(); onDismiss() }) { Text("Back to as recorded") }
                }
                TextButton(onClick = { onHide(); onDismiss() }) { Text("Hide from the Timeline") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val end = target.endMs
                if (end == null) {
                    onSave(target.baseDayStartMs + startMinute * 60_000L, null, activityId)
                } else {
                    val (s, e) = TimelineEdits.pickTimes(target.baseDayStartMs, startMinute, endMinute)
                    // Untouched times stay exactly as they were (including a few seconds the clock does not show).
                    val same = TimelineEdits.minuteOf(target.startMs, target.baseDayStartMs) == startMinute &&
                        TimelineEdits.minuteOf(end, target.baseDayStartMs) == endMinute
                    onSave(if (same) target.startMs else s, if (same) end else e, activityId)
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
