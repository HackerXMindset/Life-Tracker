package com.lifetracker.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.CallEntity
import com.lifetracker.app.data.CallStats
import com.lifetracker.app.data.ChargeSessionEntity
import com.lifetracker.app.data.ChargeStats
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.SleepNightEntity
import com.lifetracker.app.data.SleepStats
import com.lifetracker.app.data.StepDayEntity
import com.lifetracker.app.data.StepStats
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.data.UsageCollector
import com.lifetracker.app.data.UsageDays
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Stats tab: the numbers behind the Timeline. One card for each thing the phone records (phone use, steps and sleep,
 * places, trips, calls, charging) for the day chosen on the Timeline; tap a card for the full figures over any stretch of days.
 * The permissions and switches for each one live in the Settings tab instead.
 */
@Composable
fun StatsScreen(
    onOpenPhone: () -> Unit,
    onOpenSteps: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenTrips: () -> Unit,
    onOpenCalls: () -> Unit,
    onOpenCharging: () -> Unit,
    vm: TimelineViewModel = viewModel(),
) {
    val context = LocalContext.current
    val date by vm.date.collectAsState()
    val usageDay by vm.usageDay.collectAsState()
    val calls by vm.calls.collectAsState()
    val charges by vm.charges.collectAsState()
    val steps by vm.steps.collectAsState()
    val sleep by vm.sleep.collectAsState()
    val placeVisits by vm.placeVisits.collectAsState()
    val placeNames by vm.placeNames.collectAsState()
    val trips by vm.trips.collectAsState()

    val zone = ZoneId.systemDefault()
    val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val dayPlaces = PlaceRules.totals(placeVisits, dayStartMs, dayEndMs, System.currentTimeMillis())
    val usageAccess = UsageCollector.hasAccess(context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(label = "Stats", badge = "Saved on this phone", title = "Stats") }
        item {
            MonoLabel("For " + date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) + " (change the day on the Timeline)")
        }
        item(key = "phone") {
            if (usageAccess) PhoneSummary(usageDay, onClick = onOpenPhone)
            else SetupNote("Phone usage is not switched on yet. Allow it in the Settings tab to see your app time here.")
        }
        item(key = "steps") { HealthSummary(steps, sleep, onClick = onOpenSteps) }
        item(key = "places") { PlacesSummary(dayPlaces, placeNames, onClick = onOpenPlaces) }
        item(key = "trips") { TripsSummary(trips, onClick = onOpenTrips) }
        item(key = "calls") { CallsSummary(calls, onClick = onOpenCalls) }
        item(key = "charging") { ChargingSummary(charges, onClick = onOpenCharging) }
    }
}

@Composable
internal fun ChargingSummary(sessions: List<ChargeSessionEntity>, onClick: () -> Unit) {
    val totals = ChargeStats.totals(sessions)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Charging")
                Text(
                    text = if (totals.sessions == 0) "–" else "${totals.sessions} · " + ChargeStats.durationText(totals.chargingMs),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Tap for every charge",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PhoneSummary(day: UsageDays.Day, onClick: () -> Unit) {
    val top = day.perApp.take(3)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("On your phone")
                Text(
                    text = formatDuration((day.totalMs / 60_000L).toInt()),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            top.forEach { (pkg, ms) ->
                val label = day.blocks.firstOrNull { it.pkg == pkg }?.label ?: pkg
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = formatDuration((ms / 60_000L).toInt()),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Tap for every app",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CallsSummary(calls: List<CallEntity>, onClick: () -> Unit) {
    val totals = CallStats.totals(calls)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Calls")
                Text(
                    text = "${totals.calls} · " + CallStats.durationText(totals.talkSec),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (totals.missed > 0) {
                Text(
                    text = "${totals.missed} missed",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = "Tap for people and every call",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HealthSummary(steps: StepDayEntity?, sleep: SleepNightEntity?, onClick: () -> Unit) {
    val stepsText = steps?.takeIf { it.steps > 0 }?.let { StepStats.text(it.steps) }
    val sleepMinutes = sleep?.takeIf { it.source != SleepStats.SKIPPED }?.let { SleepStats.minutes(it) } ?: 0
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Steps and sleep")
                Text(
                    text = listOfNotNull(
                        stepsText?.let { "$it steps" },
                        if (sleepMinutes > 0) "slept " + formatDuration(sleepMinutes) else null,
                    ).joinToString(" · ").ifEmpty { "–" },
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Tap for days and nights",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PlacesSummary(totals: List<PlaceRules.PlaceTotal>, names: Map<String, String>, onClick: () -> Unit) {
    val text = totals.take(3).joinToString(" · ") { (names[it.placeId] ?: "Place") + " " + PlaceRules.durationText(it.ms) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Places")
                Text(
                    text = text.ifEmpty { "–" },
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Tap for places, visits and stops to review",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TripsSummary(trips: List<TripEntity>, onClick: () -> Unit) {
    val distance = trips.sumOf { it.distanceM.toLong() }.toInt()
    val ms = trips.sumOf { it.endMs - it.startMs }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonoLabel("Trips")
                Text(
                    text = "${trips.size} · " + TripRules.distanceText(distance) + " · " + PlaceRules.durationText(ms),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Tap for every trip and to say how you travelled",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
