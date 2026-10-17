package com.lifetracker.app.ui

import android.net.Uri
import androidx.annotation.DrawableRes
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
import androidx.compose.material3.MaterialTheme
import com.lifetracker.app.ui.theme.PaperCard
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import com.lifetracker.app.R
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButtonBox
import com.lifetracker.app.ui.theme.PaperButtonStyle
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

    // Export and GPX import use the same code the Places page used before; only the place on screen moved.
    val places: PlacesViewModel = viewModel()
    val transfer by places.transfer.collectAsState()
    val exportGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) places.exportReadings(uri, csv = false)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) places.exportReadings(uri, csv = true)
    }
    val exportTrips = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) places.exportTrips(uri)
    }
    val importGpx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) places.importGpx(uri)
    }

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsTop("Backups and imports", "Back up, restore, or import tracker data", onBack) }
        item { LockNote("Everything stays on this phone. No account, no server.") }
        state.message?.let { message ->
            item {
                SetupNote(message)
            }
            item { PaperButtonBox(onClick = vm::dismissMessage, style = PaperButtonStyle.Ghost, small = true) { Text("OK") } }
        }

        item { PaperSection("Backups", "the newest 5 are kept") }
        item {
            SourceBlock(
                icon = R.drawable.ic_p_cloud,
                title = "Backups",
                description = "Saves your data as a file in a folder on this phone. A safety copy is made before any restore. " +
                    "A backup is also made the first time you open the app each day. Nothing leaves the phone.",
                folderName = state.backupFolder,
                onChoose = { pickBackup.launch(startHint("Documents")) },
            ) {
                KvRow("Newest backup", state.lastBackup ?: "none yet")
                if (state.backupFolder != null) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PaperButtonBox(onClick = vm::backupNow, small = true, enabled = !state.busy) {
                            PaperIcon(R.drawable.ic_p_cloud, size = 16.dp, tint = LocalContentColor.current)
                            Text("Back up now")
                        }
                        PaperButtonBox(
                            onClick = { showRestoreList = true },
                            style = PaperButtonStyle.Ghost,
                            small = true,
                            enabled = !state.busy,
                        ) {
                            PaperIcon(R.drawable.ic_p_restore, size = 16.dp, tint = LocalContentColor.current)
                            Text("Restore")
                        }
                    }
                }
            }
        }

        item { PaperSection("Export", "you choose where it is saved") }
        item {
            Text(
                "Save your readings or trips as a file other apps can open. Places become waypoints and trips become tracks, " +
                    "with time and distance. Opens in GPSLogger, OsmAnd and most map apps.",
                fontSize = 12.5.sp,
                color = LocalPaper.current.ink2,
            )
        }
        item {
            SettingsRow(R.drawable.ic_p_ext, "Readings as GPX", "Every position reading, as a track") { exportGpx.launch("life-tracker-readings.gpx") }
        }
        item {
            SettingsRow(R.drawable.ic_p_ext, "Readings as CSV", "The same readings as a table") { exportCsv.launch("life-tracker-readings.csv") }
        }
        item {
            SettingsRow(R.drawable.ic_p_ext, "Trips as GPX", "Your trips as tracks") { exportTrips.launch("life-tracker-trips.gpx") }
        }
        if (transfer.isNotEmpty()) item { SetupNote(transfer) }

        item { PaperSection("Imports", "read only, never doubled") }
        item {
            SourceBlock(
                icon = R.drawable.ic_p_habits,
                title = "Streak",
                description = "Where Streak saves its backups. When you open the app, the newest one is imported: " +
                    "habits, notes, to-dos, and focus sessions, which appear on your Timeline. " +
                    "Nothing in Streak is changed, and importing twice never doubles anything.",
                folderName = state.streakFolder,
                onChoose = { pickStreak.launch(startHint("Streak")) },
            ) {
                if (state.streakFolder != null) {
                    NewestFile(state.streakNewest, "No Streak backup found in this folder.")
                    KvRow("Last import", state.lastStreakImport ?: "not yet")
                    ImportButton(enabled = !state.busy && state.streakNewest != null, onClick = vm::importStreak)
                }
            }
        }
        item {
            SourceBlock(
                icon = R.drawable.ic_p_food,
                title = "OpenNutriTracker",
                description = "Where OpenNutriTracker saves its export. When you open the app, the newest one is imported: " +
                    "meals, daily goals, and activities, which appear on your Timeline. " +
                    "Nothing in OpenNutriTracker is changed, and importing twice never doubles anything.",
                folderName = state.nutriFolder,
                onChoose = { pickNutri.launch(startHint("OpenNutritracker")) },
            ) {
                if (state.nutriFolder != null) {
                    NewestFile(state.nutriNewest, "No OpenNutriTracker export found in this folder.")
                    KvRow("Last import", state.lastOntImport ?: "not yet")
                    ImportButton(enabled = !state.busy && state.nutriNewest != null, onClick = vm::importOpenNutriTracker)
                }
            }
        }
        item {
            SetCard {
                Row(
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PaperIcon(R.drawable.ic_p_route)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("GPX files", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LocalPaper.current.ink)
                        Text(
                            "Tracks from GPSLogger or another logger. Importing only adds readings; nothing is replaced or deleted, " +
                                "and visits are worked out again afterwards.",
                            fontSize = 12.5.sp,
                            color = LocalPaper.current.ink2,
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)) {
                    PaperButtonBox(onClick = { importGpx.launch(arrayOf("*/*")) }, small = true) { Text("Import GPX") }
                }
            }
        }
        item {
            Text(
                text = "The app only reads the Streak and OpenNutriTracker folders. It never changes files in them.",
                fontSize = 12.sp,
                color = LocalPaper.current.ink2,
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
                        "(${formatDateTime(file.modified)}). Anything you logged since then will be gone, but phone usage and calls are kept and only added to. " +
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

/** The demo's "flat card": an icon and name with a short line, then key / value lines, then whatever [extra] adds. */
@Composable
private fun SourceBlock(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    folderName: String?,
    onChoose: () -> Unit,
    extra: @Composable () -> Unit,
) {
    val p = LocalPaper.current
    SetCard {
        Row(
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PaperIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(description, fontSize = 12.5.sp, color = p.ink2)
            }
        }
        KvRow("Folder", folderName ?: "not chosen yet")
        extra()
        Row(modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)) {
            PaperButtonBox(onClick = onChoose, style = PaperButtonStyle.Ghost, small = true) {
                PaperIcon(R.drawable.ic_p_folder, size = 16.dp, tint = LocalContentColor.current)
                Text(if (folderName == null) "Choose folder" else "Change folder")
            }
        }
    }
}

@Composable
private fun ImportButton(enabled: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
        PaperButtonBox(onClick = onClick, small = true, enabled = enabled) { Text("Import now") }
    }
}

@Composable
private fun NewestFile(file: FoundFile?, emptyText: String) {
    if (file == null) {
        Text(emptyText, fontSize = 12.5.sp, color = LocalPaper.current.ink2, modifier = Modifier.padding(vertical = 8.dp))
    } else {
        KvRow("Newest file found", file.name)
        KvRow("Modified", formatDateTime(file.modified))
    }
}
