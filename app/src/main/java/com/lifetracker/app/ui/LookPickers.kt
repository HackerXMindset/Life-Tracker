package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The colours of Streak's swatch grid, in its order. */
val STREAK_COLORS: List<Long> = listOf(
    0xFFFF3B30L, 0xFFFF6B6BL, 0xFFC0392BL, 0xFFFF375FL,
    0xFFFF2D92L, 0xFFFF9500L, 0xFFFF6D00L, 0xFFFF8C42L,
    0xFFE67E22L, 0xFFFFCC00L, 0xFFFFD60AL, 0xFFF4D03FL,
    0xFFA8E063L, 0xFF7ED321L, 0xFF30D158L, 0xFF34C759L,
    0xFF00C853L, 0xFF2ECC71L, 0xFF1ABC9CL, 0xFF4ECDC4L,
    0xFF006689L, 0xFF26C6DAL, 0xFF00BCD4L, 0xFF5AC8FAL,
    0xFF0A84FFL, 0xFF007AFFL, 0xFF2196F3L, 0xFF3498DBL,
    0xFF5856D6L, 0xFF7C3AEDL, 0xFF9B59B6L, 0xFF6C3483L,
    0xFFBF5AF2L, 0xFFE91E8CL, 0xFFFF6EB4L, 0xFFA0522DL,
    0xFF8B6914L, 0xFF8E8E93L, 0xFF636366L, 0xFF48484AL,
    0xFFFFFFFFL, 0xFF1C1C1EL,
)

