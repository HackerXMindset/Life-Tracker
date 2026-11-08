package com.lifetracker.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The pieces of the Food screen that copy GymMane's Nutrition screen: the week rings, the bottle and shaker tiles,
 * the ring cards under "Details", the chart cards and the section headings.
 * Based on GymMane by InlitX (GPL-3.0): the layout, and the bottle pictures in assets/food/vessel.
 */

// ---- headings ----

/** Small capitals, and an optional "+ …" button on the right, as GymMane's section headings. */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPaper.current
    Row(modifier = modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(), modifier = Modifier.weight(1f),
            fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = p.ink2, letterSpacing = 1.4.sp,
        )
        if (action != null) PaperPill("+ $action", false, onAction)
    }
}

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    Text(
        text.uppercase(), modifier = modifier,
        fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = p.ink2, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

// ---- the day in the week strip ----

/** A day's number inside a ring that fills as the calories come in. Green near the goal, red well over it. */
@Composable
fun DayRing(day: Int, pct: Float, selected: Boolean, future: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    val colour = when {
        pct > 1.1f -> p.bad
        pct >= 0.9f -> p.accent
        else -> p.sun
    }
    Box(
        modifier = modifier.size(38.dp).then(if (selected) Modifier.background(p.ink, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!selected && !future) {
            Canvas(modifier = Modifier.size(38.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(p.surface2, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                val shown = pct.coerceIn(0f, 1f)
                if (shown > 0f) drawArc(colour, -90f, 360f * shown, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Text(
            day.toString(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            color = if (selected) p.bg else if (future) p.ink2 else p.ink,
        )
    }
}

// ---- a bottle that fills up ----

/** GymMane's bottle: the picture, with the liquid showing through its glass up to [level] (0 to 1). */
@Composable
fun PhotoVessel(
    asset: String,
    mask: String,
    ratio: Float,
    level: Float,
    height: Dp,
    top: Float,
    bottom: Float,
    curve: Float,
    liquid: Color,
    texture: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val images by produceState<Triple<ImageBitmap, ImageBitmap, ImageBitmap?>?>(null, asset, mask, texture) {
        value = withContext(Dispatchers.IO) {
            val a = FoodPhotos.load(context, asset)
            val m = FoodPhotos.load(context, mask)
            val t = texture?.let { FoodPhotos.load(context, it) }
            if (a != null && m != null) Triple(a, m, t) else null
        }
    }
    val shown by animateFloatAsState(level.coerceIn(0f, 1f), tween(700), label = "vessel")
    val loaded = images
    Canvas(
        modifier = modifier
            .size(width = height * ratio, height = height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        if (loaded == null) return@Canvas
        val (picture, glass, tex) = loaded
        val w = size.width
        val h = size.height
        val dst = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
        val edge = 4.dp.toPx()
        clipRect(edge, edge, w - edge, h - edge) {
            drawImage(glass, dstSize = dst, colorFilter = ColorFilter.tint(Color(0x33000000), BlendMode.SrcIn))
            if (shown > 0.004f) liquidInside(w, h, shown, top, bottom, curve, liquid, tex)
        }
        drawImage(glass, dstSize = dst, blendMode = BlendMode.DstIn)
        drawImage(picture, dstSize = dst, filterQuality = FilterQuality.Medium)
    }
}

private val TEXTURE_BASE = Color(0xFFC4BEB0)

private fun DrawScope.liquidInside(
    w: Float, h: Float, level: Float, top: Float, bottom: Float, curve: Float, liquid: Color, texture: ImageBitmap?,
) {
    val eh = w * curve * 2
    val floor = h * bottom
    val ceil = h * top + eh * 0.5f
    val surface = floor - (floor - ceil) * level
    val deep = lerp(liquid, Color(0xFF0B3A66), 0.55f)
    clipRect(0f, surface, w, h) {
        if (texture == null) {
            drawRect(
                Brush.verticalGradient(
                    0f to liquid.copy(alpha = 0.30f), 0.45f to liquid.copy(alpha = 0.42f), 1f to deep.copy(alpha = 0.62f),
                    startY = surface, endY = floor,
                ),
                topLeft = Offset(0f, surface), size = Size(w, h - surface),
            )
            drawRect(
                Brush.horizontalGradient(
                    0f to deep.copy(alpha = 0.75f), 0.22f to deep.copy(alpha = 0f), 0.5f to Color.Transparent,
                    0.8f to deep.copy(alpha = 0f), 1f to deep.copy(alpha = 0.8f),
                    startX = 0f, endX = w,
                ),
                topLeft = Offset(0f, surface), size = Size(w, h - surface),
            )
            fun streak(x: Float, width: Float, alpha: Float) {
                val left = w * x - w * width / 2
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = alpha), Color.White.copy(alpha = 0f)),
                        startX = left, endX = left + w * width,
                    ),
                    topLeft = Offset(left, surface), size = Size(w * width, h - surface),
                )
            }
            streak(0.27f, 0.07f, 0.32f)
            streak(0.36f, 0.03f, 0.18f)
            streak(0.74f, 0.12f, 0.12f)
        } else {
            drawRect(liquid, topLeft = Offset(0f, surface), size = Size(w, h - surface))
            val tile = texture.width / 4
            drawImage(
                texture,
                srcOffset = IntOffset(tile, 0), srcSize = IntSize(tile, texture.height),
                dstOffset = IntOffset(0, surface.roundToInt()),
                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), (h - surface).roundToInt().coerceAtLeast(1)),
                alpha = 0.55f,
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            (liquid.red / TEXTURE_BASE.red).coerceIn(0f, 2f), 0f, 0f, 0f, 0f,
                            0f, (liquid.green / TEXTURE_BASE.green).coerceIn(0f, 2f), 0f, 0f, 0f,
                            0f, 0f, (liquid.blue / TEXTURE_BASE.blue).coerceIn(0f, 2f), 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                ),
            )
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.5f), 0.24f to Color.Black.copy(alpha = 0.08f), 0.4f to Color.White.copy(alpha = 0.1f),
                    0.7f to Color.Black.copy(alpha = 0.04f), 1f to Color.Black.copy(alpha = 0.55f),
                    startX = 0f, endX = w,
                ),
                topLeft = Offset(0f, surface), size = Size(w, h - surface),
            )
        }
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), startY = surface, endY = floor + eh,
            ),
            topLeft = Offset(0f, surface), size = Size(w, h - surface),
        )
    }
    // The flat top of the liquid.
    val faceTop = surface - eh / 2
    val faceLeft = w / 2 - w * 0.52f
    val faceSize = Size(w * 1.04f, eh)
    drawOval(
        Brush.radialGradient(
            listOf(
                lerp(liquid, Color.White, if (texture == null) 0.75f else 0.45f).copy(alpha = if (texture == null) 0.7f else 0.85f),
                lerp(liquid, Color.White, if (texture == null) 0.3f else 0.1f).copy(alpha = if (texture == null) 0.35f else 0.85f),
            ),
            center = Offset(w / 2 - w * 0.1f, surface - eh * 0.15f), radius = w * 0.6f,
        ),
        topLeft = Offset(faceLeft, faceTop), size = faceSize,
    )
    drawArc(
        Color.White.copy(alpha = if (texture == null) 0.75f else 0.35f), 7f, 166f, false,
        Offset(faceLeft, faceTop), faceSize, style = Stroke(1.1.dp.toPx()),
    )
}

