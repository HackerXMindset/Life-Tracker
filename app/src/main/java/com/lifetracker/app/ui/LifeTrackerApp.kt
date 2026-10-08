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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.BackupManager
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.MoneyPoster
import com.lifetracker.app.data.UsageCollector
import com.lifetracker.app.data.UsageSyncWorker
import com.lifetracker.app.data.ont.OntImporter
import com.lifetracker.app.data.streak.StreakImporter

enum class Tab(val title: String, @DrawableRes val icon: Int) {
    Timeline("Timeline", R.drawable.ic_tab_timeline),
    Food("Food", R.drawable.ic_tab_food),
    Habits("Habits", R.drawable.ic_tab_habits),
    Money("Money", R.drawable.ic_tab_money),
    Stats("Stats", R.drawable.ic_tab_stats),
}

@Composable
fun LifeTrackerApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.Timeline) }
    var showData by rememberSaveable { mutableStateOf(false) }
    var showActivities by rememberSaveable { mutableStateOf(false) }
    var showPhone by rememberSaveable { mutableStateOf(false) }

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
        // Add the monthly items (rent, subscriptions, salary) whose day has come.
        runCatching { MoneyPoster.postDue(context) }
        // Copy the latest app usage history, and keep copying it every few hours in the background.
        runCatching { UsageCollector.sync(context) }
        runCatching { UsageSyncWorker.schedule(context) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(painter = painterResource(item.icon), contentDescription = null) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showData) {
                DataSourcesScreen(onBack = { showData = false })
            } else if (showActivities) {
                ActivitiesScreen(onBack = { showActivities = false })
            } else if (showPhone) {
                PhoneUsageScreen(onBack = { showPhone = false })
            } else {
                when (tab) {
                    Tab.Timeline -> TimelineScreen(onOpenData = { showData = true }, onOpenActivities = { showActivities = true }, onOpenPhone = { showPhone = true })
                    Tab.Habits -> HabitsScreen()
                    Tab.Food -> FoodScreen()
                    Tab.Money -> MoneyScreen()
                    else -> ComingNextScreen(tab)
                }
            }
        }
    }
}

@Composable
private fun ComingNextScreen(tab: Tab) {
    val text = when (tab) {
        Tab.Food -> "Meals, calories and macros against your goals, plus water. It will import your OpenNutriTracker export."
        Tab.Habits -> "A 14-day grid, streaks and totals for each habit. It will import your Streak backup."
        Tab.Money -> "Income by source, spending by category and the monthly net."
        Tab.Stats -> "Pick any metric and see it by day, week, month or year, with a heatmap and short insights."
        Tab.Timeline -> ""
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            ScreenHeader(label = tab.title, badge = "Not built yet", title = tab.title)
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Coming next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
