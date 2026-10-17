package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.DaySegments
import com.lifetracker.app.ui.theme.HighlightedHeading
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.Wobble
import androidx.compose.ui.text.style.TextAlign

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

/** A heading with the orange swipe on the left and a small quiet hint on the right . */
@Composable
fun PaperSection(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Let the hint take its natural width first; constrain the heading to the
        // remaining space. Weighting the hint instead can leave it a few pixels wide,
        // causing Compose to place one letter on each line on narrow screens.
        Box(
            modifier = Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.CenterStart,
        ) {
            HighlightedHeading(title, fontSize = 17f)
        }
        if (hint != null) {
            Text(
                hint,
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp,
                color = LocalPaper.current.ink2,
                textAlign = TextAlign.End,
            )
        }
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

private fun lengthOf(ms: Long): String = formatDuration(((ms + 30_000L) / 60_000L).toInt().coerceAtLeast(1))
