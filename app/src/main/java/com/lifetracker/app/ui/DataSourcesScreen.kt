package com.lifetracker.app.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.BackupManager
import com.lifetracker.app.data.BackupManager.BackupFile
import com.lifetracker.app.data.DataSources.Slot
import com.lifetracker.app.data.FoundFile

/** A hint that makes Android's folder picker open near where the folder usually is. */
private fun startHint(folderName: String): Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$folderName")

@Composable
fun DataSourcesScreen(onBack: () -> Unit, vm: DataSourcesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    BackHandler(onBack = onBack)

    var showRestoreList by remember { mutableStateOf(false) }
    var toRestore by remember { mutableStateOf<BackupFile?>(null) }

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(Slot.Backup, uri)
    }
    val pickStreak = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(Slot.Streak, uri)
    }
    val pickNutri = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(Slot.OpenNutriTracker, uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
        }
        item {
            ScreenHeader(label = "Settings", badge = "On this phone", title = "Data sources")
        }
        state.message?.let { message ->
            item {
                Card {
                    Text(message, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = vm::dismissMessage) { Text("OK") }
                }
            }
        }
        item {
            SourceCard(
                title = "Backups",
                description = "Saves your data as a file in a folder on this phone. The newest 5 are kept, " +
                    "and a safety copy is made before any restore. Nothing leaves the phone.",
                folderName = state.backupFolder,
                onChoose = { pickBackup.launch(startHint("Documents")) },
            ) {
                Detail(if (state.lastBackup != null) "Last backup: ${state.lastBackup}" else "No backup made yet.")
                if (state.backupFolder != null) {
                    Detail("A backup is also made the first time you open the app each day.")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = vm::backupNow, enabled = !state.busy) { Text("Back up now") }
                        OutlinedButton(
                            onClick = { showRestoreList = true },
                            enabled = !state.busy,
                        ) { Text("Restore") }
                    }
                }
            }
        }
        item {
            SourceCard(
                title = "Streak",
                description = "Where Streak saves its backups. The newest one will be imported in the Habits step.",
                folderName = state.streakFolder,
                onChoose = { pickStreak.launch(startHint("Streak")) },
            ) {
                if (state.streakFolder != null) {
                    NewestFile(state.streakNewest, "No Streak backup found in this folder.")
                }
            }
        }
        item {
            SourceCard(
                title = "OpenNutriTracker",
                description = "Where OpenNutriTracker saves its export. The newest one will be imported in the Food step.",
                folderName = state.nutriFolder,
                onChoose = { pickNutri.launch(startHint("OpenNutritracker")) },
            ) {
                if (state.nutriFolder != null) {
                    NewestFile(state.nutriNewest, "No OpenNutriTracker export found in this folder.")
                }
            }
        }
        item {
            Text(
                text = "The app only reads the Streak and OpenNutriTracker folders. It never changes files in them.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showRestoreList) {
        AlertDialog(
            onDismissRequest = { showRestoreList = false },
            title = { Text("Restore from") },
            text = {
                if (state.backups.isEmpty()) {
                    Text("There are no backups in the folder yet.")
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        state.backups.forEach { file ->
                            TextButton(
                                onClick = {
                                    showRestoreList = false
                                    toRestore = file
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = if (file.isSafety) "Safety copy" else "Backup",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = formatDateTime(file.modified),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreList = false }) { Text("Close") }
            },
        )
    }

    toRestore?.let { file ->
        AlertDialog(
            onDismissRequest = { toRestore = null },
            title = { Text("Replace everything?") },
            text = {
                Text(
                    "Your timeline will become exactly what it was in this ${if (file.isSafety) "safety copy" else "backup"} " +
                        "(${formatDateTime(file.modified)}). Anything logged since then will be gone. " +
                        "Your current data is saved as a safety copy first, so you can undo this.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.restore(file)
                    toRestore = null
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { toRestore = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SourceCard(
    title: String,
    description: String,
    folderName: String?,
    onChoose: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit,
) {
    Card {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MonoLabel("Folder")
        Text(
            text = folderName ?: "Not chosen yet",
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedButton(onClick = onChoose) {
            Text(if (folderName == null) "Choose folder" else "Change folder")
        }
        extra()
    }
}

@Composable
private fun Detail(text: String) {
    Text(text = text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NewestFile(file: FoundFile?, emptyText: String) {
    if (file == null) {
        Detail(emptyText)
    } else {
        MonoLabel("Newest file found")
        Text(text = file.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Detail("Modified ${formatDateTime(file.modified)}")
    }
}
