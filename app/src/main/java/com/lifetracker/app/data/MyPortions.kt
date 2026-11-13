package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit

/** One portion you measured at home: "roti" weighs 45 g, "katori of dal" weighs 180 g. */
data class MyPortion(val name: String, val grams: Double)

/**
 * Your own weights for the things you count in pieces and bowls. A katori is not the same in every kitchen, and a roti can be 30 g or
 * 60 g, so the reader of photos and words is told your sizes and uses them instead of the usual ones. Kept in a settings file of its
 * own, which goes into backups.
 */
object MyPortions {
    const val FILE = "my_portions"
    const val MAX = 30
    private const val MAX_GRAMS = 5000.0

    /** Ideas to start from, with a usual weight. Only what is not on your list yet is shown. */
    val SUGGESTED: List<MyPortion> = listOf(
        MyPortion("roti", 40.0), MyPortion("paratha", 80.0), MyPortion("idli", 40.0),
        MyPortion("katori of dal", 150.0), MyPortion("katori of rice", 150.0), MyPortion("katori of sabzi", 120.0),
        MyPortion("tablespoon of ghee", 14.0), MyPortion("teaspoon of sugar", 4.0), MyPortion("glass of milk", 240.0),
    )

    /** A name with no line breaks and single spaces, or null when there is no name or the weight is not between 0 and 5000 g. */
    fun clean(name: String, grams: Double?): MyPortion? {
        val n = name.replace(Regex("""[\t\r\n|]"""), " ").replace(Regex("""\s+"""), " ").trim().take(40)
        val g = grams?.takeIf { it.isFinite() && it > 0 && it <= MAX_GRAMS } ?: return null
        return if (n.isEmpty()) null else MyPortion(n, g)
    }

    /** The list without repeats of a name (the last weight wins), in the order first written, at most [MAX]. */
    fun normalize(list: List<MyPortion>): List<MyPortion> =
        list.associateBy { it.name.lowercase() }.values.toList().take(MAX)

    fun encode(list: List<MyPortion>): String = normalize(list).joinToString("\n") { it.name + "\t" + it.grams }

    fun decode(text: String): List<MyPortion> = normalize(
        text.lines().mapNotNull { line ->
            val at = line.lastIndexOf('\t')
            if (at <= 0) null else clean(line.substring(0, at), line.substring(at + 1).toDoubleOrNull())
        },
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): List<MyPortion> = decode(prefs(context).getString("list", "").orEmpty())

    fun save(context: Context, list: List<MyPortion>) = prefs(context).edit { putString("list", encode(list)) }

    private fun gramsText(g: Double): String = if (g == Math.rint(g)) g.toLong().toString() else String.format(java.util.Locale.US, "%.1f", g)

    /** The sentence added to what the reader is asked, or "" when you have not set any. */
    fun promptLine(list: List<MyPortion>): String =
        if (list.isEmpty()) "" else
            "\n\nThe user's own portion sizes. Use these weights, not the usual ones, whenever the meal has one of these amounts: " +
                list.joinToString("; ") { "1 " + it.name + " = " + gramsText(it.grams) + " g" } + "."

    fun promptLine(context: Context): String = promptLine(load(context))
}
