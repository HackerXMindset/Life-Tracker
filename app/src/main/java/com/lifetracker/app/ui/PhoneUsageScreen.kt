package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import android.content.ContextWrapper
import com.lifetracker.app.data.ActivityStats
import com.lifetracker.app.data.UsageAppEntity
import com.lifetracker.app.data.UsageCollector

internal fun Context.findLifecycleOwner(): LifecycleOwner? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}

private fun msToMinutes(ms: Long): Int = (ms / 60_000L).toInt()

/** Settings and totals for app usage: allow access, sync, link apps to activities, choose what the Timeline shows. */
@Composable
fun PhoneUsageScreen(onBack: () -> Unit, vm: PhoneUsageViewModel = viewModel()) {
    val context = LocalContext.current
    val hasAccess by vm.hasAccess.collectAsState()
    val summary by vm.summary.collectAsState()
    val apps by vm.apps.collectAsState()
    val types by vm.activityTypes.collectAsState()
    val range by vm.range.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val lastSync by vm.lastSync.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    val minBlock by vm.minBlockMinutes.collectAsState()
    BackHandler(onBack = onBack)

    // When you come back from Android's settings, check again, and copy the history if access was just allowed.
    DisposableEffect(context) {
        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshAccess()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    // Opening the screen with access already on also brings the history up to date.
    androidx.compose.runtime.LaunchedEffect(hasAccess) { if (hasAccess) vm.sync() }

    var editing by remember { mutableStateOf<UsageAppEntity?>(null) }
    val byPkg = apps.associateBy { it.pkg }
    val biggest = summary.perApp.firstOrNull()?.second ?: 1L
    val hidden = apps.filter { it.ignored }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { ScreenHeader(label = "Phone", badge = "On this phone", title = "Phone usage") }

        if (!hasAccess) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Allow usage access",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Android keeps a record of which app was on screen and for how long. This is where Digital Wellbeing gets its numbers. " +
                                "The app copies that record into its own database on your phone, so you can see exact times on the Timeline. Nothing leaves the phone.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Tap the button, find Life Tracker in the list, and switch it on. Then come back.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { UsageCollector.openAccessSettings(context) }) { Text("Open usage access settings") }
                    }
                }
            }
            return@LazyColumn
        }

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
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }
                OutlinedButton(onClick = vm::sync, enabled = !syncing) { Text(if (syncing) "Copying..." else "Sync now") }
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UsageRange.entries.forEach { r ->
                    FilterChip(selected = range == r, onClick = { vm.setRange(r) }, label = { Text(r.label) })
                }
            }
        }
        item {
            Column {
                MonoLabel("Total on your phone")
                Text(
                    text = formatDuration(msToMinutes(summary.totalMs)),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (summary.perApp.isEmpty()) {
            item {
                Text(
                    "Nothing copied yet for this period. If you just allowed access, tap Sync now.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(summary.perApp, key = { "a" + it.first }) { (pkg, ms) ->
            val info = byPkg[pkg]
            val activity = info?.activityId?.takeIf { it.isNotEmpty() }?.let { types.byId(it) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = info != null) { editing = info },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            info?.label ?: pkg,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = if (activity != null) "Counts as ${activity.name}" else "Not linked to an activity",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatDuration(msToMinutes(ms)),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((ms.toFloat() / biggest).coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(activity?.let { it.displayColor() } ?: MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
        item {
            Text(
                "Tap an app to link it to an activity. Time on a linked app counts towards that activity's daily goal, on top of what you log yourself.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { MonoLabel("On the Timeline") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show app use on the Timeline", color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = showOnTimeline, onCheckedChange = vm::setShowOnTimeline)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Leave out stretches shorter than", color = MaterialTheme.colorScheme.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 5, 10).forEach { m ->
                        FilterChip(selected = minBlock == m, onClick = { vm.setMinBlockMinutes(m) }, label = { Text("$m min") })
                    }
                }
                Text(
                    "Short stretches still count in the totals. Stretches of the same app less than 2 minutes apart are shown as one.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (hidden.isNotEmpty()) {
            item { MonoLabel("Hidden apps") }
            items(hidden, key = { "h" + it.pkg }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { editing = app },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                    Text(app.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    editing?.let { app ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MonoLabel("Counts as")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = app.activityId.isEmpty(),
                            onClick = { vm.link(app, ""); editing = null },
                            label = { Text("None") },
                        )
                        types.filter { !it.archived }.forEach { t ->
                            FilterChip(
                                selected = app.activityId == t.id,
                                onClick = { vm.link(app, t.id); editing = null },
                                label = { Text(t.name) },
                                leadingIcon = {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(t.displayColor()))
                                },
                            )
                        }
                    }
                    TextButton(onClick = { vm.setIgnored(app, !app.ignored); editing = null }) {
                        Text(if (app.ignored) "Show this app again" else "Hide this app everywhere")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Close") } },
        )
    }
}
