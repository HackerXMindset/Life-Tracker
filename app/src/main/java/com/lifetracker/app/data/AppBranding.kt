package com.lifetracker.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.caverock.androidsvg.SVG
import com.lifetracker.app.MainActivity
import com.lifetracker.app.R
import java.io.File

/** Keeps the user's custom home-screen identity on this phone and creates its launcher shortcut. */
object AppBranding {
    private const val PREFS = "app_branding"
    private const val NAME_KEY = "shortcut_name"
    private const val SVG_FILE = "custom_home_icon.svg"
    private const val SHORTCUT_ID = "life_tracker_custom_identity"
    private const val MAX_SVG_BYTES = 1024 * 1024

    enum class ShortcutResult { PinRequested, Updated, Unsupported, UpdateFailed }

    fun savedName(context: Context): String =
        preferences(context).getString(NAME_KEY, null)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)

    fun saveName(context: Context, name: String) {
        preferences(context).edit {
            if (name.isBlank()) remove(NAME_KEY) else putString(NAME_KEY, name.trim())
        }
    }

    fun hasCustomIcon(context: Context): Boolean = iconFile(context).isFile

    /** Saves only valid, reasonably sized SVG text to the app's private storage. Returns an error for the UI, if any. */
    fun saveCustomSvg(context: Context, svgText: String): String? {
        val bytes = svgText.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_SVG_BYTES) {
            return "Choose an SVG file smaller than 1 MB."
        }

        return try {
            // Render once as validation so an unsupported or malformed file is not saved as the current icon.
            SVG.getFromString(svgText).renderToPicture(192, 192)
            val target = iconFile(context)
            val temporary = File(context.filesDir, "$SVG_FILE.tmp")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            null
        } catch (_: Exception) {
            File(context.filesDir, "$SVG_FILE.tmp").delete()
            "That file could not be read as a valid SVG. Choose another file."
        }
    }

    fun clearCustomIcon(context: Context) {
        iconFile(context).delete()
    }

    /** Produces a bitmap for the launcher shortcut, using the original app icon when no SVG is selected. */
    fun createIconBitmap(context: Context, size: Int = 192): Bitmap {
        val appContext = context.applicationContext
        val customFile = iconFile(appContext)
        if (customFile.isFile) {
            runCatching {
                val picture = SVG.getFromString(customFile.readText(Charsets.UTF_8))
                    .renderToPicture(size, size)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawPicture(picture, Rect(0, 0, size, size))
                return bitmap
            }
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val drawable = appContext.packageManager.getApplicationIcon(appContext.packageName)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    fun createOrUpdateShortcut(context: Context, requestedName: String): ShortcutResult {
        val appContext = context.applicationContext
        val name = requestedName.trim().ifEmpty { appContext.getString(R.string.app_name) }
        val shortcut = ShortcutInfoCompat.Builder(appContext, SHORTCUT_ID)
            // Android launchers prefer a concise short label. The full saved name remains editable in Settings.
            .setShortLabel(name.take(25))
            .setLongLabel(name.take(100))
            .setIcon(IconCompat.createWithBitmap(createIconBitmap(appContext)))
            .setIntent(
                Intent(appContext, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
            .setLongLived(true)
            .build()

        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)) {
            return ShortcutResult.Unsupported
        }

        val alreadyPinned = ShortcutManagerCompat
            .getShortcuts(appContext, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .any { it.id == SHORTCUT_ID }

        return if (alreadyPinned) {
            if (ShortcutManagerCompat.updateShortcuts(appContext, listOf(shortcut))) {
                ShortcutResult.Updated
            } else {
                ShortcutResult.UpdateFailed
            }
        } else if (ShortcutManagerCompat.requestPinShortcut(appContext, shortcut, null)) {
            ShortcutResult.PinRequested
        } else {
            ShortcutResult.Unsupported
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun iconFile(context: Context) =
        File(context.applicationContext.filesDir, SVG_FILE)
}
