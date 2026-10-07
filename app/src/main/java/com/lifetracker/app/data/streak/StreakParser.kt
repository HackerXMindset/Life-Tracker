package com.lifetracker.app.data.streak

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads a Streak backup (`backup.json`). Streak's own format is what it is, so
 * this is deliberately forgiving: one odd record is skipped instead of
 * failing the whole import, but a file that is not a Streak backup at all is
 * refused with a readable reason.
 */
object StreakParser {
    fun parse(text: String): StreakData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is not a Streak backup.")
        }
        require(root.optString("app") == "streak") { "This file is not a Streak backup." }

        return StreakData(
            exportedAt = root.optString("exportedAt"),
            habits = records(root, "habits").mapNotNull { guarded { habit(it) } },
            categories = records(root, "categories").mapNotNull { guarded { category(it) } },
            notes = records(root, "notes").mapNotNull { guarded { note(it) } },
            focus = records(root, "focus").mapNotNull { guarded { focus(it) } },
            todos = records(root, "todos").mapNotNull { guarded { todo(it) } },
            todoTags = records(root, "todoTags").mapNotNull { guarded { todoTag(it) } },
        )
    }

    /** Streak writes dates as day-month-year, for example "04-10-2026". Returns null if it is not one. */
    fun dayKey(value: String?): LocalDate? {
        if (value == null || value.length != 10) return null
        return try {
            LocalDate.of(
                value.substring(6).toInt(),
                value.substring(3, 5).toInt(),
                value.substring(0, 2).toInt(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Streak writes times without a time zone, for example "2026-09-26T14:31:40.605276". */
    fun moment(value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value)
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun <T> guarded(block: () -> T?): T? = try {
        block()
    } catch (e: JSONException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private fun records(root: JSONObject, key: String): List<JSONObject> {
        val array: JSONArray = root.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.text(key: String, fallback: String = ""): String =
        if (isNull(key)) fallback else getString(key)

    private fun JSONObject.textOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    private fun JSONObject.number(key: String, fallback: Double): Double =
        if (isNull(key)) fallback else getDouble(key)

    private fun habit(o: JSONObject): StreakHabit {
        val completions = ArrayList<StreakCompletion>()
        val map = o.optJSONObject("completions")
        if (map != null) {
            for (key in map.keys()) {
                val c = map.optJSONObject(key) ?: continue
                val day = dayKey(c.textOrNull("date")) ?: dayKey(key) ?: continue
                val hour = c.intOrNull("hour")
                completions += StreakCompletion(
                    date = day,
                    count = c.number("numberOfCompletions", c.number("count", 1.0)),
                    minuteOfDay = hour?.let { it * 60 + (c.intOrNull("minute") ?: 0) },
                )
            }
        }
        val raw = JSONObject(o.toString()).also { it.remove("completions") }.toString()
        return StreakHabit(
            id = o.getString("id"),
            name = o.text("name"),
            icon = o.text("icon", "target"),
            category = o.text("category"),
            color = if (o.isNull("color")) 0xFF738078 else o.getLong("color"),
            order = o.intOrNull("order") ?: 0,
            kind = o.intOrNull("kind") ?: 0,
            quantKind = o.intOrNull("quantKind") ?: 0,
            unitLabel = o.text("unitLabel"),
            target = o.number("numberOfCompletionsPerDay", 1.0),
            step = o.number("incrementAmount", 1.0),
            anyAmount = !o.isNull("anyAmount") && o.getBoolean("anyAmount"),
            startMinute = o.intOrNull("startMinute") ?: -1,
            durationMinutes = o.intOrNull("durationMinutes") ?: 0,
            createdOn = moment(o.textOrNull("createdAt"))?.toLocalDate(),
            archived = !o.isNull("archivedAt"),
            completions = completions,
            raw = raw,
        )
    }

    private fun category(o: JSONObject) = StreakCategory(
        id = o.getString("id"),
        name = o.getString("name"),
        color = if (o.isNull("color")) 0xFF738078 else o.getLong("color"),
        order = o.intOrNull("order") ?: 0,
    )

    private fun note(o: JSONObject) = StreakNote(
        id = o.getString("id"),
        habitId = o.text("habitId"),
        date = dayKey(o.textOrNull("date")),
        type = (o.intOrNull("type") ?: 0).coerceIn(0, 2),
        text = o.text("text"),
        minutes = o.intOrNull("minutes"),
        createdAt = o.text("createdAt"),
    )

    private fun focus(o: JSONObject) = StreakFocus(
        id = o.getString("id"),
        habitId = o.text("habitId"),
        targetMinutes = o.intOrNull("targetMinutes") ?: 0,
        seconds = o.intOrNull("seconds") ?: 0,
        completed = !o.isNull("completed") && o.getBoolean("completed"),
        startedAt = moment(o.textOrNull("startedAt")) ?: throw IllegalArgumentException("no start time"),
        label = o.text("label"),
    )

    private fun todo(o: JSONObject): StreakTodo {
        val raw = o.toString()
        return StreakTodo(
            id = o.getString("id"),
            text = o.text("text"),
            done = !o.isNull("done") && o.getBoolean("done"),
            date = dayKey(o.textOrNull("date")),
            minutes = o.intOrNull("minutes"),
            estimate = o.intOrNull("estimate"),
            priority = o.intOrNull("priority") ?: 0,
            project = o.text("project"),
            createdAt = o.text("createdAt"),
            doneAt = o.textOrNull("doneAt"),
            raw = raw,
        )
    }

    private fun todoTag(o: JSONObject) = StreakTodoTag(
        id = o.getString("id"),
        name = o.getString("name"),
        color = if (o.isNull("color")) 0xFF738078 else o.getLong("color"),
        kind = o.text("kind", "label"),
        order = o.intOrNull("order") ?: 0,
    )
}
