package com.lifetracker.app.data

import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "today", "yesterday", "3 days ago", "2 weeks ago" or a date, for how long ago something was. */
object AgoText {
    fun text(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(date, today).toInt()
        return when {
            days < 0 -> "in " + (-days) + (if (days == -1) " day" else " days")
            days == 0 -> "today"
            days == 1 -> "yesterday"
            days < 14 -> "$days days ago"
            days < 60 -> (days / 7).toString() + " weeks ago"
            else -> date.dayOfMonth.toString() + " " + date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                (if (date.year != today.year) " " + date.year else "")
        }
    }
}
