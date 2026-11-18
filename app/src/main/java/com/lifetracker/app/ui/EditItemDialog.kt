package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.lifetracker.app.R
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
    /** A second extra button (a stay: join it with the stay on the other side). */
    val extra2Label: String? = null,
    val onExtra2: (() -> Unit)? = null,
    /** A button that opens the full screen for this kind of item. */
    val openLabel: String? = null,
    val onOpen: (() -> Unit)? = null,
    /** Lines of what is inside this thing (every app in a phone period, with its time), shown under [detailsTitle]. */
    val details: List<String> = emptyList(),
    val detailsTitle: String = "Inside",
    /** For each line of [details] (same order), every time that app was opened (start, end). Holding the line shows them to the second. */
    val detailOpens: List<List<Pair<Long, Long>>> = emptyList(),
    /** For things you can delete (a trip): a button that deletes it, with Undo and a Deleted list to bring it back from. */
    val onDelete: (() -> Unit)? = null,
    val deleteLabel: String = "Delete",
)

/** Change when something happened and which category it counts as, hide it, or put it back the way the phone recorded it. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    var categoryOpen by remember(target) { mutableStateOf(false) }
    var opensOf by remember(target) { mutableStateOf<Int?>(null) }

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
                    val opens = target.detailOpens.getOrNull(i).orEmpty()
                    Text(
                        text = line,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (i == 0) 0.dp else 6.dp)
                            .combinedClickable(
                                onClick = { if (opens.isNotEmpty()) opensOf = if (opensOf == i) null else i },
                                onLongClick = { if (opens.isNotEmpty()) opensOf = if (opensOf == i) null else i },
                            ),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = paper.ink,
                    )
                    if (opensOf == i) {
                        MonoLabel("App opened in this period")
                        opens.forEach { (from, to) ->
                            Text(
                                text = "Opened at " + clockWithSeconds(from) + ", closed at " + clockWithSeconds(to) + " (" + secondsText(to - from) + ")",
                                modifier = Modifier.padding(top = 3.dp),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = paper.ink2,
                            )
                        }
                    }
                }
                if (target.detailOpens.any { it.isNotEmpty() }) {
                    Text(
                        text = "Hold an app to see exactly when it was opened and closed.",
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 11.sp,
                        color = paper.ink2,
                    )
                }
            }
        }
        if (target.canChangeCategory) {
            val chosen = if (activityId.isEmpty()) "As recorded" else types.firstOrNull { it.id == activityId }?.name ?: activityId
            Row(
                modifier = Modifier.fillMaxWidth().clickable { categoryOpen = !categoryOpen }.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoLabel("Category")
                Text(chosen, modifier = Modifier.weight(1f), fontSize = 13.sp, color = paper.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    painter = painterResource(if (categoryOpen) R.drawable.ic_chevrons_downup else R.drawable.ic_chevrons_updown),
                    contentDescription = if (categoryOpen) "Close the categories" else "Show the categories",
                    modifier = Modifier.size(16.dp),
                    tint = paper.ink2,
                )
            }
            if (categoryOpen) {
                // A fixed height with its own scroll, so the list never pushes the buttons off the screen.
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaperPill("As recorded", activityId.isEmpty(), { activityId = ""; categoryOpen = false })
                        types.filter { !it.archived }.forEach { t ->
                            PaperPill(t.name, activityId == t.id, { activityId = t.id; categoryOpen = false }, dot = t.displayColor())
                        }
                    }
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
        if (target.extra2Label != null && target.onExtra2 != null) {
            PaperButton(target.extra2Label, { target.onExtra2.invoke(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)
        }
        if (target.openLabel != null && target.onOpen != null) {
            PaperButton(target.openLabel, { target.onOpen.invoke(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)
        }
        if (target.edited) {
            PaperButton("Back to as recorded", { onReset(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)
        }
        PaperButton("Hide from the Timeline", { onHide(); onDismiss() }, style = PaperButtonStyle.Ghost, small = true)
        if (target.onDelete != null) {
            PaperButton(target.deleteLabel, { target.onDelete.invoke(); onDismiss() }, style = PaperButtonStyle.Danger, small = true)
        }
    }
}

/** A clock time to the second, like "12:01:03 am", in the phone's time zone. */
internal fun clockWithSeconds(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { t ->
        val h = t.hour % 12
        "%d:%02d:%02d %s".format(if (h == 0) 12 else h, t.minute, t.second, if (t.hour < 12) "am" else "pm")
    }

/** "42 s", "3 min 05 s" or "1 h 02 min": how long one opening lasted, exact enough to tell a glance from a stay. */
internal fun secondsText(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "%d h %02d min".format(h, m)
        m > 0 -> "%d min %02d s".format(m, s)
        else -> "$s s"
    }
}
