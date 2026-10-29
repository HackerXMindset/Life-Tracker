package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangesRulesTest {
    private val min = 60_000L
    private val text: (Long) -> String = { "t" + it / min }

    private fun trip(id: String, from: Long, to: Long) = TripEntity(id, from * min, to * min, "", "", 1500, 5, 6, "", "walk", "auto")

    private fun hidden(kind: String, id: String, at: Long, label: String = "Trip", updated: Long = 1000L) =
        TimelineEditEntity(kind, id, at * min, -1L, "", true, updated, label)

    @Test
    fun aHiddenTripAndAHiddenStayAreListedNewestFirst() {
        val trips = listOf(trip("t1", 10, 20))
        val visit = PlaceVisitEntity("v1", "home", 30 * min, 50 * min, 0.0, 0.0, "auto", false, false)
        val items = ChangesRules.items(
            emptyList(), trips, listOf(visit),
            listOf(hidden(TimelineEdits.TRIP, "t1", 10, updated = 5), hidden(TimelineEdits.VISIT, "v1", 30, updated = 9)),
            mapOf("home" to "Home"), text,
        )
        assertEquals(listOf(ChangeItem.Kind.HIDDEN_STAY, ChangeItem.Kind.HIDDEN_TRIP), items.map { it.kind })
        assertEquals("Hidden stay at Home", items[0].title)
        assertEquals("t10 to t20 · ", items[1].detail.take(13))
    }

    @Test
    fun somethingJoinedAwayByAMergeIsNotListedOnItsOwn() {
        val joinedAway = hidden(TimelineEdits.TRIP, "t2", 40, TripEditing.JOINED_TRIP_LABEL)
        assertTrue(ChangesRules.items(emptyList(), listOf(trip("t2", 40, 50)), emptyList(), listOf(joinedAway), emptyMap(), text).isEmpty())
    }

    @Test
    fun aJoinCanBeUndoneAndNamesTheJoinedTrip() {
        val joined = trip("a", 0, 60)
        val later = trip("b", 40, 60)
        val edit = TripEditEntity("m1", TripEditKind.MERGE, 20 * min, 40 * min, 7L)
        val items = ChangesRules.items(listOf(edit), listOf(joined.copy(endMs = 60 * min), later), emptyList(), emptyList(), emptyMap(), text)
        assertEquals(1, items.size)
        assertEquals(ChangeItem.Kind.JOIN, items[0].kind)
        assertTrue(items[0].canRestore)
        assertEquals("m1", items[0].editId)
    }

    @Test
    fun aJoinWithACutOnTopWaitsForTheCut() {
        val earlier = trip("a", 0, 30)
        val later = trip("b", 40, 60)
        // after the join "a" runs 0..60; it was then cut at 25
        val merge = TripEditEntity("m1", TripEditKind.MERGE, 20 * min, 40 * min, 7L)
        val cut = TripEditEntity("s1", TripEditKind.SPLIT, 25 * min, 0L, 9L)
        val trips = listOf(earlier.copy(endMs = 25 * min), trip("c", 25, 60), later)
        val items = ChangesRules.items(listOf(merge, cut), trips, emptyList(), emptyList(), emptyMap(), text)
        val join = items.firstOrNull { it.kind == ChangeItem.Kind.JOIN }
        // The join cannot be found any more (the trips changed), and says so rather than failing.
        assertTrue(join == null || !join.canRestore)
        assertFalse(items.none { it.kind == ChangeItem.Kind.CUT })
    }

    @Test
    fun anUndoneEditIsNotListed() {
        val edit = TripEditEntity("m1", TripEditKind.MERGE, 20 * min, 40 * min, 7L, undoneMs = 99L)
        assertTrue(ChangesRules.items(listOf(edit), emptyList(), emptyList(), emptyList(), emptyMap(), text).isEmpty())
    }
}
