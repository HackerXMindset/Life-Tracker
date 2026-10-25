package com.lifetracker.app.data

/**
 * Map styles: the built-in ones, your own (a style address or a tile address, with an optional key), and the rules a style's
 * provider sets for downloading maps to the phone. Plain code, no Android, so it can be tested.
 */
object MapStyles {
    /** A style that comes with the app. */
    class BuiltIn(val id: String, val name: String, val url: String)

    /** What a provider allows for ONE offline download. These are the provider's own terms, not limits of this app. */
    class Limit(val provider: String, val maxTiles: Int, val maxBytes: Long, val parallel: Int, val termsNote: String)

    /** How an address you typed is used. */
    enum class Kind { STYLE, TILES, INVALID }

    /** OpenFreeMap (free, no key). Its terms allow offline downloads of at most 3,000 tiles or 50 MB each, 4 requests at once. */
    private const val OPENFREEMAP_HOST = "https://tiles.openfreemap.org/"
    val OPENFREEMAP_LIMIT = Limit(
        provider = "OpenFreeMap",
        maxTiles = 3_000,
        maxBytes = 50L * 1024 * 1024,
        parallel = 4,
        termsNote = "OpenFreeMap allows at most 3,000 tiles or 50 MB in one download.",
    )

    val builtIn: List<BuiltIn> = listOf(
        BuiltIn("ofm-liberty", "Streets (Liberty)", OPENFREEMAP_HOST + "styles/liberty"),
        BuiltIn("ofm-bright", "Streets (Bright)", OPENFREEMAP_HOST + "styles/bright"),
        BuiltIn("ofm-positron", "Light (Positron)", OPENFREEMAP_HOST + "styles/positron"),
    )

    const val DEFAULT_ID = "ofm-liberty"

    const val ATTRIBUTION = "OpenFreeMap, © OpenMapTiles, data from OpenStreetMap"

    fun builtInById(id: String): BuiltIn? = builtIn.firstOrNull { it.id == id }

    /** The provider's limit for a download from [url], or null when nothing is known (your own styles have none). */
    fun limitFor(url: String): Limit? = if (url.startsWith(OPENFREEMAP_HOST)) OPENFREEMAP_LIMIT else null

    /** Puts your key into an address that has {key} in it. */
    fun resolve(address: String, key: String): String = address.trim().replace("{key}", key.trim())

    /** A style address (https, no tile placeholders) or a tile address (has {z}, {x} and {y}). */
    fun kind(address: String): Kind {
        val a = address.trim()
        if (!a.startsWith("https://")) return Kind.INVALID
        if (a.length < 12 || a.any { it.isWhitespace() }) return Kind.INVALID
        val hasTiles = a.contains("{z}") && a.contains("{x}") && a.contains("{y}")
        val hasAny = a.contains("{z}") || a.contains("{x}") || a.contains("{y}")
        return when {
            hasTiles -> Kind.TILES
            hasAny -> Kind.INVALID
            else -> Kind.STYLE
        }
    }

    /** What is wrong with a style you are adding, in plain words, or null when it is fine. */
    fun problem(name: String, address: String, key: String): String? = when {
        name.isBlank() -> "Give the style a name."
        kind(address) == Kind.INVALID ->
            "The address must start with https:// and be either a style address, or a tile address with {z}, {x} and {y} in it."
        address.contains("{key}") && key.isBlank() -> "This address has {key} in it, so a key is needed."
        else -> null
    }

    /** A plain map style (the style file) made from a tile address, so a raster tile source can be used like any other style. */
    fun rasterStyleJson(name: String, tileAddress: String, attribution: String = ""): String {
        val sb = StringBuilder()
        sb.append("{\"version\":8,\"name\":").append(quote(name))
        sb.append(",\"sources\":{\"tiles\":{\"type\":\"raster\",\"tiles\":[").append(quote(tileAddress))
        sb.append("],\"tileSize\":256")
        if (attribution.isNotBlank()) sb.append(",\"attribution\":").append(quote(attribution))
        sb.append("}},\"layers\":[{\"id\":\"tiles\",\"type\":\"raster\",\"source\":\"tiles\"}]}")
        return sb.toString()
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    /** An id for a style you add: "my-" and the time, so it never clashes. */
    fun newId(nowMs: Long): String = "my-$nowMs"
}
