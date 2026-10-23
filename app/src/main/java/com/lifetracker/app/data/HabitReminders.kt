package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Habit reminders (Habits H12): what a reminder is, how it is kept in the habit's own record in Streak's shape (so it
 * travels to and from Streak), and when it rings next. No Android in it, so it can be tested.
 */
object HabitReminders {
    const val DEFAULT_SNOOZE = 15
    val SNOOZE_CHOICES = listOf(5, 10, 15)
    const val MAX_SNOOZE = 720

    /** One reminder. [days] are weekdays, 1 = Monday to 7 = Sunday. */
    data class Reminder(
        val id: String,
        val hour: Int,
        val minute: Int,
        val days: List<Int> = ALL_DAYS,
        val message: String = "",
        /** 1 = every day it is on; 2 or more = every that many days, counted from [anchorEpochDay]. */
        val everyDays: Int = 1,
        /** 0 = once a day; 1 or more = repeat every that many hours, from the time until [untilMinute]. */
        val everyHours: Int = 0,
        /** Minute of the day the hourly repeat stops (included), or null for the end of the day. */
        val untilMinute: Int? = null,
        val anchorEpochDay: Long? = null,
        val snoozeMinutes: Int = DEFAULT_SNOOZE,
    ) {
        val isInterval: Boolean get() = everyDays >= 2
        val isHourly: Boolean get() = everyHours >= 1
        val startMinute: Int get() = hour * 60 + minute
    }

    val ALL_DAYS = listOf(1, 2, 3, 4, 5, 6, 7)

