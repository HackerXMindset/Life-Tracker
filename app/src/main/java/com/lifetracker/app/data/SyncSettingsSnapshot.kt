package com.lifetracker.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The settings that are copied to the second phone, as one piece of text. Only the files listed in [FILES] go: how things
 * look and are grouped. Never permissions, the sync login, tracking, backups or anything that belongs to one phone.
 */
object SyncSettingsSnapshot {
    /** The preference files that are copied. */
    val FILES = listOf("theme", "features", "timeline", "habits", "money", "map", "replay")

    /** One saved setting: its name, its kind (b, i, l, f, s or set) and its value. */
    class Item(val key: String, val kind: String, val value: Any)

    /** Turns the settings of each file into text. Anything of a kind we do not know is left out. */
    fun encode(files: Map<String, Map<String, Any?>>): String {
        val out = JSONObject()
        files.filterKeys { it in FILES }.toSortedMap().forEach { (file, all) ->
            val items = JSONObject()
            all.toSortedMap().forEach { (key, v) ->
                val one = JSONObject()
                when (v) {
                    is Boolean -> one.put("t", "b").put("v", v)
                    is Int -> one.put("t", "i").put("v", v)
                    is Long -> one.put("t", "l").put("v", v)
                    is Float -> one.put("t", "f").put("v", v.toDouble())
                    is String -> one.put("t", "s").put("v", v)
                    is Set<*> -> one.put("t", "set").put("v", JSONArray(v.map { it.toString() }.sorted()))
                    else -> return@forEach
                }
                items.put(key, one)
            }
            out.put(file, items)
        }
        return out.toString()
    }

    /** The settings in [text], by file. Files not in [FILES] are ignored, whatever the text says. */
    fun decode(text: String): Map<String, List<Item>> {
        val root = JSONObject(text)
        val out = linkedMapOf<String, List<Item>>()
        root.keys().asSequence().filter { it in FILES }.sorted().forEach { file ->
            val items = root.getJSONObject(file)
            out[file] = items.keys().asSequence().sorted().mapNotNull { key ->
                val one = items.getJSONObject(key)
                val value: Any = when (one.getString("t")) {
                    "b" -> one.getBoolean("v")
                    "i" -> one.getInt("v")
                    "l" -> one.getLong("v")
                    "f" -> one.getDouble("v").toFloat()
                    "s" -> one.getString("v")
                    "set" -> one.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                    else -> return@mapNotNull null
                }
                Item(key, one.getString("t"), value)
            }.toList()
        }
        return out
    }

    /** What this phone has now, ready to send. */
    fun read(context: Context): String =
        encode(FILES.associateWith { context.applicationContext.getSharedPreferences(it, Context.MODE_PRIVATE).all })

    /** Puts received settings in place on the second phone. */
    fun apply(context: Context, text: String) {
        decode(text).forEach { (file, items) ->
            val editor = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            items.forEach { i ->
                @Suppress("UNCHECKED_CAST")
                when (i.kind) {
                    "b" -> editor.putBoolean(i.key, i.value as Boolean)
                    "i" -> editor.putInt(i.key, i.value as Int)
                    "l" -> editor.putLong(i.key, i.value as Long)
                    "f" -> editor.putFloat(i.key, i.value as Float)
                    "s" -> editor.putString(i.key, i.value as String)
                    "set" -> editor.putStringSet(i.key, i.value as Set<String>)
                }
            }
            editor.apply()
        }
    }
}
