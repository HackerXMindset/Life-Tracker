package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitNotes
import com.lifetracker.app.data.NoteEntity
import java.time.LocalDate

/**
 * A note being written or changed. [note] is null for a new one, and then [habitId] (may be empty
 * until the writer picks a habit) and [date] say where it goes.
 */
data class NoteDraft(val habitId: String, val date: LocalDate, val note: NoteEntity? = null)

/**
 * Write a note, or change one. Save stays off while the text is blank, and there is no delete here.
 * [habits] (id to name) is given only when the writer must pick the habit.
 */
@Composable
fun NoteDialog(
    draft: NoteDraft,
    today: LocalDate,
    habitName: String?,
    habits: List<Pair<String, String>>?,
    onSave: (habitId: String, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(draft) { mutableStateOf(draft.note?.text.orEmpty()) }
    var habit by remember(draft) { mutableStateOf(draft.habitId) }
    val day = HabitNotes.dayLabel(draft.note?.date ?: draft.date.toString(), today)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.note == null) "New note" else "Edit note") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (habits != null && draft.note == null) {
                    MonoLabel("Habit")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        habits.forEach { (id, name) ->
                            FilterChip(selected = habit == id, onClick = { habit = id }, label = { Text(name) })
                        }
                    }
                } else {
                    Text(
                        text = listOfNotNull(habitName, day).joinToString("  ·  "),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (habits != null && draft.note == null) {
                    Text(day, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && (draft.note != null || habit.isNotEmpty()),
                onClick = { onSave(habit, text) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The notes of one habit, as lines inside a dialog. Tap one to change it. */
@Composable
fun HabitNotesList(
    notes: List<NoteEntity>,
    today: LocalDate,
    onEdit: (NoteEntity) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLabel("Notes (${notes.size})")
            TextButton(onClick = onAdd) { Text("Add note") }
        }
        if (notes.isEmpty()) {
            Text("No notes yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        notes.forEach { note ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onEdit(note) }.padding(vertical = 2.dp)) {
                Text(
                    text = HabitNotes.dayLabel(note.date, today),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(note.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** The All notes page: every note, newest first, filtered by habit. Tap a note to change it. */
@Composable
fun HabitNotesPage(
    notes: List<NoteEntity>,
    names: Map<String, String>,
    today: LocalDate,
    onEdit: (NoteEntity) -> Unit,
    onNew: (habitId: String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var filter by remember { mutableStateOf("") }
    val ids = HabitNotes.habitsWithNotes(notes)
    val picked = filter.takeIf { it in ids } ?: ""
    val shown = HabitNotes.forHabit(notes, picked)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(
                    text = "All notes",
                    modifier = Modifier.weight(1f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = { onNew(picked) }) { Text("New note") }
            }
        }
        if (ids.size > 1) {
            item(key = "filter") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = picked.isEmpty(), onClick = { filter = "" }, label = { Text("All") })
                    ids.forEach { id ->
                        FilterChip(selected = picked == id, onClick = { filter = id }, label = { Text(names[id] ?: "Habit") })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No notes yet. Use New note, or add one from a habit's page.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        items(shown, key = { it.id }) { note ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onEdit(note) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = listOfNotNull(
                            names[note.habitId] ?: "No habit",
                            HabitNotes.dayLabel(note.date, today),
                            HabitNotes.kindLabel(note.type).takeIf { note.type != HabitNotes.TYPE_NOTE },
                        ).joinToString("  ·  "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(note.text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
