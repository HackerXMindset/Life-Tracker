package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.MapStyleEntity
import com.lifetracker.app.data.MapStyles
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard

/** Settings > Places and trips > Maps: the style the maps use, your own styles, and where the map comes from. */
@Composable
fun MapsScreen(onBack: () -> Unit, vm: MapsViewModel = viewModel()) {
    val paper = LocalPaper.current
    val styles by vm.styles.collectAsState()
    val selected by vm.selected.collectAsState()
    var adding by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SettingsTop("Maps", "The look of the map on trips and places", onBack) }
        item { PaperSection("Map style") }
        items(MapStyles.builtIn, key = { it.id }) { s ->
            StyleRow(s.name, "Built in · OpenFreeMap", selected == s.id, onUse = { vm.select(s.id) }, onHide = null)
        }
        items(styles.filter { !it.hidden }, key = { it.id }) { s ->
            StyleRow(s.name, "Yours", selected == s.id, onUse = { vm.select(s.id) }, onHide = { vm.setHidden(s, true) })
        }
        item { PaperButton("Add your own style", { adding = true }, modifier = Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost) }
        item {
            Text(
                "A style address looks like https://example.com/style.json. A tile address has {z}, {x} and {y} in it. " +
                    "If the provider wants a key, write {key} in the address and put the key in its own box. " +
                    "A style you add is your own responsibility: check what its provider allows.",
                fontSize = 12.sp,
                color = paper.ink2,
            )
        }
        val hidden = styles.filter { it.hidden }
        if (hidden.isNotEmpty()) {
            item { PaperSection("Hidden styles", "kept, can be shown again") }
            items(hidden, key = { it.id }) { s ->
                PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.name, modifier = Modifier.weight(1f), fontSize = 14.sp, color = paper.ink)
                        PaperButton("Show again", { vm.setHidden(s, false) }, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
            }
        }
        item { PaperSection("Where the map comes from") }
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
                Text(
                    "The map is the one place where the app talks to an outside server. Asking for a map tile tells that server " +
                        "roughly which area you are looking at. Nothing else is sent: no account, no readings, no names. " +
                        "The built-in styles come from OpenFreeMap (" + MapStyles.ATTRIBUTION + ").",
                    fontSize = 13.sp,
                    color = paper.ink,
                )
            }
        }
        item {
            Text("Offline areas (maps kept on the phone) come in the next step.", fontSize = 12.sp, color = paper.ink2)
        }
    }

    if (adding) {
        AddStyleDialog(
            onAdd = { name, address, key -> vm.add(name, address, key) },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun StyleRow(name: String, sub: String, inUse: Boolean, onUse: () -> Unit, onHide: (() -> Unit)?) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false, onClick = onUse) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                Text(sub + if (inUse) " · in use" else "", fontSize = 12.sp, color = paper.ink2)
            }
            if (onHide != null) PaperButton("Hide", onHide, style = PaperButtonStyle.Ghost, small = true)
        }
    }
}

@Composable
private fun AddStyleDialog(onAdd: (String, String, String) -> String?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add your own style") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Style or tile address") })
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Key (if the address has {key})") }, singleLine = true)
                problem?.let { Text(it, fontSize = 12.sp, color = LocalPaper.current.bad) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                problem = onAdd(name, address, key)
                if (problem == null) onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
