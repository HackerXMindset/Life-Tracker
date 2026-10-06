package com.lifetracker.app.ui

import androidx.compose.ui.graphics.Color

enum class ActivityCategory(val label: String, val light: Color, val dark: Color) {
    Sleep("Sleep", Color(0xFF5B5FC7), Color(0xFF8C90F0)),
    Food("Food", Color(0xFFC28410), Color(0xFFE5B04A)),
    Study("Study", Color(0xFF17785A), Color(0xFF4CC79B)),
    Health("Health", Color(0xFF2878B4), Color(0xFF5BB2EA)),
    Routine("Routine", Color(0xFF738078), Color(0xFF96A39B)),
    Exercise("Exercise", Color(0xFFCC5F22), Color(0xFFF0935E)),
    Screen("Screen and leisure", Color(0xFF8A5FC2), Color(0xFFB79CF0)),
    Event("Life event", Color(0xFFB83A3A), Color(0xFFF08080));

    companion object {
        fun fromKey(key: String): ActivityCategory =
            entries.firstOrNull { it.name == key } ?: Routine
    }
}
