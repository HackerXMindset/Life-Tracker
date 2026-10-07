package com.lifetracker.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything a backup file holds, once it has been read and checked. */
data class BackupData(
    val version: Int,
    val exportedAt: String,
    val entries: List<EntryEntity>,
    val habits: List<HabitEntity> = emptyList(),
    val completions: List<CompletionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val todoTags: List<TodoTagEntity> = emptyList(),
    val todos: List<TodoEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val imported: List<ImportedItemEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = entries.isEmpty() && habits.isEmpty() && completions.isEmpty() && categories.isEmpty() &&
            todoTags.isEmpty() && todos.isEmpty() && notes.isEmpty()
}

/**
 * The backup file format: plain JSON you can open in any text editor.
 *
 * `version` lets later steps add more data without breaking old backups.
 * Version 1 held only Timeline entries; version 2 adds habits, to-dos, notes
 * and the record of what was imported. A file from a newer version of the app
 * is refused rather than half-read.
 */
object BackupCodec {
    const val APP_NAME = "life-tracker"
    const val FORMAT_VERSION = 2

    fun encode(data: BackupData): String =
        JSONObject()
            .put("app", APP_NAME)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", data.exportedAt)
            .put("entries", array(data.entries, ::entryJson))
            .put("habits", array(data.habits, ::habitJson))
            .put("completions", array(data.completions, ::completionJson))
            .put("categories", array(data.categories, ::categoryJson))
            .put("todoTags", array(data.todoTags, ::todoTagJson))
            .put("todos", array(data.todos, ::todoJson))
            .put("notes", array(data.notes, ::noteJson))
            .put("imported", array(data.imported, ::importedJson))
            .toString(2)

    /** Reads a backup. Throws [IllegalArgumentException] with a readable reason if it is not usable. */
    fun decode(text: String): BackupData {
        try {
            val root = JSONObject(text)
            require(root.optString("app") == APP_NAME) { "This is not a Life Tracker backup." }
            val version = root.optInt("version", -1)
            require(version in 1..FORMAT_VERSION) {
                "This backup was made by a newer version of the app. Update the app first."
            }
            return BackupData(
                version = version,
                exportedAt = root.optString("exportedAt"),
                entries = list(root, "entries", required = true, ::entry),
                habits = list(root, "habits", required = false, ::habit),
                completions = list(root, "completions", required = false, ::completion),
                categories = list(root, "categories", required = false, ::category),
                todoTags = list(root, "todoTags", required = false, ::todoTag),
                todos = list(root, "todos", required = false, ::todo),
                notes = list(root, "notes", required = false, ::note),
                imported = list(root, "imported", required = false, ::imported),
            )
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is damaged or is not a backup.")
        }
    }

    private fun <T> array(items: List<T>, convert: (T) -> JSONObject): JSONArray {
        val out = JSONArray()
        for (item in items) out.put(convert(item))
        return out
    }

    private fun <T> list(root: JSONObject, key: String, required: Boolean, convert: (JSONObject) -> T): List<T> {
        val array = if (required) root.getJSONArray(key) else root.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { convert(array.getJSONObject(it)) }
    }

    private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun entryJson(e: EntryEntity) = JSONObject()
        .put("id", e.id).put("date", e.date).put("startMinute", e.startMinute)
        .put("endMinute", e.endMinute ?: JSONObject.NULL)
        .put("category", e.category).put("title", e.title).put("note", e.note)

    private fun entry(o: JSONObject) = EntryEntity(
        id = o.getLong("id"),
        date = o.getString("date"),
        startMinute = o.getInt("startMinute"),
        endMinute = o.intOrNull("endMinute"),
        category = o.getString("category"),
        title = o.getString("title"),
        note = o.optString("note", ""),
    )

    private fun habitJson(h: HabitEntity) = JSONObject()
        .put("id", h.id).put("name", h.name).put("icon", h.icon).put("category", h.category)
        .put("color", h.color).put("kind", h.kind).put("quantKind", h.quantKind)
        .put("unitLabel", h.unitLabel).put("target", h.target).put("step", h.step)
        .put("anyAmount", h.anyAmount).put("startMinute", h.startMinute)
        .put("durationMinutes", h.durationMinutes).put("startedOn", h.startedOn)
        .put("archived", h.archived).put("sortOrder", h.sortOrder).put("raw", h.raw)

