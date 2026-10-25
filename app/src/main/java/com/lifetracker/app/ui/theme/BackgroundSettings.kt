package com.lifetracker.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Background styles, independent of the app's light/dark component theme. */
enum class BackgroundMode(val label: String) {
    SOLID("Solid"),
    GRADIENT("Gradient"),
    DOTS("Dots"),
    OLED("OLED"),
    PHOTO("Custom photo");

    companion object {
        fun parse(value: String?): BackgroundMode =
            entries.firstOrNull { it.name == value } ?: SOLID
    }
}

/** Colours are stored separately for light and dark themes so their text remains readable. */
data class BackgroundPreferences(
    val mode: BackgroundMode = BackgroundMode.SOLID,
    val solidLight: Int = 0xFFFFFCF7.toInt(),
    val solidDark: Int = 0xFF1F1A14.toInt(),
    val gradientStartLight: Int = 0xFFFFFCF7.toInt(),
    val gradientEndLight: Int = 0xFFF0DFC0.toInt(),
    val gradientStartDark: Int = 0xFF1F1A14.toInt(),
    val gradientEndDark: Int = 0xFF382F23.toInt(),
    val dotsBackgroundLight: Int = 0xFFFFFCF7.toInt(),
    val dotsColorLight: Int = 0xFFD6C6A6.toInt(),
    val dotsBackgroundDark: Int = 0xFF1F1A14.toInt(),
    val dotsColorDark: Int = 0xFF4D4130.toInt(),
    val photoUri: String? = null,
) {
    fun solid(dark: Boolean): Int = if (dark) solidDark else solidLight
    fun gradientStart(dark: Boolean): Int = if (dark) gradientStartDark else gradientStartLight
    fun gradientEnd(dark: Boolean): Int = if (dark) gradientEndDark else gradientEndLight
    fun dotsBackground(dark: Boolean): Int = if (dark) dotsBackgroundDark else dotsBackgroundLight
    fun dotsColor(dark: Boolean): Int = if (dark) dotsColorDark else dotsColorLight
}

/** Small, local-only settings store. Preferences update Compose immediately and survive app restarts. */
object BackgroundSettings {
    var preferences by mutableStateOf(BackgroundPreferences())
        private set

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val store = prefs(context)
        preferences = BackgroundPreferences(
            mode = BackgroundMode.parse(store.getString("mode", null)),
            solidLight = store.getInt("solid_light", 0xFFFFFCF7.toInt()),
            solidDark = store.getInt("solid_dark", 0xFF1F1A14.toInt()),
            gradientStartLight = store.getInt("gradient_start_light", 0xFFFFFCF7.toInt()),
            gradientEndLight = store.getInt("gradient_end_light", 0xFFF0DFC0.toInt()),
            gradientStartDark = store.getInt("gradient_start_dark", 0xFF1F1A14.toInt()),
            gradientEndDark = store.getInt("gradient_end_dark", 0xFF382F23.toInt()),
            dotsBackgroundLight = store.getInt("dots_background_light", 0xFFFFFCF7.toInt()),
            dotsColorLight = store.getInt("dots_color_light", 0xFFD6C6A6.toInt()),
            dotsBackgroundDark = store.getInt("dots_background_dark", 0xFF1F1A14.toInt()),
            dotsColorDark = store.getInt("dots_color_dark", 0xFF4D4130.toInt()),
            photoUri = store.getString("photo_uri", null),
        )
    }

    fun setMode(context: Context, mode: BackgroundMode) = update(context, preferences.copy(mode = mode))

    fun setSolidColor(context: Context, dark: Boolean, argb: Int) = update(
        context,
        if (dark) preferences.copy(solidDark = argb) else preferences.copy(solidLight = argb),
    )

    fun setGradientStart(context: Context, dark: Boolean, argb: Int) = update(
        context,
        if (dark) preferences.copy(gradientStartDark = argb) else preferences.copy(gradientStartLight = argb),
    )

    fun setGradientEnd(context: Context, dark: Boolean, argb: Int) = update(
        context,
        if (dark) preferences.copy(gradientEndDark = argb) else preferences.copy(gradientEndLight = argb),
    )

    fun setDotsBackground(context: Context, dark: Boolean, argb: Int) = update(
        context,
        if (dark) preferences.copy(dotsBackgroundDark = argb) else preferences.copy(dotsBackgroundLight = argb),
    )

    fun setDotsColor(context: Context, dark: Boolean, argb: Int) = update(
        context,
        if (dark) preferences.copy(dotsColorDark = argb) else preferences.copy(dotsColorLight = argb),
    )

    fun setPhotoUri(context: Context, uri: String?) = update(context, preferences.copy(photoUri = uri))

    private fun update(context: Context, value: BackgroundPreferences) {
        loaded = true
        preferences = value
        prefs(context).edit {
            putString("mode", value.mode.name)
            putInt("solid_light", value.solidLight)
            putInt("solid_dark", value.solidDark)
            putInt("gradient_start_light", value.gradientStartLight)
            putInt("gradient_end_light", value.gradientEndLight)
            putInt("gradient_start_dark", value.gradientStartDark)
            putInt("gradient_end_dark", value.gradientEndDark)
            putInt("dots_background_light", value.dotsBackgroundLight)
            putInt("dots_color_light", value.dotsColorLight)
            putInt("dots_background_dark", value.dotsBackgroundDark)
            putInt("dots_color_dark", value.dotsColorDark)
            if (value.photoUri == null) remove("photo_uri") else putString("photo_uri", value.photoUri)
        }
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("background_settings", Context.MODE_PRIVATE)

    /** Accepts #RGB, #RRGGBB and #AARRGGBB; returns null for incomplete or invalid input. */
    fun parseHexColor(input: String): Int? {
        var hex = input.trim().removePrefix("#")
        if (hex.length == 3 && hex.all { it.digitToIntOrNull(16) != null }) {
            hex = hex.map { "$it$it" }.joinToString("")
        }
        if (hex.length == 6 && hex.all { it.digitToIntOrNull(16) != null }) hex = "FF$hex"
        if (hex.length != 8 || !hex.all { it.digitToIntOrNull(16) != null }) return null
        return hex.toLongOrNull(16)?.toInt()
    }

    fun colorToHex(argb: Int): String = "#%08X".format(java.util.Locale.ROOT, argb.toLong() and 0xFFFFFFFFL)
}
