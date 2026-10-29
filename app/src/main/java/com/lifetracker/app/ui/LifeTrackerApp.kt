package com.lifetracker.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.BackupManager
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.CallsCollector
import com.lifetracker.app.data.ChargeSettings
import com.lifetracker.app.data.ChargeTracker
import com.lifetracker.app.data.ChargeWorker
import com.lifetracker.app.data.HealthSync
import com.lifetracker.app.data.MoneyPoster
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.PlacesTracker
import com.lifetracker.app.data.PlacesWorker
import com.lifetracker.app.data.UsageCollector
import com.lifetracker.app.data.UsageSyncWorker
import com.lifetracker.app.data.HabitSyncState
import com.lifetracker.app.data.StreakSendWorker
import com.lifetracker.app.data.ont.OntImporter
import com.lifetracker.app.data.streak.StreakImporter

enum class Tab(val title: String, @DrawableRes val icon: Int) {
    Timeline("Timeline", R.drawable.ic_tab_timeline),
    Food("Food", R.drawable.ic_tab_food),
    Habits("Habits", R.drawable.ic_tab_habits),
    Money("Money", R.drawable.ic_tab_money),
    Stats("Stats", R.drawable.ic_tab_stats),
    Settings("Settings", R.drawable.ic_tab_settings),
}

@Composable
fun LifeTrackerApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Timeline) }
    // A detail screen opened over the tabs, and which half of it: the numbers (from Stats or the Timeline) or the settings.
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var part by rememberSaveable { mutableStateOf(Part.Data) }
    // A trip or a place opened over whatever is showing (from the Timeline or the Location page); Back returns to it.
    var openTrip by rememberSaveable { mutableStateOf<String?>(null) }
    var openPlace by rememberSaveable { mutableStateOf<String?>(null) }
    fun openPage(target: Page, half: Part) {
        part = half
        page = target
    }

    // One automatic backup per day, the first time the app is opened.
    val context = LocalContext.current
    // Then pick up anything new from Streak and OpenNutriTracker. The backup comes first, so it holds the data from before the import.
    LaunchedEffect(Unit) {
        runCatching { BackupManager.autoBackupIfDue(context) }
        runCatching {
            if (DataSources(context).folder(DataSources.Slot.Streak) != null) StreakImporter.importIfNew(context)
        }
        runCatching {
            if (DataSources(context).folder(DataSources.Slot.OpenNutriTracker) != null) OntImporter.importIfNew(context)
        }
        // Then send anything changed here to Streak's folder, if that is switched on (after the import, so Streak's news comes first).
        runCatching { if (HabitSyncState(context).autoSend) StreakSendWorker.schedule(context, delaySeconds = 0) }
        // Add the monthly items (rent, subscriptions, salary) whose day has come.
        runCatching { MoneyPoster.postDue(context) }
        // Copy the latest app usage history, and keep copying it every few hours in the background.
        runCatching { UsageCollector.sync(context) }
        runCatching { UsageSyncWorker.schedule(context) }
        // Update steps (Health Connect or the phone's step counter) and work out last night's sleep.
        runCatching { HealthSync.run(context) }
        // Copy new calls from the call log, if you allowed it.
        runCatching { CallsCollector.sync(context) }
        // Note a charging session that ended while the app was closed, and keep the check running while the phone charges.
        runCatching { ChargeTracker.sample(context, banner = false) }
        runCatching { if (ChargeSettings(context).track) ChargeWorker.schedule(context) }
        // Hand the named places to Android again (cheap), and keep the 6-hourly refresh going.
        runCatching {
            if (PlacesSettings(context).enabled) {
                PlacesWorker.schedule(context)
                PlacesTracker.syncAll(context)
                PlacesTracker.resumeIfNeeded(context)
            }
        }
    }

    Scaffold(
        // The paper behind the whole app shows through; the menu bar floats on it.
        containerColor = Color.Transparent,
        bottomBar = { PaperNavBar(selected = tab, onSelect = { tab = it; page = null; openTrip = null; openPlace = null }) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val current = page
            val trip = openTrip
            val place = openPlace
            if (place != null) {
                PlaceScreen(placeId = place, onBack = { openPlace = null })
            } else if (trip != null) {
                TripScreen(
                    tripId = trip,
                    onBack = { openTrip = null },
                    onOpenTrip = { openTrip = it },
                    onOpenPlace = { openPlace = it },
                )
            } else if (current != null) {
                val back = { page = null }
                when (current) {
                    Page.Appearance -> AppearanceScreen(onBack = back)
                    Page.Backup -> DataSourcesScreen(onBack = back)
                    Page.Activities -> ActivitiesScreen(onBack = back)
                    Page.Timeline -> TimelineSettingsScreen(onBack = back)
                    Page.Apps -> AppsScreen(onBack = back)
                    Page.PlaceCategories -> PlaceCategoriesScreen(onBack = { page = Page.Places })
                    Page.Profiles -> ProfilesScreen(onBack = { page = Page.Places })
                    Page.Trips -> TripsScreen(onBack = back, onOpenTrip = { openTrip = it })
                    Page.Changes -> ChangesScreen(onBack = { page = Page.Places })
                    Page.Maps -> MapsScreen(onBack = { page = Page.Places })
                    Page.Permissions -> PermissionsScreen(onBack = back, onOpen = { page = it })
                    Page.Logs -> LogsScreen(onBack = back)
                    Page.Habits -> HabitsSettingsScreen(onBack = back)
                    Page.Phone -> PhoneUsageScreen(onBack = back, part = part)
                    Page.Calls -> CallsScreen(onBack = back, part = part)
                    Page.Charging -> ChargingScreen(onBack = back, part = part)
                    Page.Steps -> StepsSleepScreen(onBack = back, part = part)
                    Page.Places -> PlacesScreen(onBack = back, onOpen = { page = it }, onOpenPlace = { openPlace = it })
                    Page.Location -> LocationScreen(onBack = back, onOpenTrip = { openTrip = it }, onOpenPlace = { openPlace = it }, onOpenTrips = { page = Page.Trips })
                }
            } else {
                when (tab) {
                    Tab.Timeline -> TimelineScreen(
                        onOpenCharging = { openPage(Page.Charging, Part.Data) },
                        onOpenSteps = { openPage(Page.Steps, Part.Data) },
                        onOpenPlaces = { openPage(Page.Location, Part.Data) },
                        onOpenTrip = { openTrip = it },
                        onOpenPlace = { openPlace = it },
                        onOpenCalls = { openPage(Page.Calls, Part.Data) },
                        onOpenFood = { tab = Tab.Food },
                    )
                    Tab.Habits -> HabitsScreen()
                    Tab.Food -> FoodScreen()
                    Tab.Money -> MoneyScreen()
                    Tab.Stats -> StatsScreen(
                        onOpenPhone = { openPage(Page.Phone, Part.Data) },
                        onOpenSteps = { openPage(Page.Steps, Part.Data) },
                        onOpenPlaces = { openPage(Page.Location, Part.Data) },
                        onOpenTrips = { openPage(Page.Trips, Part.Data) },
                        onOpenCalls = { openPage(Page.Calls, Part.Data) },
                        onOpenCharging = { openPage(Page.Charging, Part.Data) },
                    )
                    Tab.Settings -> SettingsScreen(onOpen = { openPage(it, Part.Settings) })
                }
            }
        }
    }
}
