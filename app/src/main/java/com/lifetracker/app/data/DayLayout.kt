package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.min

/**
 * Arranges one day into what the Timeline draws, with no Android in it so it can be tested.
 *
 * Everything you did while at a place goes inside one block for that place (Home, with the phone use, meals and sleep
 * inside it). The way between two places is a travel row, and stretches with nothing known are gaps, so the day reads
 * from top to bottom without holes that are not explained. Items that happened outside any place stand on their own.
 */
object DayLayout {
    /** Nothing known for at least this long is shown as a gap. */
    const val MIN_GAP_MS = 10 * 60_000L

    /** A thing with no length of its own (a note, a to-do, a logged moment) still covers this much of the day. */
    private const val POINT_MS = 60_000L

    enum class Kind { ENTRY, TODO, NOTE, MEAL, CALL, CHARGE, SLEEP, PHONE }

    /** One thing that happened. [ref] is the underlying record the screen needs to draw and edit it. */
    class Item(
        val key: String,
        val kind: Kind,
        val startMs: Long,
        val endMs: Long,
        /** Whether the end time is worth showing (a note has none). */
        val showEnd: Boolean,
        val ref: Any?,
    ) {
        val coveredEndMs: Long get() = max(endMs, startMs + POINT_MS)
    }

    sealed interface Row {
        val key: String
        val startMs: Long
        val endMs: Long
    }

    /** A stay at a place, with what happened during it. [named] is false for a spot you have not named yet. */
    class Place(
        override val key: String,
        override val startMs: Long,
        override val endMs: Long,
        val placeId: String,
        val name: String,
        val named: Boolean,
        val ongoing: Boolean,
        val visit: PlaceVisitEntity,
        val children: List<Row>,
    ) : Row

    /** The way from one place to the next. [trip] is null when the phone has no record of how you went. */
    class Travel(
        override val key: String,
        override val startMs: Long,
        override val endMs: Long,
        val trip: TripEntity?,
        val fromName: String,
        val toName: String,
        /** For a journey with no trip: the stays on either side and the straight-line distance between them. */
        val from: PlaceVisitEntity? = null,
        val to: PlaceVisitEntity? = null,
        val straightLineM: Int = 0,
    ) : Row

    class Thing(val item: Item) : Row {
        override val key: String get() = item.key
        override val startMs: Long get() = item.startMs
        override val endMs: Long get() = item.endMs
    }

    /** Why a stretch has nothing in it. */
    enum class GapKind { NO_DATA, OFFLINE, PHONE_OFF }

    /** Time with nothing known. [note] says what is missing; [kind] says whether the phone was off or its screen stayed off. */
    class Gap(
        override val key: String,
        override val startMs: Long,
        override val endMs: Long,
        val note: String,
        val kind: GapKind = GapKind.NO_DATA,
        /** When several back-to-back stretches of the same kind were joined, each one (start, end); empty for a single stretch. */
        val pieces: List<Pair<Long, Long>> = emptyList(),
    ) : Row {
        /** The time with nothing known: the stretches added up (the joined row also spans the small bits between them). */
        val knownMs: Long get() = if (pieces.isEmpty()) endMs - startMs else pieces.sumOf { it.second - it.first }

        /** Each stretch, whether this is one or several joined. */
        val parts: List<Pair<Long, Long>> get() = pieces.ifEmpty { listOf(startMs to endMs) }
    }

    /** A stretch when the phone was off ([shutdown] true) or its screen stayed off. */
    class Off(val startMs: Long, val endMs: Long, val shutdown: Boolean)

