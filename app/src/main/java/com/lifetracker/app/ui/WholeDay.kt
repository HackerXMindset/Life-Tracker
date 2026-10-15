package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.DaySegments
import com.lifetracker.app.data.DayStripMath
import com.lifetracker.app.ui.theme.HighlightedHeading
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.Wobble
import java.time.Instant
import java.time.ZoneId

/**
 * One stretch on the 24-hour strip. [hatch] draws it as slanted pen lines instead of a solid block (a trip), and
 * [unknown] makes those lines orange (a trip with no known way of travel).
 */
data class StripSeg(
    val startMs: Long,
    val endMs: Long,
    val color: Color,
    val label: String,
    val hatch: Boolean = false,
    val unknown: Boolean = false,
)

fun DaySegments.Seg.toStrip(): StripSeg = StripSeg(startMs, endMs, Color(argb), label)

/** A heading with the orange swipe on the left and a small quiet hint on the right ("24 hours, left to right"). */
@Composable
fun PaperSection(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        HighlightedHeading(title, fontSize = 17f)
        if (hint != null) Text(hint, fontSize = 12.sp, color = LocalPaper.current.ink2, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The whole day in one card: a "Where" line (places and trips) over a "What" line (what you logged and what the phone
 * collected), midnight at the left and the next midnight at the right. Tap a stretch to see it named underneath.
 */
@Composable
fun WholeDayCard(
    where: List<StripSeg>,
    what: List<StripSeg>,
    dayStartMs: Long,
    dayEndMs: Long,
    nowMs: Long?,
    pick: StripSeg?,
    onPick: (StripSeg?) -> Unit,
) {
    val p = LocalPaper.current
    val dayMs = dayEndMs - dayStartMs
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
        StripLabel("Where")
        Band(where, 12.dp, 3.dp, dayStartMs, dayMs, null, false, pick, onPick)
        Box(Modifier.height(6.dp))
        StripLabel("What")
        Band(what, 36.dp, 2.dp, dayStartMs, dayMs, nowMs, true, pick, onPick)
        HourAxis()
        if (pick == null) {
            Text(
                "Tap a stretch on the line to see it. Tap any row below to edit it.",
                modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                fontSize = 11.5.sp,
                color = p.ink2,
            )
        } else {
            Readout(pick)
        }
    }
}

@Composable
private fun StripLabel(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(top = 2.dp, bottom = 3.dp),
        fontSize = 9.5.sp,
        letterSpacing = 0.8.sp,
        color = LocalPaper.current.ink2,
    )
}

@Composable
private fun HourAxis() {
    val p = LocalPaper.current
    Box(modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(15.dp)) {
        listOf(0, 6, 12, 18, 24).forEach { h ->
            Text(
                text = h.toString(),
                modifier = Modifier.align(BiasAlignment(-1f + 2f * h / 24f, -1f)),
                fontSize = 10.sp,
                color = p.ink2,
            )
        }
    }
}

