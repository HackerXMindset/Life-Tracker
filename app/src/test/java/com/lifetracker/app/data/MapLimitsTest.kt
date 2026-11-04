package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLimitsTest {
    private val mb = 1_000_000L
    private val day = 86_400_000L

    private fun area(id: String, source: String = AreaSource.PLACE, style: String = "s1", place: String = "p1", state: String = AreaState.DONE) =
        MapAreaEntity(
            id, id, style, "u", 77.0, 28.0, 77.2, 28.2, 8, 14, source, place, "", state,
            createdMs = 1_000L, doneMs = 2_000L, tiles = 10L, bytes = 100 * mb, totalTiles = 10L, regionId = 1L, note = "",
        )

    private fun c(id: String, mbs: Long, rank: Int, used: Long) = MapLimits.Candidate(id, mbs * mb, rank, used)

    @Test fun ranksTellWhatIsInUse() {
        val places = setOf("p1")
        assertEquals(MapLimits.YOURS, MapLimits.rank(area("a", AreaSource.HAND), "s1", places))
        assertEquals(MapLimits.NOT_IN_USE, MapLimits.rank(area("a", style = "old"), "s1", places))
        assertEquals(MapLimits.NOT_IN_USE, MapLimits.rank(area("a", place = "gone"), "s1", places))
        assertEquals(MapLimits.IN_USE, MapLimits.rank(area("a"), "s1", places))
        assertEquals(MapLimits.IN_USE, MapLimits.rank(area("w", AreaSource.WANTED, place = ""), "s1", places))
    }

    @Test fun nothingIsDeletedWhenItFitsOrThereIsNoLimit() {
        val cands = listOf(c("a", 400, MapLimits.IN_USE, 1))
        assertTrue(MapLimits.plan(cands, 400 * mb, 100 * mb, 1_000 * mb, true).delete.isEmpty())
        assertTrue(MapLimits.plan(cands, 400 * mb, 10_000 * mb, 0L, true).ok)
        assertTrue(MapLimits.plan(cands, 400 * mb, 10_000 * mb, 0L, false).ok)
    }

    @Test fun mapsNotInUseGoFirstThenTheOldestUsed() {
        val cands = listOf(
            c("recent", 300, MapLimits.IN_USE, 900),
            c("old", 300, MapLimits.IN_USE, 100),
            c("otherStyle", 300, MapLimits.NOT_IN_USE, 800),
            c("mine", 300, MapLimits.YOURS, 1),
        )
        // 1200 used, limit 1000, 100 wanted: 300 must go, and the map of the other style goes though it was used more recently.
        val one = MapLimits.plan(cands, 1_200 * mb, 100 * mb, 1_000 * mb, true)
        assertTrue(one.ok)
        assertEquals(listOf("otherStyle"), one.delete)
        // With 400 wanted, 600 must go: the other style first, then the one used longest ago, and never the one that is yours.
        val two = MapLimits.plan(cands, 1_200 * mb, 400 * mb, 1_000 * mb, true)
        assertTrue(two.ok)
        assertEquals(listOf("otherStyle", "old"), two.delete)
    }

    @Test fun yourOwnAreasAreNeverDeletedAndCanMakeItImpossible() {
        val cands = listOf(c("mine", 900, MapLimits.YOURS, 1), c("auto", 100, MapLimits.IN_USE, 1))
        val plan = MapLimits.plan(cands, 1_000 * mb, 300 * mb, 1_000 * mb, true)
        assertFalse(plan.ok)
        assertTrue(plan.delete.isEmpty())
        assertTrue(plan.problem!!.contains("areas you chose yourself"))
    }

    @Test fun withDeletingOffADownloadThatDoesNotFitIsRefusedAndNothingIsDeleted() {
        val cands = listOf(c("auto", 500, MapLimits.NOT_IN_USE, 1))
        val plan = MapLimits.plan(cands, 800 * mb, 300 * mb, 1_000 * mb, false)
        assertFalse(plan.ok)
        assertTrue(plan.delete.isEmpty())
        assertTrue(plan.problem!!.contains("Raise the limit"))
    }

    @Test fun aNewPlaceGetsItsMapAndAnUnvisitedOldOneDoesNot() {
        val now = 200L * day
        assertTrue(MapLimits.placeWanted(createdMs = now - 2 * day, lastVisitMs = null, nowMs = now))
        assertFalse(MapLimits.placeWanted(createdMs = now - 30 * day, lastVisitMs = null, nowMs = now))
        assertTrue(MapLimits.placeWanted(createdMs = now - 150 * day, lastVisitMs = now - 10 * day, nowMs = now))
        assertFalse(MapLimits.placeWanted(createdMs = now - 150 * day, lastVisitMs = now - 120 * day, nowMs = now))
    }

    @Test fun useIsTheLatestReadingInsideAnAreaOfTheStyleInUse() {
        val a = area("a")
        val other = area("b", style = "old")
        val removed = area("c", state = AreaState.REMOVED)
        val readings = listOf(
            Triple(28.1, 77.1, 500L),
            Triple(28.1, 77.1, 900L),
            Triple(50.0, 10.0, 999L), // nowhere
        )
        val used = MapLimits.usage(readings, listOf(a, other, removed), "s1")
        assertEquals(mapOf("a" to 900L), used)
    }

    @Test fun lastUsedFallsBackToWhenItWasMade() {
        val a = area("a")
        assertEquals(2_000L, MapLimits.lastUsed(a, 0L))
        assertEquals(5_000L, MapLimits.lastUsed(a, 5_000L))
    }

    @Test fun onlyBadLuckIsTriedAgainByItself() {
        assertTrue(MapLimits.isTransient("No progress for 3 minutes (timeout)"))
        assertTrue(MapLimits.isTransient("The map library could not start this download."))
        assertFalse(MapLimits.isTransient("Not enough free space: this needs about 2 GB and the phone has 1 GB free."))
        assertFalse(MapLimits.isTransient("This needs about 3 GB and your maps already take 1 GB of your 1 GB limit."))
    }

    @Test fun limitsAreReadable() {
        assertEquals("No limit", MapLimits.label(0L))
        assertEquals(6, MapLimits.CHOICES.size)
        assertTrue(MapLimits.CHOICES.contains(MapLimits.DEFAULT_BYTES))
        assertEquals(0.5f, MapLimits.fill(500 * mb, 1_000 * mb), 0.001f)
        assertEquals(0f, MapLimits.fill(500 * mb, 0L), 0.001f)
        assertEquals(1f, MapLimits.fill(2_000 * mb, 1_000 * mb), 0.001f)
        assertNull(MapLimits.plan(emptyList(), 0L, 0L, 1_000 * mb, true).problem)
    }
}
