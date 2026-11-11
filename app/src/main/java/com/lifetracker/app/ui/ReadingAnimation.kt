package com.lifetracker.app.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.lifetracker.app.data.FoodSettings
import com.lifetracker.app.ui.theme.LocalPaper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a reading is: nothing happening, waiting for the answer, the answer came, or it failed. */
enum class ReadPhase { Idle, Reading, Done, Failed }

/** The maths behind the reading animation, free of the screen so it can be tested. */
object ReadingMotion {
    /** The number shown while counting up to [target] at time [t] (0 to 1). It eases out, so it slows down as it arrives. */
    fun count(target: Double, t: Float): Double {
        val x = t.coerceIn(0f, 1f).toDouble()
        return target * (1.0 - Math.pow(1.0 - x, 3.0))
    }

    /** The stretch of the pen outline drawn at time [t] of one loop: drawn from the start first, then rubbed out from the start. */
    fun trace(t: Float): Pair<Float, Float> {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 0f to x * 2f else (x - 0.5f) * 2f to 1f
    }

    /** The left edge of the sweeping marker band: fully off the left side at 0 and fully off the right side at 1. */
    fun sweepX(t: Float, width: Float, band: Float): Float = -band + (width + band) * t.coerceIn(0f, 1f)

    /** A point on the outline of a [w] by [h] rectangle, [u] (0 to 1) of the way round clockwise from the top left. */
    fun perimeter(u: Float, w: Float, h: Float): Pair<Float, Float> {
        var d = u.coerceIn(0f, 1f) * 2f * (w + h)
        if (d <= w) return d to 0f
        d -= w
        if (d <= h) return w to d
        d -= h
        if (d <= w) return (w - d) to h
        d -= w
        return 0f to (h - d).coerceAtLeast(0f)
    }

    /** The corners of a quick red-pencil scribble of [strokes] strokes across a [w] by [h] box, from the top down. */
    fun scribble(w: Float, h: Float, strokes: Int): List<Pair<Float, Float>> {
        val n = strokes.coerceAtLeast(2)
        return (0 until n).map { i -> (if (i % 2 == 0) w * 0.06f else w * 0.94f) to h * (0.1f + 0.8f * i / (n - 1)) }
    }
}

/** True when the reading animation is switched on in What to show and Android's own "remove animations" is not on. */
@Composable
fun rememberReadingAnimation(): Boolean {
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    return remember(tick) {
        FoodSettings.readingAnimation(context) &&
            runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f
    }
}

/**
 * The phase of a reading from whether it is [busy] and whether it [failed]. After one ends the phase stays Done or Failed for a
 * moment, so the closing animation has time to play, then goes back to Idle.
 */
@Composable
fun rememberReadPhase(busy: Boolean, failed: Boolean): ReadPhase {
    var phase by remember { mutableStateOf(ReadPhase.Idle) }
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) {
            wasBusy = true
            phase = ReadPhase.Reading
        } else if (wasBusy) {
            wasBusy = false
            phase = if (failed) ReadPhase.Failed else ReadPhase.Done
            delay(1900)
            phase = ReadPhase.Idle
        }
    }
    return phase
}

/**
 * Wraps [content] (a photo, or an empty paper card) in the paper-style reading animation, with no words: while [phase] is Reading a pen
 * keeps drawing and rubbing out its outline, a yellow marker sweeps across and pencil hatching flickers in the corners; when the answer
 * comes an ink stamp lands and the card dips like pressed paper; when it fails a red pencil scribbles across it and the card shakes once.
 * With [enabled] off it is just [content].
 */
@Composable
fun ReadingStage(phase: ReadPhase, enabled: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (!enabled) Box(modifier, content = content) else ReadingStageOn(phase, modifier, content)
}

