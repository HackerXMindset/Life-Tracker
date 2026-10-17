package com.lifetracker.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.exifinterface.media.ExifInterface
import com.caverock.androidsvg.SVG
import com.lifetracker.app.MainActivity
import com.lifetracker.app.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Keeps the user's custom home-screen identity on this phone and creates its launcher shortcut. */
object AppBranding {
    private const val PREFS = "app_branding"
    private const val NAME_KEY = "shortcut_name"
    private const val DESIGN_IDS_KEY = "saved_design_ids"
    private const val PNG_FILE = "custom_home_icon.png"
    // Keep supporting SVGs selected in builds of the first version of this feature.
    private const val LEGACY_SVG_FILE = "custom_home_icon.svg"
    private const val SHORTCUT_ID = "life_tracker_custom_identity"
    private const val MAX_IMAGE_BYTES = 25 * 1024 * 1024
    private const val WORKING_IMAGE_EDGE = 2048
    private const val ICON_EDGE = 512

    enum class ShortcutResult { PinRequested, Updated, Unsupported, UpdateFailed }

    /** A locally saved name-and-icon pair that can be restored later. */
    data class SavedDesign(val id: String, val name: String)

    fun savedName(context: Context): String =
        preferences(context).getString(NAME_KEY, null)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)

    fun saveName(context: Context, name: String) {
        preferences(context).edit {
            if (name.isBlank()) remove(NAME_KEY) else putString(NAME_KEY, name.trim())
        }
    }

    fun hasCustomIcon(context: Context): Boolean =
        iconFile(context).isFile || legacySvgFile(context).isFile

    /**
     * Loads a local raster image or SVG from Android's document picker.
     * SVGs are rendered into a square viewport so the full viewBox is fitted, not cropped.
     */
    fun decodeImage(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_IMAGE_BYTES) { "Choose an image smaller than 25 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("The selected image could not be opened.")

        val mime = resolver.getType(uri).orEmpty()
        val header = bytes.copyOfRange(0, min(bytes.size, 4096)).toString(Charsets.UTF_8)
        val isSvg = mime.contains("svg", ignoreCase = true) ||
            Regex("<svg(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(header)

        return if (isSvg) {
            renderSvg(bytes.toString(Charsets.UTF_8), WORKING_IMAGE_EDGE)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "This image format is not supported by your phone."
            }

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > WORKING_IMAGE_EDGE) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: throw IllegalArgumentException("This image format is not supported by your phone.")

            applyExifOrientation(bytes, bitmap)
        }
    }

    /** Renders an SVG via its viewport, preserving the document aspect ratio and keeping the complete viewBox visible. */
    private fun renderSvg(svgText: String, edge: Int): Bitmap {
        val svg = SVG.getFromString(svgText)
        if (svg.documentViewBox == null) {
            val width = svg.documentWidth
            val height = svg.documentHeight
            require(width > 0f && height > 0f) { "This SVG needs a valid viewBox or explicit width and height." }
            svg.setDocumentViewBox(0f, 0f, width, height)
        }

        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        // Supplying the destination viewport lets AndroidSVG fit the SVG into the whole square.
        // Drawing a Picture into another Rect can apply confusing sizing to SVGs with intrinsic dimensions.
        svg.renderToCanvas(Canvas(bitmap), RectF(0f, 0f, edge.toFloat(), edge.toFloat()))
        return bitmap
    }

    private fun applyExifOrientation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }

        return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
        )
    }

    /** Fits the complete image on a transparent square canvas without cropping any artwork. */
    fun fitBitmap(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(ICON_EDGE, ICON_EDGE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val scale = min(ICON_EDGE.toFloat() / source.width, ICON_EDGE.toFloat() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        val destination = RectF((ICON_EDGE - width) / 2f, (ICON_EDGE - height) / 2f,
            (ICON_EDGE + width) / 2f, (ICON_EDGE + height) / 2f)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, destination, paint)
        return result
    }

    /** Writes the cropped, square logo into private app storage. */
    fun saveCustomIcon(context: Context, bitmap: Bitmap) {
        val target = iconFile(context)
        val temporary = File(context.filesDir, "$PNG_FILE.tmp")
        FileOutputStreamCompat.writePng(temporary, bitmap)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        // The PNG takes precedence; retaining the old SVG is harmless, but no longer necessary.
        legacySvgFile(context).delete()
    }

    fun clearCustomIcon(context: Context) {
        iconFile(context).delete()
        legacySvgFile(context).delete()
    }

    /**
     * Crops a square from the image using the same zoom and pan as the editor, then returns a 512px launcher icon.
     * Offsets and viewportSizePx are display pixels, matching Compose's pointer/graphics-layer coordinates.
     */
    fun cropBitmap(
        source: Bitmap,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        viewportSizePx: Float,
    ): Bitmap {
        val displayScale = max(
            viewportSizePx / source.width.toFloat(),
            viewportSizePx / source.height.toFloat(),
        ) * zoom.coerceAtLeast(1f)
        val side = (viewportSizePx / displayScale)
            .coerceAtMost(min(source.width, source.height).toFloat())
            .coerceAtLeast(1f)
        val left = ((source.width - side) / 2f - offsetX / displayScale)
            .coerceIn(0f, source.width - side)
        val top = ((source.height - side) / 2f - offsetY / displayScale)
            .coerceIn(0f, source.height - side)

        val sideInt = side.roundToInt().coerceAtLeast(1).coerceAtMost(min(source.width, source.height))
        val leftInt = left.roundToInt().coerceIn(0, source.width - sideInt)
        val topInt = top.roundToInt().coerceIn(0, source.height - sideInt)
        val cropped = Bitmap.createBitmap(source, leftInt, topInt, sideInt, sideInt)
        return if (cropped.width == ICON_EDGE && cropped.height == ICON_EDGE) cropped
        else Bitmap.createScaledBitmap(cropped, ICON_EDGE, ICON_EDGE, true).also {
            if (cropped !== source) cropped.recycle()
        }
    }

    /** Produces a bitmap for the launcher shortcut, supporting both new PNGs and SVGs saved by the earlier PR version. */
    fun createIconBitmap(context: Context, size: Int = 192): Bitmap {
        val appContext = context.applicationContext
        val customPng = iconFile(appContext)
        if (customPng.isFile) {
            runCatching {
                BitmapFactory.decodeFile(customPng.absolutePath)?.let { saved ->
                    if (saved.width == size && saved.height == size) return saved
                    return Bitmap.createScaledBitmap(saved, size, size, true)
                }
            }
        }

        val legacySvg = legacySvgFile(appContext)
        if (legacySvg.isFile) {
            runCatching { return renderSvg(legacySvg.readText(Charsets.UTF_8), size) }
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

    /** Saves the current logo and the supplied launcher name as a separate reusable design. */
    fun saveDesign(context: Context, requestedName: String): SavedDesign {
        val appContext = context.applicationContext
        val name = requestedName.trim().ifBlank { appContext.getString(R.string.app_name) }
        val id = UUID.randomUUID().toString().replace("-", "")
        val target = designFile(appContext, id)
        val temporary = File(appContext.filesDir, "${target.name}.tmp")
        val bitmap = createIconBitmap(appContext, ICON_EDGE)
        try {
            FileOutputStreamCompat.writePng(temporary, bitmap)
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (error: Exception) {
            temporary.delete()
            target.delete()
            throw error
        } finally {
            bitmap.recycle()
        }

        val prefs = preferences(appContext)
        val ids = prefs.getString(DESIGN_IDS_KEY, "").orEmpty()
            .split(',').filter { it.isNotBlank() }
        prefs.edit {
            putString("saved_design_name_$id", name)
            putString(DESIGN_IDS_KEY, (ids + id).joinToString(","))
        }
        return SavedDesign(id, name)
    }

    /** Returns saved designs in the order they were created, skipping missing/corrupt entries. */
    fun savedDesigns(context: Context): List<SavedDesign> {
        val appContext = context.applicationContext
        val prefs = preferences(appContext)
        return prefs.getString(DESIGN_IDS_KEY, "").orEmpty()
            .split(',').filter { it.matches(Regex("[a-f0-9]{32}")) }
            .mapNotNull { id ->
                val file = designFile(appContext, id)
                val name = prefs.getString("saved_design_name_$id", null)
                if (file.isFile && !name.isNullOrBlank()) SavedDesign(id, name) else null
            }
    }

    /** Loads a thumbnail of a saved design's logo, or null if the stored image cannot be read. */
    fun savedDesignIcon(context: Context, id: String, size: Int = 128): Bitmap? {
        if (!id.matches(Regex("[a-f0-9]{32}"))) return null
        val decoded = BitmapFactory.decodeFile(designFile(context, id).absolutePath) ?: return null
        if (decoded.width == size && decoded.height == size) return decoded
        return Bitmap.createScaledBitmap(decoded, size, size, true).also { decoded.recycle() }
    }

    /** Activates a saved design locally. The caller decides when to request a launcher shortcut update. */
    fun applySavedDesign(context: Context, id: String): SavedDesign {
        val appContext = context.applicationContext
        val design = savedDesigns(appContext).firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("This saved design is no longer available.")
        val source = designFile(appContext, id)
        val target = iconFile(appContext)
        val temporary = File(appContext.filesDir, "$PNG_FILE.apply.tmp")
        source.copyTo(temporary, overwrite = true)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        legacySvgFile(appContext).delete()
        saveName(appContext, design.name)
        return design
    }

    /** Removes one saved design without changing the currently active identity. */
    fun deleteSavedDesign(context: Context, id: String): Boolean {
        if (!id.matches(Regex("[a-f0-9]{32}"))) return false
        val appContext = context.applicationContext
        val prefs = preferences(appContext)
        val ids = prefs.getString(DESIGN_IDS_KEY, "").orEmpty()
            .split(',').filter { it.isNotBlank() }
        if (id !in ids) return false
        designFile(appContext, id).delete()
        prefs.edit {
            putString(DESIGN_IDS_KEY, ids.filterNot { it == id }.joinToString(","))
            remove("saved_design_name_$id")
        }
        return true
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun iconFile(context: Context) =
        File(context.applicationContext.filesDir, PNG_FILE)

    private fun designFile(context: Context, id: String): File =
        File(context.applicationContext.filesDir, "saved_home_icon_$id.png")

    private fun legacySvgFile(context: Context) =
        File(context.applicationContext.filesDir, LEGACY_SVG_FILE)

    /** Isolated so the PNG is fully written before a rename can make it the active icon. */
    private object FileOutputStreamCompat {
        fun writePng(file: File, bitmap: Bitmap) {
            file.outputStream().buffered().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "The cropped image could not be saved."
                }
            }
        }
    }
}
