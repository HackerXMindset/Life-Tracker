package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.MaterialTheme
import com.lifetracker.app.R
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.TallyBar
import androidx.compose.material3.Text
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
fun PhoneUsageScreen(onBack: () -> Unit, part: Part = Part.Data, vm: PhoneUsageViewModel = viewModel()) {
    val context = LocalContext.current
    val hasAccess by vm.hasAccess.collectAsState()
    val summary by vm.summary.collectAsState()
    val androidTotal by vm.androidTotalMs.collectAsState()
    val apps by vm.apps.collectAsState()
    val types by vm.activityTypes.collectAsState()
    val range by vm.range.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val lastSync by vm.lastSync.collectAsState()
    val showOnTimeline by vm.showOnTimeline.collectAsState()
    val minBlock by vm.minBlockMinutes.collectAsState()
    BackHandler(onBack = onBack)
    StartOnToday(vm::setRange)

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
        if (part == Part.Settings) {
            item { SettingsTop("Phone and usage", "App use on the Timeline", onBack) }
        } else {
            item { TextButton(onClick = onBack) { Text("Back") } }
            item { ScreenHeader(label = "Stats", badge = "On this phone", title = "Phone usage") }
        }

        if (!hasAccess && part == Part.Data) {
            item { SetupNote("Usage access is off, so there is nothing to show yet. Switch it on in the Settings tab, under Phone usage.") }
            return@LazyColumn
        }

        if (!hasAccess) {
            item {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            androidTotal?.let { androidMs ->
                item {
                    SetupNote(
                        "Check: this app counts ${formatDuration(msToMinutes(summary.totalMs))}. Android's own total for the same days is " +
                            "${formatDuration(msToMinutes(androidMs))}. Android's total reads a little higher than Digital Wellbeing, and apps you " +
                            "hid are left out of this app's number, so compare with Digital Wellbeing for the final check.",
                    )
                }
            }

            if (summary.perApp.isEmpty()) {
                item {
                    Text(
                        "Nothing copied for these days. If you just allowed access, tap Sync now. Android keeps only about a week of detail, so days from before the app first copied cannot be recovered.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(summary.perApp, key = { "a" + it.first }) { (pkg, ms) ->
                val info = byPkg[pkg]
                val activity = info?.activityId?.takeIf { it.isNotEmpty() }?.let { types.byId(it) }
                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 12.dp,
                    shadow = false,
                    onClick = if (info != null) ({ editing = info }) else null,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    info?.label ?: pkg,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        TallyBar(
                            progress = (ms.toFloat() / biggest).coerceIn(0.02f, 1f),
                            color = activity?.let { it.displayColor() } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            height = 12.dp,
                        )
                    }
                }
            }
            item {
                Text(
                    "Tap an app to link it to an activity. Time on a linked app counts as time on that activity (for example Study), on top of what you log yourself.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        }
        if (part == Part.Settings) {
            item {
                SetCard {
                    ToggleRow(
                        label = "Show app use on the Timeline",
                        checked = showOnTimeline,
                        onChange = vm::setShowOnTimeline,
                        hint = "Each stretch appears with its exact time",
                        first = true,
                    )
                }
            }
            item { PaperSection("Leave out stretches shorter than", "they stay in the totals") }
            item {
                val options = listOf(1, 2, 5, 10)
                PaperSegmented(
                    options = options.map { "$it min" },
                    selected = options.indexOf(minBlock),
                    onSelect = { vm.setMinBlockMinutes(options[it]) },
                )
            }
            item {
                Text(
                    "Stretches of the same app less than 2 minutes apart are shown as one.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hidden.isNotEmpty()) {
                item { PaperSection("Apps") }
                items(hidden, key = { "h" + it.pkg }) { app ->
                    SettingsRow(R.drawable.ic_p_eyeoff, app.label, "Hidden everywhere · tap to change", onClick = { editing = app })
                }
            }
            item {
                LockNote(
                    (if (lastSync.isEmpty()) "Not copied yet." else "Last copied $lastSync.") +
                        " Android keeps about a week of detail, so the app saves its own copy.",
                )
            }
        }
    }

    editing?.let { app ->
        AppCategoryDialog(
            app = app,
            types = types,
            onLink = { vm.link(app, it) },
            onIgnore = { vm.setIgnored(app, it) },
            onDismiss = { editing = null },
        )
    }
}
