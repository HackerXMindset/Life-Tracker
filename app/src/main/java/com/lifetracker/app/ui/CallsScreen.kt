package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperSegmented
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.lifetracker.app.data.CallStats
import com.lifetracker.app.data.CallsCollector
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WHEN_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, h:mm a")

private fun whenText(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(WHEN_FORMAT)

/** Calls: allow access, totals, the people you talked to most, and Timeline options. */
@Composable
fun CallsScreen(onBack: () -> Unit, part: Part = Part.Data, vm: CallsViewModel = viewModel()) {
    val context = LocalContext.current
    val hasAccess by vm.hasAccess.collectAsState()
    val calls by vm.calls.collectAsState()
    val range by vm.range.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val lastSync by vm.lastSync.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    val minTalk by vm.minTalkMinutes.collectAsState()
    val copyingOlder by vm.copyingOlder.collectAsState()
    val olderResult by vm.olderResult.collectAsState()
    BackHandler(onBack = onBack)
    StartOnToday(vm::setRange)

    // When you come back from Android's settings, check again.
    DisposableEffect(context) {
        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshAccess()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(hasAccess) { if (hasAccess) vm.sync() }

    val totals = CallStats.totals(calls)
    val people = CallStats.perPerson(calls)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (part == Part.Settings) {
            item { SettingsTop("Calls", "Which calls show up", onBack) }
        } else {
            item { TextButton(onClick = onBack) { Text("Back") } }
            item { ScreenHeader(label = "Stats", badge = "On this phone", title = "Calls") }
        }

        if (!hasAccess && part == Part.Data) {
            item { SetupNote("Call log access is off, so there is nothing to show yet. Allow it in the Settings tab, under Calls.") }
            return@LazyColumn
        }

        if (!hasAccess) {
            item { SetupNote("Needs call log access. Allow it in Settings > Permissions, where it also says why and how.") }
            return@LazyColumn
        }

        if (part == Part.Data) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        MonoLabel("Last copied")
                        Text(
                            text = if (lastSync.isEmpty()) "Not yet" else lastSync,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                        )
                    }
                    OutlinedButton(onClick = vm::sync, enabled = !syncing) { Text(if (syncing) "Copying..." else "Sync now") }
                }
            }
            item { RangePicker(choice = range, onChange = vm::setRange) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CallStat("Calls", totals.calls.toString(), Modifier.weight(1f))
                    CallStat("Talked", CallStats.durationText(totals.talkSec), Modifier.weight(1f))
                    CallStat("Missed", totals.missed.toString(), Modifier.weight(1f))
                }
            }

            if (calls.isEmpty()) {
                item {
                    Text(
                        "No calls on these days. If you just allowed access, tap Sync now. For older days, use Copy older calls below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { PaperSection("People") }
                items(people.take(15), key = { "p" + it.key }) { person ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                person.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                "${person.calls} call${if (person.calls == 1) "" else "s"} · last ${whenText(person.lastMs)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            CallStats.durationText(person.talkSec),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                item { PaperSection("Latest calls") }
                items(calls.sortedByDescending { it.startMs }.take(30), key = { "c" + it.id }) { call ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            CallStats.title(call),
                            fontSize = 15.sp,
                            color = if (call.type == CallStats.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            whenText(call.startMs) + " · " + CallStats.detail(call),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        }
        if (part == Part.Settings) {
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ToggleRow(
                            "Show calls on the Timeline",
                            showOnTimeline,
                            vm::setShowOnTimeline,
                            first = true,
                        )
                    }
                }
            }
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Only show calls longer than",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Shorter calls stay in Stats.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val choices = CallStats.MIN_TALK_CHOICES.toList()
                        PaperSegmented(
                            options = choices.map { "$it min" },
                            selected = choices.indexOf(minTalk),
                            onSelect = { vm.setMinTalkMinutes(choices[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LockNote(
                            "Missed calls, declined calls and calls nobody answered are never drawn on the Timeline. They still count in the Calls stats.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Older calls",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Normal syncs only look at new calls. If days before your first copy look empty, copy the whole call log once. Safe to repeat.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = vm::copyOlder,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !copyingOlder,
                        ) {
                            Text(if (copyingOlder) "Copying..." else "Copy older calls")
                        }
                        if (olderResult.isNotEmpty()) {
                            Text(olderResult, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Text(
                        "Backups include your calls, with numbers and names, as plain text. Keep the backup folder private.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CallStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MonoLabel(label)
        Text(
            text = value,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
