package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import java.time.LocalDate

/**
 * A note being written or changed. [note] is null for a new one, and then [habitId] (may be empty
 * until the writer picks a habit) and [date] say where it goes.
 */
data class NoteDraft(val habitId: String, val date: LocalDate, val note: NoteEntity? = null)

/**
 * Write a note, or change one. Save stays off while the text is blank. A note being changed has **Delete note** at the
 * bottom (after a question): the note is hidden and kept, and comes back from Settings > Habits. [habits] (id to name)
 * is given only when the writer must pick the habit.
 */
@Composable
fun NoteDialog(
    draft: NoteDraft,
    today: LocalDate,
    habitName: String?,
    habits: List<Pair<String, String>>?,
    onSave: (habitId: String, text: String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var askDelete by remember(draft) { mutableStateOf(false) }
    var text by remember(draft) { mutableStateOf(draft.note?.text.orEmpty()) }
    var habit by remember(draft) { mutableStateOf(draft.habitId) }
    val day = HabitNotes.dayLabel(draft.note?.date ?: draft.date.toString(), today)
    val paper = LocalPaper.current
    PaperDialog(
        title = if (draft.note == null) "New note" else "Edit note",
        onDismiss = onDismiss,
        confirmText = "Save",
        confirmEnabled = text.isNotBlank() && (draft.note != null || habit.isNotEmpty()),
        onConfirm = { onSave(habit, text) },
    ) {
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
                        PaperPill(name, habit == id, { habit = id })
                    }
                }
            } else {
                Text(
                    text = listOfNotNull(habitName, day).joinToString("  ·  "),
                    fontSize = 13.sp,
                    color = paper.ink2,
                )
            }
            if (habits != null && draft.note == null) {
                Text(day, fontSize = 13.sp, color = paper.ink2)
            }
            PaperField(
                value = text,
                onValueChange = { text = it },
                label = "Note",
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            if (draft.note != null && onDelete != null) {
                PaperButton("Delete note", { askDelete = true }, style = PaperButtonStyle.Danger, small = true)
            }
        }
    }
    if (askDelete && onDelete != null) {
        PaperDialog(
            title = "Delete this note?",
            onDismiss = { askDelete = false },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            onConfirm = { askDelete = false; onDelete() },
        ) {
            Text("The note is hidden, not erased. Settings > Habits > Deleted habits and notes brings it back.", fontSize = 14.sp, color = paper.ink)
        }
    }
}

/** The notes of one habit, as lines inside a dialog. Tap one to change it. */
@Composable
fun HabitNotesList(
    notes: List<NoteEntity>,
    today: LocalDate,
    onEdit: (NoteEntity) -> Unit,
    onAdd: () -> Unit,
) {
    val paper = LocalPaper.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLabel("Notes (${notes.size})")
            PaperButton("Add note", onAdd, style = PaperButtonStyle.Ghost, small = true)
        }
        if (notes.isEmpty()) {
            Text("No notes yet.", fontSize = 13.sp, color = paper.ink2)
        }
        notes.forEach { note ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onEdit(note) }.padding(vertical = 2.dp)) {
                Text(
                    text = HabitNotes.dayLabel(note.date, today),
                    fontSize = 11.5.sp,
                    color = paper.ink2,
                )
                Text(note.text, fontSize = 14.sp, color = paper.ink)
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
    val paper = LocalPaper.current
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
                PaperButton("Back", onBack, style = PaperButtonStyle.Ghost, small = true)
                Text(
                    text = "All notes",
                    modifier = Modifier.weight(1f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = paper.ink,
                )
                PaperButton("New note", { onNew(picked) }, small = true)
            }
        }
        if (ids.size > 1) {
            item(key = "filter") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PaperPill("All", picked.isEmpty(), { filter = "" })
                    ids.forEach { id ->
                        PaperPill(names[id] ?: "Habit", picked == id, { filter = id })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No notes yet. Use New note, or add one from a habit's page.",
                    fontSize = 14.sp,
                    color = paper.ink2,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        items(shown, key = { it.id }) { note ->
            PaperCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 14.dp,
                shadow = false,
                onClick = { onEdit(note) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = listOfNotNull(
                            names[note.habitId] ?: "No habit",
                            HabitNotes.dayLabel(note.date, today),
                            HabitNotes.kindLabel(note.type).takeIf { note.type != HabitNotes.TYPE_NOTE },
                        ).joinToString("  ·  "),
                        fontSize = 12.sp,
                        color = paper.ink2,
                    )
                    Text(note.text, fontSize = 15.sp, color = paper.ink)
                }
            }
        }
    }
}
