package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperChip
import com.lifetracker.app.ui.theme.PaperSegmented
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.HabitSource
import com.lifetracker.app.data.HabitSyncState
import com.lifetracker.app.data.HabitsSettings
import com.lifetracker.app.data.StreakLink
import com.lifetracker.app.data.StreakSendWorker
import com.lifetracker.app.data.streak.StreakFolderWriter
import com.lifetracker.app.data.streak.StreakImporter
import kotlinx.coroutines.launch

private fun deletedOn(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))

/** One deleted habit or note, with its Restore button. */
@Composable
private fun DeletedRow(title: String, line: String, onRestore: () -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = paper.ink, maxLines = 2)
                Text(line, fontSize = 12.sp, color = paper.ink2)
            }
            com.lifetracker.app.ui.theme.PaperButton("Restore", onRestore, small = true)
        }
    }
}

/** Settings for the Habits tab: where your habits come from. */
@Composable
fun HabitsSettingsScreen(onBack: () -> Unit, vm: HabitsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val deletedHabits by vm.deletedHabits.collectAsState()
    val deletedNotes by vm.deletedNotes.collectAsState()
    val names by vm.habitNames.collectAsState()
    val settings = remember { HabitsSettings(context) }
    var source by remember { mutableStateOf(settings.source) }
    var weigh by remember { mutableStateOf(settings.weighDifficulty) }
    var remindersOn by remember { mutableStateOf(settings.remindersOn) }
    var quiet by remember { mutableStateOf(settings.quietWhenDone) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    val sources = remember { DataSources(context) }
    var folderTick by remember { mutableStateOf(0) }
    val folder = remember(folderTick) { sources.folder(DataSources.Slot.Streak)?.let { sources.folderName(it) } }
    val canWrite = remember(folderTick) { StreakFolderWriter.canWrite(context) }
    var sending by remember { mutableStateOf(false) }
    var sendMessage by remember { mutableStateOf<String?>(null) }
    val syncState = remember { HabitSyncState(context) }
    var auto by remember { mutableStateOf(syncState.autoSend) }
    val lastSent = remember(sendMessage) { syncState.lastSentText }
    val autoNote = remember(sendMessage, auto) { syncState.lastSendNote }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            sendMessage = try {
                sources.setFolder(DataSources.Slot.Streak, uri)
                "Folder saved."
            } catch (e: Exception) {
                e.message ?: "Android would not allow that folder."
            }
            folderTick++
        }
    }

    val streakInstalled = StreakLink.isInstalled(context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsTop("Habits", "Where habits come from", onBack) }
        item { PaperSection("Today ring") }
        item {
            ToggleRow(
                label = "Weigh the Today ring by difficulty",
                checked = weigh,
                onChange = { weigh = it; vm.setWeighDifficulty(it) },
                hint = "Off: every habit counts the same. On: easy habits count 1, medium 2, hard 3 (a habit with no difficulty counts as medium). Only the ring fill changes, not \"3 of 5\".",
                first = true,
            )
        }
        item { PaperSection("Reminders") }
        item {
            ToggleRow(
                label = "Habit reminders",
                checked = remindersOn,
                onChange = { remindersOn = it; vm.setRemindersOn(it) },
                hint = "Off: no habit rings. Each habit's reminders are set when you edit it. Needs the Notifications permission, and Exact alarms for the exact minute (Settings > Permissions).",
                first = true,
            )
        }
        item {
            ToggleRow(
                label = "Stay quiet when already done",
                checked = quiet,
                onChange = { quiet = it; vm.setQuietWhenDone(it) },
                hint = "A reminder does not ring for a habit you have already done today, and ticking a habit takes its banner away.",
            )
        }
        item { PaperSection("Track habits with") }
        item {
            PaperSegmented(
                options = listOf("Streak", "This app"),
                selected = if (source == HabitSource.Streak) 0 else 1,
                onSelect = { i ->
                    if (i == 0) {
                        if (source != HabitSource.Streak) confirm = true
                    } else {
                        settings.source = HabitSource.Own
                        source = HabitSource.Own
                        message = null
                    }
                },
            )
        }
        item {
            SetupNote(
                if (source == HabitSource.Streak) {
                    "Your habits come from Streak's backups. Tapping a habit opens it in Streak."
                } else {
                    "You add and track habits here. Nothing is imported from Streak, so nothing you change is put back, and you don't need Streak installed."
                },
            )
        }
        if (source == HabitSource.Streak) {
            item {
                SetCard {
                    KvRow("Streak app", if (streakInstalled) "installed" else "not found", first = true)
                    KvRow("Streak backup folder", folder ?: "not chosen yet")
                }
            }
            item {
                LockNote(
                    "For now, a habit you change here that also exists in Streak goes back to Streak's version each time a " +
                        "Streak backup is imported, unless the change has already been sent to Streak (see below). Days you tick here are kept.",
                )
            }
        }
        if (busy) item { SetupNote("Importing from Streak...") }
        message?.let { item { SetupNote(it) } }

        item { PaperSection("Send your changes to Streak", "what you changed here") }
        item {
            SetupNote(
                "Streak can take in changes from a shared folder. This app writes a Streak backup file there holding only " +
                    "what you changed here (new habits, edits, ticks, notes). Streak reads it the next time it checks the folder.",
            )
        }
        item {
            SetCard {
                ToggleRow(
                    label = "Send automatically",
                    checked = auto,
                    onChange = {
                        auto = it
                        syncState.autoSend = it
                        if (it) StreakSendWorker.schedule(context, delaySeconds = 0)
                    },
                    hint = "A few seconds after a change, and each time the app opens. Needs the folder to allow writing.",
                    first = true,
                )
                KvRow("Streak's folder", folder ?: "not chosen yet")
                KvRow("Writing allowed", if (canWrite) "yes" else "no")
                KvRow("Last sent", lastSent ?: "never")
            }
        }
        autoNote?.let { note -> item { SetupNote(note) } }
        item {
            SettingsRow(
                R.drawable.ic_p_restore,
                if (sending) "Sending..." else "Send to Streak now",
                if (canWrite) "Saves a Streak-format file into the shared folder" else "Choose the folder again first",
            ) {
                if (canWrite && !sending) {
                    sending = true
                    sendMessage = null
                    scope.launch {
                        sendMessage = try {
                            StreakFolderWriter.send(context).message
                        } catch (e: Exception) {
                            e.message ?: "Could not send to Streak."
                        }
                        sending = false
                    }
                }
            }
        }
        item {
            SettingsRow(
                R.drawable.ic_p_folder,
                if (canWrite) "Change folder" else "Choose the folder again",
                "Pick the folder Streak also reads",
            ) { pickFolder.launch(null) }
        }
        sendMessage?.let { item { SetupNote(it) } }
        item { LockNote("A day you un-tick here stays ticked in Streak, because Streak only ever adds days.", R.drawable.ic_p_note) }

        item { PaperSection("Deleted habits and notes", "hidden, not erased") }
        // A note deleted together with its habit comes back with the habit, so it is not listed on its own.
        val habitDeletedAt = deletedHabits.associate { it.id to it.deletedAt }
        val loneNotes = deletedNotes.filter { habitDeletedAt[it.habitId] != it.deletedAt }
        if (deletedHabits.isEmpty() && loneNotes.isEmpty()) {
            item { SetupNote("Nothing deleted. A habit you delete, or a note, waits here until you restore it.") }
        }
        items(deletedHabits, key = { "h" + it.id }) { habit ->
            DeletedRow(
                title = habit.name,
                line = "Habit, deleted ${deletedOn(habit.deletedAt)}. Its notes and days come back with it.",
                onRestore = { vm.restoreHabit(habit) },
            )
        }
        items(loneNotes, key = { "n" + it.id }) { note ->
            DeletedRow(
                title = note.text.lineSequence().firstOrNull().orEmpty().take(80).ifBlank { "Note" },
                line = "Note on ${names[note.habitId] ?: "a habit"}, deleted ${deletedOn(note.deletedAt)}",
                onRestore = { vm.restoreNote(note) },
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Import habits from Streak?") },
            text = {
                Text(
                    "Streak's newest backup is imported now. A habit that exists in both places is replaced by Streak's version " +
                        "(its name, colour, schedule and so on). Days you ticked here stay. Habits only you made stay as they are.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    settings.source = HabitSource.Streak
                    source = HabitSource.Streak
                    busy = true
                    message = null
                    scope.launch {
                        message = try {
                            StreakImporter.importIfNew(context, force = true)?.describe() ?: "Nothing to import yet."
                        } catch (e: Exception) {
                            e.message ?: "Could not import from Streak."
                        }
                        busy = false
                    }
                }) { Text("Switch and import") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SourceCard(selected: Boolean, title: String, text: String, onClick: () -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // A hand-drawn round tick box: the chosen one has an ink dot.
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .border(1.5.dp, paper.pen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(12.dp).background(paper.ink, CircleShape))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (selected) PaperChip("In use")
                }
                Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