    private fun habit(o: JSONObject) = HabitEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        icon = o.getString("icon"),
        category = o.getString("category"),
        color = o.getLong("color"),
        kind = o.getInt("kind"),
        quantKind = o.getInt("quantKind"),
        unitLabel = o.getString("unitLabel"),
        target = o.getDouble("target"),
        step = o.getDouble("step"),
        anyAmount = o.getBoolean("anyAmount"),
        startMinute = o.getInt("startMinute"),
        durationMinutes = o.getInt("durationMinutes"),
        startedOn = o.getString("startedOn"),
        archived = o.getBoolean("archived"),
        sortOrder = o.getInt("sortOrder"),
        raw = o.getString("raw"),
    )

    private fun completionJson(c: CompletionEntity) = JSONObject()
        .put("habitId", c.habitId).put("date", c.date).put("count", c.count)
        .put("minuteOfDay", c.minuteOfDay ?: JSONObject.NULL)

    private fun completion(o: JSONObject) = CompletionEntity(
        habitId = o.getString("habitId"),
        date = o.getString("date"),
        count = o.getDouble("count"),
        minuteOfDay = o.intOrNull("minuteOfDay"),
    )

    private fun categoryJson(c: CategoryEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("color", c.color).put("sortOrder", c.sortOrder)

    private fun category(o: JSONObject) = CategoryEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        sortOrder = o.getInt("sortOrder"),
    )

    private fun todoTagJson(t: TodoTagEntity) = JSONObject()
        .put("id", t.id).put("name", t.name).put("color", t.color).put("kind", t.kind)
        .put("sortOrder", t.sortOrder)

    private fun todoTag(o: JSONObject) = TodoTagEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        kind = o.getString("kind"),
        sortOrder = o.getInt("sortOrder"),
    )

    private fun todoJson(t: TodoEntity) = JSONObject()
        .put("id", t.id).put("text", t.text).put("done", t.done)
        .put("date", t.date ?: JSONObject.NULL).put("minutes", t.minutes ?: JSONObject.NULL)
        .put("estimate", t.estimate ?: JSONObject.NULL).put("priority", t.priority)
        .put("project", t.project).put("createdAt", t.createdAt)
        .put("doneAt", t.doneAt ?: JSONObject.NULL).put("raw", t.raw)

    private fun todo(o: JSONObject) = TodoEntity(
        id = o.getString("id"),
        text = o.getString("text"),
        done = o.getBoolean("done"),
        date = o.textOrNull("date"),
        minutes = o.intOrNull("minutes"),
        estimate = o.intOrNull("estimate"),
        priority = o.getInt("priority"),
        project = o.getString("project"),
        createdAt = o.getString("createdAt"),
        doneAt = o.textOrNull("doneAt"),
        raw = o.getString("raw"),
    )

    private fun noteJson(n: NoteEntity) = JSONObject()
        .put("id", n.id).put("habitId", n.habitId).put("date", n.date).put("type", n.type)
        .put("text", n.text).put("minutes", n.minutes ?: JSONObject.NULL).put("createdAt", n.createdAt)

    private fun note(o: JSONObject) = NoteEntity(
        id = o.getString("id"),
        habitId = o.getString("habitId"),
        date = o.getString("date"),
        type = o.getInt("type"),
        text = o.getString("text"),
        minutes = o.intOrNull("minutes"),
        createdAt = o.getString("createdAt"),
    )

    private fun importedJson(i: ImportedItemEntity) = JSONObject()
        .put("source", i.source).put("sourceId", i.sourceId).put("entryId", i.entryId)

    private fun imported(o: JSONObject) = ImportedItemEntity(
        source = o.getString("source"),
        sourceId = o.getString("sourceId"),
        entryId = o.getLong("entryId"),
    )
}
