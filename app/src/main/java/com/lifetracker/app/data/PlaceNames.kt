package com.lifetracker.app.data

import org.json.JSONObject

/**
 * The rules for naming a spot: which names OpenStreetMap offers, in your language, best first, plus the names you gave before.
 * Free of Android and of the internet so it can be tested; [PlaceLookup] does the asking.
 */
object PlaceNames {
    enum class Source { YOURS, NEARBY, ADDRESS }

    /** One idea for a name. [kind] is a plain word such as "Railway station" or "Cafe" (empty when unknown). */
    class Suggestion(val name: String, val source: Source, val distanceM: Double, val kind: String = "") {
        /** "Prayagraj Junction (Railway station)" for a button label, or just the name. */
        fun label(): String = if (kind.isEmpty() || source == Source.YOURS) name else "$name ($kind)"
    }

    const val MAX = 5

    /** How far from the spot a named thing may be to be offered. */
    const val NEAR_M = 80

    /** The tags that make something a place you might go to, most useful first, with the word shown for each value kind. */
    private val POI_KEYS = listOf("shop", "amenity", "railway", "public_transport", "tourism", "leisure", "historic", "office", "healthcare", "education", "aeroway")

    /** Plain buildings and areas with only a name are offered, but after real places. */
    private const val BUILDING_PENALTY_M = 35.0

    private fun kindWord(tags: JSONObject): String {
        for (k in POI_KEYS) {
            val v = tags.optString(k, "")
            if (v.isNotEmpty() && v != "yes") return v.replace('_', ' ').replaceFirstChar { it.uppercase() }
            if (v == "yes" && k == "shop") return "Shop"
        }
        return ""
    }

    private fun isPoi(tags: JSONObject): Boolean = POI_KEYS.any { tags.optString(it, "").isNotEmpty() }

    private fun isLatin(text: String): Boolean = text.all { it.code < 0x250 || !it.isLetter() }

    /**
     * The name to show: in [lang] when OpenStreetMap has it ("name:en"), then the international name, then the local one.
     * With [lang] empty the local name is used as it is written on the map.
     */
    fun pickName(tags: JSONObject, lang: String): String {
        val local = tags.optString("name", "").trim()
        if (lang.isEmpty()) return local
        val wanted = tags.optString("name:$lang", "").trim()
        if (wanted.isNotEmpty()) return wanted
        val intl = tags.optString("int_name", "").trim()
        if (intl.isNotEmpty() && isLatin(intl)) return intl
        return local
    }

    /** The named things in an Overpass answer, the best first: real places before plain buildings, then nearest. Repeats are dropped. */
    fun parseOverpass(json: String, lat: Double, lng: Double, lang: String = ""): List<Suggestion> = try {
        val elements = JSONObject(json).optJSONArray("elements")
        val found = ArrayList<Pair<Double, Suggestion>>()
        if (elements != null) {
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                val tags = e.optJSONObject("tags") ?: continue
                val name = pickName(tags, lang)
                if (name.isEmpty()) continue
                val centre = e.optJSONObject("center")
                val eLat = if (e.has("lat")) e.getDouble("lat") else centre?.optDouble("lat", Double.NaN) ?: Double.NaN
                val eLng = if (e.has("lon")) e.getDouble("lon") else centre?.optDouble("lon", Double.NaN) ?: Double.NaN
                val d = if (eLat.isNaN() || eLng.isNaN()) NEAR_M.toDouble() else PlaceRules.distanceM(lat, lng, eLat, eLng)
                val poi = isPoi(tags)
                found += (d + (if (poi) 0.0 else BUILDING_PENALTY_M)) to Suggestion(name, Source.NEARBY, d, kindWord(tags))
            }
        }
        val seen = HashSet<String>()
        found.sortedBy { it.first }.map { it.second }.filter { seen.add(it.name.lowercase()) }.take(MAX)
    } catch (e: Exception) {
        emptyList()
    }

    /** The places you named before that are close to this spot (including ones you stopped watching), nearest first. */
    fun remembered(places: List<PlaceEntity>, lat: Double, lng: Double): List<Suggestion> =
        places.mapNotNull { p ->
            val d = PlaceRules.distanceM(lat, lng, p.lat, p.lng)
            if (p.name.isNotBlank() && d <= maxOf(p.radiusM, 50) + 60) Suggestion(p.name.trim(), Source.YOURS, d) else null
        }.sortedBy { it.distanceM }.distinctBy { it.name.lowercase() }

    /** Your own names first, then what is nearby, then the street address; no repeats; at most [MAX]. */
    fun combine(yours: List<Suggestion>, nearby: List<Suggestion>, address: Suggestion?): List<Suggestion> {
        val seen = HashSet<String>()
        return (yours + nearby + listOfNotNull(address)).filter { seen.add(it.name.lowercase()) }.take(MAX)
    }

    /** Saves a list of suggestions as text (one per line) for the phone to keep. */
    fun encode(list: List<Suggestion>): String =
        list.joinToString("\n") { it.source.name + "\t" + it.name.replace('\t', ' ').replace('\n', ' ') + "\t" + it.kind + "\t" + it.distanceM.toInt() }

    fun decode(text: String): List<Suggestion> =
        text.lines().mapNotNull { line ->
            val p = line.split("\t")
            if (p.size < 2 || p[1].isBlank()) return@mapNotNull null
            val source = runCatching { Source.valueOf(p[0]) }.getOrDefault(Source.NEARBY)
            Suggestion(p[1], source, p.getOrNull(3)?.toDoubleOrNull() ?: 0.0, p.getOrNull(2).orEmpty())
        }
}

/** When to ask "Where are you?": only for a stop at an unnamed spot, after your minimum stay, outside quiet hours and snoozes. */
object NameAskRules {
    class Config(
        val enabled: Boolean,
        val minStayMin: Int,
        /** Quiet hours as minutes after midnight; equal values mean no quiet hours. The end may be earlier than the start (overnight). */
        val quietFromMin: Int,
        val quietToMin: Int,
    )

    fun inQuietHours(minuteOfDay: Int, fromMin: Int, toMin: Int): Boolean = when {
        fromMin == toMin -> false
        fromMin < toMin -> minuteOfDay in fromMin until toMin
        else -> minuteOfDay >= fromMin || minuteOfDay < toMin
    }

    /**
     * True when a notification should be shown for this stop. [stayMs] is how long you have been there, [askedVisitIds] the stops
     * already asked about, [spotAskedMs] when this same spot was last asked about (0 for never), [snoozeUntilMs] the end of a snooze.
     */
    fun shouldAsk(
        cfg: Config,
        unnamed: Boolean,
        ignored: Boolean,
        visitId: String,
        stayMs: Long,
        minuteOfDay: Int,
        nowMs: Long,
        askedVisitIds: Set<String>,
        spotAskedMs: Long,
        snoozeUntilMs: Long,
    ): Boolean = cfg.enabled && unnamed && !ignored &&
        stayMs >= cfg.minStayMin * 60_000L &&
        !inQuietHours(minuteOfDay, cfg.quietFromMin, cfg.quietToMin) &&
        nowMs >= snoozeUntilMs &&
        visitId !in askedVisitIds &&
        (spotAskedMs == 0L || nowMs - spotAskedMs >= SPOT_AGAIN_MS)

    /** The same spot is not asked about again for this long. */
    const val SPOT_AGAIN_MS = 6 * 60 * 60_000L
}
