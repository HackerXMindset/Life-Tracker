package com.lifetracker.app.data

/**
 * The Places list: search, nearest first, and pages. No Android in here so it can be tested.
 */
object PlaceList {
    const val PAGE_SIZE = 10

    /** One place in the list, with how far it is from [Page.here] (null when the position is not known). */
    class Row(val place: PlaceEntity, val distanceM: Double?)

    class Page(val rows: List<Row>, val page: Int, val pageCount: Int, val total: Int)

    /** The places you keep (not deleted), the ones you can find with [query] in their name or kind. */
    fun matching(places: List<PlaceEntity>, query: String, kindLabel: (String) -> String = PlaceKinds::label): List<PlaceEntity> {
        val q = query.trim().lowercase()
        return places.filter { it.deletedMs == 0L && (q.isEmpty() || it.name.lowercase().contains(q) || kindLabel(it.kind).lowercase().contains(q)) }
    }

    /**
     * The places from the closest to the farthest from [here] (latitude, longitude), by the edge of a drawn area and the centre of a
     * circle. Without a position they go by name. Places you stopped watching go after the ones you watch.
     */
    fun arrange(places: List<PlaceEntity>, here: Pair<Double, Double>?): List<Row> {
        val rows = places.map { Row(it, here?.let { h -> PlaceShape.distanceToM(it, h.first, h.second) }) }
        return rows.sortedWith(
            compareBy<Row> { it.place.archived }
                .thenBy { it.distanceM ?: 0.0 }
                .thenBy { it.place.name.lowercase() },
        )
    }

    /** One page (counting from 0; a page past the end is the last page). */
    fun page(rows: List<Row>, page: Int, size: Int = PAGE_SIZE): Page {
        val count = ((rows.size + size - 1) / size).coerceAtLeast(1)
        val p = page.coerceIn(0, count - 1)
        return Page(rows.drop(p * size).take(size), p, count, rows.size)
    }

    /** "350 m away", "2.4 km away", or null. */
    fun distanceText(m: Double?): String? = when {
        m == null -> null
        m < 1.0 -> "here"
        m < 1000.0 -> "${m.toInt()} m away"
        m < 100_000.0 -> String.format(java.util.Locale.ENGLISH, "%.1f km away", m / 1000.0)
        else -> "${(m / 1000.0).toInt()} km away"
    }
}
