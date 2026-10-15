package com.lifetracker.app.ui.theme

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lifetracker.app.R

/**
 * The sheet of paper behind the whole app, as in the demo: cream colour, two faint fold lines and age blotches (light
 * paper only), darker edges, and then the grain and loose fibres laid over everything, cards included.
 *
 * The grain and blotches are small pictures repeated like wallpaper (`paper_tooth`, `paper_stain`), not drawn live,
 * so scrolling stays smooth and the battery is spared.
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val paper = LocalPaper.current
    val context = LocalContext.current
    val density = LocalDensity.current.density
    // Each picture is made at twice the size it is shown at, so it is sharp on a phone screen.
    val tooth = remember(density) { tileBrush(context, R.drawable.paper_tooth, 512f, density) }
    val stain = remember(density) { tileBrush(context, R.drawable.paper_stain, 520f, density) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(paper.bg)
                if (!paper.dark) {
                    stain?.let { drawRect(it) }
                    fold(vertical = true, at = 0.34f)
                    fold(vertical = false, at = 0.58f)
                }
                vignette(paper.vig)
            }
            .drawWithContent {
                drawContent()
                tooth?.let { drawRect(it, alpha = 0.62f, blendMode = BlendMode.Multiply) }
            },
        content = content,
    )
}

/** A picture drawn over and over, [dp] wide on screen. Null if the picture cannot be read. */
private fun tileBrush(context: android.content.Context, res: Int, dp: Float, density: Float): ShaderBrush? {
    val options = BitmapFactory.Options().apply { inScaled = false }
    val bitmap = runCatching { BitmapFactory.decodeResource(context.resources, res, options) }.getOrNull() ?: return null
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    val scale = dp * density / bitmap.width
    shader.setLocalMatrix(Matrix().apply { setScale(scale, scale) })
    return ShaderBrush(shader)
}

/** A faint crease: a thin dark line with a light edge beside it. */
private fun DrawScope.fold(vertical: Boolean, at: Float) {
    val dark = Color(0x21462D0F) // rgba(70,45,15,.13)
    val light = Color(0x80FFFAEB) // rgba(255,250,235,.5)
    val reach = 6.dp.toPx()
    val stops = arrayOf(0f to Color.Transparent, 0.25f to dark, 0.5f to light, 1f to Color.Transparent)
    if (vertical) {
        val x = size.width * at - 2.dp.toPx()
        drawRect(
            Brush.horizontalGradient(*stops, startX = x, endX = x + reach),
            topLeft = Offset(x, 0f),
            size = Size(reach, size.height),
        )
    } else {
        val y = size.height * at - 2.dp.toPx()
        drawRect(
            Brush.verticalGradient(*stops, startY = y, endY = y + reach),
            topLeft = Offset(0f, y),
            size = Size(size.width, reach),
        )
    }
}

/** The edges of the sheet are a little darker than the middle (an oval, 130% wide and 90% tall, centred 40% down). */
private fun DrawScope.vignette(color: Color) {
    val rx = size.width * 1.3f
    val ry = size.height * 0.9f
    val center = Offset(size.width * 0.5f, size.height * 0.4f)
    withTransform({ scale(1f, ry / rx, pivot = center) }) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to color,
                center = center,
                radius = rx,
            ),
            radius = rx,
            center = center,
        )
    }
}
