package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameMillis
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.PlaceRules
import com.lifetracker.app.data.ReplayRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.roundToInt

/** Everything the replay of one day needs. */
private class ReplayDay(
    val samples: List<ReplayRules.Sample>,
    val events: List<ReplayRules.Event>,
    val places: List<PlaceEntity>,
    val startMs: Long,
    val endMs: Long,
)

private suspend fun loadDay(context: android.content.Context, date: LocalDate): ReplayDay = withContext(Dispatchers.IO) {
    val db = AppDatabase.get(context)
    val zone = ZoneId.systemDefault()
    val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val limit = if (now in from until to) now else to
    val fixes = db.rawDao().between(from, to).filter { it.acc <= 200f }
    val samples = fixes.map { ReplayRules.Sample(it.ms, it.lat, it.lng) }
    val places = db.placeDao().activePlaces()
    val names = places.associate { it.id to it.name }
    val events = ArrayList<ReplayRules.Event>()
    db.placeDao().visitsAround(from, to)
        .filter { !it.ignored && PlaceRules.lengthMs(it, now) >= PlaceRules.MIN_STAY_MS }
        .forEach { v ->
            val name = names[v.placeId]?.takeIf { it.isNotBlank() } ?: "Unnamed spot"
            if (v.startMs in from until to) events += ReplayRules.Event(v.startMs, "Arrived at $name", "", ReplayRules.Kind.ARRIVE)
            if (!v.ongoing && v.endMs in from until to) events += ReplayRules.Event(v.endMs, "Left $name", "", ReplayRules.Kind.LEAVE)
        }
    db.entryDao().forDate(date.toString()).first().forEach { e ->
        val ms = from + e.startMinute * 60_000L
        val what = e.title.ifBlank { e.category }
        events += ReplayRules.Event(ms, what, if (e.title.isBlank()) "" else e.category, ReplayRules.Kind.LOGGED)
    }
    val start = samples.firstOrNull()?.ms ?: from
    val end = maxOf(samples.lastOrNull()?.ms ?: limit, events.maxOfOrNull { it.ms } ?: 0L).coerceAtMost(limit).coerceAtLeast(start + 1)
    ReplayDay(samples, events.sortedBy { it.ms }, places, start, end)
}

/** The speed slider is a log scale from 1 to 480 minutes of the day for each real second. */
private fun speedOf(position: Float): Int = Math.exp(position * ln(480.0)).roundToInt().coerceIn(1, 480)
private fun positionOf(minPerS: Int): Float = (ln(minPerS.toDouble()) / ln(480.0)).toFloat().coerceIn(0f, 1f)

/**
 * A day played back like a film: a clock runs through the day, the dot sits where you were between the two nearest readings
 * and the path trails behind it. Arriving at or leaving a place, and each activity you logged, pops up as a card when the clock
 * reaches it. The speed only changes how fast the clock runs.
 */
@Composable
fun ReplayScreen(date: LocalDate, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val zone = remember { ZoneId.systemDefault() }
    var day by remember { mutableStateOf<ReplayDay?>(null) }
    var clock by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var minPerS by remember { mutableIntStateOf(20) }
    var skip by remember { mutableStateOf(true) }
    var follow by remember { mutableStateOf(true) }
    var fit by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf<List<ReplayRules.Event>>(emptyList()) }
    BackHandler(onBack = onBack)

    LaunchedEffect(date) {
        val d = loadDay(context, date)
        day = d
        clock = d.startMs
        recent = emptyList()
    }

    val d = day
    val quiet = remember(d) { d?.let { ReplayRules.quietSpans(it.samples, it.startMs, it.endMs) }.orEmpty() }

    LaunchedEffect(playing, d) {
        val loaded = d ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        if (clock >= loaded.endMs) clock = loaded.startMs
        var last = withFrameMillis { it }
        while (playing) {
            val now = withFrameMillis { it }
            val before = clock
            val after = ReplayRules.advance(before, now - last, minPerS, skip, quiet, loaded.endMs)
            last = now
            clock = after
            val hit = ReplayRules.crossed(loaded.events, before, after)
            if (hit.isNotEmpty()) recent = (recent + hit).takeLast(3)
            if (after >= loaded.endMs) playing = false
        }
    }

    fun jump(to: Long) {
        val loaded = d ?: return
        val target = to.coerceIn(loaded.startMs, loaded.endMs)
        clock = target
        // Cards shown are the latest events up to the new time.
        recent = loaded.events.filter { it.ms <= target }.takeLast(3)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            SettingsTop("Replay " + date, if (d == null) "Loading the day..." else if (d.samples.isEmpty()) "No position readings on this day" else ReplayRules.clockText(clock, zone), onBack)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ReplayMap(
                samples = d?.samples.orEmpty(),
                clockMs = clock,
                places = d?.places.orEmpty(),
                follow = follow,
                fitRequest = fit,
                modifier = Modifier.fillMaxSize(),
            )
            recent.lastOrNull()?.let { e ->
                PaperCard(modifier = Modifier.align(Alignment.TopStart).padding(12.dp), padding = 10.dp) {
                    Text(ReplayRules.clockText(e.ms, zone) + " · " + e.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                    if (e.subtitle.isNotEmpty()) Text(e.subtitle, fontSize = 12.sp, color = paper.ink2)
                }
            }
            if (!follow) {
                PaperButton("Follow the dot", { follow = true }, small = true, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (d != null) {
                Slider(
                    value = (clock - d.startMs).toFloat(),
                    onValueChange = { playing = false; jump(d.startMs + it.toLong()) },
                    valueRange = 0f..(d.endMs - d.startMs).toFloat(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PaperButton("Previous", { ReplayRules.stepTo(d.events, clock, false)?.let { playing = false; jump(it) } }, style = PaperButtonStyle.Ghost, small = true)
                    PaperButton(if (playing) "Pause" else "Play", { playing = !playing }, small = true)
                    PaperButton("Next", { ReplayRules.stepTo(d.events, clock, true)?.let { playing = false; jump(it) } }, style = PaperButtonStyle.Ghost, small = true)
                    PaperButton("Whole day", { follow = false; fit++ }, style = PaperButtonStyle.Ghost, small = true)
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReplayRules.SPEEDS.forEach { s -> PaperPill(ReplayRules.speedLabel(s), minPerS == s, { minPerS = s }) }
                }
                Text("Speed: " + ReplayRules.speedLabel(minPerS) + " (drag for your own)", fontSize = 12.sp, color = paper.ink2)
                Slider(value = positionOf(minPerS), onValueChange = { minPerS = speedOf(it) }, valueRange = 0f..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperPill("Skip quiet stretches", skip, { skip = !skip })
                    PaperPill("Follow the dot", follow, { follow = !follow })
                }
                if (recent.size > 1) {
                    recent.dropLast(1).reversed().forEach { e ->
                        Text(ReplayRules.clockText(e.ms, zone) + " · " + e.title, fontSize = 12.sp, color = paper.ink2)
                    }
                }
            }
        }
    }
}
