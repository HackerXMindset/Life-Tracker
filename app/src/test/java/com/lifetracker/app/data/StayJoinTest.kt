package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StayJoinTest {
    private val min = 60_000L
    private val dayEnd = 24 * 60 * min
    private val names = mapOf("hall" to "Millat College Hall", "lab" to "Millat College Lab")

    private fun visit(id: String, place: String, from: Long, to: Long, ongoing: Boolean = false) =
        PlaceVisitEntity(id, place, from * min, to * min, 0.0, 0.0, "auto", ongoing, false)

    private fun walk(id: String, from: Long, to: Long) =
        TripEntity(id, from * min, to * min, "hall", "lab", 200, 4, 5, "", "walk", "auto")

    private fun times(v: PlaceVisitEntity) = v.startMs to v.endMs

    /** What the Timeline does with the edits: hide what is hidden, apply the times, then lay the day out. */
    private fun show(visits: List<PlaceVisitEntity>, trips: List<TripEntity>, edits: Map<String, TimelineEditEntity>): List<DayLayout.Row> {
        val v = visits.mapNotNull {
            val e = edits[TimelineEdits.key(TimelineEdits.VISIT, it.id)]
            if (TimelineEdits.isHidden(e)) return@mapNotNull null
            val (s, en) = TimelineEdits.times(it.startMs, it.endMs, e)
            it.copy(startMs = s, endMs = en)
        }
        val t = trips.filter { !TimelineEdits.isHidden(edits[TimelineEdits.key(TimelineEdits.TRIP, it.id)]) }
        return DayLayout.build(emptyList(), v, t, names, 0L, dayEnd, dayEnd + 1)
    }

    private fun map(change: StayJoin.Change, base: Map<String, TimelineEditEntity> = emptyMap()): Map<String, TimelineEditEntity> {
        val out = HashMap(base)
        for (e in change.edits) {
            if (e.kind == TimelineEdits.VISIT && StayJoin.isEmpty(e)) out.remove(e.key) else out[e.key] = e
        }
        return out
    }

    private val a = visit("a", "hall", 0, 60)
    private val b = visit("b", "lab", 70, 130)
    private val w = walk("w", 60, 70)

    @Test
    fun twoStaysBecomeOneWithTheWalkInside() {
        val change = StayJoin.join(a, b, times(a), times(b), listOf(w), emptyMap(), 1L)!!
        val rows = show(listOf(a, b), listOf(w), map(change))
        val stay = rows.single() as DayLayout.Place
        assertEquals("Millat College Hall", stay.name)
        assertEquals(0L, stay.startMs)
        assertEquals(130 * min, stay.endMs)
    }

    @Test
    fun youCanKeepTheLaterName() {
        val change = StayJoin.join(b, a, times(b), times(a), listOf(w), emptyMap(), 1L)!!
        val stay = show(listOf(a, b), listOf(w), map(change)).single() as DayLayout.Place
        assertEquals("Millat College Lab", stay.name)
        assertEquals(0L, stay.startMs)
        assertEquals(130 * min, stay.endMs)
    }

    @Test
    fun theHiddenOnesRememberTheJoinAndAreNotListedAsHidden() {
        val change = StayJoin.join(a, b, times(a), times(b), listOf(w), emptyMap(), 1L)!!
        val edits = map(change)
        assertTrue(StayJoin.isJoined(edits["visit|b"]))
        assertTrue(StayJoin.isJoined(edits["trip|w"]))
        assertEquals("a", StayJoin.keeperOf(edits.getValue("visit|b")))
        assertFalse(StayJoin.isJoined(edits["visit|a"]))
    }

    @Test
    fun separatingPutsEverythingBack() {
        val joined = map(StayJoin.join(a, b, times(a), times(b), listOf(w), emptyMap(), 1L)!!)
        val back = StayJoin.separate(joined.getValue("visit|b"), joined.values.toList(), 2L)!!
        val rows = show(listOf(a, b), listOf(w), map(back, joined))
        val stays = rows.filterIsInstance<DayLayout.Place>()
        assertEquals(listOf("Millat College Hall", "Millat College Lab"), stays.map { it.name })
        assertEquals(60 * min, stays[0].endMs)
        assertTrue(rows.any { it is DayLayout.Travel })
        assertNull(map(back, joined)["visit|a"])
    }

    @Test
    fun yourOwnEditBeforeTheJoinComesBackAfterSeparating() {
        val own = TimelineEdits.edit(TimelineEdits.VISIT, "a", startMs = 5 * min, endMs = 55 * min, activityId = "study", now = 0L)
        val base = mapOf(own.key to own)
        val joined = map(StayJoin.join(a, b, 5 * min to 55 * min, times(b), listOf(w), base, 1L)!!, base)
        assertEquals("study", joined.getValue("visit|a").activityId)
        val back = map(StayJoin.separate(joined.getValue("visit|b"), joined.values.toList(), 2L)!!, joined)
        assertEquals(5 * min, back.getValue("visit|a").startMs)
        assertEquals(55 * min, back.getValue("visit|a").endMs)
    }

    @Test
    fun theStayInProgressIsAlwaysTheOneKept() {
        val now = visit("n", "lab", 70, 130, ongoing = true)
        assertEquals("n", StayJoin.forcedKeeper(a, now)?.id)
        assertNotNull(StayJoin.join(now, a, times(now), times(a), emptyList(), emptyMap(), 1L))
        // Dropping the stay in progress into an earlier one is refused.
        assertNull(StayJoin.join(a, now, times(a), times(now), emptyList(), emptyMap(), 1L))
        // The end of the kept stay in progress stays open.
        val change = StayJoin.join(now, a, times(now), times(a), emptyList(), emptyMap(), 1L)!!
        assertEquals(-1L, change.edits.first { it.refId == "n" }.endMs)
    }

    @Test
    fun anUnnamedSpotTakesTheNameOfTheNamedOne() {
        val unnamed = visit("u", "", 70, 130)
        assertEquals("a", StayJoin.forcedKeeper(a, unnamed)?.id)
        assertEquals("a", StayJoin.forcedKeeper(unnamed.copy(startMs = 0L, endMs = 60 * min), a.copy(startMs = 70 * min, endMs = 130 * min))?.id)
        assertEquals("a", StayJoin.forcedKeeper(a, visit("a2", "hall", 70, 130))?.id)
    }

    @Test
    fun twoDifferentNamesMeansYouChoose() {
        assertNull(StayJoin.forcedKeeper(a, b))
    }

    @Test
    fun aTripOutsideTheGapIsLeftAlone() {
        val far = walk("far", 200, 230)
        val change = StayJoin.join(a, b, times(a), times(b), listOf(w, far), emptyMap(), 1L)!!
        assertTrue(change.edits.any { it.refId == "w" })
        assertFalse(change.edits.any { it.refId == "far" })
    }

    @Test
    fun neighboursAreTheStaysAroundOne() {
        val c = visit("c", "hall", 140, 200)
        val rows = DayLayout.build(emptyList(), listOf(a, b, c), emptyList(), names, 0L, dayEnd, dayEnd + 1)
        val keys = rows.filterIsInstance<DayLayout.Place>().map { it.key }
        val (before, after) = DayLayout.stayNeighbours(rows, keys[1])
        assertEquals(keys[0], before?.key)
        assertEquals(keys[2], after?.key)
        assertNull(DayLayout.stayNeighbours(rows, keys[0]).first)
    }
}
