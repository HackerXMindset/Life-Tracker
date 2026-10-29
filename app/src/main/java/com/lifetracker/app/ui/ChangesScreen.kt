package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.ChangeItem
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import androidx.compose.material3.Text

/** Settings > Places and trips > Changes: everything you joined, cut or hid, each with a way back. */
@Composable
fun ChangesScreen(onBack: () -> Unit, vm: ChangesViewModel = viewModel()) {
    val paper = LocalPaper.current
    val items by vm.items.collectAsState()
    val message by vm.message.collectAsState()
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsTop("Changes", "Joined, cut and hidden, with a way back", onBack) }
        item {
            Text(
                "Anything you join, cut or hide is listed here. Nothing is ever deleted, so every row can be brought back.",
                fontSize = 12.sp,
                color = paper.ink2,
            )
        }
        message?.let { text ->
            item {
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text, fontSize = 14.sp, color = paper.ink)
                    PaperButton("OK", vm::clearMessage, style = PaperButtonStyle.Ghost, small = true)
                }
            }
        }
        if (items.any { it.kind == ChangeItem.Kind.JOIN }) {
            item {
                PaperButton("Bring back all joined trips", vm::bringBackAllJoins, modifier = Modifier.fillMaxWidth())
            }
        }
        if (items.isEmpty()) {
            item { Text("Nothing to bring back yet.", fontSize = 14.sp, color = paper.ink2) }
        }
        items(items, key = { it.key }) { item ->
            PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = paper.ink)
                Text(item.detail, fontSize = 13.sp, color = paper.ink2)
                if (item.canRestore) {
                    PaperButton(restoreLabel(item), { vm.restore(item) }, small = true)
                } else {
                    Text(item.why, fontSize = 12.sp, color = paper.ink2)
                }
            }
        }
    }
}

private fun restoreLabel(item: ChangeItem) = when (item.kind) {
    ChangeItem.Kind.JOIN -> "Separate the trips again"
    ChangeItem.Kind.CUT -> "Put the trip back together"
    ChangeItem.Kind.HIDDEN_TRIP -> "Show the trip again"
    ChangeItem.Kind.HIDDEN_STAY -> "Show the stay again"
}
