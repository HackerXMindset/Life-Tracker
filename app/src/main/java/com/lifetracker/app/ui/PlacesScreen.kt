package com.lifetracker.app.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperSegmented
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.PlaceEntity
import androidx.compose.material3.Switch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.lifetracker.app.data.PauseSettings
import com.lifetracker.app.data.PauseText
import com.lifetracker.app.data.SystemSituation
import com.lifetracker.app.data.PlaceKinds
import com.lifetracker.app.data.PlaceRules

/** Places: the spots you named, the stops waiting for a name, and every visit. */
@Composable
fun PlacesScreen(onBack: () -> Unit, onOpen: (Page) -> Unit, onOpenPlace: (String) -> Unit, vm: PlacesViewModel = viewModel()) {
    val context = LocalContext.current
    val places by vm.places.collectAsState()
    val placeCategories by vm.categories.collectAsState()
    val recordTrips by vm.recordTrips.collectAsState()
    val heartbeat by vm.heartbeat.collectAsState()
    val heightCm by vm.heightCm.collectAsState()
    val strideM by vm.strideM.collectAsState()
    val strideLearned by vm.strideLearned.collectAsState()
    var heightText by remember(heightCm) { mutableStateOf(if (heightCm > 0) heightCm.toString() else "") }
    val tripPace by vm.tripIntervalSec.collectAsState()
    val showTripsOnTimeline by vm.showTripsOnTimeline.collectAsState()
    val enabled by vm.enabled.collectAsState()
    val interval by vm.intervalSec.collectAsState()
    val useGps by vm.useGps.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    val access by vm.access.collectAsState()
    val status by vm.status.collectAsState()
    val alwaysOn by vm.alwaysOn.collectAsState()
    val recording by vm.recording.collectAsState()
    val alwaysOnInterval by vm.alwaysOnIntervalSec.collectAsState()
    val readings by vm.readings.collectAsState()
    val rebuilding by vm.rebuilding.collectAsState()
    var adding by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)


    DisposableEffect(context) {
        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }


    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("Places and trips", "Permissions and recording", onBack) }

        item {
            SetCard {
                ToggleRow("Watch my places", enabled, vm::setEnabled, hint = "Needs location all the time, set once", first = true)
            }
        }

        recordingItems(recording, vm)

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

        item { PaperSection("Changes") }
        item {
            SettingsRow(R.drawable.ic_p_route, "Changes", "Trips and stays you joined, cut or hid, and the way back") { onOpen(Page.Changes) }
        }
        item { PaperSection("Maps") }
        item {
            SettingsRow(R.drawable.ic_p_route, "Maps", "The map style for trips and places, and your own styles") { onOpen(Page.Maps) }
        }
        item { PaperSection("Profiles") }
        item {
            SettingsRow(R.drawable.ic_p_pin, "Tracking profiles", "Change how GPS all day records while charging, driving, on a Wi-Fi...") { onOpen(Page.Profiles) }
        }

        item { PaperSection("Places") }
        item {
            SettingsRow(R.drawable.ic_p_pin, "Place categories", "Colour and icon for Home, Study, Gym and your own") { onOpen(Page.PlaceCategories) }
        }
        if (places.isEmpty()) {
            item {
                Text(
                    "No places yet. Add one (Home, Library, Gym), or stay somewhere for 3 minutes and it appears on the Location page for you to name.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(places, key = { it.id }) { place ->
                SettingsRow(
                    icon = null,
                    title = place.name + if (place.archived) "  (not watched)" else "",
                    hint = "${place.radiusM} m circle · " + (if (place.pauseHere) "pause zone · " else "") + (placeCategories.firstOrNull { it.id == place.kind }?.name ?: PlaceKinds.label(place.kind)),
                    onClick = { onOpenPlace(place.id) },
                )
            }
        }
        item { Button(onClick = { adding = true }) { Text("Add a place") } }

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
                ToggleRow("Check once every 15 minutes", heartbeat, vm::setHeartbeat, hint = "A safety net: one position now and then, so a stop is not missed")
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
                "Recording has two ways. Gentle wakes the app when you come near a place, leave one, or the phone goes still or starts moving, " +
                    "and reads your position only then. GPS all day reads your position all day at the pace you set (a notification stays) " +
                    "and keeps every good reading, so the path is exact; tracking profiles change the pace while something is true, " +
                    "and a pause zone at a place stops the readings there. " +
                    "Staying in one spot for 3 minutes is a stay: inside a place's circle it is a visit to that place, anywhere else it waits " +
                    "for you to name it in the Location page. A trip is the way between two stays. On a trip's page you can join it to a neighbour, " +
                    "cut it in two, write a note on one reading, or hide it, and every change can be undone. " +
                    "The circle you give a place is the real size used to decide you are there; Android's own ring around it is at least 150 m, only to wake the app. " +
                    "Names for unnamed spots are suggested by OpenStreetMap over the internet (only the position of that stop is sent, once), " +
                    "and map pictures are fetched from the map style you chose when you view a map or download an offline area. " +
                    "Your readings, places and trips never leave the phone except when you export or back them up. " +
                    "Nothing is deleted: \"not watched\" only stops the watching, and hiding keeps what you hid. Backups include your places, visits, trips and readings.",
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
            categories = placeCategories,
            onFetchHere = vm::fetchHere,
            onSave = { name, kind, radius, lat, lng, pause -> vm.addPlace(name, kind, radius, lat, lng, pause) },
            onArchiveToggle = null,
            onDismiss = { adding = false },
        )
    }
}

