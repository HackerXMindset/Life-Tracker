package com.lifetracker.app.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.AccuracyMode
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.BluetoothWatch
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.ProfileCondition
import com.lifetracker.app.data.ProfileRules
import com.lifetracker.app.data.ProfileText
import com.lifetracker.app.data.TrackEngine
import com.lifetracker.app.data.TrackingProfileEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).trackingProfileDao()

    /** Every profile, hidden ones last. */
    val profiles: StateFlow<List<TrackingProfileEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(profile: TrackingProfileEntity) {
        viewModelScope.launch {
            dao.upsert(profile)
            TrackEngine.refresh()
        }
    }

    /** Hiding keeps the profile (with the time) and showing it again clears that time. */
    fun setHidden(profile: TrackingProfileEntity, hidden: Boolean) =
        save(profile.copy(deletedAt = if (hidden) System.currentTimeMillis() else 0L))
}

/** Settings > Tracking profiles: rules that change how GPS all day records while something is true. */
@Composable
fun ProfilesScreen(onBack: () -> Unit, vm: ProfilesViewModel = viewModel()) {
    val all by vm.profiles.collectAsState()
    val context = LocalContext.current
    val paper = LocalPaper.current
    var editing by remember { mutableStateOf<TrackingProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var inForce by remember { mutableStateOf(PlacesSettings(context).trackProfileId) }
    LaunchedEffect(Unit) {
        while (true) {
            inForce = PlacesSettings(context).trackProfileId
            delay(2_000L)
        }
    }

    val current = editing
    if (current != null) {
        ProfileEditor(
            initial = current,
            isNew = creating,
            onBack = { editing = null; creating = false },
            onSave = { vm.save(it); editing = null; creating = false },
            onHide = { vm.setHidden(current, true); editing = null; creating = false },
        )
    } else {
        BackHandler(onBack = onBack)
        val shown = all.filter { it.deletedAt == 0L }
        val hidden = all.filter { it.deletedAt != 0L }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SettingsTop("Tracking profiles", "Rules that change how it records", onBack) }
            item {
                Text(
                    "A profile is a rule: while something is true (charging, in the car, on a Wi-Fi, moving fast, still), GPS all day records " +
                        "with the profile's pace and accuracy instead of your own. The highest priority that holds wins. Profiles only work when " +
                        "Recording is set to GPS all day.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val name = shown.firstOrNull { it.id == inForce }?.name
                SetCard { KvRow("In force now", name ?: "None, your own settings apply", first = true) }
            }
            items(shown, key = { it.id }) { p ->
                PaperCard(modifier = Modifier.fillMaxWidth(), onClick = { editing = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.name, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = paper.ink)
                        if (p.id == inForce) Text("In force", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = paper.ink2)
                    }
                    Text(ProfileText.describe(p), fontSize = 13.sp, color = paper.ink2)
                }
            }
            item {
                PaperButton("New profile", {
                    creating = true
                    editing = TrackingProfileEntity(
                        id = "pf" + System.currentTimeMillis(),
                        name = "",
                        condition = ProfileCondition.CHARGING,
                        intervalSec = 5,
                        createdMs = System.currentTimeMillis(),
                    )
                })
            }
            if (hidden.isNotEmpty()) {
                item { PaperSection("Hidden", "kept, not deleted") }
                items(hidden, key = { "h" + it.id }) { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.name, modifier = Modifier.weight(1f), fontSize = 15.sp, color = paper.ink2)
                        PaperButton("Show again", { vm.setHidden(p, false) }, style = PaperButtonStyle.Ghost, small = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit, suffix: String = "", decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { t -> onChange(t.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(8)) },
        label = { Text(label) },
        suffix = if (suffix.isEmpty()) null else ({ Text(suffix, fontSize = 12.sp) }),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileEditor(
    initial: TrackingProfileEntity,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (TrackingProfileEntity) -> Unit,
    onHide: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var condition by remember { mutableStateOf(initial.condition) }
    var text by remember { mutableStateOf(initial.conditionText) }
    var speed by remember {
        mutableStateOf(if (initial.conditionNumber > 0.0) ProfileText.describe(initial).let { initial.conditionNumber.toString().removeSuffix(".0") } else "")
    }
    var interval by remember { mutableStateOf(initial.intervalSec.toString()) }
    var minMove by remember { mutableStateOf(initial.minMoveM.toString()) }
    var accuracy by remember { mutableStateOf(initial.accuracyMode) }
    var priority by remember { mutableStateOf(initial.priority.toString()) }
    var on by remember { mutableStateOf(initial.activateDelaySec.toString()) }
    var off by remember { mutableStateOf(initial.deactivateDelaySec.toString()) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(): TrackingProfileEntity = initial.copy(
        name = name.trim().ifEmpty { ProfileText.conditionLabel(condition) },
        condition = condition,
        conditionText = if (condition == ProfileCondition.WIFI_NAMED || condition == ProfileCondition.BLUETOOTH) text else "",
        conditionNumber = if (condition == ProfileCondition.SPEED_ABOVE || condition == ProfileCondition.SPEED_BELOW) {
            speed.replace(',', '.').toDoubleOrNull() ?: 0.0
        } else {
            0.0
        },
        intervalSec = interval.toIntOrNull() ?: 0,
        minMoveM = minMove.toIntOrNull() ?: 0,
        accuracyMode = accuracy,
        priority = priority.toIntOrNull() ?: 0,
        activateDelaySec = on.toIntOrNull() ?: 0,
        deactivateDelaySec = off.toIntOrNull() ?: 0,
        enabled = enabled,
    )

    HabitPage(
        title = if (isNew) "New profile" else "Edit profile",
        onBack = onBack,
        confirmText = "Save",
        onConfirm = {
            val p = build()
            val problem = ProfileRules.problem(p)
            if (problem != null) error = problem else onSave(p)
        },
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Text(ProfileText.describe(build()), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LocalPaper.current.ink)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            PaperSection("When")
            ProfileCondition.all.forEach { id ->
                PaperPill(ProfileText.conditionLabel(id), condition == id, { condition = id }, modifier = Modifier.fillMaxWidth())
            }
            when (condition) {
                ProfileCondition.WIFI_NAMED -> {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Wi-Fi name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Android shows a Wi-Fi's name to an app only with location allowed (Settings > Permissions). Capital letters do not matter.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProfileCondition.BLUETOOTH -> {
                    val paired = remember { BluetoothWatch.paired(context) }
                    if (!BluetoothWatch.hasPermission(context)) {
                        Text(
                            "Allow Bluetooth devices in Settings > Permissions, then come back to pick one.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (paired.isEmpty()) {
                        Text("No paired Bluetooth devices found. Pair the device in Android first.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        paired.forEach { (deviceName, address) ->
                            PaperPill(
                                deviceName.ifEmpty { address },
                                ProfileText.deviceAddress(text).equals(address, ignoreCase = true),
                                { text = ProfileText.packDevice(address, deviceName) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                ProfileCondition.SPEED_ABOVE, ProfileCondition.SPEED_BELOW -> {
                    NumField("Speed", speed, { speed = it }, "km/h", decimal = true)
                    Text("Judged on the average speed of the last 5 readings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProfileCondition.STATIONARY -> Text(
                    "Still means the phone has moved under 0.3 m per second for a full minute. A still profile always keeps every reading.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProfileCondition.CAR_MODE -> Text(
                    "The phone is in Android's car mode (a car dock or driving mode). A phone using Android Auto on a car screen does not always set it; use A Bluetooth device for such a car.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }

            PaperSection("Then record")
            NumField("Seconds between readings", interval, { interval = it }, "s")
            NumField("Keep a reading only if it moved at least", minMove, { minMove = it }, "m (0 = every reading)")
            Text("Accuracy", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PaperSegmented(
                options = listOf("Same as mine", "High", "Balanced", "Low power"),
                selected = if (accuracy.isEmpty()) 0 else AccuracyMode.all.indexOf(accuracy) + 1,
                onSelect = { accuracy = if (it == 0) "" else AccuracyMode.all[it - 1] },
            )

            PaperSection("Timing")
            NumField("Priority (the higher number wins)", priority, { priority = it })
            NumField("Switch on after the condition has held for", on, { on = it }, "s")
            NumField("Switch off after the condition has ended for", off, { off = it }, "s")
            SetCard { ToggleRow("Switched on", enabled, { enabled = it }, first = true) }

            if (!isNew) {
                PaperButton("Hide this profile", onHide, style = PaperButtonStyle.Danger)
                Text(
                    "A hidden profile is kept and can be shown again from the list.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
