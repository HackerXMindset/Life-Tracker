package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.lifetracker.app.ui.theme.Wobble
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
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.ReplayRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperPill
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import com.lifetracker.app.data.ReplayGifRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
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
    val trips: List<TripEntity>,
    val startMs: Long,
    val endMs: Long,
)

private suspend fun loadDay(context: android.content.Context, first: LocalDate, last: LocalDate): ReplayDay = withContext(Dispatchers.IO) {
    val db = AppDatabase.get(context)
    val zone = ZoneId.systemDefault()
    val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val limit = if (now in from until to) now else to
    val fixes = db.rawDao().between(from, to).filter { it.acc <= 200f }
    // A month of readings is more than a replay needs: keep about twenty thousand, always the last one.
    val stride = ((fixes.size + MAX_SAMPLES - 1) / MAX_SAMPLES).coerceAtLeast(1)
    val kept = if (stride == 1) fixes else fixes.filterIndexed { i, _ -> i % stride == 0 || i == fixes.lastIndex }
    val samples = kept.map { ReplayRules.Sample(it.ms, it.lat, it.lng) }
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
    var date = first
    var days = 0
    while (!date.isAfter(last) && days < 400) {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        db.entryDao().forDate(date.toString()).first().forEach { e ->
            val what = e.title.ifBlank { e.category }
            events += ReplayRules.Event(dayStart + e.startMinute * 60_000L, what, if (e.title.isBlank()) "" else e.category, ReplayRules.Kind.LOGGED)
        }
        date = date.plusDays(1)
        days++
    }
    val trips = db.tripDao().tripsBetween(from, to).first()
    val start = samples.firstOrNull()?.ms ?: from
    val end = maxOf(samples.lastOrNull()?.ms ?: limit, events.maxOfOrNull { it.ms } ?: 0L).coerceAtMost(limit).coerceAtLeast(start + 1)
    ReplayDay(samples, events.sortedBy { it.ms }, places, trips, start, end)
}

private const val MAX_SAMPLES = 20_000

/** The speed slider is a log scale from 1 minute to 1 day of the day for each real second. */
private fun speedOf(position: Float): Int = Math.exp(position * ln(1440.0)).roundToInt().coerceIn(1, 1440)
private fun positionOf(minPerS: Int): Float = (ln(minPerS.toDouble()) / ln(1440.0)).toFloat().coerceIn(0f, 1f)

/**
 * A day played back like a film: a clock runs through the day, the dot sits where you were between the two nearest readings
 * and the path trails behind it. Arriving at or leaving a place, and each activity you logged, pops up as a card when the clock
 * reaches it. The speed only changes how fast the clock runs.
 */
