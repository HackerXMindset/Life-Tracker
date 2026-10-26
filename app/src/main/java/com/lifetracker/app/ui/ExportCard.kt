package com.lifetracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.ExportFormat
import com.lifetracker.app.data.ExportPlan
import com.lifetracker.app.data.ExportPlans
import com.lifetracker.app.data.ExportSchedule
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperSegmented
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/**
 * Export your readings as GPX, KML, GeoJSON or CSV, for everything, one day or a range; and the automatic export into a folder.
 * Places and trips are not in these files, only the position readings.
 */
@Composable
fun ExportCard(vm: ExportViewModel = viewModel()) {
    val paper = LocalPaper.current
    val auto by vm.auto.collectAsState()
    val message by vm.message.collectAsState()
    var format by remember { mutableStateOf(ExportFormat.GPX) }
    var scope by remember { mutableIntStateOf(ExportPlans.ALL) }
    var from by remember { mutableStateOf(LocalDate.now().toString()) }
    var to by remember { mutableStateOf(LocalDate.now().toString()) }
    var problem by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ExportPlan?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refresh()
            delay(3_000)
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val plan = pending
        if (uri != null && plan != null) vm.export(uri, plan, format)
        pending = null
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(uri)
    }
    val formats = ExportFormat.entries

    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Save your readings as a file", fontSize = 15.sp, color = paper.ink)
            PaperSegmented(formats.map { it.label }, formats.indexOf(format), { format = formats[it] })
            PaperSegmented(listOf("Everything", "One day", "A range"), scope, { scope = it })
            if (scope != ExportPlans.ALL) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperField(value = from, onValueChange = { from = it }, label = if (scope == ExportPlans.DAY) "Day (2026-10-26)" else "From (2026-10-01)", modifier = Modifier.weight(1f), singleLine = true)
                    if (scope == ExportPlans.RANGE) {
                        PaperField(value = to, onValueChange = { to = it }, label = "To (2026-10-07)", modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
            PaperButton(
                "Export",
                {
                    val plan = ExportPlans.plan(scope, from, to, format, ZoneId.systemDefault(), LocalDate.now(), System.currentTimeMillis())
                    problem = plan.problem.orEmpty()
                    if (plan.problem == null) {
                        pending = plan
                        save.launch(plan.fileName)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (problem.isNotEmpty()) Text(problem, fontSize = 12.sp, color = paper.bad)
            if (message.isNotEmpty()) Text(message, fontSize = 13.sp, color = paper.ink)
        }
    }

    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Automatic export", fontSize = 15.sp, color = paper.ink)
            Text(
                "Saves the readings since the last export into a folder you choose, every day or every week, while the battery is not low. " +
                    "Readings that are added later by an import are not in a later file; use Everything for those.",
                fontSize = 12.sp,
                color = paper.ink2,
            )
            val modes = listOf(ExportSchedule.OFF, ExportSchedule.DAILY, ExportSchedule.WEEKLY)
            PaperSegmented(modes.map { ExportSchedule.label(it) }, modes.indexOf(auto.mode).coerceAtLeast(0), { vm.setMode(modes[it]) })
            PaperSegmented(formats.map { it.label }, formats.indexOf(auto.format), { vm.setAutoFormat(formats[it]) })
            Text("Folder: " + (auto.folderName ?: "not chosen"), fontSize = 13.sp, color = paper.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton("Choose folder", { pickFolder.launch(null) }, style = PaperButtonStyle.Ghost, small = true)
                PaperButton("Export now", vm::exportNow, style = PaperButtonStyle.Ghost, small = true, enabled = auto.folderName != null)
            }
            if (auto.mode != ExportSchedule.OFF && auto.folderName == null) {
                Text("Choose a folder, or nothing is saved.", fontSize = 12.sp, color = paper.bad)
            }
            if (auto.note.isNotEmpty()) Text(auto.note, fontSize = 12.sp, color = paper.ink2)
        }
    }
}
