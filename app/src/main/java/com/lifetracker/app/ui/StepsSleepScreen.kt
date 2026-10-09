package com.lifetracker.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.HealthConnectSync
import com.lifetracker.app.data.SleepNightEntity
import com.lifetracker.app.data.SleepStats
import com.lifetracker.app.data.StepStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun dayText(date: String): String = runCatching { LocalDate.parse(date).format(DAY_FORMAT) }.getOrDefault(date)

private fun minuteOf(ms: Long): Int =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }

/** A night being edited: [startMinute] and [endMinute] are minutes after midnight, and you woke up on [date]. */
private class NightEdit(val date: LocalDate, val startMinute: Int, val endMinute: Int, val existing: SleepNightEntity?)

/** Steps and sleep: connect Health Connect, see steps by day, see and fix each night's sleep. */
@Composable
fun StepsSleepScreen(onBack: () -> Unit, vm: StepsSleepViewModel = viewModel()) {
    val context = LocalContext.current
    val hc by vm.hcState.collectAsState()
    val days by vm.days.collectAsState()
    val nights by vm.nights.collectAsState()
    val range by vm.range.collectAsState()
    val useCounter by vm.useStepCounter.collectAsState()
    val counterPermission by vm.counterPermission.collectAsState()
    val estimate by vm.estimateSleep.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val lastSync by vm.lastSync.collectAsState()
    var editing by remember { mutableStateOf<NightEdit?>(null) }
    BackHandler(onBack = onBack)

    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refresh()
        vm.sync()
    }
    val askActivity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refresh()
        if (granted) vm.setUseStepCounter(true)
    }

    DisposableEffect(context) {
        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val stepTotals = StepStats.totals(days)
    val avgSleep = SleepStats.averageMinutes(nights)

    fun openSettings() {
        val intents = listOf(
            Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
            Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        for (i in intents) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) break
        }
    }

    fun pickTime(initialMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initialMinute / 60, initialMinute % 60, false).show()
    }

    fun addNight() {
        val today = LocalDate.now()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val date = LocalDate.of(y, m + 1, d)
                val existing = nights.firstOrNull { it.date == date.toString() }
                editing = NightEdit(
                    date,
                    existing?.let { minuteOf(it.startMs) } ?: 23 * 60,
                    existing?.let { minuteOf(it.endMs) } ?: 7 * 60,
                    existing,
                )
            },
            today.year, today.monthValue - 1, today.dayOfMonth,
        ).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { ScreenHeader(label = "Phone", badge = "On this phone", title = "Steps and sleep") }

        item {
            when (hc) {
                HcState.Checking -> Unit
                HcState.Connected -> HealthCard(
                    title = "Health Connect is connected",
                    text = "Steps come from Health Connect, which on Android 14 and newer counts your phone's steps itself. " +
                        "It only started counting when an app was first allowed to read steps, so earlier days stay empty unless another app saved them there.",
                ) {
                    TextButton(onClick = ::openSettings) { Text("Open Health Connect") }
                }
                HcState.NeedsPermission -> HealthCard(
                    title = "Allow Health Connect",
                    text = "The app reads your steps and any sleep other apps saved there, and keeps its own copy on this phone. Nothing leaves the phone.",
                ) {
                    Button(onClick = { askHealth.launch(HealthConnectSync.REQUEST) }) { Text("Allow") }
                    TextButton(onClick = ::openSettings) { Text("Open Health Connect settings") }
                }
                HcState.NeedsUpdate -> HealthCard(
                    title = "Update Health Connect",
                    text = "Health Connect needs an update from the Play Store before the app can read it.",
                ) {
                    Button(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthConnectSync.PACKAGE}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }) { Text("Open Play Store") }
                }
                HcState.Unavailable -> HealthCard(
                    title = "Health Connect is not available",
                    text = "This phone does not have Health Connect. Switch on the phone's step counter below to count steps.",
                ) {}
            }
        }

        if (vm.hasCounterSensor) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Use the phone's step counter", color = MaterialTheme.colorScheme.onBackground)
                        Switch(
                            checked = useCounter && counterPermission,
                            onCheckedChange = { on ->
                                if (!on) vm.setUseStepCounter(false)
                                else if (counterPermission) vm.setUseStepCounter(true)
                                else askActivity.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                            },
                        )
                    }
                    Text(
                        "Only used on days Health Connect has no steps. Counts from when you switch it on, in readings taken whenever the app syncs, " +
                            "so steps are filed under the day of the reading. Asks for the \"Physical activity\" permission.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    MonoLabel("Last updated")
                    Text(
                        text = if (lastSync.isEmpty()) "Not yet" else lastSync,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }
                OutlinedButton(onClick = vm::sync, enabled = !syncing) { Text(if (syncing) "Updating..." else "Update now") }
            }
        }

        item { RangePicker(choice = range, onChange = vm::setRange) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HealthStat("Steps", StepStats.text(stepTotals.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), Modifier.weight(1f))
                HealthStat("Per day", StepStats.text(stepTotals.average), Modifier.weight(1f))
                HealthStat("Sleep per night", if (avgSleep == 0) "–" else formatDuration(avgSleep), Modifier.weight(1f))
            }
        }

        item { MonoLabel("Sleep · tap a night to fix it") }
        if (nights.none { it.source != SleepStats.SKIPPED }) {
            item {
                Text(
                    "No sleep found on these days. It is worked out from when you stop using the phone at night, so it needs Phone usage switched on, " +
                        "and it only covers days the app has copied. You can add a night yourself.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(nights.filter { it.source != SleepStats.SKIPPED }, key = { "n" + it.date }) { night ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        editing = NightEdit(LocalDate.parse(night.date), minuteOf(night.startMs), minuteOf(night.endMs), night)
                    },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        dayText(night.date),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        formatDuration(SleepStats.minutes(night)),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    formatMinute(minuteOf(night.startMs)) + " → " + formatMinute(minuteOf(night.endMs)) + " · " + SleepStats.label(night.source),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { OutlinedButton(onClick = ::addNight) { Text("Add or fix a night") } }

        item { MonoLabel("Steps by day") }
        if (days.none { it.steps > 0 }) {
            item {
                Text(
                    "No steps on these days yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(days.filter { it.steps > 0 }.take(120), key = { "d" + it.date }) { day ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dayText(day.date), color = MaterialTheme.colorScheme.onBackground)
                Text(
                    StepStats.text(day.steps),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        item { MonoLabel("Settings") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Work out sleep from phone use", color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = estimate, onCheckedChange = vm::setEstimateSleep)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show on the Timeline", color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = showOnTimeline, onCheckedChange = vm::setShowOnTimeline)
            }
        }
        item {
            Text(
                "The sleep estimate is the longest stretch of 3 to 14 hours at night with no phone use, ignoring quick checks under 5 minutes between 12:30 and 6 am. " +
                    "Reading in bed without touching the phone looks like sleep, so fix any night that is off. A night you edit is never overwritten. " +
                    "On the Timeline, sleep counts towards your Sleep goal on days you did not log Sleep yourself. " +
                    "Steps and sleep are included in backups and only ever added to.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { edit ->
        var startMinute by remember(edit) { mutableStateOf(edit.startMinute) }
        var endMinute by remember(edit) { mutableStateOf(edit.endMinute) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Sleep, " + dayText(edit.date.toString())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You woke up on this day. If you fell asleep at a later clock time than you woke, it counts as the evening before.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { pickTime(startMinute) { startMinute = it } }) {
                        Text("Fell asleep  " + formatMinute(startMinute))
                    }
                    OutlinedButton(onClick = { pickTime(endMinute) { endMinute = it } }) {
                        Text("Woke up  " + formatMinute(endMinute))
                    }
                    if (edit.existing != null) {
                        TextButton(onClick = {
                            vm.skipNight(edit.existing)
                            editing = null
                        }) { Text("This was not sleep") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = startMinute != endMinute,
                    onClick = {
                        if (vm.saveNight(edit.date, startMinute, endMinute)) editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HealthCard(title: String, text: String, actions: @Composable () -> Unit) {
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

@Composable
private fun HealthStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MonoLabel(label)
        Text(
            text = value,
            fontSize = 17.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
