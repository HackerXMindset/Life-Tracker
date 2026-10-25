package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.roundToInt
import com.lifetracker.app.data.AreaSource
import com.lifetracker.app.data.AreaState
import com.lifetracker.app.data.MapAreaEntity
import com.lifetracker.app.data.MapStyleEntity
import com.lifetracker.app.data.OfflineRules
import com.lifetracker.app.data.TileMath
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
    val areas by vm.areas.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val average by vm.average.collectAsState()
    val styleLimit by vm.styleLimit.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var framing by remember { mutableStateOf(false) }
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
        item { PaperSection("Offline maps", "kept on the phone") }
        item {
            SetCard {
                ToggleRow(
                    "A map for every place", prefs.autoPlaces, vm::setAutoPlaces, first = true,
                    hint = "Each place gets the map around it as soon as it is made, and again when it changes",
                )
                if (prefs.autoPlaces) ReachSlider("Around a place", prefs.placeReachKm, vm::setPlaceReach)
                ToggleRow(
                    "A map where you go", prefs.autoWanted, vm::setAutoWanted,
                    hint = "A reading outside every downloaded area asks for the map around it; with no connection it waits",
                )
                if (prefs.autoWanted) ReachSlider("Around where you went", prefs.wantedReachKm, vm::setWantedReach)
                ToggleRow(
                    "Use mobile data for map downloads", prefs.mobileData, vm::setMobileData,
                    hint = "Off means Wi-Fi only; downloads then wait for Wi-Fi",
                )
            }
        }
        item {
            val limit = styleLimit
            Text(
                (if (limit != null) limit.termsNote + " (the provider's rule, not the app's). " else "Your own style has no limit from the app. ") +
                    "The only refusal from the app is not enough free space. Automatic areas are never deleted automatically.",
                fontSize = 12.sp,
                color = paper.ink2,
            )
        }
        item { PaperButton("Download an area", { framing = true }, modifier = Modifier.fillMaxWidth()) }
        item {
            val used = areas.filter { it.state == AreaState.DONE }.sumOf { it.bytes }
            Text(
                "Maps on the phone: " + TileMath.sizeText(used) + " · " + TileMath.sizeText(vm.freeBytes()) + " free",
                fontSize = 13.sp,
                color = paper.ink,
            )
        }
        if (areas.isEmpty()) {
            item { Text("No areas yet.", fontSize = 13.sp, color = paper.ink2) }
        } else {
            items(areas, key = { it.id }) { a -> AreaRow(a, styles, onAgain = { vm.downloadAgain(a) }, onDelete = { vm.deleteArea(a) }) }
        }
    }

    if (framing) {
        var start by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            start = vm.startPoint()
            ready = true
        }
        if (ready) {
            MapFrameDialog(
                start = start,
                average = average,
                freeBytes = vm.freeBytes(),
                limit = styleLimit,
                onDownload = { name, bounds, minZ, maxZ, done -> vm.addByHand(name, bounds, minZ, maxZ, done) },
                onDismiss = { framing = false },
            )
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
private fun ReachSlider(label: String, km: Int, onChange: (Int) -> Unit) {
    val paper = LocalPaper.current
    Column(modifier = Modifier.fillMaxWidth().dashedTop(paper.line).padding(vertical = 8.dp)) {
        Text("$label: $km km each way", fontSize = 13.sp, color = paper.ink)
        Slider(
            value = km.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 1f..25f,
            colors = SliderDefaults.colors(thumbColor = paper.ink, activeTrackColor = paper.ink, inactiveTrackColor = paper.line),
        )
    }
}

@Composable
private fun AreaRow(a: MapAreaEntity, styles: List<MapStyleEntity>, onAgain: () -> Unit, onDelete: () -> Unit) {
    val paper = LocalPaper.current
    val styleName = MapStyles.builtInById(a.styleId)?.name ?: styles.firstOrNull { it.id == a.styleId }?.name ?: "a style that was removed"
    PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
        Text(a.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = paper.ink)
        Text(styleName + " · " + AreaSource.label(a.source) + " · zoom " + a.minZoom + " to " + a.maxZoom, fontSize = 12.sp, color = paper.ink2)
        Text(OfflineRules.stateText(a.state, a.source, a.tiles, a.totalTiles, a.bytes, a.note), fontSize = 13.sp, color = if (a.state == AreaState.FAILED) paper.bad else paper.ink)
        if (a.state == AreaState.DONE && a.doneMs > 0L) Text("Downloaded " + formatDateTime(a.doneMs), fontSize = 12.sp, color = paper.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton("Download again", onAgain, style = PaperButtonStyle.Ghost, small = true)
            if (a.state != AreaState.REMOVED) PaperButton("Delete area", onDelete, style = PaperButtonStyle.Ghost, small = true)
        }
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