    /**
     * Cuts the stretch [from, to) at the [offs]: the part inside a phone-off period is [GapKind.PHONE_OFF], the part inside
     * a screen-off period is [GapKind.OFFLINE], and the rest is [GapKind.NO_DATA]. Pieces shorter than [minMs] are dropped.
     */
    fun cutGap(from: Long, to: Long, offs: List<Off>, minMs: Long, noDataNote: String): List<Gap> {
        val points = sortedSetOf(from, to)
        for (o in offs) {
            if (o.endMs > from && o.startMs < to) {
                points += max(o.startMs, from)
                points += min(o.endMs, to)
            }
        }
        val list = points.toList()
        val out = ArrayList<Gap>()
        var i = 0
        while (i < list.size - 1) {
            val a = list[i]
            val b = list[i + 1]
            val covering = offs.filter { it.startMs <= a && it.endMs >= b }
            val kind = when {
                covering.any { it.shutdown } -> GapKind.PHONE_OFF
                covering.isNotEmpty() -> GapKind.OFFLINE
                else -> GapKind.NO_DATA
            }
            val last = out.lastOrNull()
            if (last != null && last.kind == kind && last.endMs == a) {
                out[out.size - 1] = Gap(last.key, last.startMs, b, last.note, kind)
            } else {
                val note = when (kind) {
                    GapKind.PHONE_OFF -> "The phone was switched off"
                    GapKind.OFFLINE -> "The screen stayed off"
                    GapKind.NO_DATA -> noDataNote
                }
                out += Gap("gap$a", a, b, note, kind)
            }
            i++
        }
        return out.filter { it.endMs - it.startMs >= minMs }
    }

    fun build(
        items: List<Item>,
        visits: List<PlaceVisitEntity>,
        trips: List<TripEntity>,
        placeNames: Map<String, String>,
        dayStartMs: Long,
        dayEndMs: Long,
        nowMs: Long,
        showPlaces: Boolean = true,
        showTrips: Boolean = true,
        minGapMs: Long = MIN_GAP_MS,
        offs: List<Off> = emptyList(),
        showGaps: Boolean = true,
    ): List<Row> {
        // On today nothing after now can be missing yet.
        val limit = if (nowMs in dayStartMs until dayEndMs) nowMs else dayEndMs

        class Stay(val visit: PlaceVisitEntity, val startMs: Long, val endMs: Long, val ongoing: Boolean)

        val stays = ArrayList<Stay>()
        if (showPlaces) {
            // An open/stale stay may claim it continued until now even though a later stay was saved.
            // End every stay at the next visit's actual start instead of moving that later visit forward.
            val eligibleVisits = visits
                .filter { !it.ignored && PlaceRules.lengthMs(it, nowMs) >= PlaceRules.MIN_STAY_MS }
                .sortedBy { it.startMs }
            eligibleVisits.forEachIndexed { index, visit ->
                val nextStart = eligibleVisits.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
                val recordedEnd = if (visit.ongoing) max(nowMs, visit.startMs) else visit.endMs
                val end = min(min(recordedEnd, nextStart), limit)
                val start = max(visit.startMs, dayStartMs)
                if (end > start) stays += Stay(visit, start, end, ongoing = visit.ongoing && nextStart > nowMs)
            }
        }

        val inside = stays.map { ArrayList<Item>() }
        val loose = ArrayList<Item>()
        for (item in items.sortedBy { it.startMs }) {
            val at = stayFor(item, stays.map { it.startMs to it.endMs })
            if (at >= 0) inside[at] += item else loose += item
        }

        val places = stays.mapIndexed { i, s ->
            val things = inside[i].map { Thing(it) }
            val holes: List<Gap> = if (!showGaps) emptyList() else holes(inside[i].map { it.startMs to min(it.coveredEndMs, s.endMs) }, s.startMs, s.endMs, minGapMs)
                .flatMap { (a, b) -> cutGap(a, b, offs, minGapMs, "Phone not used") }
            val id = s.visit.placeId
            Place(
                key = "place" + s.visit.id,
                startMs = s.startMs,
                endMs = s.endMs,
                placeId = id,
                name = placeNames[id]?.takeIf { it.isNotBlank() } ?: "Unnamed spot",
                named = id.isNotEmpty(),
                ongoing = s.ongoing,
                visit = s.visit,
                children = joinGaps((things + holes).sortedBy { it.startMs }),
            )
        }

        val travel = if (showTrips) {
            trips.map {
                Travel("trip" + it.id, max(it.startMs, dayStartMs), it.endMs, it, placeNames[it.fromPlaceId].orEmpty(), placeNames[it.toPlaceId].orEmpty())
            }
        } else {
            emptyList()
        }

        val ordered = (places + travel + loose.map { Thing(it) })
            .sortedWith(compareBy<Row> { it.startMs }.thenBy { if (it is Place) 0 else 1 })

        // Between the first and last thing of the day, time nothing explains becomes a gap, or an unknown journey
        // when it lies between two different places.
        val out = ArrayList<Row>()
        var frontier = Long.MIN_VALUE
        var frontierRow: Row? = null
        for (row in ordered) {
            val before = frontierRow
            if (before != null && row.startMs - frontier >= minGapMs) {
                if (showTrips && before is Place && row is Place && before.placeId != row.placeId) {
                    out += Travel(
                        "unknown$frontier", frontier, row.startMs, null, before.name, row.name,
                        from = before.visit, to = row.visit, straightLineM = PlaceTrail.straightLineM(before.visit, row.visit),
                    )
                } else if (showGaps) {
                    out += cutGap(frontier, row.startMs, offs, minGapMs, "no place and no phone data")
                }
            }
            out += row
            if (row.endMs > frontier) {
                frontier = row.endMs
                frontierRow = row
            }
        }
        return joinGaps(out)
    }

