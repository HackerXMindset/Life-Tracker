package com.lifetracker.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** The chosen look (Light, Dark or System), remembered across restarts. Changing it redraws the whole app at once. */
object AppTheme {
    var mode by mutableStateOf(ThemeMode.System)
        private set

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        mode = ThemeModes.parse(prefs(context).getString("mode", null))
    }

    fun set(context: Context, value: ThemeMode) {
        loaded = true
        mode = value
        prefs(context).edit { putString("mode", value.name) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("theme", Context.MODE_PRIVATE)
}
