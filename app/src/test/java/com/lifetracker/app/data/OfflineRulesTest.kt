package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRulesTest {
    private fun area(style: String, lat: Double, lng: Double, state: String = AreaState.DONE) =
        AreaView("a", style, TileMath.around(lat, lng, 3.0), state)

    @Test
    fun aReadingIsCoveredOnlyByAnAreaOfItsOwnStyle() {
        val areas = listOf(area("s1", 10.0, 10.0))
        assertTrue(OfflineRules.covered(areas, "s1", 10.01, 10.01))
        assertFalse(OfflineRules.covered(areas, "s2", 10.01, 10.01))
        assertFalse(OfflineRules.covered(areas, "s1", 11.0, 10.0))
    }

    @Test
    fun aFailedOrRemovedAreaCoversNothing_aQueuedOneDoes() {
        assertFalse(OfflineRules.covered(listOf(area("s", 1.0, 1.0, AreaState.FAILED)), "s", 1.0, 1.0))
        assertFalse(OfflineRules.covered(listOf(area("s", 1.0, 1.0, AreaState.REMOVED)), "s", 1.0, 1.0))
        assertTrue(OfflineRules.covered(listOf(area("s", 1.0, 1.0, AreaState.QUEUED)), "s", 1.0, 1.0))
    }

    @Test
    fun anAreaForAPlaceUsesTheReachOrTheCircleWhicheverIsBigger() {
        val small = OfflineRules.placeBounds(0.0, 0.0, 100, 3.0)
        val big = OfflineRules.placeBounds(0.0, 0.0, 5000, 3.0)
        assertEquals(6.0 / 111.32, small.north - small.south, 0.001)
        assertEquals(10.0 / 111.32, big.north - big.south, 0.001)
    }

    @Test
    fun theSignatureChangesWhenTheAreaChanges() {
        val b = TileMath.around(10.0, 10.0, 3.0)
        val s = OfflineRules.signature(b, "s", 8, 14)
        assertEquals(s, OfflineRules.signature(TileMath.around(10.0, 10.0, 3.0), "s", 8, 14))
        assertFalse(s == OfflineRules.signature(TileMath.around(10.0, 10.0, 4.0), "s", 8, 14))
        assertFalse(s == OfflineRules.signature(b, "t", 8, 14))
        assertFalse(s == OfflineRules.signature(b, "s", 8, 15))
    }

    @Test
    fun readingsInsideAnAreaWantNothing() {
        val areas = listOf(area("s", 10.0, 10.0))
        val wanted = OfflineRules.wanted(listOf(10.0 to 10.0, 10.01 to 10.02), areas, "s", 2.0, 8, 14, null)
        assertTrue(wanted.isEmpty())
    }

    @Test
    fun readingsOutsideAskForOneBoxAroundThem_andNeighboursAreJoined() {
        val line = (0..10).map { 20.0 to (30.0 + it * 0.01) }
        val wanted = OfflineRules.wanted(line, emptyList(), "s", 2.0, 8, 14, null)
        assertEquals(1, wanted.size)
        assertTrue(line.all { (la, ln) -> wanted[0].contains(la, ln) })
    }

    @Test
    fun farApartReadingsStaySeparate() {
        val wanted = OfflineRules.wanted(listOf(20.0 to 30.0, 25.0 to 40.0), emptyList(), "s", 2.0, 8, 14, null)
        assertEquals(2, wanted.size)
    }

    @Test
    fun aDiagonalRoadIsNotOneHugeBox() {
        val diagonal = (0..40).map { (20.0 + it * 0.04) to (30.0 + it * 0.04) }
        val wanted = OfflineRules.wanted(diagonal, emptyList(), "s", 2.0, 8, 14, null)
        assertTrue("got ${wanted.size}", wanted.size > 1)
        assertTrue(diagonal.all { (la, ln) -> wanted.any { it.contains(la, ln) } })
    }

    @Test
    fun theProvidersLimitStopsAJoin() {
        val line = (0..30).map { 20.0 to (30.0 + it * 0.02) }
        val limit = MapStyles.Limit("P", 100, 50L * 1024 * 1024, 4, "note")
        val wanted = OfflineRules.wanted(line, emptyList(), "s", 2.0, 8, 14, limit)
        assertTrue(wanted.size > 1)
        assertTrue(wanted.all { TileMath.count(it, 8, 14) <= 100 })
    }

    @Test
    fun theLimitMessageNamesTheRuleAndTheNumbers() {
        val limit = MapStyles.OPENFREEMAP_LIMIT
        assertNull(OfflineRules.overLimit(100, 1_000_000, limit))
        assertNull(OfflineRules.overLimit(1_000_000, 1_000_000_000, null))
        val msg = OfflineRules.overLimit(5_000, 10_000_000, limit)
        assertNotNull(msg)
        assertTrue(msg!!.contains("3,000 tiles or 50 MB"))
        assertTrue(msg.contains("5000 tiles"))
        assertNotNull(OfflineRules.overLimit(100, 60L * 1024 * 1024, limit))
    }

    @Test
    fun onlyAFullDiskRefuses_andBothNumbersAreShown() {
        assertNull(OfflineRules.storageProblem(10_000_000, 10_000_000))
        val msg = OfflineRules.storageProblem(3L * 1024 * 1024, 1024L * 1024)
        assertEquals("Not enough free space: this needs about 3.0 MB and the phone has 1.0 MB free.", msg)
    }

    @Test
    fun theListLineReadsInPlainWords() {
        assertEquals("Downloaded · 214 tiles · 3.0 MB", OfflineRules.stateText(AreaState.DONE, AreaSource.HAND, 214, 214, 3L * 1024 * 1024, ""))
        assertEquals("Downloading · 40%", OfflineRules.stateText(AreaState.DOWNLOADING, AreaSource.PLACE, 40, 100, 0, ""))
        assertEquals("Waiting for a connection or a turn", OfflineRules.stateText(AreaState.QUEUED, AreaSource.WANTED, 0, 0, 0, ""))
        assertEquals("Did not finish: no space", OfflineRules.stateText(AreaState.FAILED, AreaSource.HAND, 0, 0, 0, "no space"))
        assertEquals("Tiles deleted", OfflineRules.stateText(AreaState.REMOVED, AreaSource.HAND, 0, 0, 0, ""))
    }
}
