package com.lifetracker.app.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every setting of the app, for the one backup file. Settings live in small files on the phone ("shared preferences": the theme,
 * what is switched on, reminder times, thresholds, and so on). The backup keeps all of them, found by looking at what is on the
 * phone rather than from a list, so a setting added later is saved without anyone remembering to add it.
 *
 * Left out on purpose: the folders you chose (Android only lets this phone use them), the app's own log, the focus timer that is
 * running right now, the pairing with another phone, and files that belong to Android or to libraries.
 */
object SettingsBackup {
    /** Files that stay on this phone. */
    val LEFT_OUT: Set<String> = setOf("data_sources", "applog", "focus-state", "habit_sync", "food_ai", "lock_settings")

    /** Whether the settings file called [name] goes into a backup. */
    fun included(name: String): Boolean =
        name.isNotBlank() && name !in LEFT_OUT && !name.contains('.') &&
            !name.startsWith("WebView", ignoreCase = true) && !name.startsWith("mbgl", ignoreCase = true) &&
            !name.startsWith("MapLibre", ignoreCase = true) && !name.startsWith("androidx", ignoreCase = true)

    /** The settings files on this phone that a backup holds, by file name. */
    fun fileNames(context: Context): List<String> {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        return dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".xml") }
            .map { it.name.removeSuffix(".xml") }
            .filter(::included)
            .sorted()
    }

    /** Every setting now: file name, then key, then value (Boolean, Int, Long, Float, String or Set of String). */
    fun collect(context: Context): Map<String, Map<String, Any>> {
        val out = LinkedHashMap<String, Map<String, Any>>()
        for (name in fileNames(context)) {
            val all = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).all
            val values = LinkedHashMap<String, Any>()
            for ((k, v) in all) if (v != null) values[k] = v
            if (values.isNotEmpty()) out[name] = values
        }
        return out
    }

    /**
     * Puts the settings of a backup back. A file named in the backup is replaced as a whole; a file the backup does not
     * mention is left alone. The app should be restarted afterwards, because some settings are read once when it starts.
     * Returns how many settings were put back.
     */
    fun apply(context: Context, settings: Map<String, Map<String, Any>>): Int {
        var count = 0
        for ((name, values) in settings) {
            if (!included(name)) continue
            val editor = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            editor.clear()
            for ((k, v) in values) {
                @Suppress("UNCHECKED_CAST")
                when (v) {
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                    is String -> editor.putString(k, v)
                    is Set<*> -> editor.putStringSet(k, (v as Set<String>).toSet())
                    else -> continue
                }
                count++
            }
            editor.commit()
        }
        return count
    }
}

/** Settings as JSON, one array per value: ["b", true], ["i", 3], ["l", 12345], ["f", 1.5], ["s", "text"] or ["set", ["a", "b"]]. */
object SettingsCodec {
    fun toJson(settings: Map<String, Map<String, Any>>): JSONObject {
        val root = JSONObject()
        for ((file, values) in settings) {
            val o = JSONObject()
            for ((k, v) in values) typed(v)?.let { o.put(k, it) }
            root.put(file, o)
        }
        return root
    }

    private fun typed(v: Any): JSONArray? = when (v) {
        is Boolean -> JSONArray().put("b").put(v)
        is Int -> JSONArray().put("i").put(v)
        is Long -> JSONArray().put("l").put(v)
        is Float -> JSONArray().put("f").put(v.toDouble())
        is String -> JSONArray().put("s").put(v)
        is Set<*> -> JSONArray().put("set").put(JSONArray().also { a -> v.sorted().forEach { a.put(it) } })
        else -> null
    }

    private fun Set<*>.sorted(): List<String> = map { it.toString() }.sorted()

    fun fromJson(root: JSONObject?): Map<String, Map<String, Any>> {
        if (root == null) return emptyMap()
        val out = LinkedHashMap<String, Map<String, Any>>()
        for (file in root.keys()) {
            val o = root.optJSONObject(file) ?: continue
            val values = LinkedHashMap<String, Any>()
            for (k in o.keys()) {
                val pair = o.optJSONArray(k) ?: continue
                val value: Any = when (pair.optString(0)) {
                    "b" -> pair.optBoolean(1)
                    "i" -> pair.optInt(1)
                    "l" -> pair.optLong(1)
                    "f" -> pair.optDouble(1).toFloat()
                    "s" -> pair.optString(1)
                    "set" -> {
                        val a = pair.optJSONArray(1) ?: continue
                        (0 until a.length()).map { a.optString(it) }.toSet()
                    }
                    else -> continue
                }
                values[k] = value
            }
            out[file] = values
        }
        return out
    }
}
