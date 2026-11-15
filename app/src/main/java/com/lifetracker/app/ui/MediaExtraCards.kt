package com.lifetracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.lifetracker.app.data.MediaLog
import com.lifetracker.app.data.MediaService
import com.lifetracker.app.data.MediaSettings
import com.lifetracker.app.data.MediaSync
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import kotlinx.coroutines.launch

private fun clock(minute: Int): String = "%d:%02d".format(minute / 60, minute % 60)

/** Where Mihon's and Kotatsu's automatic backups are kept, so what they say about your reading can be used. */
@Composable
fun MediaBackupCard(onChange: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val sources = remember { DataSources(context) }
    var tick by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(DataSources.Slot.Mihon) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            message = try {
                sources.setFolder(picking, uri)
                onChange()
                "Folder saved. It is read at the next check."
            } catch (e: Exception) {
                e.message ?: "Android would not allow that folder."
            }
            tick++
        }
    }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Backups from Mihon and Kotatsu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Text(
                "Their automatic backups say exactly when each chapter was read and for how long. Choose the folder each one saves its backups in " +
                    "(in Mihon: Settings, Data and storage, Automatic backups; in Kotatsu: Settings, Data and privacy). Kairos only reads from it, and uses " +
                    "the real reading time instead of a guess. These come first when the same chapter is also on a tracker.",
                fontSize = 12.5.sp, color = p.ink2,
            )
            listOf(DataSources.Slot.Mihon to "Mihon (or Komikku, Tachiyomi)", DataSources.Slot.Kotatsu to "Kotatsu").forEach { (slot, label) ->
                val uri = remember(tick) { sources.folder(slot) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, fontSize = 14.sp, color = p.ink)
                    Text(if (uri == null) "No folder chosen." else sources.folderName(uri), fontSize = 12.5.sp, color = p.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperButton(if (uri == null) "Choose folder" else "Change", { picking = slot; launcher.launch(null) }, small = true)
                        if (uri != null) PaperButton("Remove", { sources.clearFolder(slot); onChange(); tick++ }, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
            }
            if (message.isNotEmpty()) Text(message, fontSize = 12.5.sp, color = p.ink, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Which place wins when the same chapter or episode is in more than one. */
@Composable
fun MediaPriorityCard() {
    val context = LocalContext.current
    val p = LocalPaper.current
    val settings = remember { MediaSettings(context) }
    var order by remember { mutableStateOf(settings.priority) }
    fun move(i: Int, by: Int) {
        val j = i + by
        if (j < 0 || j >= order.size) return
        val list = order.toMutableList()
        val x = list[i]; list[i] = list[j]; list[j] = x
        order = list
        settings.priority = list
    }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("When the same thing is in two places", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Text(
                "A chapter or episode that Mihon, AniList, MyAnimeList or Simkl all note is added once, from the one highest in this list. " +
                    "If a better one turns up later (a backup arriving after a tracker), it replaces the earlier entry, unless you changed that entry.",
                fontSize = 12.5.sp, color = p.ink2,
            )
            order.forEachIndexed { i, service ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}. ${service.label}", fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f))
                    PaperButton("Up", { move(i, -1) }, style = PaperButtonStyle.Ghost, small = true, enabled = i > 0)
                    PaperButton("Down", { move(i, 1) }, style = PaperButtonStyle.Ghost, small = true, enabled = i < order.size - 1)
                }
            }
        }
    }
}

/** Doubtful ones that were not added by themselves. */
@Composable
fun MediaPendingCard(refresh: Int, onChange: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val scope = rememberCoroutineScope()
    val items = remember(refresh) { MediaLog(context).pending() }
    if (items.isEmpty()) return
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Maybe (${items.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Text("These look doubtful, so they were not put on the Timeline. Add the ones that are real.", fontSize = 12.5.sp, color = p.ink2)
            items.take(12).forEach { m ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.event.entryTitle, fontSize = 14.sp, color = p.ink)
                    Text("${m.event.service.label} · ${m.reason}", fontSize = 12.sp, color = p.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperButton("Add", { scope.launch { runCatching { MediaSync.accept(context, m.event.id) }; onChange() } }, small = true)
                        PaperButton("Skip", { scope.launch { runCatching { MediaSync.dismiss(context, m.event.id) }; onChange() } }, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
            }
        }
    }
}

/** What was added lately, with how its time was found, and a way to take it back. */
@Composable
fun MediaRecentCard(refresh: Int, onChange: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val scope = rememberCoroutineScope()
    val all = remember(refresh) { MediaLog(context).recent().reversed() }
    if (all.isEmpty()) return
    val lastRun = all.first().runMs
    val lastCount = all.count { it.runMs == lastRun }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recently added", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Text(
                "Taken off here, an entry stays off. 'Real' is measured reading time, 'app' is time in the app, 'estimated' is a guess from the length.",
                fontSize = 12.5.sp, color = p.ink2,
            )
            PaperButton("Undo the last check ($lastCount)", {
                scope.launch { runCatching { MediaSync.remove(context, all.filter { it.runMs == lastRun }.map { it.entryId }.toSet()) }; onChange() }
            }, style = PaperButtonStyle.Ghost, small = true)
            all.take(15).forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.title, fontSize = 14.sp, color = p.ink)
                        Text("${r.date} ${clock(r.startMinute)}–${clock(r.endMinute)} · ${r.how} · ${r.event.service.label}", fontSize = 12.sp, color = p.ink2)
                    }
                    PaperButton("Remove", { scope.launch { runCatching { MediaSync.remove(context, setOf(r.entryId)) }; onChange() } }, style = PaperButtonStyle.Ghost, small = true)
                }
            }
        }
    }
}
