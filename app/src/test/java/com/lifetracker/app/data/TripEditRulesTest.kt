package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEditRulesTest {
    private val t0 = 1_760_000_000_000L
    private val min = 60_000L

    private fun fix(minute: Int, lat: Double) = RawFixEntity(t0 + minute * min, lat, 77.2, 8f)

    /** Two trips with a 20 minute silence between them. */
    private val firstHalf = (0..4).map { fix(it, 28.6 + it * 0.001) }
    private val secondHalf = (25..29).map { fix(it, 28.604 + (it - 24) * 0.001) }
    private val all = firstHalf + secondHalf

    /** One 10 reading trip. */
    private val ride = (0..9).map { fix(it, 28.6 + it * 0.001) }

    @Test
    fun aMergeJoinsTwoNeighbouringTripsAndBringsAlongTheReadingsBetween() {
        val cut = TripCutter.cut(all)
        assertEquals(2, cut.size)
        val between = fix(12, 28.6045)
        val edit = TripEditRules.mergeEdit("m1", cut[0], cut[1], 100L)
        val merged = TripEditRules.apply(cut, all + between, listOf(edit))
        assertEquals(1, merged.size)
        assertEquals(t0, merged[0].startMs)
        assertEquals(t0 + 29 * min, merged[0].endMs)
        assertEquals(11, merged[0].points.size)
        assertTrue(merged[0].points.any { it.ms == between.ms })
    }

    @Test
    fun aMergeWhoseBoundaryIsGoneIsSkipped() {
        val cut = TripCutter.cut(all)
        val stale = TripEditEntity("m2", TripEditKind.MERGE, t0 + 3 * min, t0 + 25 * min, 100L)
        assertEquals(2, TripEditRules.apply(cut, all, listOf(stale)).size)
    }

    @Test
    fun aSplitCutsAtTheChosenReadingAndBothPiecesAreKeptEvenIfSmall() {
        val cut = TripCutter.cut(ride)
        val edit = TripEditRules.splitEdit("s1", t0 + 5 * min, 100L)
        val pieces = TripEditRules.apply(cut, ride, listOf(edit))
        assertEquals(2, pieces.size)
        assertEquals(t0 + 4 * min, pieces[0].endMs)
        assertEquals(t0 + 5 * min, pieces[1].startMs)
        assertTrue(pieces.all { it.manual })
        assertEquals(5, pieces[0].points.size)
        assertEquals(5, pieces[1].points.size)
    }

    @Test
    fun aSplitNeedsTwoReadingsOnEachSideAndARealReading() {
        val seg = Segment(ride)
        assertFalse(TripEditRules.canSplit(seg, t0 + 1 * min))
        assertTrue(TripEditRules.canSplit(seg, t0 + 2 * min))
        assertTrue(TripEditRules.canSplit(seg, t0 + 8 * min))
        assertFalse(TripEditRules.canSplit(seg, t0 + 9 * min))
        assertFalse(TripEditRules.canSplit(seg, t0 + 5 * min + 1000))
        val skipped = TripEditRules.apply(TripCutter.cut(ride), ride, listOf(TripEditRules.splitEdit("s2", t0 + 9 * min, 100L)))
        assertEquals(1, skipped.size)
    }

    @Test
    fun aSplitAndThenAMergeOfTheSamePlaceMakeOneTripAgain() {
        val cut = TripCutter.cut(ride)
        val split = TripEditRules.splitEdit("s1", t0 + 5 * min, 100L)
        val pieces = TripEditRules.apply(cut, ride, listOf(split))
        val merge = TripEditRules.mergeEdit("m1", pieces[0], pieces[1], 200L)
        val result = TripEditRules.apply(cut, ride, listOf(split, merge))
        assertEquals(1, result.size)
        assertEquals(10, result[0].points.size)
        // The last action on a boundary wins, whatever order the list is in.
        assertEquals(1, TripEditRules.apply(cut, ride, listOf(merge, split)).size)
    }

    @Test
    fun aSplitMadeAfterAMergeWins() {
        val cut = TripCutter.cut(all)
        val merge = TripEditRules.mergeEdit("m1", cut[0], cut[1], 100L)
        val merged = TripEditRules.apply(cut, all, listOf(merge))
        assertEquals(1, merged.size)
        val split = TripEditRules.splitEdit("s1", t0 + 25 * min, 200L)
        val result = TripEditRules.apply(cut, all, listOf(merge, split))
        assertEquals(2, result.size)
        assertEquals(t0 + 4 * min, result[0].endMs)
        assertEquals(t0 + 25 * min, result[1].startMs)
    }

    @Test
    fun unknownEditKindsAreIgnored() {
        val cut = TripCutter.cut(all)
        val odd = TripEditEntity("x", "rotate", t0, 0L, 1L)
        assertEquals(2, TripEditRules.apply(cut, all, listOf(odd)).size)
    }

    @Test
    fun theWholeChainGivesTheTripsToShow() {
        val wobble = (0..5).map { fix(it, 28.6 + (it % 2) * 0.0001) }
        assertTrue(TripEditRules.trips(wobble, emptyList(), emptyList()).isEmpty())
        val split = TripEditRules.splitEdit("s", t0 + 3 * min, 1L)
        assertEquals(2, TripEditRules.trips(wobble, emptyList(), listOf(split)).size)
        assertEquals(1, TripEditRules.trips(ride, emptyList(), emptyList()).size)
    }
}
