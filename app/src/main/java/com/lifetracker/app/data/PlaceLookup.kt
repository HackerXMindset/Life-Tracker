package com.lifetracker.app.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Suggests a name for a spot by asking OpenStreetMap: first the named shop, library, gym, hostel or other place
 * within 60 m (Overpass), then the street address (Nominatim). This is the only thing in the app that uses the
 * internet, and it sends only the coordinates of a stop you have not named, once per spot, when the review list is open.
 */
object PlaceLookup {
    private const val USER_AGENT = "LifeTracker/1.0 (private personal app; one lookup per unnamed stop)"
    private const val NEAR_M = 60

    class Candidate(val name: String, val distanceM: Double)

    /** The named things in an Overpass answer, nearest first. */
    fun parseOverpass(json: String, lat: Double, lng: Double): List<Candidate> = try {
        val elements = JSONObject(json).optJSONArray("elements")
        val out = ArrayList<Candidate>()
        if (elements != null) {
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                val name = e.optJSONObject("tags")?.optString("name", "")?.trim().orEmpty()
                if (name.isEmpty()) continue
                val centre = e.optJSONObject("center")
                val eLat = if (e.has("lat")) e.getDouble("lat") else centre?.optDouble("lat", Double.NaN) ?: Double.NaN
                val eLng = if (e.has("lon")) e.getDouble("lon") else centre?.optDouble("lon", Double.NaN) ?: Double.NaN
                val d = if (eLat.isNaN() || eLng.isNaN()) NEAR_M.toDouble() else PlaceRules.distanceM(lat, lng, eLat, eLng)
                out.add(Candidate(name, d))
            }
        }
        out.sortedBy { it.distanceM }
    } catch (e: Exception) {
        emptyList()
    }

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

    /** A suggested name for the spot, or null if nothing was found or there is no internet. */
    suspend fun suggest(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        val near = runCatching { overpass(lat, lng) }.getOrNull()?.let { parseOverpass(it, lat, lng) }?.firstOrNull()
        if (near != null) return@withContext near.name
        runCatching { nominatim(lat, lng) }.getOrNull()?.let { parseNominatim(it) }
    }

    private fun overpass(lat: Double, lng: Double): String {
        val around = "around:$NEAR_M,$lat,$lng"
        val keys = listOf("amenity", "shop", "leisure", "tourism", "office", "building", "healthcare", "education")
        val query = "[out:json][timeout:10];(" + keys.joinToString("") { "nwr($around)[\"name\"][\"$it\"];" } + ");out tags center 25;"
        return request(
            URL("https://overpass-api.de/api/interpreter"),
            "data=" + URLEncoder.encode(query, "UTF-8"),
        )
    }

    private fun nominatim(lat: Double, lng: Double): String =
        request(
            URL(
                String.format(java.util.Locale.ENGLISH, "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&addressdetails=1&lat=%.6f&lon=%.6f", lat, lng),
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
