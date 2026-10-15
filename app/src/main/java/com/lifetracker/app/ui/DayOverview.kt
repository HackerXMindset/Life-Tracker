package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.DaySegments
import kotlin.math.cos
import kotlin.math.sin

private const val DAY_MS = 24 * 60 * 60_000L
private val NOW_ORANGE = Color(0xFFFF9A3C)

/**
 * The 24-hour pie: midnight at the top, noon at the bottom. The thick outer ring is what you logged yourself; the thinner
 * inner ring is what the phone collected for you (app use, sleep, meals, trips). Under it, the time for each kind of thing.
 */
@Composable
fun DayPie(logged: List<DaySegments.Seg>, collected: List<DaySegments.Seg>, all: List<DaySegments.Seg>, dayStartMs: Long, nowMs: Long?) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val known = DaySegments.totalMs(all)
    val loggedMs = DaySegments.totalMs(logged)
    val totals = DaySegments.byLabel(all)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        val s = size.minDimension
                        val c = Offset(size.width / 2, size.height / 2)
                        val outerStroke = s * 0.095f
                        val innerStroke = s * 0.05f
                        val outerR = s / 2 - outerStroke / 2
                        val innerR = outerR - outerStroke / 2 - innerStroke / 2 - 6.dp.toPx()

                        fun ring(r: Float, stroke: Float, list: List<DaySegments.Seg>) {
                            drawArc(track, -90f, 360f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(stroke))
                            list.forEach {
                                val start = -90f + 360f * (it.startMs - dayStartMs).toFloat() / DAY_MS
                                val sweep = 360f * it.lengthMs.toFloat() / DAY_MS
                                drawArc(Color(it.argb), start, sweep.coerceAtLeast(0.6f), false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(stroke))
                            }
                        }
                        ring(outerR, outerStroke, logged)
                        ring(innerR, innerStroke, collected)

                        // An hour mark every 3 hours, just outside the outer ring.
                        for (i in 0 until 8) {
                            val a = Math.toRadians(-90.0 + i * 45.0)
                            val r1 = outerR + outerStroke / 2 + 3.dp.toPx()
                            val r2 = r1 + 5.dp.toPx()
                            drawLine(tick, Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()), Offset(c.x + (r2 * cos(a)).toFloat(), c.y + (r2 * sin(a)).toFloat()), 1.5.dp.toPx())
                        }

                        if (nowMs != null) {
                            val a = Math.toRadians(-90.0 + 360.0 * (nowMs - dayStartMs) / DAY_MS)
                            val r1 = innerR - innerStroke / 2 - 4.dp.toPx()
                            val r2 = outerR + outerStroke / 2 + 2.dp.toPx()
                            drawLine(NOW_ORANGE, Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()), Offset(c.x + (r2 * cos(a)).toFloat(), c.y + (r2 * sin(a)).toFloat()), 3.dp.toPx(), StrokeCap.Round)
                            drawCircle(NOW_ORANGE, 5.dp.toPx(), Offset(c.x + (r2 * cos(a)).toFloat(), c.y + (r2 * sin(a)).toFloat()))
                        }
                    }
                    listOf(
                        "00" to Alignment.TopCenter, "06" to Alignment.CenterEnd,
                        "12" to Alignment.BottomCenter, "18" to Alignment.CenterStart,
                    ).forEach { (text, where) ->
                        Text(
                            text = text,
                            modifier = Modifier.align(where),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = quiet,
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = if (known > 0) durationOf(known) else "–",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (nowMs != null) "tracked so far" else "tracked",
                            fontSize = 13.sp,
                            color = quiet,
                        )
                        if (loggedMs > 0) {
                            Text(text = "you logged " + durationOf(loggedMs), fontSize = 12.sp, color = quiet)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendPill(dashed = false, label = "You logged")
                    LegendPill(dashed = true, label = "Collected for you")
                }
            }
        }
        if (totals.isNotEmpty()) {
            MonoLabel("Where the time went")
            totals.forEach { (label, argb, ms) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(argb)))
                        Text(text = label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        text = durationOf(ms),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendPill(dashed: Boolean, label: String) {
    val color = NOW_ORANGE
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(14.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    if (dashed) {
                        drawRoundRect(
                            color, cornerRadius = r,
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))),
                        )
                    } else {
                        drawRoundRect(color, cornerRadius = r)
                    }
                },
        )
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "14h 10m" from milliseconds, never rounding a real stretch down to nothing. */
private fun durationOf(ms: Long): String = formatDuration(((ms + 30_000L) / 60_000L).toInt().coerceAtLeast(1))
