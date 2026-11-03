package com.lifetracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Export your GPS readings in three plain steps: what (everything, a day or a range), how (GPX, KML, GeoJSON, CSV or the Google
 * Timeline layout), then save. Below it, the automatic export into a folder. Places and trips are not in these files, only readings.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    var count by remember { mutableStateOf<Int?>(null) }
    val zone = ZoneId.systemDefault()

    LaunchedEffect(Unit) {
        while (true) {
            vm.refresh()
            delay(3_000)
        }
    }

    // How many readings the choice would save, so nobody exports an empty file by accident.
    LaunchedEffect(scope, from, to) {
        val plan = ExportPlans.plan(scope, from, to, format, zone, LocalDate.now(), System.currentTimeMillis())
        count = if (plan.problem == null) vm.count(plan) else null
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

    fun quick(days: Int) {
        val today = LocalDate.now()
        scope = ExportPlans.RANGE
        from = today.minusDays((days - 1).toLong()).toString()
        to = today.toString()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    PaperSection("Save your GPS readings to a file", "step by step")
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoLabel("1  What to export")
            PaperSegmented(listOf("Everything", "One day", "A range"), scope, { scope = it })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Today", false, { scope = ExportPlans.DAY; from = LocalDate.now().toString() })
                PaperPill("Last 7 days", false, { quick(7) })
                PaperPill("Last 30 days", false, { quick(30) })
                PaperPill("Last 365 days", false, { quick(365) })
            }
            if (scope != ExportPlans.ALL) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperField(
                        value = from, onValueChange = { from = it },
                        label = if (scope == ExportPlans.DAY) "Day (2026-10-26)" else "From (2026-10-01)",
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    if (scope == ExportPlans.RANGE) {
                        PaperField(value = to, onValueChange = { to = it }, label = "To (2026-10-07)", modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            }
            count?.let { n ->
                Text(
                    if (n == 0) "No readings in this time." else NumberFormat.getIntegerInstance().format(n) + " reading" + (if (n == 1) "" else "s") + " will be saved.",
                    fontSize = 13.sp,
                    color = if (n == 0) paper.bad else paper.ink,
                )
            }

            MonoLabel("2  What kind of file")
            formats.forEach { f -> OptionRow(f.label, f.help, format == f) { format = f } }
            if (format == ExportFormat.TIMELINE) {
                Text(
                    "This is not the file Google gives you. It is written in the same layout, so tools that read a Timeline.json, and this app's own import, can read it. " +
                        "Places and trips are not in it, only where you were and when.",
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
            }

            MonoLabel("3  Save it")
            PaperButton(
                "Choose where to save",
                {
                    val plan = ExportPlans.plan(scope, from, to, format, zone, LocalDate.now(), System.currentTimeMillis())
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

    PaperSection("Automatic export", "into a folder")
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Saves the readings since the last export into a folder you choose, every day or every week, while the battery is not low. " +
                    "Readings that are added later by an import are not in a later file; use Everything above for those.",
                fontSize = 12.sp,
                color = paper.ink2,
            )
            MonoLabel("How often")
            val modes = listOf(ExportSchedule.OFF, ExportSchedule.DAILY, ExportSchedule.WEEKLY)
            PaperSegmented(modes.map { ExportSchedule.label(it) }, modes.indexOf(auto.mode).coerceAtLeast(0), { vm.setMode(modes[it]) })
            MonoLabel("What kind of file")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                formats.forEach { f -> PaperPill(f.label, auto.format == f, { vm.setAutoFormat(f) }) }
            }
            MonoLabel("Where")
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
}
