package com.lifetracker.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The small photo kept with a meal you added from a photo, so the log shows what you ate. It lives on this phone only
 * (not in backups) and is removed when the meal is deleted.
 */
object MealPhotos {
    private const val SIDE = 320
    private const val QUALITY = 78

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "meal-photos")

    fun file(context: Context, mealId: String): File = File(dir(context), safe(mealId) + ".jpg")

    /** Ids are made by the app, but they become file names, so keep only letters, digits and dashes. */
    fun safe(id: String): String = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "x" }

    /** Shrinks [jpeg] to a small square-ish picture and keeps it for [mealId]. */
    suspend fun save(context: Context, mealId: String, jpeg: ByteArray) = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SIDE && bounds.outHeight / (sample * 2) >= SIDE) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching
            val scale = SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            dir(context).mkdirs()
            file(context, mealId).writeBytes(out.toByteArray())
        }
        Unit
    }

    suspend fun load(context: Context, mealId: String): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(context, mealId)
        if (!f.exists()) null else runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
    }

    fun delete(context: Context, mealIds: Collection<String>) {
        for (id in mealIds) runCatching { file(context, id).delete() }
    }
}
