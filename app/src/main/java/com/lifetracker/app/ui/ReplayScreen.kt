package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.animation.core.animateFloatAsState
import com.lifetracker.app.data.ReplaySettings
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
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
    /** Each stay at a place: start, end and the name. */
    val stays: List<Triple<Long, Long, String>>,
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
    val stays = ArrayList<Triple<Long, Long, String>>()
    db.placeDao().visitsAround(from, to)
        .filter { !it.ignored && PlaceRules.lengthMs(it, now) >= PlaceRules.MIN_STAY_MS }
        .forEach { v ->
            val name = names[v.placeId]?.takeIf { it.isNotBlank() } ?: "Unnamed spot"
            stays += Triple(v.startMs, if (v.ongoing) limit else v.endMs, name)
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
    ReplayDay(samples, events.sortedBy { it.ms }, places, trips, stays, start, end)
}

private const val MAX_SAMPLES = 20_000

/** The speed slider is a log scale from real time (1x) to a day for each real second. */
private fun speedOf(position: Float): Double = Math.exp(position * ln(86_400.0)).roundToInt().coerceIn(1, 86_400).toDouble()
private fun positionOf(speed: Double): Float = (ln(speed.coerceAtLeast(1.0)) / ln(86_400.0)).toFloat().coerceIn(0f, 1f)

/** A colour for each way of travelling, for the strip under the map. */
private fun modeColor(mode: String): Color = when (mode) {
    "walk", "run" -> Color(0xFF4CAF50)
    "cycle", "scooter", "skateboard" -> Color(0xFF26A69A)
    "car", "bike" -> Color(0xFFFF9800)
    "auto" -> Color(0xFFFFC107)
    "bus" -> Color(0xFF9C27B0)
    "train" -> Color(0xFF3F51B5)
    "flight" -> Color(0xFFE91E63)
    "boat" -> Color(0xFF03A9F4)
    else -> Color(0xFF8E8E93)
}

