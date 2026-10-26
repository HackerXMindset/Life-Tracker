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
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard

/**
 * Import position readings from a file: GPX, KML, GeoJSON, CSV or Google Timeline. A preview says what is in it and what would
 * be new; only Import adds them. Nothing is replaced or deleted.
 */
@Composable
fun ImportCard(vm: ImportViewModel = viewModel()) {
    val paper = LocalPaper.current
    val state by vm.state.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.choose(uri)
    }
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Import position readings", fontSize = 15.sp, color = paper.ink)
            Text(
                "From a GPX, KML, GeoJSON or CSV file, or from Google Timeline (Records.json, or the export made on the phone). " +
                    "The file type is found from what is inside. You see what it holds first. Importing only adds readings; " +
                    "nothing is replaced or deleted, and visits are worked out again afterwards.",
                fontSize = 12.sp,
                color = paper.ink2,
            )
            PaperButton("Choose a file", { pick.launch(arrayOf("*/*")) }, style = PaperButtonStyle.Ghost, small = true, enabled = !state.busy)
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
