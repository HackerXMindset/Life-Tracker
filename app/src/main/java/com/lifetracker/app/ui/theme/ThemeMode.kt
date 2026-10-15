package com.lifetracker.app.ui.theme

/** Light, Dark, or whatever the phone is set to. */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

object ThemeModes {
    /** True when the dark paper should be used. */
    fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    /** Reads a saved name back. Anything unknown means System. */
    fun parse(text: String?): ThemeMode = ThemeMode.entries.firstOrNull { it.name == text } ?: ThemeMode.System
}
