package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSheet
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.EntryEntity
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    date: LocalDate,
    existing: List<EntryEntity>,
    types: List<ActivityTypeEntity>,
    onCreateType: (ActivityTypeEntity, (ActivityTypeEntity) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (EntryEntity) -> Unit,
    /** Set to change an entry that is already on the Timeline instead of adding a new one. */
    initial: EntryEntity? = null,
    /** Shown as a Delete button when editing. */
    onDelete: (() -> Unit)? = null,
    /** Start and end (minutes into the day) to begin with, for filling in a gap. */
    presetStartMinute: Int? = null,
    presetEndMinute: Int? = null,
) {
    val context = LocalContext.current
    val isToday = date == LocalDate.now()
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }

    val defaultStart = remember {
        if (presetStartMinute != null) return@remember presetStartMinute
        val lastEnd = existing.mapNotNull { it.endMinute }.maxOrNull()
        when {
            lastEnd != null -> lastEnd.coerceAtMost(1438)
            isToday -> (nowMinute - 30).coerceAtLeast(0)
            else -> 9 * 60
        }
    }
    val defaultEnd = remember {
        if (presetEndMinute != null) return@remember presetEndMinute
        if (isToday && nowMinute > defaultStart) nowMinute else (defaultStart + 30).coerceAtMost(1439)
    }

    val choices = types.filter { !it.archived || it.id == initial?.category }
    // The id of the chosen activity. Until one is picked, the first available is used.
    var chosenId by remember { mutableStateOf<String?>(initial?.category) }
    val category = choices.firstOrNull { it.id == chosenId } ?: choices.firstOrNull { it.id == "Study" } ?: choices.firstOrNull()
    var showNewType by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: defaultStart) }
    var end by remember { mutableIntStateOf(initial?.endMinute ?: defaultEnd) }
    var moment by remember { mutableStateOf(initial != null && initial.endMinute == null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            initial / 60,
            initial % 60,
            false,
        ).show()
    }

    val paper = LocalPaper.current

    fun trySave() {
        when {
            category == null -> error = "Pick an activity first."
            title.isBlank() -> error = "Give it a short name."
            !moment && end <= start -> error = "End time must be after the start time."
            else -> onSave(
                EntryEntity(
                    id = initial?.id ?: 0,
                    date = initial?.date ?: date.toString(),
                    startMinute = start,
                    endMinute = if (moment) null else end,
                    category = category!!.id,
                    title = title.trim(),
                    note = note.trim(),
                ),
            )
        }
    }

    // The fields are the same for adding (a bottom sheet) and for changing (a full-screen window).
    val fields: @Composable ColumnScope.() -> Unit = {
        Text("What did you do?", fontSize = 12.sp, color = paper.ink2)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { c ->
                PaperPill(c.name, category?.id == c.id, { chosenId = c.id }, dot = c.displayColor())
            }
            PaperPill("+ New", false, { showNewType = true })
        }

        PaperField(
            value = title,
            onValueChange = { title = it; error = null },
            label = "Name",
            placeholder = "e.g. Polity revision",
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Start", fontSize = 12.sp, color = paper.ink2)
                PaperButton(
                    text = formatMinute(start),
                    onClick = { pickTime(start) { start = it; error = null } },
                    modifier = Modifier.fillMaxWidth(),
                    style = PaperButtonStyle.Ghost,
                )
            }
            if (!moment) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("End", fontSize = 12.sp, color = paper.ink2)
                    PaperButton(
                        text = formatMinute(end),
                        onClick = { pickTime(end) { end = it; error = null } },
                        modifier = Modifier.fillMaxWidth(),
                        style = PaperButtonStyle.Ghost,
                    )
                }
            }
        }

        SwitchRow("Just a moment, no end time", moment) { moment = it; error = null }

        PaperField(value = note, onValueChange = { note = it }, label = "Note (optional)", minLines = 3)

        error?.let {
            Text(text = it, color = paper.bad, fontSize = 13.sp)
        }
    }

    if (initial != null) {
        EditWindow(
            title = initial.title.ifBlank { "Edit entry" },
            sub = "Something you logged",
            onClose = onDismiss,
            onSave = { trySave() },
        ) {
            fields()
            if (onDelete != null) PaperButton("Delete", onDelete, modifier = Modifier.fillMaxWidth(), style = PaperButtonStyle.Danger)
        }
    } else {
        PaperSheet(onDismiss = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Log something",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = paper.ink,
                )
                fields()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PaperButton("Cancel", onDismiss, modifier = Modifier.weight(1f), style = PaperButtonStyle.Ghost)
                    PaperButton(text = "Add to timeline", onClick = { trySave() }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showNewType) {
        ActivityEditorDialog(
            initial = null,
            onSave = { newType ->
                onCreateType(newType) { saved -> chosenId = saved.id }
                showNewType = false
            },
            onDismiss = { showNewType = false },
        )
    }
}