    /** The reminders in a habit's record (none when it has none or the record is unreadable). */
    fun read(raw: String): List<Reminder> {
        val array = runCatching { JSONObject(raw).optJSONArray("reminders") }.getOrNull() ?: return emptyList()
        val out = ArrayList<Reminder>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            if (!o.has("hour") || !o.has("minute")) continue
            val days = (o.optJSONArray("selectedDays") ?: o.optJSONArray("days"))
                ?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.distinct().sorted() }
                ?: ALL_DAYS
            out += Reminder(
                id = id,
                hour = o.optInt("hour").coerceIn(0, 23),
                minute = o.optInt("minute").coerceIn(0, 59),
                days = days,
                message = o.optString("message", ""),
                everyDays = o.optInt("everyDays", 1).coerceAtLeast(1),
                everyHours = o.optInt("everyHours", 0).coerceAtLeast(0),
                untilMinute = if (o.isNull("untilMinute") || !o.has("untilMinute")) null else o.optInt("untilMinute"),
                anchorEpochDay = if (o.isNull("anchorEpochDay") || !o.has("anchorEpochDay")) null else o.optLong("anchorEpochDay"),
                snoozeMinutes = o.optInt("snoozeMinutes", DEFAULT_SNOOZE).coerceIn(1, MAX_SNOOZE),
            )
        }
        return out
    }

    /** The record with [reminders] in it. Keys of a reminder that this app does not know are kept. */
    fun write(raw: String, reminders: List<Reminder>): String {
        val record = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        put(record, reminders)
        return record.toString()
    }

    /** Puts [reminders] into a habit's record [record] (see [write]). */
    fun put(record: JSONObject, reminders: List<Reminder>) {
        val old = HashMap<String, JSONObject>()
        record.optJSONArray("reminders")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { old[it.optString("id")] = it }
        }
        val array = JSONArray()
        for (r in reminders) {
            val o = old[r.id] ?: JSONObject()
            o.put("id", r.id)
            o.put("hour", r.hour)
            o.put("minute", r.minute)
            o.put("selectedDays", JSONArray(r.days))
            o.put("message", r.message)
            o.put("everyDays", r.everyDays)
            o.put("everyHours", r.everyHours)
            o.put("untilMinute", r.untilMinute ?: JSONObject.NULL)
            o.put("anchorEpochDay", r.anchorEpochDay ?: JSONObject.NULL)
            o.put("snoozeMinutes", r.snoozeMinutes)
            array.put(o)
        }
        record.put("reminders", array)
    }

    /** Minutes of the day this reminder rings, in order: one, or every [Reminder.everyHours] from the time to the end time. */
    fun slots(r: Reminder): List<Int> {
        if (!r.isHourly) return listOf(r.startMinute)
        val end = r.untilMinute?.takeIf { it > r.startMinute } ?: (24 * 60 - 1)
        val out = ArrayList<Int>()
        var at = r.startMinute
        while (at <= end) {
            out += at
            at += r.everyHours * 60
        }
        return out
    }

    /**
     * Whether [day] is a day this reminder can ring on. The habit decides first ([habitRings]: scheduled, not paused, not a
     * rest day). Then, like Streak: an hourly reminder uses its weekdays; a reminder for every N days uses its start day;
     * otherwise its weekdays. A reminder for every N days with no start day of its own counts from [anchorFallback] (the day
     * the habit began, so the rhythm never moves). [habitDrives] is a habit that is itself "every N days": its own days decide, not the weekdays.
     */
    fun ringsOn(r: Reminder, day: LocalDate, anchorFallback: LocalDate, habitDrives: Boolean = false, habitRings: (LocalDate) -> Boolean = { true }): Boolean {
        if (!habitRings(day)) return false
        if (habitDrives) return true
        if (r.isInterval && !r.isHourly) {
            val anchor = r.anchorEpochDay ?: anchorFallback.toEpochDay()
            return Math.floorMod(day.toEpochDay() - anchor, r.everyDays.toLong()) == 0L
        }
        return day.dayOfWeek.value in r.days
    }

    /** The first moment after [after] this reminder rings, looking up to about a year ahead, or null when it never does. */
    fun next(
        r: Reminder,
        after: LocalDateTime,
        habitDrives: Boolean = false,
        anchorFallback: LocalDate = after.toLocalDate(),
        habitRings: (LocalDate) -> Boolean = { true },
    ): LocalDateTime? {
        val today = after.toLocalDate()
        val slots = slots(r)
        for (offset in 0..HORIZON_DAYS) {
            val day = today.plusDays(offset.toLong())
            if (!ringsOn(r, day, anchorFallback, habitDrives, habitRings)) continue
            for (slot in slots) {
                val at = day.atTime(slot / 60, slot % 60)
                if (at.isAfter(after)) return at
            }
        }
        return null
    }

    const val HORIZON_DAYS = 400

    /** What is wrong with a reminder, in plain words, or null when it can be saved. */
    fun problem(r: Reminder): String? = when {
        r.hour !in 0..23 || r.minute !in 0..59 -> "Pick a time."
        !r.isInterval && r.days.isEmpty() -> "Pick at least one day."
        r.isHourly && r.everyHours > 23 -> "Repeat every 1 to 23 hours."
        r.isHourly && r.untilMinute != null && r.untilMinute <= r.startMinute -> "The last time must be after the first."
        r.snoozeMinutes !in 1..MAX_SNOOZE -> "Snooze must be 1 to $MAX_SNOOZE minutes."
        else -> null
    }

    private val NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    /** A reminder in words: "Every day at 08:00", "Mon, Wed at 08:00", "Every 2 days at 08:00", "Every 2 hours from 09:00 to 17:00". */
    fun describe(r: Reminder): String {
        val daysText = when {
            r.days.size == 7 -> "every day"
            r.days == listOf(1, 2, 3, 4, 5) -> "on weekdays"
            r.days == listOf(6, 7) -> "on weekends"
            r.days.isEmpty() -> "never"
            else -> r.days.joinToString(", ") { NAMES[it - 1] }
        }
        val time = clock(r.startMinute)
        return when {
            r.isHourly -> {
                val every = if (r.everyHours == 1) "Every hour" else "Every ${r.everyHours} hours"
                val to = r.untilMinute?.takeIf { it > r.startMinute }?.let { " to ${clock(it)}" } ?: ""
                val on = if (r.days.size == 7) "" else ", $daysText"
                "$every from $time$to$on"
            }
            r.isInterval -> "Every ${r.everyDays} days at $time"
            else -> "${daysText.replaceFirstChar { it.uppercase() }} at $time"
        }
    }
}