    /**
     * Back-to-back stretches of the same kind (No data, then another No data a few minutes later with nothing between)
     * become one row that remembers each piece, so a day does not show a list of "No data" lines.
     */
    fun joinGaps(rows: List<Row>): List<Row> {
        val out = ArrayList<Row>()
        for (row in rows) {
            val last = out.lastOrNull()
            if (row is Gap && last is Gap && last.kind == row.kind) {
                out[out.size - 1] = Gap(last.key, last.startMs, maxOf(last.endMs, row.endMs), last.note, last.kind, last.parts + row.parts)
            } else {
                out += row
            }
        }
        return out
    }

    /** How far before a stay began an item may start and still belong to it (entries only know whole minutes). */
    const val EDGE_TOLERANCE_MS = 2 * 60_000L

    /**
     * Which of the [stays] (start to end pairs) [item] belongs to, or -1 when it belongs to none. It belongs to the stay it
     * overlaps most, as long as it starts inside that stay (a few minutes early is fine, because entries made with Fill in
     * only know whole minutes and so can start seconds before the stay) or more than half of it lies inside.
     */
    fun stayFor(item: Item, stays: List<Pair<Long, Long>>): Int {
        val length = (item.coveredEndMs - item.startMs).coerceAtLeast(1L)
        var best = -1
        var bestOverlap = 0L
        stays.forEachIndexed { i, (from, to) ->
            val overlap = min(item.coveredEndMs, to) - max(item.startMs, from)
            if (overlap <= 0L) return@forEachIndexed
            val startsInside = item.startMs >= from - EDGE_TOLERANCE_MS && item.startMs < to
            if ((startsInside || overlap * 2 >= length) && overlap > bestOverlap) {
                best = i
                bestOverlap = overlap
            }
        }
        return best
    }

    /** The stretches of [from, to) not covered by any of [covered] that last at least [minMs]. */
    fun holes(covered: List<Pair<Long, Long>>, from: Long, to: Long, minMs: Long): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        var cursor = from
        for ((a, b) in covered.map { max(it.first, from) to min(it.second, to) }.filter { it.second > it.first }.sortedBy { it.first }) {
            if (a - cursor >= minMs) out += cursor to a
            cursor = max(cursor, b)
        }
        if (to - cursor >= minMs) out += cursor to to
        return out
    }

    /**
     * Gives each stretch (start to end) a lane so that stretches that overlap in time sit side by side and the others share a
     * lane. Lane 0 is used first. The answer is in the same order as [spans].
     */
    fun lanes(spans: List<Pair<Long, Long>>): List<Int> {
        val order = spans.indices.sortedWith(compareBy({ spans[it].first }, { spans[it].second }, { it }))
        val laneEnds = ArrayList<Long>()
        val out = IntArray(spans.size)
        for (i in order) {
            val (start, end) = spans[i]
            var lane = laneEnds.indexOfFirst { it <= start }
            if (lane < 0) {
                lane = laneEnds.size
                laneEnds += end
            } else {
                laneEnds[lane] = end
            }
            out[i] = lane
        }
        return out.toList()
    }
}
