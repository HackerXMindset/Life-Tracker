package com.lifetracker.app.data

/**
 * The size limit for offline maps, and which maps to delete to stay under it. No Android in this file, so it can be tested.
 *
 * Every downloaded area has a rank. Rank 0 is not in use (a map of another style than the one you use, or of a place you no
 * longer have). Rank 1 is made by the app and in use. Rank 2 is an area you chose yourself: it is never deleted by the app.
 * When there is not enough room the app deletes rank 0 first, then rank 1, the one you were in longest ago first.
 */
object MapLimits {
    private const val MB = 1_000_000L
    private const val GB = 1_000_000_000L
    private const val DAY_MS = 86_400_000L

    /** The sizes offered for the limit, in bytes. 0 is no limit. */
    val CHOICES: List<Long> = listOf(250 * MB, 500 * MB, GB, 2 * GB, 5 * GB, 0L)

    /** What the limit is until you change it. */
    const val DEFAULT_BYTES = GB

    /** A place nobody went to for this long does not get a map of its own. */
    const val UNUSED_DAYS = 90

    /** A place you just added gets its map at once, before there is any visit. */
    const val NEW_PLACE_DAYS = 7

    fun label(bytes: Long): String = if (bytes <= 0L) "No limit" else TileMath.sizeText(bytes)

    const val IN_USE = 1
    const val NOT_IN_USE = 0
    const val YOURS = 2

    fun rank(area: MapAreaEntity, currentStyleId: String, activePlaceIds: Set<String>): Int = when {
        area.source == AreaSource.HAND -> YOURS
        area.styleId != currentStyleId -> NOT_IN_USE
        area.source == AreaSource.PLACE && area.placeId !in activePlaceIds -> NOT_IN_USE
        else -> IN_USE
    }

    /** When an area was last of use: the last reading inside it, or when it was finished or asked for. */
    fun lastUsed(area: MapAreaEntity, usedMs: Long): Long = maxOf(usedMs, area.doneMs, area.createdMs)

    class Candidate(val id: String, val bytes: Long, val rank: Int, val lastUsedMs: Long)

    /** What to do before a download of [extraBytes]: the areas to delete (first to last), or why it cannot be done. */
    class Plan(val delete: List<String>, val problem: String?) {
        val ok: Boolean get() = problem == null
    }

    /**
     * Works out how to fit [extraBytes] under [limitBytes] when [usedBytes] is already on the phone. A limit of 0 is no limit.
     * With [autoDelete] off nothing is ever deleted: a download that does not fit is refused, and says why.
     */
    fun plan(candidates: List<Candidate>, usedBytes: Long, extraBytes: Long, limitBytes: Long, autoDelete: Boolean): Plan {
        if (limitBytes <= 0L || usedBytes + extraBytes <= limitBytes) return Plan(emptyList(), null)
        if (!autoDelete) {
            return Plan(
                emptyList(),
                "This needs about " + TileMath.sizeText(extraBytes) + " and your maps already take " + TileMath.sizeText(usedBytes) +
                    " of your " + label(limitBytes) + " limit. Raise the limit, or switch on deleting the least used maps.",
            )
        }
        val order = candidates.filter { it.rank < YOURS && it.bytes > 0L }
            .sortedWith(compareBy<Candidate>({ it.rank }, { it.lastUsedMs }))
        val chosen = ArrayList<String>()
        var left = usedBytes
        for (c in order) {
            if (left + extraBytes <= limitBytes) break
            chosen += c.id
            left -= c.bytes
        }
        if (left + extraBytes > limitBytes) {
            return Plan(
                emptyList(),
                "This needs about " + TileMath.sizeText(extraBytes) + ", and even with the maps the app may delete gone, the areas you chose yourself " +
                    "(" + TileMath.sizeText(left) + ") would pass your " + label(limitBytes) + " limit. Raise the limit or delete an area.",
            )
        }
        return Plan(chosen, null)
    }

    /** Whether a place that has no map yet should get one: it is new, or somebody went there lately. */
    fun placeWanted(createdMs: Long, lastVisitMs: Long?, nowMs: Long): Boolean =
        nowMs - createdMs <= NEW_PLACE_DAYS * DAY_MS || (lastVisitMs != null && nowMs - lastVisitMs <= UNUSED_DAYS * DAY_MS)

    /** For each area, the latest time of a reading inside it (areas of the style in use only). Readings are (lat, lng, time). */
    fun usage(readings: List<Triple<Double, Double, Long>>, areas: List<MapAreaEntity>, styleId: String): Map<String, Long> {
        val out = HashMap<String, Long>()
        val mine = areas.filter { it.styleId == styleId && it.state != AreaState.REMOVED }
        if (mine.isEmpty()) return out
        for ((lat, lng, ms) in readings) {
            for (a in mine) {
                if (a.bounds.contains(lat, lng) && ms > (out[a.id] ?: 0L)) out[a.id] = ms
            }
        }
        return out
    }

    /** Failures that are only bad luck on the way (a stall, the library not starting) and worth trying again. */
    fun isTransient(note: String): Boolean = note.startsWith("No progress") || note.startsWith("The map library could not")

    /** How often failed downloads that were bad luck are tried again by themselves. */
    const val RETRY_EVERY_MS = 6 * 3_600_000L

    /** One line for the top of the maps list: how much is used, of what limit. */
    fun usedText(usedBytes: Long, limitBytes: Long): String =
        TileMath.sizeText(usedBytes) + (if (limitBytes > 0L) " of " + label(limitBytes) else " (no limit)")

    /** How full the limit is, 0 to 1 (0 with no limit). */
    fun fill(usedBytes: Long, limitBytes: Long): Float = if (limitBytes <= 0L) 0f else (usedBytes.toDouble() / limitBytes).toFloat().coerceIn(0f, 1f)
}