/** GymMane's tile: a small heading with a "…" button, the bottle, then the number and a hint. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VesselTile(
    title: String,
    onMenu: () -> Unit,
    onTap: () -> Unit,
    onHold: () -> Unit,
    value: String,
    unit: String,
    hint: String,
    hintColor: Color,
    glow: Color,
    modifier: Modifier = Modifier,
    vessel: @Composable () -> Unit,
) {
    val p = LocalPaper.current
    PaperCard(modifier = modifier, padding = 12.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(title, Modifier.weight(1f))
            PaperIconButton(R.drawable.ic_p_dots, "Options", onClick = onMenu, modifier = Modifier.size(34.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.22f), Color.Transparent)))
                .combinedClickable(onClick = onTap, onLongClick = onHold)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { vessel() }
        Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1)
            Text(unit, fontSize = 12.sp, color = p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(hint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = hintColor, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Two tiles of the same height next to each other. */
@Composable
fun TilePair(left: (@Composable (Modifier) -> Unit)?, right: (@Composable (Modifier) -> Unit)?) {
    if (left == null && right == null) return
    if (left == null || right == null) {
        (left ?: right)!!.invoke(Modifier.fillMaxWidth())
        return
    }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        left(Modifier.weight(1f).fillMaxHeight())
        right(Modifier.weight(1f).fillMaxHeight())
    }
}