/** The whole stretch as a bar: trips in the colour of how you travelled, stays as a thin line, cards as ticks. Tap or drag to jump. */
@Composable
private fun ReplayStrip(day: ReplayDay, clock: Long, quiet: List<ReplayRules.Quiet>, onSeek: (Long) -> Unit) {
    val paper = LocalPaper.current
    val span = (day.endMs - day.startMs).coerceAtLeast(1L).toFloat()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(day) { detectTapGestures { o -> onSeek(day.startMs + (o.x / size.width * span).toLong()) } }
            .pointerInput(day) {
                detectHorizontalDragGestures { change, _ -> onSeek(day.startMs + (change.position.x.coerceIn(0f, size.width.toFloat()) / size.width * span).toLong()) }
            },
    ) {
        val w = size.width
        val h = size.height
        fun x(ms: Long) = ((ms - day.startMs) / span * w).coerceIn(0f, w)
        drawRoundRect(paper.surface2, size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
        quiet.forEach { q -> drawRect(paper.ink2.copy(alpha = 0.12f), Offset(x(q.startMs), 0f), androidx.compose.ui.geometry.Size(x(q.endMs) - x(q.startMs), h)) }
        day.stays.forEach { (a, b, _) -> drawRect(paper.ink2.copy(alpha = 0.45f), Offset(x(a), h * 0.78f), androidx.compose.ui.geometry.Size(maxOf(2f, x(b) - x(a)), h * 0.22f)) }
        day.trips.forEach { t -> drawRect(modeColor(t.mode), Offset(x(t.startMs), h * 0.22f), androidx.compose.ui.geometry.Size(maxOf(2f, x(t.endMs) - x(t.startMs)), h * 0.5f)) }
        day.events.forEach { e -> drawLine(paper.ink.copy(alpha = 0.55f), Offset(x(e.ms), 0f), Offset(x(e.ms), h * 0.2f), 2f) }
        drawLine(paper.accent, Offset(x(clock), 0f), Offset(x(clock), h), 4f)
    }
}

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
    val settings = remember { ReplaySettings(context) }
    var speed by remember { mutableDoubleStateOf(settings.speed) }
    var smartOn by remember { mutableStateOf(settings.smart) }
    var busySlow by remember { mutableDoubleStateOf(settings.busySlow) }
    var quietBoost by remember { mutableDoubleStateOf(settings.quietBoost) }
    var factor by remember { mutableDoubleStateOf(1.0) }
    var showTune by remember { mutableStateOf(false) }
    var cardShown by remember { mutableStateOf(false) }
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
            val dt = now - last
            factor = if (smartOn) {
                ReplayRules.easeFactor(factor, ReplayRules.smartFactor(ReplayRules.Smart(busySlow, quietBoost), loaded.samples, loaded.events, before), dt)
            } else {
                1.0
            }
            val after = ReplayRules.advance(before, dt, speed, skip, quiet, loaded.endMs, factor)
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
    val cum = remember(d) { d?.let { ReplayRules.cumulative(it.samples) } ?: DoubleArray(0) }
    // A card shows for a few seconds, then fades.
    LaunchedEffect(recent.lastOrNull()) {
        if (recent.isEmpty()) return@LaunchedEffect
        cardShown = true
        delay(5_000L)
        cardShown = false
    }
    val cardAlpha by animateFloatAsState(if (cardShown) 1f else 0f, label = "card")

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
        val choice = GifChoice(ReplayGifRules.SIZES[gifSize].second, ReplayGifRules.LENGTHS[gifLength], gifFit, speed, skip, if (smartOn) ReplayRules.Smart(busySlow, quietBoost) else null)
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
            recent.lastOrNull()?.takeIf { cardAlpha > 0.01f }?.let { e ->
                Column(
                    modifier = Modifier.align(Alignment.TopStart).alpha(cardAlpha).padding(8.dp).background(paper.surface.copy(alpha = 0.92f), Wobble.Small).padding(horizontal = 8.dp, vertical = 5.dp),
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
                // What is happening now: how fast you were going, how far you had gone, how you were travelling or where you were.
                val kmh = ReplayRules.groundSpeedMs(d.samples, clock) * 3.6
                val metres = ReplayRules.distanceAt(d.samples, cum, clock)
                val trip = d.trips.firstOrNull { clock in it.startMs..it.endMs }
                val stay = d.stays.firstOrNull { clock in it.first..it.second }
                val what = when {
                    trip != null -> TripModes.label(trip.mode)
                    stay != null -> stay.third
                    else -> ""
                }
                Text(
                    listOf(what, "%d km/h".format(kmh.roundToInt()), TripRules.distanceText(metres.roundToInt()) + " so far").filter { it.isNotEmpty() }.joinToString(" \u00b7 ") +
                        "  |  " + ReplayRules.speedLabel(speed) + (if (smartOn) " \u00d7%.1f".format(factor) else ""),
                    fontSize = 11.sp, color = paper.ink2, maxLines = 1,
                )
                ReplayStrip(d, clock, quiet) { playing = false; jump(it) }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniButton("\u2039 Card", false) { ReplayRules.stepTo(d.events, clock, false)?.let { playing = false; jump(it) } }
                    MiniButton(if (playing) "Pause" else "Play", true) { playing = !playing }
                    MiniButton("Card \u203a", false) { ReplayRules.stepTo(d.events, clock, true)?.let { playing = false; jump(it) } }
                    MiniButton("Smart speed", smartOn) { smartOn = !smartOn; settings.smart = smartOn }
                    MiniButton("Tune", false) { showTune = true }
                    MiniButton("Skip quiet", skip) { skip = !skip }
                    MiniButton("Follow", follow) { follow = !follow }
                    MiniButton("Auto zoom", autoZoom) { autoZoom = !autoZoom }
                    MiniButton("Whole day", false) { follow = false; fit++ }
                    MiniButton("Make a GIF", false) { gifResult = ""; showGif = true }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReplayRules.SPEEDS.forEach { sp -> MiniButton(ReplayRules.speedLabel(sp), speed == sp) { speed = sp; settings.speed = sp } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speed " + ReplayRules.speedLabel(speed), fontSize = 11.sp, color = paper.ink2)
                    Slider(
                        value = positionOf(speed),
                        onValueChange = { speed = speedOf(it) },
                        onValueChangeFinished = { settings.speed = speed },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
            }
        }
    }


    if (showTune) {
        PaperDialog(
            title = "Smart speed",
            onDismiss = { showTune = false },
            dismissText = "Close",
            confirmText = "Reset",
            onConfirm = { settings.resetSmart(); busySlow = settings.busySlow; quietBoost = settings.quietBoost },
        ) {
            Text(
                "Busy moments (walking about, turning a lot, a place or an activity close by) play slower, quiet ones faster, so you see the detail and skip the dull parts. Your speed is multiplied by these.",
                fontSize = 12.sp, color = paper.ink2,
            )
            Text("When busy: " + "%.2f".format(busySlow) + "x of your speed", fontSize = 12.sp, color = paper.ink)
            Slider(
                value = (ln(busySlow) / ln(50.0) * -1.0).toFloat().coerceIn(0f, 1f),
                onValueChange = { busySlow = Math.exp(-it * ln(50.0)); settings.busySlow = busySlow },
                valueRange = 0f..1f,
            )
            Text("When quiet: " + "%.1f".format(quietBoost) + "x your speed", fontSize = 12.sp, color = paper.ink)
            Slider(
                value = ((quietBoost - 1.0) / 19.0).toFloat().coerceIn(0f, 1f),
                onValueChange = { quietBoost = 1.0 + it * 19.0; settings.quietBoost = quietBoost },
                valueRange = 0f..1f,
            )
        }
    }

    if (showGif && d != null) {
        val running = gifProgress != null
        val seconds = ReplayGifRules.LENGTHS[gifLength]
        val speedNote = if (gifFit) "The whole day plays in $seconds seconds." else "Plays at " + ReplayRules.speedLabel(speed) + ", and stops after $seconds seconds if the day is longer."
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
