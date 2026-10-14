package com.lifetracker.app.data.streak

import com.lifetracker.app.data.CategoryEntity
import com.lifetracker.app.data.CompletionEntity
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.data.NoteEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Random
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a Streak backup file (Habits H5a) so Streak can take in the changes made here, the same way it takes in a
 * second phone's backup from its sync folder.
 *
 * Streak decides what to do with each item from a "last changed" stamp, and for a habit it already has, it keeps
 * its own copy unless the stamp here is newer, then adds the days from both. So the file holds only what was
 * changed here ([changes]: id to the time in milliseconds). Everything else is left out, so Streak's own habits
 * are never overwritten by an older copy.
 */
object StreakBackupWriter {
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val EXPORTED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    /** Streak names its devices with eight hex characters. */
    fun newDeviceId(random: Random = java.security.SecureRandom()): String =
        (1..8).joinToString("") { "0123456789abcdef"[random.nextInt(16)].toString() }

    /** `streak_backup_2026-10-14_21-30-05_ab12cd34.json`. Streak ignores any file whose name does not end in `_<device>.json`. */
    fun fileName(now: LocalDateTime, device: String): String = "streak_backup_${now.format(STAMP)}_$device.json"

    /** True for a file this device wrote, whatever its time stamp. */
    fun isOurs(name: String, device: String): Boolean =
        name.startsWith("streak_backup_") && name.endsWith("_$device.json")

    /** Streak writes days as "04-10-2026". */
    fun dayKey(day: LocalDate): String = "%02d-%02d-%04d".format(day.dayOfMonth, day.monthValue, day.year)

    /**
     * The backup text, or null when nothing changed here is something Streak would take in (Streak refuses a file
     * with no habits and no notes).
     */
    fun build(
        device: String,
        now: LocalDateTime,
        habits: List<HabitEntity>,
        completions: List<CompletionEntity>,
        notes: List<NoteEntity>,
        categories: List<CategoryEntity>,
        changes: Map<String, Long>,
    ): String? {
        val changedHabits = habits.filter { it.id in changes }
        val changedNotes = notes.filter { it.id in changes }
        if (changedHabits.isEmpty() && changedNotes.isEmpty()) return null

        val byHabit = completions.groupBy { it.habitId }
        val habitArray = JSONArray()
        for (habit in changedHabits) {
            val o = runCatching { JSONObject(habit.raw) }.getOrNull() ?: continue
            o.put("id", habit.id)
            o.put("completions", completionMap(byHabit[habit.id].orEmpty()))
            habitArray.put(o)
        }
        val noteArray = JSONArray()
        for (note in changedNotes) noteArray.put(noteJson(note))
        if (habitArray.length() == 0 && noteArray.length() == 0) return null

        val used = changedHabits.map { it.category }.filter { it.isNotBlank() }.toSet()
        val categoryArray = JSONArray()
        categories.filter { it.name in used }.forEach {
            categoryArray.put(
                JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("icon", "folder").put("order", it.sortOrder),
            )
        }

        val stamps = JSONObject()
        changes.filterKeys { id -> changedHabits.any { it.id == id } || changedNotes.any { it.id == id } }
            .forEach { (id, at) -> stamps.put(id, at) }

        return JSONObject()
            .put("app", "streak")
            .put("version", 2)
            .put("exportedAt", now.format(EXPORTED))
            .put("device", device)
            .put("changes", stamps)
            .put("deleted", JSONObject())
            .put("habits", habitArray)
            .put("categories", categoryArray)
            .put("notes", noteArray)
            .put("focus", JSONArray())
            .put("todos", JSONArray())
            .put("todoTags", JSONArray())
            .put("settings", JSONObject())
            .toString(2)
    }

    private fun completionMap(list: List<CompletionEntity>): JSONObject {
        val out = JSONObject()
        for (c in list) {
            val day = runCatching { LocalDate.parse(c.date) }.getOrNull() ?: continue
            val key = dayKey(day)
            val o = JSONObject().put("date", key).put("numberOfCompletions", c.count)
            c.minuteOfDay?.let { o.put("hour", it / 60).put("minute", it % 60) }
            out.put(key, o)
        }
        return out
    }

    private fun noteJson(note: NoteEntity): JSONObject {
        val day = runCatching { LocalDate.parse(note.date) }.getOrNull()
        return JSONObject()
            .put("id", note.id)
            .put("habitId", note.habitId)
            .put("date", day?.let { dayKey(it) } ?: "")
            .put("type", note.type)
            .put("text", note.text)
            .put("minutes", note.minutes ?: JSONObject.NULL)
            .put("photos", JSONArray())
            .put("createdAt", note.createdAt)
    }
}
