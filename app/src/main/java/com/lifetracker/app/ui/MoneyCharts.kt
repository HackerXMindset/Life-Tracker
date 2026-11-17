package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.BrandColors
import com.lifetracker.app.data.MoneyViews
import com.lifetracker.app.data.Period
import com.lifetracker.app.ui.theme.LocalPaper
import java.time.LocalDate
import java.time.YearMonth

/** A round badge for a shop: its first letter on the colour people know it by (or the category's colour for a shop it does not know). */
@Composable
fun MerchantBadge(name: String, fallback: Color, size: Dp = 40.dp) {
    val paper = LocalPaper.current
    val bg = BrandColors.of(name)?.let { argbColor(it) } ?: fallback
    val fg = if (bg.luminance() > 0.5f) Color(0xFF2A2118) else Color.White
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(size)
            .background(bg, CircleShape)
            .border(1.25.dp, paper.pen, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = fg, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

/** One bar for each day (a week or a month) or month (a year). Tap a bar to pick it; tap it again to let go. */
@Composable
fun SpendBars(
    buckets: List<MoneyViews.Bucket>,
    period: Period,
    today: LocalDate,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    val paper = LocalPaper.current
    val max = buckets.maxOfOrNull { it.spent } ?: 0.0
    val chartHeight = 110.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 12) 2.dp else 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEachIndexed { i, b ->
            val picked = selected == i
            val now = when (period) {
                Period.Year -> YearMonth.from(b.start) == YearMonth.from(today)
                else -> b.start == today
            }
            val fraction = if (max > 0) (b.spent / max).toFloat() else 0f
            val color = when {
                picked -> paper.ink
                b.spent <= 0.0 -> paper.line
                now -> paper.sun
                else -> paper.ink2
            }
            val label = if (period == Period.Month && i % 5 != 0) " " else b.label
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelect(if (picked) null else i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxOf(3.dp, chartHeight * fraction))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(color),
                )
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 10.sp, color = if (picked || now) paper.ink else paper.ink2, maxLines = 1)
            }
        }
    }
}

/** A ring split into coloured parts, with anything you want in the middle. */
@Composable
fun Donut(parts: List<Pair<Color, Double>>, diameter: Dp = 132.dp, content: @Composable () -> Unit = {}) {
    val paper = LocalPaper.current
    val total = parts.sumOf { it.second }
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.17f
            val inset = stroke / 2f
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = paper.surface2, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = Stroke(width = stroke),
            )
            if (total > 0.0) {
                var start = -90f
                parts.forEach { (color, value) ->
                    val sweep = (value / total * 360.0).toFloat()
                    drawArc(
                        color = color, startAngle = start, sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f), useCenter = false,
                        topLeft = Offset(inset, inset), size = arc, style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
        }
        content()
    }
}

/** A month as a calendar, each day shaded by how much was spent. Tap a day to pick it. */
@Composable
fun SpendCalendar(
    month: YearMonth,
    daily: Map<LocalDate, Double>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
) {
    val paper = LocalPaper.current
    val max = daily.values.maxOrNull() ?: 0.0
    val cells = MoneyViews.calendarCells(month)
    val rows = cells.chunked(7).map { row -> row + List(7 - row.size) { null } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, modifier = Modifier.weight(1f), fontSize = 10.sp, color = paper.ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { day ->
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val level = MoneyViews.heatLevel(daily[day] ?: 0.0, max)
                        val shape = RoundedCornerShape(6.dp)
                        val picked = day == selected
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(if (level == 0) paper.surface2.copy(alpha = 0.5f) else paper.sun.copy(alpha = 0.18f + 0.2f * level), shape)
                                .then(
                                    when {
                                        picked -> Modifier.border(2.dp, paper.ink, shape)
                                        day == today -> Modifier.border(1.25.dp, paper.pen, shape)
                                        else -> Modifier
                                    },
                                )
                                .clip(shape)
                                .clickable { onSelect(if (picked) null else day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(day.dayOfMonth.toString(), fontSize = 12.sp, color = paper.ink, fontWeight = if (level >= 3) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