/** Turns a hue (0 to 360), saturation and value (0 to 1) into the 0xFFRRGGBB number colours are kept as. */
fun hsvToColorLong(h: Float, s: Float, v: Float): Long {
    val c = v * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
    return 0xFF000000L or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** The hue (0 to 360), saturation and value (0 to 1) of a 0xFFRRGGBB colour. */
fun colorLongToHsv(color: Long): FloatArray {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return floatArrayOf(if (h < 0f) h + 360f else h, if (max == 0f) 0f else d / max, max)
}

/** "#RRGGBB" for a colour kept as a number. */
fun hexText(color: Long): String = "#" + "%06X".format(color and 0xFFFFFFL)

/**
 * Streak's colour picker: a grid of swatches (8 in a row) and a Custom tab with a hue ring around a square for how
 * strong and how bright. [selected] and the result are 0xFFRRGGBB numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerPanel(selected: Long, onSelected: (Long) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    var custom by remember { mutableStateOf(STREAK_COLORS.none { it == selected }) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaperSegmented(listOf("Colours", "Custom"), if (custom) 1 else 0, { custom = it == 1 })
        if (custom) {
            HueRingPicker(selected, onSelected)
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gap = 10.dp
                val side = ((maxWidth - gap * 7) / 8).coerceIn(30.dp, 40.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                    STREAK_COLORS.forEach { c ->
                        val on = c == selected
                        val color = Color(c.toInt())
                        val light = (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) > 0.6f
                        Box(
                            modifier = Modifier
                                .size(side)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color)
                                .border(if (on) 2.5.dp else 1.dp, if (on) p.ink else p.line, RoundedCornerShape(10.dp))
                                .clickable { onSelected(c) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Text("✓", color = if (light) Color(0xFF1C1C1E) else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(selected.toInt())).border(1.dp, p.line, CircleShape))
            Text(hexText(selected), fontSize = 13.sp, color = p.ink2)
        }
    }
}

/** A ring of hues around a square of strengths and brightnesses, drawn and dragged by hand. */
@Composable
private fun HueRingPicker(selected: Long, onSelected: (Long) -> Unit) {
    val p = LocalPaper.current
    val start = remember { colorLongToHsv(selected) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var mode by remember { mutableIntStateOf(0) }

    fun pickAt(pos: Offset, size: IntSize) {
        val w = size.width.toFloat()
        val c = Offset(w / 2f, w / 2f)
        val half = w * 0.5f - w * 0.14f - w * 0.04f
        if (mode == 1) {
            var deg = Math.toDegrees(atan2((pos.y - c.y).toDouble(), (pos.x - c.x).toDouble())).toFloat()
            if (deg < 0f) deg += 360f
            hue = deg
        } else if (mode == 2) {
            sat = ((pos.x - (c.x - half)) / (2f * half)).coerceIn(0f, 1f)
            value = 1f - ((pos.y - (c.y - half)) / (2f * half)).coerceIn(0f, 1f)
        }
        onSelected(hsvToColorLong(hue, sat, value))
    }

    fun decide(pos: Offset, size: IntSize) {
        val w = size.width.toFloat()
        val d = hypot(pos.x - w / 2f, pos.y - w / 2f)
        mode = if (d > w * 0.5f - w * 0.14f - w * 0.02f) 1 else 2
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos -> decide(pos, size); pickAt(pos, size) },
                        onDrag = { change, _ -> change.consume(); pickAt(change.position, size) },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos -> decide(pos, size); pickAt(pos, size) }
                },
        ) {
            val w = size.width
            val c = Offset(w / 2f, w / 2f)
            val ring = w * 0.12f
            val mid = w / 2f - ring / 2f - w * 0.02f
            val hues = (0..6).map { Color(hsvToColorLong(it * 60f % 360f, 1f, 1f).toInt()) } + Color(hsvToColorLong(0f, 1f, 1f).toInt())
            drawCircle(brush = Brush.sweepGradient(hues, c), radius = mid, center = c, style = Stroke(ring))
            val half = w * 0.5f - w * 0.14f - w * 0.04f
            val topLeft = Offset(c.x - half, c.y - half)
            val box = Size(half * 2f, half * 2f)
            drawRect(Color(hsvToColorLong(hue, 1f, 1f).toInt()), topLeft, box)
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent), topLeft.x, topLeft.x + box.width), topLeft, box)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), topLeft.y, topLeft.y + box.height), topLeft, box)
            // The two handles.
            val a = Math.toRadians(hue.toDouble())
            val hp = Offset(c.x + mid * cos(a).toFloat(), c.y + mid * sin(a).toFloat())
            drawCircle(Color.White, ring * 0.42f, hp)
            drawCircle(Color(hsvToColorLong(hue, 1f, 1f).toInt()), ring * 0.32f, hp)
            val svp = Offset(topLeft.x + sat * box.width, topLeft.y + (1f - value) * box.height)
            drawCircle(Color.White, ring * 0.34f, svp, style = Stroke(3f))
            drawCircle(Color(hsvToColorLong(hue, sat, value).toInt()), ring * 0.26f, svp)
        }
    }
}

/**
 * Streak's icon picker: an Icons or Emojis switch, a row of categories and a grid of tiles. The result is a picture
 * name or an emoji; "No icon" gives an empty string. [tint] is the colour the pictures are drawn in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerPanel(selected: String, tint: Color, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    var emojis by remember { mutableStateOf(IconCatalog.isEmoji(selected)) }
    var category by remember { mutableIntStateOf(0) }
    val sets = if (emojis) IconSets.emojis else IconSets.icons
    val shown = sets[category.coerceIn(0, sets.size - 1)].second
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaperSegmented(listOf("Icons", "Emojis"), if (emojis) 1 else 0, { emojis = it == 1; category = 0 })
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sets.forEachIndexed { i, (name, _) -> PaperPill(name, i == category, { category = i }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { v ->
                val on = IconCatalog.norm(v) == IconCatalog.norm(selected) && selected.isNotEmpty()
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) p.surface2 else p.surface)
                        .border(if (on) 2.dp else 1.dp, if (on) p.ink else p.line, RoundedCornerShape(10.dp))
                        .clickable { onSelected(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    IconGlyph(icon = v, tint = tint, size = 26.dp)
                }
            }
        }
        if (selected.isNotEmpty()) {
            PaperButton("No icon", { onSelected("") }, style = PaperButtonStyle.Ghost, small = true)
        }
    }
}
