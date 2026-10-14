package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.HabitSource
import com.lifetracker.app.data.HabitSyncState
import com.lifetracker.app.data.HabitsSettings
import com.lifetracker.app.data.StreakLink
import com.lifetracker.app.data.StreakSendWorker
import com.lifetracker.app.data.streak.StreakFolderWriter
import com.lifetracker.app.data.streak.StreakImporter
import kotlinx.coroutines.launch

/** Settings for the Habits tab: where your habits come from. */
@Composable
fun HabitsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { HabitsSettings(context) }
    var source by remember { mutableStateOf(settings.source) }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { ScreenHeader(label = "Settings", badge = "On this phone", title = "Habits") }
        item { MonoLabel("Where your habits live") }
        item {
            SourceCard(
                selected = source == HabitSource.Own,
                title = "This app's own data",
                text = "You add and track habits here. Nothing is imported from Streak, so nothing you change is put back, and you don't need Streak installed.",
                onClick = {
                    settings.source = HabitSource.Own
                    source = HabitSource.Own
                    message = null
                },
            )
        }
        item {
            SourceCard(
                selected = source == HabitSource.Streak,
                title = "Import from Streak",
                text = "Your habits come from Streak's backups, as before. Tapping a habit opens it in Streak.",
                onClick = { if (source != HabitSource.Streak) confirm = true },
            )
        }
        if (source == HabitSource.Streak) {
            item {
                SetupNote(
                    "Streak app: " + (if (StreakLink.isInstalled(context)) "installed" else "not found on this phone") + "\n" +
                        "Streak backup folder: " + (folder ?: "not chosen yet (set it under Backup and import)") + "\n\n" +
                        "For now, a habit you change here that also exists in Streak goes back to Streak's version each time a " +
                        "Streak backup is imported, unless the change has already been sent to Streak (see below). Days you tick here are kept.",
                )
            }
        }
        if (busy) item { SetupNote("Importing from Streak...") }
        message?.let { item { SetupNote(it) } }

        item { MonoLabel("Send your changes to Streak") }
        item {
            SetupNote(
                "Streak can take in changes from a shared folder. This app writes a Streak backup file there holding only " +
                    "what you changed here (new habits, edits, ticks, notes). Streak reads it the next time it checks the folder.\n\n" +
                    "Streak's folder: " + (folder ?: "not chosen yet") + "\n" +
                    "Writing allowed: " + (if (canWrite) "yes" else "no, choose the folder again below") + "\n" +
                    "Last sent: " + (lastSent ?: "never") + "\n\n" +
                    "A day you un-tick here stays ticked in Streak, because Streak only ever adds days.",
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Send automatically", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "A few seconds after a change, and each time the app opens. Needs the folder to allow writing.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = auto,
                        onCheckedChange = {
                            auto = it
                            syncState.autoSend = it
                            if (it) StreakSendWorker.schedule(context, delaySeconds = 0)
                        },
                    )
                }
            }
        }
        autoNote?.let { note -> item { SetupNote(note) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pickFolder.launch(null) }) { Text(if (canWrite) "Change folder" else "Choose the folder again") }
                TextButton(
                    enabled = canWrite && !sending,
                    onClick = {
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
                    },
                ) { Text(if (sending) "Sending..." else "Send to Streak now") }
            }
        }
        sendMessage?.let { item { SetupNote(it) } }
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
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
