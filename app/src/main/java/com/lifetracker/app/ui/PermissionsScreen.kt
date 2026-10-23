package com.lifetracker.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifetracker.app.data.CallsCollector
import com.lifetracker.app.data.ChargeNotifier
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.DataSources.Slot
import com.lifetracker.app.data.HabitReminderScheduler
import com.lifetracker.app.data.HealthConnectSync
import com.lifetracker.app.data.KeepAlive
import com.lifetracker.app.data.Perm
import com.lifetracker.app.data.PermText
import com.lifetracker.app.data.PermissionGuide
import com.lifetracker.app.data.PlacesTracker
import com.lifetracker.app.data.StepCounterSync
import com.lifetracker.app.data.UsageCollector
import com.lifetracker.app.ui.theme.PaperCard

/** What Android says right now for every permission that can be checked at once (Health Connect and folders need more work). */
fun quickPermissionStates(context: Context): Map<Perm, Boolean> = mapOf(
    Perm.UsageAccess to UsageCollector.hasAccess(context),
    Perm.Location to PlacesTracker.hasLocation(context),
    Perm.Background to PlacesTracker.hasBackgroundLocation(context),
    Perm.Activity to (PlacesTracker.hasActivityRecognition(context) && StepCounterSync.hasPermission(context)),
    Perm.Calls to CallsCollector.hasAccess(context),
    Perm.Notifications to ChargeNotifier.canNotify(context),
    Perm.ExactAlarms to HabitReminderScheduler.canRingExactly(context),
    Perm.Battery to KeepAlive.isExempt(context),
)

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun openHealthConnect(context: Context) {
    val intents = listOf(
        Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    for (i in intents) {
        if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) break
    }
}

/** Settings > Permissions: every permission the app asks for, in one place, with why and how. */
@Composable
fun PermissionsScreen(onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var hc by remember { mutableStateOf<Boolean?>(null) }
    var hcNeedsUpdate by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    // Look again whenever you come back from an Android screen.
    DisposableEffect(context) {
        val owner = context.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(tick) {
        when (HealthConnectSync.status(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                hcNeedsUpdate = false
                hc = HealthConnectSync.granted(context).containsAll(HealthConnectSync.PERMISSIONS)
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> { hcNeedsUpdate = true; hc = false }
            else -> { hcNeedsUpdate = false; hc = null }
        }
    }

    val again = { tick++ }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { again() }
    val askOne = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { again() }
    val askCalls = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { again() }
    val askHealth = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { again() }

    val states = remember(tick) { quickPermissionStates(context) }
    val sources = remember(tick) { DataSources(context) }
    val backupFolder = sources.folder(Slot.Backup)?.let(sources::folderName)
    val streakFolder = sources.folder(Slot.Streak)?.let(sources::folderName)
    val nutriFolder = sources.folder(Slot.OpenNutriTracker)?.let(sources::folderName)

    fun allowed(p: Perm): Boolean = when (p) {
        Perm.HealthConnect -> hc == true
        Perm.Folders -> backupFolder != null
        else -> states[p] == true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("Permissions", "What the app may do, and why", onBack) }
        item {
            Text(
                "Android only lets an app see these things if you allow them. Everything stays on this phone. " +
                    "Each card says whether it is allowed now, why the app asks, and how to switch it on.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(PermissionGuide.all, key = { it.perm.name }) { text ->
            val perm = text.perm
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PermCard(text, allowed(perm), note = when (perm) {
                Perm.HealthConnect -> when {
                    hcNeedsUpdate -> "Health Connect needs an update from the Play Store first."
                    hc == null -> "This phone does not have Health Connect. The phone's step counter (Physical activity) still works."
                    else -> null
                }
                Perm.Folders -> "Backup folder: ${backupFolder ?: "not chosen"}\nStreak folder: ${streakFolder ?: "not chosen"}\nOpenNutriTracker folder: ${nutriFolder ?: "not chosen"}"
                Perm.Background -> if (!states.getValue(Perm.Location)) "Allow Location first." else null
                else -> null
            }) {
                when (perm) {
                    Perm.UsageAccess -> Button(onClick = { UsageCollector.openAccessSettings(context) }) { Text("Open usage access") }
                    Perm.Location -> if (!allowed(perm)) Button(onClick = {
                        askLocation.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }) { Text("Allow location") }
                    Perm.Background -> if (!allowed(perm)) Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 29) askOne.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        },
                        enabled = states.getValue(Perm.Location),
                    ) { Text("Allow all the time") }
                    Perm.Activity -> if (!allowed(perm)) Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 29) askOne.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                    }) { Text("Allow physical activity") }
                    Perm.Calls -> if (!allowed(perm)) Button(onClick = { askCalls.launch(CallsCollector.PERMISSIONS) }) { Text("Allow") }
                    Perm.Notifications -> if (!allowed(perm)) Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            askOne.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }) { Text("Allow") }
                    Perm.ExactAlarms -> if (!allowed(perm)) Button(onClick = { HabitReminderScheduler.openExactAlarmSettings(context) }) { Text("Allow") }
                    Perm.Battery -> if (!allowed(perm)) Button(onClick = {
                        KeepAlive.open(context, KeepAlive.exemptionIntents(context))
                    }) { Text("Allow unrestricted") }
                    Perm.HealthConnect -> {
                        if (hcNeedsUpdate) {
                            Button(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthConnectSync.PACKAGE}"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }) { Text("Open Play Store") }
                        } else if (hc != null) {
                            if (hc != true) Button(onClick = { askHealth.launch(HealthConnectSync.REQUEST) }) { Text("Allow") }
                            TextButton(onClick = { openHealthConnect(context) }) { Text("Open Health Connect") }
                        }
                    }
                    Perm.Folders -> Button(onClick = { onOpen(Page.Backup) }) { Text("Open Backups and imports") }
                }
                if (perm != Perm.Folders && perm != Perm.HealthConnect) {
                    TextButton(onClick = { openAppSettings(context) }) { Text("Open this app's settings") }
                }
            }
            if (perm == Perm.UsageAccess) {
                SetCard {
                    AccordionRow(
                        PermissionGuide.restrictedTitle(Build.VERSION.SDK_INT),
                        "If the usage access switch is greyed out or says \"Restricted setting\", Android is blocking it for apps that did not come from a store.\n\n" +
                            PermissionGuide.restrictedSteps(Build.VERSION.SDK_INT).mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n") +
                            "\n\nThe three dots only appear after you have tried to switch usage access on once.",
                        first = true,
                    )
                }
            }
            }
        }
        item {
            Text(
                "Nothing leaves the phone. You can take any permission back in Android's own settings at any time; the app keeps what it already saved.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermCard(text: PermText, allowed: Boolean, note: String?, actions: @Composable () -> Unit) {
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (allowed) "✓ Allowed" else "Not allowed",
                    fontSize = 12.sp,
                    fontWeight = if (allowed) FontWeight.Bold else FontWeight.Normal,
                    color = if (allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MonoLabel("Why")
            Text(text.why, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MonoLabel("How to switch it on")
            Text(text.how, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (note != null) Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            actions()
        }
    }
}
