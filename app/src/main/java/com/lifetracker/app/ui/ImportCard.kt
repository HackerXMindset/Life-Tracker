package com.lifetracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.ImportKind
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard

/**
 * Import position readings from a GPX, KML, GeoJSON or CSV file. A preview says what is in it and what would be new; only Import
 * adds them. Nothing is replaced or deleted.
 */
@Composable
fun ImportCard(vm: ImportViewModel = viewModel(key = "files")) {
    ImportBox(
        vm = vm,
        title = "Import position readings from a file",
        intro = "A GPX, KML, GeoJSON or CSV file, such as one made by GPSLogger, OsmAnd or this app's Export. The file type is found from what is inside. " +
            "You see what it holds first. Importing only adds readings; nothing is replaced or deleted, and visits are worked out again afterwards. " +
            "For your Google Timeline use the card above.",
        button = "Choose a file",
        only = null,
    )
}

/** Import the Timeline.json that Google Maps makes on the phone, with the steps to get it. Other files are refused. */
@Composable
fun TimelineImportCard(vm: ImportViewModel = viewModel(key = "timeline")) {
    ImportBox(
        vm = vm,
        title = "Import your Google Timeline",
        intro = "Google keeps your Timeline on the phone now. To get the file: open Google Maps, go to your Timeline settings and choose Export Timeline data " +
            "(the menu names change now and then). It saves a file called Timeline.json. Then pick that file here.\n\n" +
            "Only that file is read: the places you went and the paths between them become GPS readings, and visits and trips are worked out again. " +
            "The older Takeout files (Records.json, Semantic Location History) are not read. You see what it holds first, nothing is replaced, and importing twice never doubles anything.",
        button = "Choose Timeline.json",
        only = ImportKind.TIMELINE_NEW,
    )
}

@Composable
private fun ImportBox(vm: ImportViewModel, title: String, intro: String, button: String, only: ImportKind?) {
    val paper = LocalPaper.current
    val state by vm.state.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.choose(uri, only)
    }
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 15.sp, color = paper.ink)
            Text(intro, fontSize = 12.sp, color = paper.ink2)
            PaperButton(button, { pick.launch(arrayOf("*/*")) }, style = PaperButtonStyle.Ghost, small = true, enabled = !state.busy)
            if (state.busy) Text(state.message, fontSize = 13.sp, color = paper.ink)
            state.preview?.let { p ->
                Text(vm.previewText(p), fontSize = 13.sp, color = paper.ink)
                if (!state.busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperButton("Import ${p.added}", vm::confirm, small = true, enabled = state.canImport)
                        PaperButton("Cancel", vm::cancel, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
            }
            if (!state.busy && state.message.isNotEmpty()) Text(state.message, fontSize = 13.sp, color = paper.ink)
        }
    }
}
