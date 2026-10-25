package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import com.lifetracker.app.data.PathFrame
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.Wobble

/**
 * A path on plain paper (the map comes in a later stage): north is up, proportions are true. [path] is the way (latitude and
 * longitude of each reading, in order). [selected] is the reading you picked, [marked] the readings that carry a note, and
 * [circle] a place's circle. [spots] are single positions (the stays of a place). Tapping picks the nearest reading.
 */
@Composable
fun PathCanvas(
    path: List<Pair<Double, Double>>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    selected: Int = -1,
    marked: Set<Int> = emptySet(),
    circle: PathFrame.Circle? = null,
    spots: List<Pair<Double, Double>> = emptyList(),
    onPick: ((Int) -> Unit)? = null,
) {
    val paper = LocalPaper.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val padding = 22f
    val frame = remember(path, circle, spots, size) {
        PathFrame.of(
            positions = path + spots,
            width = size.width.toFloat().coerceAtLeast(1f),
            height = size.height.toFloat().coerceAtLeast(1f),
            padding = padding,
            circles = listOfNotNull(circle),
        )
    }
    val drawn = remember(frame, path) { path.map { frame.project(it.first, it.second) } }
    val shape = Wobble.Card
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(paper.surface2, shape)
            .border(1.25.dp, paper.line, shape)
            .onSizeChanged { size = it }
            .semantics { contentDescription = description }
            .then(
                if (onPick != null) {
                    Modifier.pointerInput(drawn) {
                        detectTapGestures { tap ->
                            val i = frame.nearest(drawn, tap.x, tap.y)
                            if (i >= 0) onPick(i)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // A faint ruled grid so the paper reads as a map background.
        val step = 48.dp.toPx()
        var x = step
        while (x < this.size.width) {
            drawLine(paper.line.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, this.size.height), 1f)
            x += step
        }
        var y = step
        while (y < this.size.height) {
            drawLine(paper.line.copy(alpha = 0.5f), Offset(0f, y), Offset(this.size.width, y), 1f)
            y += step
        }
        if (circle != null) {
            val c = frame.project(circle.lat, circle.lng)
            val r = frame.pixels(circle.radiusM)
            drawCircle(paper.accentSoft.copy(alpha = 0.55f), r, Offset(c.x, c.y))
            drawCircle(paper.accent, r, Offset(c.x, c.y), style = Stroke(2.dp.toPx()))
        }
        for ((lat, lng) in spots) {
            val p = frame.project(lat, lng)
            drawCircle(paper.ink2, 3.dp.toPx(), Offset(p.x, p.y))
        }
        if (drawn.size >= 2) {
            val line = Path()
            drawn.forEachIndexed { i, p -> if (i == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y) }
            drawPath(line, paper.ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (drawn.isNotEmpty()) {
            val first = drawn.first()
            val last = drawn.last()
            drawCircle(paper.accent, 6.dp.toPx(), Offset(first.x, first.y))
            if (drawn.size >= 2) drawCircle(paper.bad, 6.dp.toPx(), Offset(last.x, last.y))
        }
        for (i in marked) {
            val p = drawn.getOrNull(i) ?: continue
            drawCircle(paper.sun, 5.dp.toPx(), Offset(p.x, p.y))
            drawCircle(paper.ink, 5.dp.toPx(), Offset(p.x, p.y), style = Stroke(1.5.dp.toPx()))
        }
        val s = drawn.getOrNull(selected)
        if (s != null) {
            drawCircle(paper.sun.copy(alpha = 0.35f), 14.dp.toPx(), Offset(s.x, s.y))
            drawCircle(paper.ink, 9.dp.toPx(), Offset(s.x, s.y), style = Stroke(2.5.dp.toPx()))
        }
    }
}
