package com.lifetracker.app.data

import java.text.NumberFormat
import java.util.Locale

/** What a backup holds, in plain lines (for showing before a restore and after a backup). No Android, so it can be tested. */
object BackupSummary {
    class Line(val label: String, val count: Int)

    /** Everything the backup holds that is not empty, in the order a person thinks of it. */
    fun lines(d: BackupData): List<Line> {
        val all = listOf(
            Line("Timeline entries", d.entries.size),
            Line("Habits", d.habits.size),
            Line("Habit ticks", d.completions.size),
            Line("To-dos and notes", d.todos.size + d.notes.size),
            Line("Meals and water", d.meals.size + d.water.size),
            Line("Money entries", d.moneyEntries.size),
            Line("Places", d.places.size),
            Line("Place visits", d.placeVisits.size),
            Line("Trips", d.trips.size),
            Line("GPS readings", d.rawFixes.size),
            Line("Phone usage sessions", d.usageSessions.size),
            Line("Calls", d.calls.size),
            Line("Charging sessions", d.chargeSessions.size),
            Line("Days of steps", d.stepDays.size),
            Line("Nights of sleep", d.sleepNights.size),
            Line("Focus sessions", d.focusSessions.size),
            Line("Rules and goals", d.rules.size + d.goals.size),
            Line("Offline map areas (names and shapes only, not the map files)", d.mapAreas.size),
            Line("Settings", settingCount(d)),
        )
        return all.filter { it.count > 0 }
    }

    fun settingCount(d: BackupData): Int = d.settings.values.sumOf { it.size }

    /** "Habits 12 · GPS readings 41,203 ...", one line per item. */
    fun text(d: BackupData, locale: Locale = Locale.getDefault()): String {
        val f = NumberFormat.getIntegerInstance(locale)
        return lines(d).joinToString("\n") { it.label + ": " + f.format(it.count) }
    }

    /** Whether a backup carries the settings (files before version 33 do not, so a restore leaves the settings as they are). */
    fun hasSettings(d: BackupData): Boolean = d.version >= 33
}
