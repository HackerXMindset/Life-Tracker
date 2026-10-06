package com.lifetracker.app.ui

import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** Minutes after midnight (0..1439) as a clock time such as "8:15 AM". */
fun formatMinute(minute: Int): String =
    LocalTime.of((minute / 60).coerceIn(0, 23), (minute % 60).coerceIn(0, 59)).format(TIME_FORMAT)

/** A length of time such as "45m", "3h" or "2h 05m". */
fun formatDuration(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m.toString().padStart(2, '0')}m"
    }
}
