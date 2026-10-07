package com.lifetracker.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything a backup file holds, once it has been read and checked. */
data class BackupData(
    val version: Int,
    val exportedAt: String,
    val entries: List<EntryEntity>,
)

/**
 * The backup file format: plain JSON you can open in any text editor.
 *
 * `version` lets later steps add habits, food and money without breaking old
 * backups. A file from a newer version of the app is refused rather than
 * half-read.
 */
object BackupCodec {
    const val APP_NAME = "life-tracker"
    const val FORMAT_VERSION = 1

    fun encode(entries: List<EntryEntity>, exportedAt: String): String {
        val list = JSONArray()
        for (e in entries) {
            list.put(
                JSONObject()
                    .put("id", e.id)
                    .put("date", e.date)
                    .put("startMinute", e.startMinute)
                    .put("endMinute", e.endMinute ?: JSONObject.NULL)
                    .put("category", e.category)
                    .put("title", e.title)
                    .put("note", e.note),
            )
        }
        return JSONObject()
            .put("app", APP_NAME)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("entries", list)
            .toString(2)
    }

    /** Reads a backup. Throws [IllegalArgumentException] with a readable reason if it is not usable. */
    fun decode(text: String): BackupData {
        try {
            val root = JSONObject(text)
            require(root.optString("app") == APP_NAME) { "This is not a Life Tracker backup." }
            val version = root.optInt("version", -1)
            require(version in 1..FORMAT_VERSION) {
                "This backup was made by a newer version of the app. Update the app first."
            }
            val list = root.getJSONArray("entries")
            val entries = (0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                EntryEntity(
                    id = o.getLong("id"),
                    date = o.getString("date"),
                    startMinute = o.getInt("startMinute"),
                    endMinute = if (o.isNull("endMinute")) null else o.getInt("endMinute"),
                    category = o.getString("category"),
                    title = o.getString("title"),
                    note = o.optString("note", ""),
                )
            }
            return BackupData(version, root.optString("exportedAt"), entries)
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is damaged or is not a backup.")
        }
    }
}