@Composable
fun ReplayScreen(from: LocalDate, to: LocalDate, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val zone = remember { ZoneId.systemDefault() }
    var day by remember { mutableStateOf<ReplayDay?>(null) }
    var clock by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var minPerS by remember { mutableIntStateOf(20) }
    var skip by remember { mutableStateOf(true) }
    var follow by remember { mutableStateOf(true) }
    var autoZoom by remember { mutableStateOf(true) }
    var fit by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf<List<ReplayRules.Event>>(emptyList()) }
    BackHandler(onBack = onBack)

    LaunchedEffect(from, to) {
        val d = loadDay(context, from, to)
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

    val camera = remember { ReplayCamera() }

    // The GIF: what you chose, how far along it is, and how it ended.
    var showGif by remember { mutableStateOf(false) }
    var gifSize by remember { mutableIntStateOf(1) }
    var gifLength by remember { mutableIntStateOf(1) }
    var gifFit by remember { mutableStateOf(true) }
    var gifProgress by remember { mutableStateOf<Float?>(null) }
    var gifResult by remember { mutableStateOf("") }
    var gifJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val gifColors = GifColors(paper.bg.toArgb(), paper.ink.toArgb(), paper.ink2.toArgb(), paper.accent.toArgb(), paper.surface.toArgb())
    val saveGif = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/gif")) { uri ->
        val loaded = day
        if (uri == null || loaded == null) return@rememberLauncherForActivityResult
        gifResult = ""
        gifProgress = 0f
        val choice = GifChoice(ReplayGifRules.SIZES[gifSize].second, ReplayGifRules.LENGTHS[gifLength], gifFit, minPerS, skip)
        gifJob = scope.launch {
            try {
                var written = 0L
                val raw = context.contentResolver.openOutputStream(uri) ?: error("Could not open the file")
                val counting = object : FilterOutputStream(BufferedOutputStream(raw, 64 * 1024)) {
                    override fun write(b: Int) { out.write(b); written++ }
                    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); written += len }
                }
                withContext(Dispatchers.Default) {
                    counting.use {
                        makeReplayGif(
                            it, loaded.samples, loaded.events, loaded.places, loaded.trips, loaded.startMs, loaded.endMs,
                            ReplayRules.quietSpans(loaded.samples, loaded.startMs, loaded.endMs), choice, gifColors,
                        ) { f -> gifProgress = f }
                    }
                }
                gifResult = "Saved: " + ReplayGifRules.sizeText(written)
            } catch (e: kotlinx.coroutines.CancellationException) {
                gifResult = "Stopped"
                throw e
            } catch (e: Exception) {
                gifResult = "Could not make the GIF: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                gifProgress = null
                gifJob = null
            }
        }
    }
    // How you were travelling at the clock's time: the picture on the dot.
    val icon = remember(d, clock) {
        val trip = d?.trips?.firstOrNull { clock in it.startMs..it.endMs }
        if (trip != null) modeIcon(trip.mode) else "\uD83D\uDCCD"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("\u2039 Back", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = paper.ink, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp))
            Text(
                if (d != null && d.samples.isEmpty()) "No readings for $title" else "Replay $title",
                fontSize = 13.sp, color = paper.ink2, modifier = Modifier.weight(1f), maxLines = 1,
            )
            Text(ReplayRules.clockText(clock, zone), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = paper.ink)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ReplayMap(
                samples = d?.samples.orEmpty(),
                clockMs = clock,
                places = d?.places.orEmpty(),
                follow = follow,
                autoZoom = autoZoom,
                fitRequest = fit,
                icon = icon,
                camera = camera,
                onUserPanned = { follow = false },
                onUserZoomed = { autoZoom = false },
                modifier = Modifier.fillMaxSize(),
            )
            recent.lastOrNull()?.let { e ->
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(paper.surface.copy(alpha = 0.92f), Wobble.Small).padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(ReplayRules.clockText(e.ms, zone) + " \u00b7 " + e.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = paper.ink, maxLines = 2)
                    if (e.subtitle.isNotEmpty()) Text(e.subtitle, fontSize = 10.sp, color = paper.ink2)
                }
            }
            Column(modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniButton("+", false) { autoZoom = false; camera.zoom(1.0) }
                MiniButton("\u2212", false) { autoZoom = false; camera.zoom(-1.0) }
            }
        }
        if (d != null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Slider(
                    value = (clock - d.startMs).toFloat(),
                    onValueChange = { playing = false; jump(d.startMs + it.toLong()) },
                    valueRange = 0f..(d.endMs - d.startMs).toFloat(),
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniButton("\u2039 Card", false) { ReplayRules.stepTo(d.events, clock, false)?.let { playing = false; jump(it) } }
                    MiniButton(if (playing) "Pause" else "Play", true) { playing = !playing }
                    MiniButton("Card \u203a", false) { ReplayRules.stepTo(d.events, clock, true)?.let { playing = false; jump(it) } }
                    MiniButton("Whole day", false) { follow = false; fit++ }
                    MiniButton("Skip quiet", skip) { skip = !skip }
                    MiniButton("Follow", follow) { follow = !follow }
                    MiniButton("Auto zoom", autoZoom) { autoZoom = !autoZoom }
                    MiniButton("Make a GIF", false) { gifResult = ""; showGif = true }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReplayRules.SPEEDS.forEach { sp -> MiniButton(ReplayRules.speedLabel(sp), minPerS == sp) { minPerS = sp } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speed " + ReplayRules.speedLabel(minPerS), fontSize = 11.sp, color = paper.ink2)
                    Slider(value = positionOf(minPerS), onValueChange = { minPerS = speedOf(it) }, valueRange = 0f..1f, modifier = Modifier.weight(1f).padding(start = 8.dp))
                }
            }
        }
    }


    if (showGif && d != null) {
        val running = gifProgress != null
        val seconds = ReplayGifRules.LENGTHS[gifLength]
        val speedNote = if (gifFit) "The whole day plays in $seconds seconds." else "Plays at " + ReplayRules.speedLabel(minPerS) + ", and stops after $seconds seconds if the day is longer."
        PaperDialog(
            title = "Make a GIF",
            onDismiss = { if (running) gifJob?.cancel() else showGif = false },
            confirmText = if (running) null else "Save GIF",
            onConfirm = { saveGif.launch("replay-" + title.replace(Regex("[^A-Za-z0-9-]+"), "-").trim('-') + ".gif") },
            dismissText = if (running) "Stop" else "Close",
        ) {
            Text("Size", fontSize = 12.sp, color = paper.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReplayGifRules.SIZES.forEachIndexed { k, (label, px) -> PaperPill("$label $px", gifSize == k, { if (!running) gifSize = k }) }
            }
            Text("Length", fontSize = 12.sp, color = paper.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReplayGifRules.LENGTHS.forEachIndexed { k, sec -> PaperPill("$sec s", gifLength == k, { if (!running) gifLength = k }) }
            }
            Text("Speed", fontSize = 12.sp, color = paper.ink2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Fit the day", gifFit, { if (!running) gifFit = true })
                PaperPill("My speed", !gifFit, { if (!running) gifFit = false })
            }
            Text(speedNote + (if (skip) " Quiet stretches are skipped." else ""), fontSize = 12.sp, color = paper.ink2)
            gifProgress?.let { Text("Making the GIF... " + (it * 100).toInt() + "%", fontSize = 13.sp, color = paper.ink) }
            if (gifResult.isNotEmpty()) Text(gifResult, fontSize = 13.sp, color = paper.ink)
        }
    }
}

/** A small button for the replay's controls: small text so the map keeps the room. */
@Composable
private fun MiniButton(text: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPaper.current
    val shape = Wobble.Pill
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (on) p.ink else p.surface, shape)
            .border(1.dp, p.pen, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) p.bg else p.ink, maxLines = 1)
    }
}
