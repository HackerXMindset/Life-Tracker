package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
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
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
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
    /** Lines of what is inside this thing (every app in a phone period, with its time), shown under [detailsTitle]. */
    val details: List<String> = emptyList(),
    val detailsTitle: String = "Inside",
)

/** Change when something happened and which category it counts as, hide it, or put it back the way the phone recorded it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditItemWindow(
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

    val paper = LocalPaper.current
    EditWindow(
        title = target.title,
        sub = formatMinute(startMinute) + (if (target.endMs != null) " to " + formatMinute(endMinute) else ""),
        onClose = onDismiss,
        onSave = {
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
        },
    ) {
        MonoLabel("When")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton("Started  " + formatMinute(startMinute), { pick(startMinute) { startMinute = it } }, style = PaperButtonStyle.Ghost, small = true)
            if (target.endMs != null) {
                PaperButton("Ended  " + formatMinute(endMinute), { pick(endMinute) { endMinute = it } }, style = PaperButtonStyle.Ghost, small = true)
            }
        }
        if (target.details.isNotEmpty()) {
            MonoLabel(target.detailsTitle)
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
                target.details.forEachIndexed { i, line ->
                    Text(
                        text = line,
                        modifier = Modifier.padding(top = if (i == 0) 0.dp else 6.dp),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = paper.ink,
                    )
                }
            }
        }
        if (target.canChangeCategory) {
            MonoLabel("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("As recorded", activityId.isEmpty(), { activityId = "" })
                types.filter { !it.archived }.forEach { t ->
                    PaperPill(t.name, activityId == t.id, { activityId = t.id }, dot = t.displayColor())
                }
            }
        }
        Text(
            "The phone's own record is never changed. Your change sits on top of it, and \"Back to as recorded\" removes it.",
            fontSize = 11.5.sp,
            color = paper.ink2,
        )
        if (target.extraLabel != null && target.onExtra != null) {
            PaperButton(target.extraLabel, { target.onExtra.invoke(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)
        }
        if (target.openLabel != null && target.onOpen != null) {
            PaperButton(target.openLabel, { target.onOpen.invoke(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)
        }
        if (target.edited) {
            PaperButton("Back to as recorded", { onReset(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)
        }
        PaperButton("Hide from the Timeline", { onHide(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)
    }
}
