package com.lifetracker.app.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.Feature
import com.lifetracker.app.data.Features
import com.lifetracker.app.data.FoundFile
import com.lifetracker.app.data.SourceScanner
import com.lifetracker.app.data.gymmane.GymManSync
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import kotlinx.coroutines.launch

/**
 * Export > "For GymMane": saves a file that GymMane can restore, with the food, water, supplements, fasts and weights that were
 * logged in Kairos. Either a copy of your own GymMane backup with that added (nothing of yours is lost), or a file with only Kairos' data.
 */
@Composable
fun GymManExportCard() {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val scope = rememberCoroutineScope()
    if (!Features.isOn(context, Feature.FOOD)) return

    var message by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf<Uri?>(null) }
    var kairosOnly by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<FoundFile?>(null) }

    LaunchedEffect(Unit) {
        found = runCatching { DataSources(context).folder(DataSources.Slot.GymMane)?.let { SourceScanner.newestGymMane(context, it) } }.getOrNull()
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    message = GymManSync.exportTo(context, uri, if (kairosOnly) null else base)
                    failed = false
                } catch (e: Exception) {
                    message = e.message ?: "Could not save the file."
                    failed = true
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            base = uri
            kairosOnly = false
            save.launch(GymManSync.exportFileName())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaperSection("For GymMane", "a file GymMane can restore")
        PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Saves your food, water, supplements, fasts and weights from Kairos in GymMane's own backup format. " +
                        "Open GymMane, go to its restore option and choose the file.",
                    fontSize = 12.sp, color = paper.ink2,
                )
                MonoLabel("Added to your GymMane backup")
                Text(
                    "A copy of your GymMane backup with what only exists here added. Nothing in GymMane is lost or changed, " +
                        "and your own backup file is not touched." +
                        (found?.let { " Uses " + it.name + "." } ?: " You will be asked to choose your GymMane backup."),
                    fontSize = 12.sp, color = paper.ink2,
                )
                PaperButton(
                    "Save with my GymMane backup",
                    {
                        kairosOnly = false
                        val f = found
                        if (f != null) {
                            base = f.uri
                            save.launch(GymManSync.exportFileName())
                        } else {
                            pick.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                MonoLabel("Only Kairos' data")
                Text(
                    "GymMane replaces everything it has when it restores a file, so this wipes its workouts and notes. " +
                        "Use it only on a GymMane with nothing in it yet.",
                    fontSize = 12.sp, color = paper.bad,
                )
                PaperButton(
                    "Save Kairos data only",
                    { kairosOnly = true; save.launch(GymManSync.exportFileName()) },
                    modifier = Modifier.fillMaxWidth(),
                    style = PaperButtonStyle.Ghost,
                )
                if (message.isNotEmpty()) Text(message, fontSize = 13.sp, color = if (failed) paper.bad else paper.ink)
            }
        }
    }
}
