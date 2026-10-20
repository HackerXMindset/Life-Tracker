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
import androidx.compose.material3.MaterialTheme
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperSegmented
import androidx.compose.material3.Text
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
import com.lifetracker.app.data.KeepAlive
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.PlaceKinds
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.PlaceTrail
import com.lifetracker.app.data.TripRules
import java.time.Instant
import java.time.ZoneId

/** Places: the spots you named, the stops waiting for a name, and every visit. */
@Composable
fun PlacesScreen(onBack: () -> Unit, part: Part = Part.Data, vm: PlacesViewModel = viewModel()) {
    val context = LocalContext.current
    val data by vm.data.collectAsState()
    val placeCategories by vm.categories.collectAsState()
    val clusters by vm.clusters.collectAsState()
    val range by vm.range.collectAsState()
    val trail by vm.trail.collectAsState()
    val recordTrips by vm.recordTrips.collectAsState()
    val heightCm by vm.heightCm.collectAsState()
    val strideM by vm.strideM.collectAsState()
    val strideLearned by vm.strideLearned.collectAsState()
    var heightText by remember(heightCm) { mutableStateOf(if (heightCm > 0) heightCm.toString() else "") }
    val tripPace by vm.tripIntervalSec.collectAsState()
    val showTripsOnTimeline by vm.showTripsOnTimeline.collectAsState()
    val enabled by vm.enabled.collectAsState()
    val interval by vm.intervalSec.collectAsState()
    val useGps by vm.useGps.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    val access by vm.access.collectAsState()
    val status by vm.status.collectAsState()
    val alwaysOn by vm.alwaysOn.collectAsState()
    val alwaysOnInterval by vm.alwaysOnIntervalSec.collectAsState()
    val readings by vm.readings.collectAsState()
    val rebuilding by vm.rebuilding.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlaceEntity?>(null) }
    var naming by remember { mutableStateOf<PlaceRules.Cluster?>(null) }
    BackHandler(onBack = onBack)
    StartOnToday(vm::setRange)


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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (part == Part.Settings) {
            item { SettingsTop("Places and trips", "Permissions and recording", onBack) }
        } else {
            item { TextButton(onClick = onBack) { Text("Back") } }
            item { ScreenHeader(label = "Stats", badge = "On this phone", title = "Places") }
        }

        if (part == Part.Data) {
            if (!enabled) {
                item { SetupNote("Place watching is switched off. Turn it on in the Settings tab, under Places and trips.") }
            }

            if (clusters.isNotEmpty()) {
                item { PaperSection("To review · ${clusters.size} spot${if (clusters.size == 1) "" else "s"}") }
                items(clusters, key = { it.ids.first() }) { cluster ->
                    PlacesCard(
                        title = "${cluster.visits.size} stop${if (cluster.visits.size == 1) "" else "s"} · " + PlaceRules.durationText(cluster.totalMs),
                        text = (suggestions[vm.suggestionKey(cluster)]?.let { "Looks like: $it\n" } ?: "") + formatDateTime(cluster.firstMs) +
                            (if (cluster.visits.size > 1) "  to  " + formatDateTime(cluster.lastMs) else "") + "\n" +
                            PlaceRules.coordinatesText(cluster.lat, cluster.lng),
                    ) {
                        val hint = suggestions[vm.suggestionKey(cluster)]
                        if (hint != null) {
                            Button(
                                onClick = { vm.addPlace(hint, "other", PlaceRules.DEFAULT_RADIUS, cluster.lat, cluster.lng) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Use \"$hint\"") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hint != null) OutlinedButton(onClick = { naming = cluster }) { Text("Rename") } else Button(onClick = { naming = cluster }) { Text("Name it") }
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

            item { RangePicker(choice = range, onChange = vm::setRange) }
            item {
                val journeys = trail.filterIsInstance<PlaceTrail.Journey>()
                val real = journeys.mapNotNull { it.trip }
                val stayMs = trail.filterIsInstance<PlaceTrail.Stay>().sumOf { it.endMs - it.startMs }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "At places " + PlaceRules.durationText(stayMs),
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Travel " + TripRules.distanceText(real.sumOf { it.distanceM.toLong() }.toInt()) + " in " +
                            PlaceRules.durationText(real.sumOf { it.endMs - it.startMs }) +
                            " · ${journeys.size} journey${if (journeys.size == 1) "" else "s"}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trail.isEmpty()) {
                item {
                    Text(
                        "No places or journeys on these days. Stay somewhere for 3 minutes and it appears here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(trail.take(300), key = { it.key }) { row ->
                    when (row) {
                        is PlaceTrail.Stay -> StayCard(row, now)
                        is PlaceTrail.Journey -> JourneyCard(row, onMode = vm::setJourneyMode)
                    }
                }
            }
        }

        if (part == Part.Settings) {
            item {
                SetCard {
                    ToggleRow("Watch my places", enabled, vm::setEnabled, hint = "Needs location all the time, set once", first = true)
                }
            }

            if (!access.all || !access.battery) {
                item {
                    SetupNote(
                        "Needs location all the time, physical activity and unrestricted battery" +
                            " (location " + tick(access.location) + ", all the time " + tick(access.background) +
                            ", activity " + tick(access.activity) + ", battery " + tick(access.battery) + ")." +
                            " Allow them in Settings > Permissions, where each says why and how.",
                    )
                }
            }

            item { PaperSection("How closely to watch") }
            item {
                Text(
                    "At a spot the app does not know, it checks your position this often until you leave. At a place you named it checks twice and then rests.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val options = listOf(60, 120, 300)
                PaperSegmented(
                    options = listOf("1 min", "2 min", "5 min"),
                    selected = options.indexOf(interval),
                    onSelect = { vm.setIntervalSec(options[it]) },
                )
            }
            item {
                SetCard {
                    ToggleRow("Use GPS", useGps, vm::setUseGps, hint = "Most exact, more battery", first = true)
                    ToggleRow("Always on", alwaysOn, vm::setAlwaysOn, hint = "More complete, more battery")
                }
            }
            if (alwaysOn) {
                item {
                    Text(
                        "Reads your position all the time, using Wi-Fi and mobile network while you stand still and GPS while you move. A notification shows all the time. How often while standing still:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    val options = listOf(60, 120, 300)
                    PaperSegmented(
                        options = listOf("1 min", "2 min", "5 min"),
                        selected = options.indexOf(alwaysOnInterval),
                        onSelect = { vm.setAlwaysOnIntervalSec(options[it]) },
                    )
                }
            }

            item { PaperSection("Record my trips", "how often to check") }
            item { SetCard { ToggleRow("Record my trips", recordTrips, vm::setRecordTrips, first = true) } }
            item {
                Text(
                    "While you are on the way, the app reads your position this often. Quicker is more exact for speed and route, and uses more battery. Trips need place watching to be switched on.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val options = listOf(15, 30, 60)
                PaperSegmented(
                    options = listOf("15 s", "30 s", "1 min"),
                    selected = options.indexOf(tripPace),
                    onSelect = { vm.setTripIntervalSec(options[it]) },
                )
            }

            item { PaperSection("Walking", "step length for walks") }
            item {
                Text(
                    "For a walk, the app counts your steps and trusts them over the position readings when the readings wander. Your step length starts at your height times 0.415 and is learned from your own walks.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { text -> heightText = text.filter { it.isDigit() }.take(3) },
                    label = { Text("Your height in cm") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            }
            item {
                SetCard {
                    KvRow("Step length in use", String.format(java.util.Locale.ENGLISH, "%.2f m", strideM) + if (strideLearned) " (learned)" else "", first = true)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setHeightCm(heightText.toIntOrNull() ?: 0) }) { Text("Save height") }
                    OutlinedButton(enabled = strideLearned, onClick = vm::resetStride) { Text("Learn again") }
                }
            }

            item { PaperSection("On the Timeline") }
            item {
                SetCard {
                    ToggleRow("Show visits on the Timeline", showOnTimeline, vm::setShowOnTimeline, first = true)
                    ToggleRow("Show trips on the Timeline", showTripsOnTimeline, vm::setShowTripsOnTimeline)
                }
            }

            item { PaperSection("Your places") }
            if (data.rows.isEmpty()) {
                item {
                    Text(
                        "No places yet. Add one (Home, Library, Gym), or stay somewhere for 3 minutes and it appears in the Places stats for you to name.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(data.rows, key = { it.place.id }) { row ->
                    SettingsRow(
                        icon = null,
                        title = row.place.name + if (row.place.archived) "  (not watched)" else "",
                        hint = "${row.place.radiusM} m circle · " + (placeCategories.firstOrNull { it.id == row.place.kind }?.name ?: PlaceKinds.label(row.place.kind)),
                        onClick = { editing = row.place },
                    )
                }
            }
            item { Button(onClick = { adding = true }) { Text("Add a place") } }

            item { PaperSection("Readings") }
            item {
                PlacesCard(
                    title = "${readings.total} readings stored",
                    text = "Every position reading is kept for good, so visits and trips can be worked out again later.\n\n" +
                        "Readings in each of the last 24 hours (oldest first):\n" + readings.hourly.joinToString(" ") + "\n\n" + readings.gapText,
                ) {
                    OutlinedButton(enabled = !rebuilding, onClick = vm::rebuildAll) {
                        Text(if (rebuilding) "Tidying..." else "Tidy everything from my readings")
                    }
                }
            }
            item { PaperSection("Status") }
            item {
                PlacesCard(title = if (enabled) "Status" else "Status (switched off)", text = status) {}
            }
            item {
                PlacesCard(
                    title = "If visits never appear",
                    text = "Open Android Settings > Apps > this app, set Battery to No restrictions, " +
                        "and set Location to Allow all the time. Android otherwise stops background work to save battery. " +
                        "The status above shows when Android last told the app something, so you can tell whether it works.",
                ) {}
            }

            item { PaperSection("How it works") }
            item {
                Text(
                    "Android wakes the app when you come near a place, leave one, or the phone goes still or starts moving. " +
                        "Then the app reads your position (a notification shows while it does). Staying in one spot for 3 minutes is a stay: " +
                        "inside a place's circle it is a visit to that place, anywhere else it waits for you to name it. " +
                        "A trip is the way between two stays. If the phone has no record of how you got from one place to another, the Places list " +
                        "shows an unknown journey with the straight-line distance, and you can say how you went. " +
                        "The circle you give a place is the real size used to decide you are there; Android's own ring around it is at least 150 m, only to wake the app. " +
                        "Names for unnamed spots are suggested by OpenStreetMap over the internet (only the position of that stop is sent, once). " +
                        "Places, trips and readings are never deleted: \"not watched\" only stops the watching, and \"Not a place\" only hides. " +
                        "Backups include your places, visits and trips. Nothing leaves the phone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (adding) {
        PlaceDialog(
            title = "Add a place",
            initial = null,
            cluster = null,
            categories = placeCategories,
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
            categories = placeCategories,
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
            categories = placeCategories,
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
    categories: List<com.lifetracker.app.data.PlaceCategoryEntity>,
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
                    categories.filter { !it.archived || it.id == kind }.forEach { k ->
                        FilterChip(selected = kind == k.id, onClick = { kind = k.id }, label = { Text(k.name) })
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
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}