private fun tick(done: Boolean) = if (done) "done ✓" else "not yet"

@Composable
internal fun PlaceDialog(
    title: String,
    initial: PlaceEntity?,
    cluster: PlaceRules.Cluster?,
    suggestion: String? = null,
    categories: List<com.lifetracker.app.data.PlaceCategoryEntity>,
    onFetchHere: ((com.lifetracker.app.data.Fix?) -> Unit) -> Unit,
    onSave: (String, String, Int, Double, Double, PauseSettings) -> Unit,
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
    val context = LocalContext.current
    val start = remember { initial?.let { PauseSettings.of(it) } ?: PauseSettings() }
    var pauseHere by remember { mutableStateOf(start.pauseHere) }
    var wifi by remember { mutableStateOf(start.wifi) }
    var motionless by remember { mutableStateOf(start.motionless) }
    var heartbeat by remember { mutableStateOf(start.heartbeatMin.toString()) }
    var ownDelay by remember { mutableStateOf(start.entryDelaySec >= 0) }
    var delay by remember { mutableStateOf(if (start.entryDelaySec >= 0) start.entryDelaySec.toString() else "30") }
    val pause = PauseSettings(
        pauseHere, wifi, motionless,
        heartbeat.toIntOrNull() ?: -1,
        if (ownDelay) (delay.toIntOrNull() ?: -2) else -1,
    )
    val pauseProblem = PauseText.problem(pause)
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        MapPickDialog(
            startLat = parsed?.first,
            startLng = parsed?.second,
            onPicked = { lat, lng ->
                coords = PlaceRules.coordinatesText(lat, lng)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedButton(onClick = { picking = true }) { Text("Pick on map") }
                Text(
                    message.ifEmpty {
                        "Tip: in Google Maps, press and hold a spot and copy the numbers at the top, then paste them here. " +
                            "The circle is the real size: 30 to 50 m suits a room or a shop, 100 to 150 m a campus."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Pause zone", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pause recording here", modifier = Modifier.weight(1f))
                    Switch(checked = pauseHere, onCheckedChange = { pauseHere = it })
                }
                if (pauseHere) {
                    OutlinedTextField(
                        value = wifi,
                        onValueChange = { wifi = it },
                        label = { Text("Pause on these Wi-Fi names (one per line)") },
                        minLines = 2,
                    )
                    OutlinedButton(onClick = {
                        val ssid = SystemSituation.wifi(context).second
                        if (ssid == null) {
                            message = "Could not see a Wi-Fi name. Connect to the Wi-Fi and allow location (Settings > Permissions)."
                        } else {
                            wifi = PauseText.addWifi(wifi, ssid)
                            message = ""
                        }
                    }) { Text("Add the Wi-Fi I am on") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pause when motionless", modifier = Modifier.weight(1f))
                        Switch(checked = motionless, onCheckedChange = { motionless = it })
                    }
                    OutlinedTextField(
                        value = heartbeat,
                        onValueChange = { heartbeat = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Heartbeat reading every (minutes, 0 = none)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !ownDelay, onClick = { ownDelay = false }, label = { Text("Automatic delay") })
                        FilterChip(selected = ownDelay, onClick = { ownDelay = true }, label = { Text("My own delay") })
                    }
                    if (ownDelay) {
                        OutlinedTextField(
                            value = delay,
                            onValueChange = { delay = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("Keep recording after arriving (seconds)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
                Text(
                    pauseProblem ?: PauseText.describe(pause),
                    fontSize = 12.sp,
                    color = if (pauseProblem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                enabled = name.isNotBlank() && parsed != null && pauseProblem == null,
                onClick = {
                    parsed?.let { onSave(name, kind, radius, it.first, it.second, PauseText.clean(pause)) }
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
