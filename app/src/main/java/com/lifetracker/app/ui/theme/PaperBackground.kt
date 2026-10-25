package com.lifetracker.app.ui.theme

import android.content.Context
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the selected background behind the entire app. Background styles intentionally avoid
 * the paper grain, folds and stains; cards and other UI components keep their existing styling.
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val paper = LocalPaper.current
    val preferences = BackgroundSettings.preferences
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = preferences.photoUri,
        key2 = preferences.mode,
    ) {
        value = if (preferences.mode == BackgroundMode.PHOTO && !preferences.photoUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { loadBackgroundPhoto(context, preferences.photoUri!!) }.getOrNull()
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                when (preferences.mode) {
                    BackgroundMode.SOLID -> drawRect(Color(if (paper.dark) preferences.solidDark else preferences.solidLight))
                    BackgroundMode.GRADIENT -> drawRect(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(preferences.gradientStart(paper.dark)),
                                Color(preferences.gradientEnd(paper.dark)),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                    BackgroundMode.DOTS -> {
                        drawRect(Color(preferences.dotsBackground(paper.dark)))
                        val spacing = 24.dp.toPx()
                        val radius = 1.4.dp.toPx()
                        var x = spacing / 2f
                        while (x < size.width) {
                            var y = spacing / 2f
                            while (y < size.height) {
                                drawCircle(
                                    color = Color(preferences.dotsColor(paper.dark)),
                                    radius = radius,
                                    center = Offset(x, y),
                                )
                                y += spacing
                            }
                            x += spacing
                        }
                    }
                    BackgroundMode.OLED -> drawRect(if (paper.dark) Color.Black else Color.White)
                    BackgroundMode.PHOTO -> drawRect(paper.bg)
                }
            },
    ) {
        if (preferences.mode == BackgroundMode.PHOTO) {
            if (photo != null) {
                Image(
                    bitmap = photo!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // A subtle, theme-matched wash keeps the foreground UI legible over busy photos.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (paper.dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.24f)),
            )
        }
        content()
    }
}

/** Decode off the main thread, downsampling very large photos to reduce memory use. */
private fun loadBackgroundPhoto(context: Context, uriString: String): ImageBitmap? {
    val uri = Uri.parse(uriString)
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 2048) sampleSize *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val imageStream = resolver.openInputStream(uri) ?: return null
    val bitmap = imageStream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    return bitmap.asImageBitmap()
}