@Composable
private fun ReadingStageOn(phase: ReadPhase, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = LocalPaper.current
    val shake = remember { Animatable(0f) }
    val dip = remember { Animatable(0f) }
    val stamp = remember { Animatable(1f) }
    val show = remember { Animatable(0f) }
    val scribble = remember { Animatable(0f) }
    var last by remember { mutableStateOf(phase) }

    LaunchedEffect(phase) {
        val from = last
        last = phase
        when (phase) {
            ReadPhase.Reading -> {
                show.snapTo(0f)
                scribble.snapTo(0f)
            }
            ReadPhase.Done -> if (from == ReadPhase.Reading) {
                stamp.snapTo(0f)
                show.snapTo(1f)
                stamp.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                dip.animateTo(1f, tween(70))
                dip.animateTo(0f, tween(220))
                delay(900)
                show.animateTo(0f, tween(350))
            }
            ReadPhase.Failed -> if (from == ReadPhase.Reading) {
                shake.snapTo(0f)
                scribble.snapTo(0f)
                coroutineScope {
                    launch { shake.animateTo(1f, tween(420, easing = LinearEasing)) }
                    launch { scribble.animateTo(1f, tween(380, easing = LinearEasing)) }
                }
                delay(700)
                scribble.animateTo(0f, tween(350))
            }
            ReadPhase.Idle -> Unit
        }
    }

    Box(
        modifier.graphicsLayer {
            translationX = (Math.sin(shake.value * Math.PI * 6) * (1f - shake.value) * 7.dp.toPx()).toFloat()
            val s = 1f - 0.018f * dip.value
            scaleX = s
            scaleY = s
        },
    ) {
        content()
        if (phase == ReadPhase.Reading) ReadingOverlay(Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
            val a = show.value * (stamp.value * 3f).coerceAtMost(1f)
            if (a > 0f) {
                val c = Offset(size.width - 38.dp.toPx(), 38.dp.toPx())
                val sc = 2.4f - 1.4f * stamp.value
                withTransform({
                    rotate(-14f, c)
                    scale(sc, sc, c)
                }) {
                    drawCircle(p.surface.copy(alpha = 0.9f * a), 22.dp.toPx(), c)
                    drawCircle(p.ink.copy(alpha = a), 22.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    drawCircle(p.ink.copy(alpha = a), 17.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
                    val tick = Path().apply {
                        moveTo(c.x - 8.dp.toPx(), c.y + 1.dp.toPx())
                        lineTo(c.x - 2.dp.toPx(), c.y + 7.dp.toPx())
                        lineTo(c.x + 9.dp.toPx(), c.y - 7.dp.toPx())
                    }
                    drawPath(tick, p.ink.copy(alpha = a), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (scribble.value > 0f) {
                val pts = ReadingMotion.scribble(size.width, size.height, 7)
                val total = pts.size - 1
                val f = scribble.value * total
                val full = f.toInt().coerceAtMost(total)
                val path = Path().apply {
                    moveTo(pts[0].first, pts[0].second)
                    for (i in 1..full) lineTo(pts[i].first, pts[i].second)
                    if (full < total) {
                        val frac = f - full
                        lineTo(pts[full].first + (pts[full + 1].first - pts[full].first) * frac, pts[full].second + (pts[full + 1].second - pts[full].second) * frac)
                    }
                }
                drawPath(path, p.bad.copy(alpha = 0.85f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** What plays while waiting: the pen outline, the yellow marker sweep and the pencil hatching. Only composed while reading, so nothing runs otherwise. */
@Composable
private fun ReadingOverlay(modifier: Modifier) {
    val p = LocalPaper.current
    val clock = rememberInfiniteTransition(label = "reading")
    val loop = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pen")
    val sweep = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing)), label = "marker")
    val pulse = clock.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse), label = "hatch")
    Box(
        modifier.drawWithCache {
            val inset = 3.dp.toPx()
            val amp = 1.4.dp.toPx()
            val w = size.width - 2 * inset
            val h = size.height - 2 * inset
            val outline = Path()
            val steps = 180
            for (i in 0..steps) {
                val u = i / steps.toFloat()
                val (x, y) = ReadingMotion.perimeter(u, w, h)
                val j = (Math.sin(u * 44.0) + 0.6 * Math.sin(u * 97.0 + 1.0)).toFloat() * amp
                val px = inset + x + j * 0.8f
                val py = inset + y + j
                if (i == 0) outline.moveTo(px, py) else outline.lineTo(px, py)
            }
            val measure = PathMeasure().apply { setPath(outline, false) }
            val length = measure.length
            val segment = Path()
            onDrawBehind {
                // The yellow marker, like the one under the headings, sweeping across.
                val band = size.width * 0.28f
                val x = ReadingMotion.sweepX(sweep.value, size.width, band)
                val skew = size.height * 0.25f
                clipRect {
                    val marker = Path().apply {
                        moveTo(x + skew, 0f)
                        lineTo(x + band + skew, 0f)
                        lineTo(x + band, size.height)
                        lineTo(x, size.height)
                        close()
                    }
                    drawPath(marker, p.sun.copy(alpha = 0.38f))
                }
                // Pencil hatching that flickers in two corners.
                val hatch = p.pencil.copy(alpha = 0.15f + 0.35f * pulse.value)
                for (i in 0..3) {
                    val dx = 12.dp.toPx() + i * 7.dp.toPx()
                    drawLine(hatch, Offset(dx, 12.dp.toPx()), Offset(dx - 16.dp.toPx(), 28.dp.toPx()), 1.6.dp.toPx(), StrokeCap.Round)
                    drawLine(hatch, Offset(size.width - dx, size.height - 12.dp.toPx()), Offset(size.width - dx + 16.dp.toPx(), size.height - 28.dp.toPx()), 1.6.dp.toPx(), StrokeCap.Round)
                }
                // The pen drawing its outline, then rubbing it out.
                val (from, to) = ReadingMotion.trace(loop.value)
                if (to - from > 0.002f && length > 0f) {
                    segment.reset()
                    measure.getSegment(from * length, to * length, segment, true)
                    drawPath(segment, p.ink, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (from == 0f) drawCircle(p.ink, 3.5.dp.toPx(), measure.getPosition(to * length))
                }
            }
        },
    )
}

/** A number that counts up from 0 to [target] when [play] is on (and starts again when [token] changes), else just shows [target]. */
@Composable
fun CountUpText(target: Double, token: Int, play: Boolean, fontSize: TextUnit, fontWeight: FontWeight, color: Color, modifier: Modifier = Modifier) {
    val t = remember(token) { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(token, play) {
        if (play) t.animateTo(1f, tween(900, easing = LinearEasing))
    }
    Text(Math.round(ReadingMotion.count(target, t.value)).toString(), modifier = modifier, fontSize = fontSize, fontWeight = fontWeight, color = color)
}

/** A yellow marker stroke that swipes under the text after [delayMs] and then fades, once per [token]. Nothing when [play] is off. */
@Composable
fun Modifier.markerSwipe(token: Int, play: Boolean, delayMs: Int = 0): Modifier {
    val p = LocalPaper.current
    val grow = remember(token) { Animatable(if (play) 0f else 1f) }
    val fade = remember(token) { Animatable(if (play) 1f else 0f) }
    LaunchedEffect(token, play) {
        if (play) {
            delay(delayMs.toLong())
            grow.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            delay(350)
            fade.animateTo(0f, tween(600))
        }
    }
    return drawBehind {
        val a = fade.value
        if (a > 0f && grow.value > 0f) {
            val h = size.height * 0.45f
            drawRoundRect(
                p.sun.copy(alpha = 0.5f * a),
                Offset(0f, size.height - h - 1.dp.toPx()),
                Size(size.width * grow.value, h),
                CornerRadius(4.dp.toPx()),
            )
        }
    }
}
