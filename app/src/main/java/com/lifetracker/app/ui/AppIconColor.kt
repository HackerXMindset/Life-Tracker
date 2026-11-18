package com.lifetracker.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.lifetracker.app.data.IconColor

/** The colour of an installed app's logo, read once and remembered (the pie colours each app by it). */
object AppIconColor {
    private val cache = HashMap<String, Int>()

    /** The logo colour of [pkg], or null when the app is not installed any more or its icon cannot be read. */
    fun of(context: Context, pkg: String): Int? {
        cache[pkg]?.let { return it }
        return try {
            val icon = context.packageManager.getApplicationIcon(pkg)
            val size = 48
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bitmap))
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            bitmap.recycle()
            IconColor.dominant(pixels).also { cache[pkg] = it }
        } catch (_: Exception) {
            null
        }
    }
}
