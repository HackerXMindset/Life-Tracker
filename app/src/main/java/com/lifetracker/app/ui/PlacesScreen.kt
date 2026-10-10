package com.lifetracker.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.PlaceKinds
import com.lifetracker.app.data.PlaceRules
import java.time.Instant
import java.time.ZoneId

/** Places: the spots you named, the stops waiting for a name, and every visit. */
@Composable
fun PlacesScreen(onBack: () -> Unit, vm: PlacesViewModel = viewModel()) {
    val context = LocalContext.current
    val data by vm.data.collectAsState()
    val clusters by vm.clusters.collectAsState()
    val range by vm.range.collectAsState()
    val enabled by vm.enabled.collectAsState()
    val interval by vm.intervalSec.collectAsState()
    val useGps by vm.useGps.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    val access by vm.access.collectAsState()
    val status by vm.status.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlaceEntity?>(null) }
    var naming by remember { mutableStateOf<PlaceRules.Cluster?>(null) }
    BackHandler(onBack = onBack)

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val askActivity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    DisposableEffect(context) {
        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(clusters) { vm.lookUp(clusters) }

    val now = System.currentTimeMillis()
    val shownMs = data.rows.sumOf { it.ms }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { ScreenHeader(label = "Phone", badge = "On this phone", title = "Places") }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Watch my places", color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = enabled, onCheckedChange = vm::setEnabled)
            }
        }

        if (!access.all) {
            item {
                PlacesCard(
                    title = "Three permissions, one after the other",
                    text = "1. Location: " + tick(access.location) + "\n" +
                        "2. Location \"Allow all the time\": " + tick(access.background) + "\n" +
                        "3. Physical activity (notices when the phone stays still): " + tick(access.activity) + "\n\n" +
                        "Without \"all the time\", Android only tells the app about places while it is open. " +
                        "Nothing leaves the phone.",
                ) {
                    if (!access.location) {
                        Button(onClick = {
                            askLocation.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }) { Text("Allow location") }
                    } else if (!access.background) {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 29) askBackground.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }) { Text("Allow all the time") }
                    } else if (!access.activity) {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 29) askActivity.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                        }) { Text("Allow physical activity") }
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("Open this app's settings") }
                }
            }
        }

        item {
            PlacesCard(title = if (enabled) "Status" else "Status (switched off)", text = status) {}
        }

        item { MonoLabel("How closely to watch") }
        item {
            Text(
                "At a spot the app does not know, it checks your position this often until you leave. At a place you named it checks twice and then rests.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60 to "1 min", 120 to "2 min", 300 to "5 min").forEach { (sec, label) ->
                    FilterChip(selected = interval == sec, onClick = { vm.setIntervalSec(sec) }, label = { Text(label) })
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Use GPS (most exact, more battery)", color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = useGps, onCheckedChange = vm::setUseGps)
            }
        }

        if (clusters.isNotEmpty()) {
            item { MonoLabel("To review · ${clusters.size} spot${if (clusters.size == 1) "" else "s"}") }
            items(clusters, key = { it.ids.first() }) { cluster ->
                PlacesCard(
                    title = "${cluster.visits.size} stop${if (cluster.visits.size == 1) "" else "s"} · " + PlaceRules.durationText(cluster.totalMs),
                    text = (suggestions[vm.suggestionKey(cluster)]?.let { "Looks like: $it\n" } ?: "") + formatDateTime(cluster.firstMs) +
                        (if (cluster.visits.size > 1) "  to  " + formatDateTime(cluster.lastMs) else "") + "\n" +
                        PlaceRules.coordinatesText(cluster.lat, cluster.lng),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { naming = cluster }) { Text("Name it") }
                        OutlinedButton(onClick = {
                            val label = Uri.encode("Stop")
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:${cluster.lat},${cluster.lng}?q=${cluster.lat},${cluster.lng}($label)"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }) { Text("Map") }
                        TextButton(onClick = { vm.ignore(cluster) }) { Text("Not a place") }
                    }
                }
            }
        }

        item { MonoLabel("Your places") }
        if (data.rows.isEmpty()) {
            item {
                Text(
                    "No places yet. Add one (Home, Library, Gym), or stay somewhere for 5 minutes and it appears above for you to name.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(data.rows, key = { it.place.id }) { row ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { editing = row.place },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        row.place.name + if (row.place.archived) "  (not watched)" else "",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        PlaceKinds.label(row.place.kind) + " · ${row.place.radiusM} m" +
                            if (row.visits > 0) " · " + PlaceRules.durationText(row.ms) + " in ${row.visits} visit${if (row.visits == 1) "" else "s"}" else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Button(onClick = { adding = true }) { Text("Add a place") } }

        item { MonoLabel("Visits") }
        item { RangePicker(choice = range, onChange = vm::setRange) }
        item {
            Text(
                "Time at your places: " + PlaceRules.durationText(shownMs),
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (data.visits.isEmpty()) {
            item {
                Text(
                    "No visits on these days.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(data.visits.take(200), key = { it.visit.id }) { row ->
                val v = row.visit
                val zone = ZoneId.systemDefault()
                val end = if (v.ongoing) "now" else Instant.ofEpochMilli(v.endMs).atZone(zone).let { formatMinute(it.hour * 60 + it.minute) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        formatDateTime(v.startMs) + " → " + end + " · " + PlaceRules.durationText(PlaceRules.lengthMs(v, now)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { MonoLabel("On the Timeline") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show visits on the Timeline", color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = showOnTimeline, onCheckedChange = vm::setShowOnTimeline)
            }
        }
        item { MonoLabel("How it works") }
        item {
            Text(
                "Android wakes the app when you come near a place, leave one, or the phone goes still or starts moving. " +
                    "Then the app reads your position (a notification shows while it does). Staying in one spot for 5 minutes is a stay: " +
                    "inside a place's circle it is a visit to that place, anywhere else it waits above for you to name it. " +
                    "At a spot the app does not know it keeps checking at the pace you chose until you leave, and the start and end times come from when " +
                    "the phone went still and started moving. At a named place it checks twice and rests until you move. " +
                    "The circle you give a place is the real size used to decide you are there; Android's own ring around it is at least 150 m, only to wake the app. " +
                    "Names for unnamed spots are suggested by OpenStreetMap over the internet (only the position of that stop is sent, once). " +
                    "Places are never deleted: \"not watched\" only stops the watching, and \"Not a place\" only hides.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            PlacesCard(
                title = "If visits never appear (Xiaomi / HyperOS)",
                text = "Open Android Settings > Apps > this app and turn on Autostart, set Battery saver to No restrictions, " +
                    "and set Location to Allow all the time. HyperOS otherwise stops background work to save battery. " +
                    "The status line above shows when Android last told the app something, so you can tell whether it works.",
            ) {}
        }
        item {
            Text(
                "Backups include your places and visits. Nothing leaves the phone.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (adding) {
        PlaceDialog(
            title = "Add a place",
            initial = null,
            cluster = null,
            onFetchHere = vm::fetchHere,
            onSave = { name, kind, radius, lat, lng -> vm.addPlace(name, kind, radius, lat, lng) },
            onArchiveToggle = null,
            onDismiss = { adding = false },
        )
    }
    naming?.let { cluster ->
        PlaceDialog(
            title = "Name this spot",
            initial = null,
            cluster = cluster,
            suggestion = suggestions[vm.suggestionKey(cluster)],
            onFetchHere = vm::fetchHere,
            onSave = { name, kind, radius, lat, lng -> vm.addPlace(name, kind, radius, lat, lng) },
            onArchiveToggle = null,
            onDismiss = { naming = null },
        )
    }
    editing?.let { place ->
        PlaceDialog(
            title = "Edit place",
            initial = place,
            cluster = null,
            onFetchHere = vm::fetchHere,
            onSave = { name, kind, radius, lat, lng ->
                vm.updatePlace(place.copy(name = name.trim(), kind = kind, radiusM = radius, lat = lat, lng = lng))
            },
            onArchiveToggle = { vm.updatePlace(place.copy(archived = !place.archived)) },
            onDismiss = { editing = null },
        )
    }
}

private fun tick(done: Boolean) = if (done) "done ✓" else "not yet"

@Composable
private fun PlaceDialog(
    title: String,
    initial: PlaceEntity?,
    cluster: PlaceRules.Cluster?,
    suggestion: String? = null,
    onFetchHere: ((com.lifetracker.app.data.Fix?) -> Unit) -> Unit,
    onSave: (String, String, Int, Double, Double) -> Unit,
    onArchiveToggle: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: suggestion ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: "other") }
    var radius by remember { mutableStateOf(initial?.radiusM ?: PlaceRules.DEFAULT_RADIUS) }
    var coords by remember {
        mutableStateOf(
            when {
                initial != null -> PlaceRules.coordinatesText(initial.lat, initial.lng)
                cluster != null -> PlaceRules.coordinatesText(cluster.lat, cluster.lng)
                else -> ""
            },
        )
    }
    var fetching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val parsed = PlaceRules.parseCoordinates(coords)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlaceKinds.list.forEach { k ->
                        FilterChip(selected = kind == k.id, onClick = { kind = k.id }, label = { Text(k.label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceRules.RADII.forEach { r ->
                        FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text("$r m") })
                    }
                }
                OutlinedTextField(
                    value = coords,
                    onValueChange = { coords = it },
                    label = { Text("Position (latitude, longitude)") },
                    singleLine = true,
                    isError = coords.isNotBlank() && parsed == null,
                )
                OutlinedButton(
                    enabled = !fetching,
                    onClick = {
                        fetching = true
                        message = ""
                        onFetchHere { fix ->
                            fetching = false
                            if (fix == null) {
                                message = "Could not read the position. Check that location is on and allowed."
                            } else {
                                coords = PlaceRules.coordinatesText(fix.lat, fix.lng)
                            }
                        }
                    },
                ) { Text(if (fetching) "Reading position..." else "Use where I am now") }
                Text(
                    message.ifEmpty {
                        "Tip: in Google Maps, press and hold a spot and copy the numbers at the top, then paste them here. " +
                            "The circle is the real size: 30 to 50 m suits a room or a shop, 100 to 150 m a campus."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onArchiveToggle != null) {
                    TextButton(onClick = {
                        onArchiveToggle()
                        onDismiss()
                    }) { Text(if (initial?.archived == true) "Watch this place again" else "Stop watching this place") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && parsed != null,
                onClick = {
                    parsed?.let { onSave(name, kind, radius, it.first, it.second) }
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlacesCard(title: String, text: String, actions: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}
