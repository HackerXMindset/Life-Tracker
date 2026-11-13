package com.lifetracker.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.MealPhotos
import com.lifetracker.app.ui.theme.LocalPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Food photos from the app's assets, decoded once and kept in memory. */
object FoodPhotos {
    private val cache = LruCache<String, ImageBitmap>(200)

    fun cached(asset: String?): ImageBitmap? = if (asset == null) null else cache.get(asset)

    fun load(context: Context, asset: String): ImageBitmap? {
        cache.get(asset)?.let { return it }
        val bitmap = runCatching {
            context.applicationContext.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap() ?: return null
        cache.put(asset, bitmap)
        return bitmap
    }
}

/** A food's photo in a small paper tile. Without a photo it shows the food's first letter. */
@Composable
fun FoodPhoto(asset: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    val context = LocalContext.current
    // Keyed by the asset: a grid reuses a cell for another food when you change category or scroll, and the old picture must not stay.
    var bitmap by remember(asset) { mutableStateOf(FoodPhotos.cached(asset)) }
    LaunchedEffect(asset) {
        if (asset != null && bitmap == null) bitmap = withContext(Dispatchers.IO) { FoodPhotos.load(context, asset) }
    }
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier = modifier
            .size(size)
            .background(p.surface2, shape)
            .border(1.25.dp, p.line, shape)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size / 12),
            )
        } else {
            Text(
                text = name.trim().take(1).uppercase(),
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                color = p.ink2,
            )
        }
    }
}

/** Up to three photos side by side, overlapping, like a plate; a dashed empty slot with a plus when there is nothing yet. */
@Composable
fun FoodPlate(items: List<Pair<String?, String>>, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    if (items.isEmpty()) {
        val shape = RoundedCornerShape(size / 4)
        Box(
            modifier = modifier.size(size).border(1.5.dp, p.line, shape).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            PaperIcon(R.drawable.ic_p_plus, size = size / 3, tint = p.ink2)
        }
        return
    }
    val shown = items.take(3)
    val step = size * 0.42f
    Box(modifier = modifier.width(size + step * (shown.size - 1)).size(width = size + step * (shown.size - 1), height = size)) {
        shown.asReversed().forEachIndexed { i, (asset, name) ->
            val slot = shown.size - 1 - i
            FoodPhoto(asset, name, size, modifier = Modifier.offset(x = step * slot))
        }
    }
}

/** The small photo kept with a meal that was added from a photo; any other meal shows its food's picture. */
@Composable
fun MealThumb(mealId: String, asset: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val own by produceState<ImageBitmap?>(null, mealId) { value = MealPhotos.load(context, mealId)?.asImageBitmap() }
    val image = own
    if (image == null) {
        FoodPhoto(asset, name, size, modifier)
    } else {
        val shape = RoundedCornerShape(size / 4)
        Box(modifier.size(size).border(1.25.dp, p.line, shape).clip(shape)) {
            Image(bitmap = image, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
