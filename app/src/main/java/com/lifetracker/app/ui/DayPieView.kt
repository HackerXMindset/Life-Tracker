package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.DayPieMath
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripModes
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.Wobble
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The day as a pie: what you did is the inner pie, where you were is the ring around it. Tap a slice (or a row under
 * it) to see it named, with what it overlapped. Sizes are shares of the time that was recorded.
 */
@Composable
fun DayPieView(what: List<StripSeg>, where: List<StripSeg>, trips: List<TripEntity>) {
    val paper = LocalPaper.current
    // Trips all go into one "Travel" slice on the places ring.
    val taskPieces = what.map { DayPieMath.Piece(it.label, it.color.toArgb(), it.startMs, it.endMs) }
    val placePieces = where.map { DayPieMath.Piece(if (it.hatch) "Travel" else it.label, it.color.toArgb(), it.startMs, it.endMs) }
    val tasks = DayPieMath.slices(taskPieces)
    val places = DayPieMath.slices(placePieces)
    val total = DayPieMath.totalMs(tasks)
    var sel by remember { mutableStateOf<String?>(null) }

    if (total <= 0L) {
        PaperCard(modifier = Modifier.fillMaxWidth(), shadow = false) {
            Text("Nothing was recorded on this day.", fontSize = 11.5.sp, color = paper.ink2)
        }
        return
    }

    val selKind = sel?.substringBefore(':')
    val selName = sel?.substringAfter(':')
    // Names that overlap the chosen slice stay bright; everything else fades.
    val relatedPlaces: Set<String>? = if (selKind == "a") DayPieMath.related(selName!!, taskPieces, placePieces).map { it.first }.toSet() else null
    val relatedTasks: Set<String>? = if (selKind == "p") DayPieMath.related(selName!!, placePieces, taskPieces).map { it.first }.toSet() else null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(tasks, places) {
                        detectTapGestures { off ->
                            val w = size.width.toFloat()
                            val s = w / 320f
                            val dx = off.x - w / 2f
                            val dy = off.y - w / 2f
                            val r = sqrt(dx * dx + dy * dy) / s
                            var angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                            if (angle < 0f) angle += 360f
                            sel = when {
                                r <= 128f -> DayPieMath.hit(tasks, angle)?.let { "a:" + tasks[it].label }
                                r <= 152f && places.isNotEmpty() -> DayPieMath.hit(places, angle)?.let { "p:" + places[it].label }
                                else -> null
                            }
                        }
                    },
            ) {
                val s = size.width / 320f
                val c = Offset(160f * s, 160f * s)

                fun ring(slices: List<DayPieMath.Slice>, key: String, r: Float, related: Set<String>?) {
                    val sweeps = DayPieMath.sweeps(slices)
                    slices.forEachIndexed { i, slice ->
                        val (start, sweep) = sweeps[i]
                        val on = sel == "$key:${slice.label}"
                        val dim = sel != null && !on && related?.contains(slice.label) != true
                        var dx = 0f
                        var dy = 0f
                        if (on && sweep < 359f) {
                            val t = Math.toRadians((start + sweep / 2f - 90f).toDouble())
                            dx = (7f * cos(t)).toFloat() * s
                            dy = (7f * sin(t)).toFloat() * s
                        }
                        val topLeft = Offset(c.x - r * s, c.y - r * s)
                        val box = Size(2f * r * s, 2f * r * s)
                        translate(dx, dy) {
                            drawArc(Color(slice.argb).copy(alpha = if (dim) 0.22f else 1f), start - 90f, sweep, true, topLeft, box)
                            drawArc(paper.surface, start - 90f, sweep, true, topLeft, box, style = Stroke(2.6f * s, join = StrokeJoin.Round))
                        }
                    }
                }

                if (places.isNotEmpty()) ring(places, "p", 150f, relatedPlaces)
                // A paper disc between the ring and the pie.
                drawCircle(paper.surface, radius = 137f * s, center = c)
                ring(tasks, "a", 126f, relatedTasks)
            }
        }

        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp, shadow = false) {
            val name = selName
            val readoutLabel = sel
            if (readoutLabel == null || name == null) {
                Text(
                    "Tap a slice to see it. Tasks are the inner pie, places are the outer ring.",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    fontSize = 11.5.sp,
                    color = paper.ink2,
                )
            } else {
                val isTask = selKind == "a"
                val slice = (if (isTask) tasks else places).firstOrNull { it.label == name }
                if (slice != null) {
                    val related = if (isTask) DayPieMath.related(name, taskPieces, placePieces) else DayPieMath.related(name, placePieces, taskPieces)
                    val text = DayPieMath.percent(slice.ms, total).toString() + "% of recorded time" +
                        (if (related.isNotEmpty()) " · " + (if (isTask) "at " else "doing ") +
                            related.take(4).joinToString(", ") { it.first + " " + lengthOfMs(it.second) } else "")
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(9.dp).fillMaxHeight().background(Color(slice.argb), RoundedCornerShape(3.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(slice.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = paper.ink)
                            Text(text, fontSize = 11.sp, color = paper.ink2)
                        }
                        Text(lengthOfMs(slice.ms), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = paper.ink)
                    }
                }
            }
        }

        PaperSection("Tasks", "the inner pie")
        PieList(tasks, "a", sel) { sel = if (sel == it) null else it }
        if (places.isNotEmpty()) {
            PaperSection("Places", "the outer ring")
            PieList(places, "p", sel) { sel = if (sel == it) null else it }
        }
        HowYouGotAround(trips)
    }
}

