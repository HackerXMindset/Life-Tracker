package com.lifetracker.app.ui.theme

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit

/** The five full-screen background styles available from Appearance. */
enum class BackgroundMode(val title: String) {
    Solid("Solid"),
    Gradient("Gradient"),
    Dots("Dots"),
    Oled("OLED"),
    Photo("Photo");

    companion object {
        fun parse(value: String?): BackgroundMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Solid
    }
}

/**
 * User-owned background settings. Light and dark variants are stored separately, so switching app theme
 * does not leave dark text on a dark backdrop (or light text on a light backdrop).
 */
@Immutable
data class BackgroundStyle(
    val mode: BackgroundMode = BackgroundMode.Solid,
    val lightSolidArgb: Int = 0xFFFFFCF7.toInt(),
    val darkSolidArgb: Int = 0xFF1F1A14.toInt(),
    val lightGradientStartArgb: Int = 0xFFFFF1DC.toInt(),
    val lightGradientEndArgb: Int = 0xFFDDE7F5.toInt(),
    val darkGradientStartArgb: Int = 0xFF29233B.toInt(),
    val darkGradientEndArgb: Int = 0xFF121217.toInt(),
    val lightDotsBackgroundArgb: Int = 0xFFFFFCF7.toInt(),
    val darkDotsBackgroundArgb: Int = 0xFF1F1A14.toInt(),
    val lightDotsArgb: Int = 0x667B7B7B,
    val darkDotsArgb: Int = 0x668F8FA0,
    val photoUri: String? = null,
) {
    fun solidArgb(dark: Boolean): Int = if (dark) darkSolidArgb else lightSolidArgb
    fun gradientStartArgb(dark: Boolean): Int =
        if (dark) darkGradientStartArgb else lightGradientStartArgb
    fun gradientEndArgb(dark: Boolean): Int =
        if (dark) darkGradientEndArgb else lightGradientEndArgb
    fun dotsBackgroundArgb(dark: Boolean): Int =
        if (dark) darkDotsBackgroundArgb else lightDotsBackgroundArgb
    fun dotsArgb(dark: Boolean): Int = if (dark) darkDotsArgb else lightDotsArgb
}

/** Small local preference store; no database migration or network access is required. */
object BackgroundSettings {
    private const val PREFS = "background_settings"

    var style by mutableStateOf(BackgroundStyle())
        private set

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val prefs = prefs(context)
        style = BackgroundStyle(
            mode = BackgroundMode.parse(prefs.getString("mode", null)),
            lightSolidArgb = prefs.getInt("light_solid", 0xFFFFFCF7.toInt()),
            darkSolidArgb = prefs.getInt("dark_solid", 0xFF1F1A14.toInt()),
            lightGradientStartArgb = prefs.getInt("light_gradient_start", 0xFFFFF1DC.toInt()),
            lightGradientEndArgb = prefs.getInt("light_gradient_end", 0xFFDDE7F5.toInt()),
            darkGradientStartArgb = prefs.getInt("dark_gradient_start", 0xFF29233B.toInt()),
            darkGradientEndArgb = prefs.getInt("dark_gradient_end", 0xFF121217.toInt()),
            lightDotsBackgroundArgb = prefs.getInt("light_dots_bg", 0xFFFFFCF7.toInt()),
            darkDotsBackgroundArgb = prefs.getInt("dark_dots_bg", 0xFF1F1A14.toInt()),
            lightDotsArgb = prefs.getInt("light_dots", 0x667B7B7B),
            darkDotsArgb = prefs.getInt("dark_dots", 0x668F8FA0),
            photoUri = prefs.getString("photo_uri", null),
        )
        loaded = true
    }

    fun set(context: Context, value: BackgroundStyle) {
        loaded = true
        style = value
        prefs(context).edit {
            putString("mode", value.mode.name)
            putInt("light_solid", value.lightSolidArgb)
            putInt("dark_solid", value.darkSolidArgb)
            putInt("light_gradient_start", value.lightGradientStartArgb)
            putInt("light_gradient_end", value.lightGradientEndArgb)
            putInt("dark_gradient_start", value.darkGradientStartArgb)
            putInt("dark_gradient_end", value.darkGradientEndArgb)
            putInt("light_dots_bg", value.lightDotsBackgroundArgb)
            putInt("dark_dots_bg", value.darkDotsBackgroundArgb)
            putInt("light_dots", value.lightDotsArgb)
            putInt("dark_dots", value.darkDotsArgb)
            if (value.photoUri == null) remove("photo_uri") else putString("photo_uri", value.photoUri)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

val LocalBackgroundStyle = staticCompositionLocalOf { BackgroundStyle() }
