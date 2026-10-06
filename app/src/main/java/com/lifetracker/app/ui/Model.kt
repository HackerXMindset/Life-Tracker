package com.lifetracker.app.ui

import androidx.compose.ui.graphics.Color
import java.time.LocalTime

enum class ActivityCategory(val label: String, val light: Color, val dark: Color) {
    Sleep("Sleep", Color(0xFF5B5FC7), Color(0xFF8C90F0)),
    Food("Food", Color(0xFFC28410), Color(0xFFE5B04A)),
    Study("Study", Color(0xFF17785A), Color(0xFF4CC79B)),
    Health("Health", Color(0xFF2878B4), Color(0xFF5BB2EA)),
    Routine("Routine", Color(0xFF738078), Color(0xFF96A39B)),
    Exercise("Exercise", Color(0xFFCC5F22), Color(0xFFF0935E)),
    Screen("Screen and leisure", Color(0xFF8A5FC2), Color(0xFFB79CF0)),
    Event("Life event", Color(0xFFB83A3A), Color(0xFFF08080)),
}

/** A logged activity. [end] is null for a single moment such as a test result. */
data class Entry(
    val start: LocalTime,
    val end: LocalTime?,
    val category: ActivityCategory,
    val title: String,
    val note: String = "",
)

/** Placeholder entries until the database arrives in the next step. */
object SampleData {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    val day: List<Entry> = listOf(
        Entry(t(0, 0), t(6, 50), ActivityCategory.Sleep, "Sleep", "6h 50m"),
        Entry(t(6, 50), t(7, 30), ActivityCategory.Routine, "Morning routine"),
        Entry(t(7, 30), t(8, 0), ActivityCategory.Food, "Breakfast", "Poha, tea"),
        Entry(t(8, 15), t(11, 15), ActivityCategory.Study, "Maths", "Chapter 7 exercises"),
        Entry(t(11, 15), t(11, 45), ActivityCategory.Screen, "Phone break", "Messages, reels"),
        Entry(t(12, 0), t(12, 40), ActivityCategory.Food, "Lunch", "Roti, sabzi, dal"),
        Entry(t(14, 0), t(16, 30), ActivityCategory.Study, "Physics", "Optics notes"),
        Entry(t(16, 45), t(17, 30), ActivityCategory.Exercise, "Cycling", "12.4 km"),
        Entry(t(17, 30), null, ActivityCategory.Event, "Mock test, Physics", "41 of 60"),
        Entry(t(18, 0), t(19, 30), ActivityCategory.Study, "Revision", "Flashcards"),
        Entry(t(20, 0), t(20, 40), ActivityCategory.Food, "Dinner"),
        Entry(t(21, 0), t(22, 45), ActivityCategory.Screen, "Anime", "4 episodes"),
        Entry(t(22, 45), t(23, 30), ActivityCategory.Routine, "Wind down"),
    )
}
