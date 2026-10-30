package com.lifetracker.app.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Suggests names for a spot by asking OpenStreetMap: the named shops, stations, landmarks and other places within 80 m
 * (Overpass), then the street address (Nominatim) when there are few. This is the only thing in the app that uses the
 * internet, and it sends only the coordinates of a stop you have not named, once per spot, when the review list is open.
 */
object PlaceLookup {
    private const val USER_AGENT = "LifeTracker/1.0 (private personal app; one lookup per unnamed stop)"
    /** The named things in an Overpass answer, best first (see [PlaceNames.parseOverpass]). */
    fun parseOverpass(json: String, lat: Double, lng: Double, lang: String = ""): List<PlaceNames.Suggestion> =
        PlaceNames.parseOverpass(json, lat, lng, lang)

    /** The place name or street address in a Nominatim answer, or null. */
    fun parseNominatim(json: String): String? = try {
        val o = JSONObject(json)
        val name = o.optString("name", "").trim()
        val address = o.optJSONObject("address")
        val road = address?.optString("road", "").orEmpty().trim()
        val area = (address?.optString("suburb", "").orEmpty().ifEmpty { address?.optString("neighbourhood", "").orEmpty() }).trim()
        when {
            name.isNotEmpty() -> name
            road.isNotEmpty() && area.isNotEmpty() -> "$road, $area"
            road.isNotEmpty() -> road
            else -> o.optString("display_name", "").split(",").take(2).joinToString(",").trim().ifEmpty { null }
        }
    } catch (e: Exception) {
        null
    }

    /** Cache key: about 11 m squares, so a spot is only looked up once. */
    fun key(lat: Double, lng: Double): String = String.format(java.util.Locale.ENGLISH, "%.4f_%.4f", lat, lng)

    /** The best few names for the spot (exact places first, the street address last), or an empty list with no internet. */
    suspend fun suggestAll(lat: Double, lng: Double, lang: String): List<PlaceNames.Suggestion> = withContext(Dispatchers.IO) {
        val nearby = runCatching { overpass(lat, lng) }.getOrNull()?.let { parseOverpass(it, lat, lng, lang) }.orEmpty()
        val address = if (nearby.size >= 3) null else runCatching { nominatim(lat, lng, lang) }.getOrNull()?.let { parseNominatim(it) }
            ?.let { PlaceNames.Suggestion(it, PlaceNames.Source.ADDRESS, 0.0) }
        PlaceNames.combine(emptyList(), nearby, address)
    }

    /** The single best name, or null (kept for callers that want one). */
    suspend fun suggest(lat: Double, lng: Double, lang: String = ""): String? = suggestAll(lat, lng, lang).firstOrNull()?.name

    private fun overpass(lat: Double, lng: Double): String {
        val around = "around:${PlaceNames.NEAR_M},$lat,$lng"
        val keys = listOf("amenity", "shop", "leisure", "tourism", "office", "building", "healthcare", "education", "railway", "public_transport", "historic", "aeroway")
        val query = "[out:json][timeout:10];(" + keys.joinToString("") { "nwr($around)[\"name\"][\"$it\"];" } + ");out tags center 40;"
        return request(
            URL("https://overpass-api.de/api/interpreter"),
            "data=" + URLEncoder.encode(query, "UTF-8"),
        )
    }

    private fun nominatim(lat: Double, lng: Double, lang: String): String =
        request(
            URL(
                String.format(java.util.Locale.ENGLISH, "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&addressdetails=1&lat=%.6f&lon=%.6f", lat, lng) +
                    (if (lang.isNotEmpty()) "&accept-language=$lang" else ""),
            ),
            null,
        )

    private fun request(url: URL, post: String?): String {
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            if (post != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(post.toByteArray(Charsets.UTF_8)) }
            }
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }
}