/** The shake colour: a guess from its name. */
fun shakeLiquid(name: String?): Color {
    val n = name.orEmpty().lowercase()
    return when {
        "choc" in n || "cocoa" in n -> Color(0xFF6B4226)
        "straw" in n || "berry" in n -> Color(0xFFE88CA5)
        "coffee" in n || "mocha" in n -> Color(0xFF9B6B43)
        "banana" in n || "vanilla" in n -> Color(0xFFEBDDA8)
        "mango" in n -> Color(0xFFF2B33D)
        else -> Color(0xFFD9C7A3)
    }
}

// ---- the ring cards under "Details" ----

/** A ring with a picture in it, a small heading, the number and a line under it. */
@Composable
fun RingCard(icon: Int, color: Color, label: String, value: String, unit: String, sub: String, pct: Float, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    PaperCard(modifier = modifier, padding = 12.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(56.dp)) {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(p.surface2, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    val shown = pct.coerceIn(0f, 1f)
                    if (shown > 0f) drawArc(color, -90f, 360f * shown, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                PaperIcon(icon, size = 17.dp, tint = color)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = p.ink2, letterSpacing = 0.8.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1)
                    Text(" $unit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink2, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text(sub, fontSize = 11.sp, color = p.ink2, maxLines = 1)
            }
        }
    }
}

/** Rings two to a row. */
@Composable
fun RingGrid(cards: List<@Composable (Modifier) -> Unit>) {
    cards.chunked(2).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { it(Modifier.weight(1f)) }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

// ---- cards with a small line chart ----

/** The week's or the weight's card: a heading with an arrow, a big number, a few lines, and the chart. */
@Composable
fun ChartCard(
    icon: Int,
    color: Color,
    title: String,
    value: String,
    unit: String,
    lines: List<String>,
    chart: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PaperIcon(icon, size = 16.dp, tint = color)
            Kicker(title, Modifier.weight(1f))
            if (onClick != null) PaperIcon(R.drawable.ic_p_chev, size = 14.dp, tint = p.ink2)
        }
        Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = p.ink, maxLines = 1)
            if (unit.isNotEmpty()) Text(" $unit", fontSize = 12.5.sp, color = p.ink2, modifier = Modifier.padding(bottom = 4.dp), maxLines = 1)
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp), horizontalAlignment = Alignment.End) {
                lines.forEach { Text(it, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End) }
            }
        }
        if (chart != null) Box(modifier = Modifier.padding(top = 8.dp)) { chart() }
    }
}

/** Points joined by a line, a dashed goal line, labels along the bottom. A null value leaves a gap. */
@Composable
fun ValueLineChart(
    values: List<Double?>,
    labels: List<String>,
    color: Color,
    goal: Double? = null,
    highlight: Int = -1,
    fromZero: Boolean = true,
    height: Dp = 130.dp,
    tone: ((Double) -> Color)? = null,
) {
    val p = LocalPaper.current
    val present = values.filterNotNull()
    if (present.isEmpty()) return
    var low = minOf(present.min(), goal ?: present.min())
    var high = maxOf(present.max(), goal ?: present.max())
    if (fromZero) low = 0.0
    if (high - low < 1e-6) high = low + 1.0
    val pad = (high - low) * 0.12
    if (!fromZero) low -= pad
    high += pad
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val n = values.size
            fun x(i: Int) = if (n == 1) size.width / 2 else size.width * (i + 0.5f) / n
            fun y(v: Double) = size.height - ((v - low) / (high - low)).toFloat() * size.height
            if (goal != null) {
                drawLine(
                    p.ink2.copy(alpha = 0.6f), Offset(0f, y(goal)), Offset(size.width, y(goal)), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
            val path = Path()
            var open = false
            values.forEachIndexed { i, v ->
                if (v == null) { open = false; return@forEachIndexed }
                if (!open) { path.moveTo(x(i), y(v)); open = true } else path.lineTo(x(i), y(v))
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val dot = tone?.invoke(v) ?: color
                val big = i == highlight
                drawCircle(p.surface, if (big) 6.dp.toPx() else 4.5.dp.toPx(), Offset(x(i), y(v)))
                drawCircle(dot, if (big) 4.5.dp.toPx() else 3.dp.toPx(), Offset(x(i), y(v)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEachIndexed { i, text ->
                Text(
                    text, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.5.sp,
                    fontWeight = if (i == highlight) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (i == highlight) p.ink else p.ink2, maxLines = 1,
                )
            }
        }
    }
}

/** A small picture from assets/food/vessel. */
@Composable
fun PeekImage(name: String, height: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { FoodPhotos.load(context, "food/vessel/$name.webp") }
    }
    val bitmap = image ?: return
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier.height(height).width(height * bitmap.width / bitmap.height))
}
