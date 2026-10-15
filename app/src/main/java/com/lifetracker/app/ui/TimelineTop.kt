package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.Wobble
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private const val DAYS_BACK = 60
private const val DAYS_AHEAD = 30

/** The words at the top of the Timeline. Kept apart from the drawing so they can be tested. */
object TimelineTop {
    /** "Today", "Yesterday", "Tomorrow", or the weekday for any other day. */
    fun title(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE"))
    }

    /** "Sunday, 11 October 2026". */
    fun subtitle(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))

    /** How far through the day it is, 0 at midnight to 1 at the next midnight. */
    fun dayFraction(minuteOfDay: Int): Float = (minuteOfDay.coerceIn(0, 1440) / 1440f)

    /** "5h 36m left today". */
    fun leftText(minuteOfDay: Int): String = formatDuration((1440 - minuteOfDay.coerceIn(0, 1440)).coerceAtLeast(0)) + " left today"
}

/** The big title, the date under it, and (today only) the sun arc. */
@Composable
fun TimelineHeader(date: LocalDate) {
    val p = LocalPaper.current
    val today = LocalDate.now()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = TimelineTop.title(date, today),
                fontSize = 38.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                maxLines = 1,
            )
            Text(text = TimelineTop.subtitle(date), fontSize = 12.sp, color = p.ink2)
        }
        if (date == today) SunArc()
    }
}

/** A half circle of dots from midnight to midnight, inked up to now, with the sun at "now". */
@Composable
private fun SunArc() {
    val p = LocalPaper.current
    var minute by remember { mutableStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            minute = LocalTime.now().let { it.hour * 60 + it.minute }
        }
    }
    val fraction = TimelineTop.dayFraction(minute)
    val text = TimelineTop.leftText(minute)
    Column(
        modifier = Modifier.semantics { contentDescription = text },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(width = 78.dp, height = 44.dp)) {
            // The drawing is 78 by 44 units: a half circle of radius 34 whose centre is (39, 40).
            val s = size.width / 78f
            val topLeft = Offset(5f * s, 6f * s)
            val arcSize = Size(68f * s, 68f * s)
            drawArc(
                color = p.line, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(
                    width = 3f * s, cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f * s, 7f * s)),
                ),
            )
            if (fraction > 0f) {
                drawArc(
                    color = p.ink, startAngle = 180f, sweepAngle = 180f * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = 3f * s, cap = StrokeCap.Round),
                )
            }
            val angle = Math.PI * fraction
            val center = Offset((39f - 34f * Math.cos(angle).toFloat()) * s, (40f - 34f * Math.sin(angle).toFloat()) * s)
            drawCircle(p.sun, radius = 6f * s, center = center)
            drawCircle(p.ink, radius = 6f * s, center = center, style = Stroke(width = 2f * s))
        }
        Text(text = text, fontSize = 10.5.sp, color = p.ink2, maxLines = 1)
    }
}

/** The days as paper tiles: the chosen one inked black, today ringed in pen, days with entries get an orange dot. */
@Composable
fun PaperWeekStrip(selected: LocalDate, datesWithEntries: Set<String>, onSelect: (LocalDate) -> Unit) {
    val p = LocalPaper.current
    val today = remember { LocalDate.now() }
    // Oldest on the left; the coming month is on the right, so planned to-dos can be seen ahead of time.
    val days = remember { (-DAYS_BACK..DAYS_AHEAD).map { today.plusDays(it.toLong()) } }
    val listState = rememberLazyListState()
    // Bring today into view near the right edge, with a few coming days visible after it.
    LaunchedEffect(Unit) { listState.scrollToItem((DAYS_BACK - 3).coerceAtLeast(0)) }

    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(days, key = { it.toString() }) { day ->
            val isSelected = day == selected
            val isToday = day == today
            val hasEntries = day.toString() in datesWithEntries
            val shape = Wobble.Small
            val text = if (isSelected) p.bg else p.ink
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 68.dp)
                    .clip(shape)
                    .then(if (isSelected) Modifier.background(p.ink, shape) else Modifier)
                    .then(if (isToday && !isSelected) Modifier.border(1.5.dp, p.ink, shape) else Modifier)
                    .clickable { onSelect(day) }
                    .semantics { contentDescription = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = day.format(DateTimeFormatter.ofPattern("EEE")),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) p.bg else p.ink2,
                        maxLines = 1,
                    )
                    Text(text = day.dayOfMonth.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text, maxLines = 1)
                }
                if (hasEntries) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 7.dp)
                            .size(6.dp)
                            .background(p.sun, CircleShape),
                    )
                }
            }
        }
    }
}

/** Steps, phone and studied time in one card, split by dashed lines. */
@Composable
fun PaperTrio(steps: String, phone: String, study: String) {
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp)) {
            TrioCell("Steps", steps, first = true, modifier = Modifier.weight(1f))
            TrioCell("Phone", phone, first = false, modifier = Modifier.weight(1f))
            TrioCell("Studied", study, first = false, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrioCell(label: String, value: String, first: Boolean, modifier: Modifier) {
    val p = LocalPaper.current
    Column(
        modifier = modifier
            .drawBehind {
                if (!first) {
                    drawLine(
                        color = p.line, start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label.uppercase(), fontSize = 10.sp, letterSpacing = 0.6.sp, color = p.ink2, maxLines = 1)
        Text(
            text = value,
            modifier = Modifier.padding(top = 5.dp),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = p.ink,
            maxLines = 1,
        )
    }
}
