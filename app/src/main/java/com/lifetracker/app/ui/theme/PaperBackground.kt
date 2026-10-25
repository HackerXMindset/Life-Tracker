package com.lifetracker.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the selected background once behind all app screens. Cards and controls are composed afterward
 * and are not modified by the background style.
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val paper = LocalPaper.current
    val style = LocalBackgroundStyle.current
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(
        initialValue = null,
        key1 = style.photoUri,
        key2 = style.mode,
    ) {
        value = if (style.mode == BackgroundMode.Photo && style.photoUri != null) {
            runCatching { decodeBackgroundPhoto(context, style.photoUri) }.getOrNull()
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                when (style.mode) {
                    BackgroundMode.Solid -> drawRect(Color(style.solidArgb(paper.dark)))
                    BackgroundMode.Gradient -> drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(style.gradientStartArgb(paper.dark)),
                                Color(style.gradientEndArgb(paper.dark)),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                    BackgroundMode.Dots -> {
                        drawRect(Color(style.dotsBackgroundArgb(paper.dark)))
                        val gap = 24.dp.toPx()
                        val radius = 1.15.dp.toPx()
                        val dotColor = Color(style.dotsArgb(paper.dark))
                        var y = gap / 2f
                        while (y < size.height) {
                            var x = gap / 2f
                            while (x < size.width) {
                                drawCircle(dotColor, radius, Offset(x, y))
                                x += gap
                            }
                            y += gap
                        }
                    }
                    BackgroundMode.Oled -> drawRect(if (paper.dark) Color.Black else Color.White)
                    BackgroundMode.Photo -> drawRect(Color(style.solidArgb(paper.dark)))
                }
            },
        content = {
            if (style.mode == BackgroundMode.Photo && photo != null) {
                Image(
                    bitmap = photo!!.asImageBitmap(),
                    contentDescription = "Selected background photo",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            if (paper.dark) Color.Black.copy(alpha = 0.48f)
                            else Color.White.copy(alpha = 0.42f),
                        ),
                )
            }
            content()
        },
    )
}

/** Decode off the main thread and cap the largest image edge to keep memory use reasonable. */
private suspend fun decodeBackgroundPhoto(context: Context, uriText: String): Bitmap? =
    withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriText)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 2048) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val imageStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        imageStream.use { BitmapFactory.decodeStream(it, null, options) }
    }