@Composable
private fun Band(
    segs: List<StripSeg>,
    bandHeight: androidx.compose.ui.unit.Dp,
    minWidth: androidx.compose.ui.unit.Dp,
    dayStartMs: Long,
    dayMs: Long,
    nowMs: Long?,
    grid: Boolean,
    pick: StripSeg?,
    onPick: (StripSeg?) -> Unit,
) {
    val p = LocalPaper.current
    val pad = if (nowMs != null) 3.dp else 0.dp
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(bandHeight + pad * 2)
            .pointerInput(segs, dayStartMs, dayMs) {
                detectTapGestures { offset ->
                    val ms = dayStartMs + (offset.x / size.width * dayMs).toLong()
                    val index = DayStripMath.hit(segs.map { it.startMs to it.endMs }, ms, (dayMs * 0.012).toLong())
                    onPick(index?.let { segs[it] })
                }
            },
    ) {
        val w = size.width
        val top = pad.toPx()
        val h = bandHeight.toPx()
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, top, w, top + h, CornerRadius(7.dp.toPx())))
        }
        clipPath(clip) {
            drawRect(p.surface2, Offset(0f, top), Size(w, h))
            if (grid) {
                for (i in 1..7) {
                    val x = w * i / 8f
                    drawLine(p.line, Offset(x, top), Offset(x, top + h), 1.dp.toPx())
                }
            }
            segs.forEach { s ->
                val x0 = w * DayStripMath.fraction(s.startMs, dayStartMs, dayMs)
                val x1 = maxOf(w * DayStripMath.fraction(s.endMs, dayStartMs, dayMs), x0 + minWidth.toPx())
                val faded = pick != null && pick != s
                if (s.hatch) {
                    val c = (if (s.unknown) p.sun else p.ink2).copy(alpha = if (faded) 0.35f else 1f)
                    clipRect(x0, top, x1, top + h) { hatchLines(x0, x1, top, top + h, c, 8.5.dp.toPx(), 3.dp.toPx()) }
                } else {
                    drawRect(s.color.copy(alpha = if (faded) 0.35f else 1f), Offset(x0, top), Size(x1 - x0, h))
                }
                drawLine(p.surface, Offset(x1, top), Offset(x1, top + h), 1.dp.toPx())
                if (pick == s) {
                    drawRect(
                        p.ink, Offset(x0 + 1.dp.toPx(), top + 1.dp.toPx()),
                        Size((x1 - x0 - 2.dp.toPx()).coerceAtLeast(1f), h - 2.dp.toPx()), style = Stroke(2.dp.toPx()),
                    )
                }
            }
            if (nowMs != null) {
                // What has not happened yet is veiled in pencil hatching.
                val nowX = w * DayStripMath.fraction(nowMs, dayStartMs, dayMs)
                drawRect(p.surface.copy(alpha = 0.55f), Offset(nowX, top), Size(w - nowX, h))
                clipRect(nowX, top, w, top + h) {
                    hatchLines(nowX, w, top, top + h, p.ink2.copy(alpha = 0.35f), 7.dp.toPx(), 1.dp.toPx())
                }
            }
        }
        if (nowMs != null) {
            val x = w * DayStripMath.fraction(nowMs, dayStartMs, dayMs)
            drawLine(p.sun, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** Slanted lines from bottom-left to top-right across a box, like pencil hatching. */
private fun DrawScope.hatchLines(x0: Float, x1: Float, top: Float, bottom: Float, color: Color, pitch: Float, stroke: Float) {
    val rise = bottom - top
    var x = x0 - rise
    while (x < x1) {
        drawLine(color, Offset(x, bottom), Offset(x + rise, top), stroke)
        x += pitch
    }
}

@Composable
private fun Readout(seg: StripSeg) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(p.surface, shape)
            .border(1.25.dp, p.pen, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(9.dp)
                .fillMaxHeight()
                .background(if (seg.unknown) p.sun else seg.color, RoundedCornerShape(3.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(seg.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = p.ink)
            Text(clockText(seg.startMs) + " to " + clockText(seg.endMs), fontSize = 11.sp, color = p.ink2)
        }
        Text(lengthOf(seg.endMs - seg.startMs), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = p.ink)
    }
}

/** A row of small labels, one for each kind of thing the day held, with the time spent. Scrolls sideways. */
@Composable
fun ActivityPills(totals: List<Triple<String, Int, Long>>) {
    if (totals.isEmpty()) return
    val p = LocalPaper.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        totals.forEach { (label, argb, ms) ->
            Row(
                modifier = Modifier
                    .background(p.surface, Wobble.Pill)
                    .border(1.25.dp, p.pen, Wobble.Pill)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).background(Color(argb), CircleShape))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = 1)
                Text(lengthOf(ms), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
            }
        }
    }
}

private fun clockText(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { formatMinute(it.hour * 60 + it.minute) }

private fun lengthOf(ms: Long): String = formatDuration(((ms + 30_000L) / 60_000L).toInt().coerceAtLeast(1))
