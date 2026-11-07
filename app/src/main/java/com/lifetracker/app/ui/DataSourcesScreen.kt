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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.BackupManager
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.PaperToggle
import com.lifetracker.app.data.BackupManager.BackupFile
import com.lifetracker.app.data.DataSources.Slot
import com.lifetracker.app.data.FoundFile

/** A hint that makes Android's folder picker open near where the folder usually is. */
private fun startHint(folderName: String): Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$folderName")

@Composable
fun DataSourcesScreen(onBack: () -> Unit, vm: DataSourcesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(Slot.Backup, uri)
    }
    val pickStreak = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(Slot.Streak, uri)
    }
    val pickNutri = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(Slot.OpenNutriTracker, uri)
    }
    val pickGym = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(Slot.GymMane, uri)
    }
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.saveCopy(uri)
    }
    val openCopy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.previewFile(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsTop("Backup, export and import", "One backup for everything, GPS files, other apps", onBack) }
        item { LockNote("Everything stays on this phone. No account, no server.") }
        item { PaperSegmented(listOf("Backup", "Export", "Import"), tab, { tab = it }) }
        if (state.busy) item { Text("Working, one moment...", fontSize = 13.sp, color = LocalPaper.current.ink2) }
        state.message?.let { message ->
            item { SetupNote(message) }
            item { PaperButtonBox(onClick = vm::dismissMessage, style = PaperButtonStyle.Ghost, small = true) { Text("OK") } }
        }

        when (tab) {
            0 -> {
                item { PaperSection("One backup, everything in it", "no separate backups") }
                item {
                    Text(
                        "A backup is one file. It holds all your data: Timeline, habits, money, food, to-dos, notes, places, trips, every GPS reading, " +
                            "phone usage, calls, charging, steps, sleep, rules and goals; and every setting of the app. " +
                            "The offline map files are not in it (the areas are remembered, the map files can be downloaded again).",
                        fontSize = 12.5.sp,
                        color = LocalPaper.current.ink2,
                    )
                }
                item {
                    SetCard {
                        KvRow("Folder", state.backupFolder ?: "not chosen yet", first = true)
                        KvRow("Newest backup", state.lastBackup ?: "none yet")
                        Column(modifier = Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PaperButtonBox(onClick = { pickBackup.launch(startHint("Documents")) }, style = PaperButtonStyle.Ghost, small = true) {
                                    PaperIcon(R.drawable.ic_p_folder, size = 16.dp, tint = LocalContentColor.current)
                                    Text(if (state.backupFolder == null) "Choose folder" else "Change folder")
                                }
                                if (state.backupFolder != null) {
                                    PaperButtonBox(onClick = vm::backupNow, small = true, enabled = !state.busy) {
                                        PaperIcon(R.drawable.ic_p_cloud, size = 16.dp, tint = LocalContentColor.current)
                                        Text("Back up now")
                                    }
                                }
                            }
                            Text(
                                "A backup is made by itself the first time you open the app each day, once a folder is chosen. The newest 5 are kept.",
                                fontSize = 12.sp,
                                color = LocalPaper.current.ink2,
                            )
                        }
                    }
                }

                item { PaperSection("Keep a copy anywhere", "for a new phone or the cloud") }
                item {
                    SetCard {
                        Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Save the same one file wherever you like (Drive, a USB stick, another folder), or open one to restore from it.",
                                fontSize = 12.5.sp,
                                color = LocalPaper.current.ink2,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PaperButtonBox(onClick = { saveCopy.launch(BackupManager.fileName()) }, small = true, enabled = !state.busy) { Text("Save to a file") }
                                PaperButtonBox(
                                    onClick = { openCopy.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                                    style = PaperButtonStyle.Ghost, small = true, enabled = !state.busy,
                                ) { Text("Restore from a file") }
                            }
                        }
                    }
                }

                item { PaperSection("Restore", "pick one to see what is in it first") }
                if (state.backups.isEmpty()) {
                    item {
                        Text(
                            if (state.backupFolder == null) "Choose a backup folder above to see the backups in it." else "There are no backups in the folder yet.",
                            fontSize = 13.sp,
                            color = LocalPaper.current.ink2,
                        )
                    }
                } else {
                    items(state.backups, key = { it.uri.toString() }) { file ->
                        BackupRow(file, enabled = !state.busy, onClick = { vm.preview(file) })
                    }
                }
                item {
                    Text(
                        "A restore saves a safety copy of what you have now first (shown in this list as Safety copy), so it can be undone.",
                        fontSize = 12.sp,
                        color = LocalPaper.current.ink2,
                    )
                }
            }

            1 -> {
                item { ExportCard() }
                item { PaperSection("Trips") }
                item {
                    TripsExportRow()
                }
            }

            else -> {
                item { PaperSection("Google Timeline") }
                item { TimelineImportCard() }
                item { PaperSection("Other GPS files", "GPX, KML, GeoJSON, CSV") }
                item { ImportCard() }
                item { PaperSection("Other apps", "read only, never doubled") }
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
                    SourceBlock(
                        icon = R.drawable.ic_p_food,
                        title = "GymMane",
                        description = "Where GymMane saves its automatic backups. When you open the app, the newest one is imported: " +
                            "food log, water, supplements, fasts and weight. Nothing in GymMane is changed, " +
                            "and importing twice never doubles anything.",
                        folderName = state.gymFolder,
                        onChoose = { pickGym.launch(startHint("GymMane")) },
                    ) {
                        if (state.gymFolder != null) {
                            NewestFile(state.gymNewest, "No GymMane backup found in this folder.")
                            KvRow("Last import", state.lastGymImport ?: "not yet")
                            ImportButton(enabled = !state.busy && state.gymNewest != null, onClick = vm::importGymMane)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Send what is new here back", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Writes a copy of GymMane's newest backup with your food, water, supplements, fasts and weights from here added, " +
                                            "as gymmane-kairos-….zip in the same folder. GymMane's own files are never touched. " +
                                            "To use it, restore that file inside GymMane.",
                                        fontSize = 12.sp,
                                        color = LocalPaper.current.ink2,
                                    )
                                }
                                PaperToggle(checked = state.gymSend, onChange = vm::setGymSend)
                            }
                            KvRow("Last sent", state.lastGymSend ?: "not yet")
                            Row(modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) {
                                PaperButtonBox(onClick = vm::sendGymMane, small = true, enabled = !state.busy && state.gymNewest != null) { Text("Send now") }
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "The app only reads the Streak, OpenNutriTracker and GymMane backups. It never changes them; the only thing it adds to the GymMane folder is its own gymmane-kairos copy, and only if you switch that on.",
                        fontSize = 12.sp,
                        color = LocalPaper.current.ink2,
                    )
                }
            }
        }
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text("Replace everything?") },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        (if (pending.isSafety) "This safety copy" else "This backup") +
                            (if (pending.exportedAt.isNotBlank()) " was made on ${pending.exportedAt.replace('T', ' ').take(16)}" else "") + " and holds:",
                    )
                    Text(pending.summary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your Timeline, habits, to-dos, notes, food, money, rules and goals become exactly what is in it. " +
                            "GPS readings, places, trips, calls, phone usage, charging, steps and sleep are added to what you have, never removed. " +
                            (if (pending.hasSettings) "Every setting is put back, and the app restarts." else "It is an older backup with no settings, so your settings stay as they are.") +
                            " What you have now is saved as a safety copy first.",
                        fontSize = 13.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Cancel") } },
        )
    }

    if (state.restartNeeded) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restart the app") },
            text = { Text("Your settings were put back. The app has to start again to use them.") },
            confirmButton = { TextButton(onClick = { AppRestart.restart(context) }) { Text("Restart now") } },
        )
    }
}

/** One backup in the list: what it is, when, and a Restore button. */
@Composable
private fun BackupRow(file: BackupFile, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPaper.current
    SetCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (file.isSafety) "Safety copy" else "Backup", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(formatDateTime(file.modified), fontSize = 12.sp, color = p.ink2)
            }
            PaperButtonBox(onClick = onClick, style = PaperButtonStyle.Ghost, small = true, enabled = enabled) {
                PaperIcon(R.drawable.ic_p_restore, size = 16.dp, tint = LocalContentColor.current)
                Text("Restore")
            }
        }
    }
}

/** Trips as GPX tracks: the same code the Places page used. */
@Composable
private fun TripsExportRow() {
    val places: PlacesViewModel = viewModel()
    val transfer by places.transfer.collectAsState()
    val exportTrips = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) places.exportTrips(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsRow(R.drawable.ic_p_ext, "Trips as GPX", "Your saved trips as tracks, one per trip") { exportTrips.launch("life-tracker-trips.gpx") }
        if (transfer.isNotEmpty()) SetupNote(transfer)
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