/** The rows under the pie: a colour square, the name and the time. Tapping one picks that slice. */
@Composable
private fun PieList(slices: List<DayPieMath.Slice>, key: String, sel: String?, onPick: (String) -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp, shadow = false) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            slices.forEachIndexed { i, slice ->
                val id = "$key:${slice.label}"
                val on = sel == id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (on) Modifier.background(paper.surface2, RoundedCornerShape(10.dp)) else Modifier)
                        .drawBehind {
                            if (!on && i < slices.lastIndex) {
                                drawLine(
                                    paper.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
                                )
                            }
                        }
                        .clickable { onPick(id) }
                        .padding(horizontal = 4.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .border(1.5.dp, paper.ink2, RoundedCornerShape(6.dp))
                            .padding(2.dp)
                            .background(Color(slice.argb), RoundedCornerShape(4.dp)),
                    )
                    Text(slice.label, modifier = Modifier.weight(1f), fontSize = 14.sp, color = paper.ink)
                    Text(lengthOfMs(slice.ms), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = paper.ink2)
                }
            }
        }
    }
}

/** Time and distance per way of travel, from the day's trips. */
@Composable
private fun HowYouGotAround(trips: List<TripEntity>) {
    if (trips.isEmpty()) return
    val paper = LocalPaper.current
    val groups = trips.groupBy {
        if (it.mode == "vehicle" && it.modeSource != "you") "Unknown way of travel" else TripModes.label(it.mode)
    }.map { (label, list) -> Triple(label, list.sumOf { it.endMs - it.startMs }, list.sumOf { it.distanceM }) }
        .sortedByDescending { it.second }
    val totalMs = groups.sumOf { it.second }
    val totalM = groups.sumOf { it.third }
    PaperSection("How you got around", "from your trips")
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp, shadow = false) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            groups.forEach { (label, ms, m) ->
                KeyValueRow(label, lengthOfMs(ms) + " · " + TripRules.distanceText(m), paper.ink2, paper.ink)
            }
            KeyValueRow(
                "Total",
                lengthOfMs(totalMs) + " · " + TripRules.distanceText(totalM) + " · " + trips.size + (if (trips.size == 1) " trip" else " trips"),
                paper.ink, paper.ink,
            )
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String, labelColor: Color, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = labelColor, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun lengthOfMs(ms: Long): String = formatDuration(((ms + 30_000L) / 60_000L).toInt().coerceAtLeast(1))
