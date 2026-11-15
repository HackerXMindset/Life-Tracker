package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMatchTest {
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L
    private val order = MediaService.DEFAULT_PRIORITY

    private fun ev(
        id: String, service: MediaService, title: String = "Solo Leveling", at: Long = t0, from: Double? = 46.0, to: Double? = 46.0,
        ids: Set<String> = emptySet(), kind: MediaKind = MediaKind.MANGA,
    ) = MediaEvent(id, service, kind, title, at, 1, "", null, ids, from, to)

    @Test
    fun theSameTitleIsRecognisedByNumberOrByName() {
        assertTrue(MediaMatch.same(ev("a", MediaService.ANILIST, ids = setOf("mal:manga:1")), ev("b", MediaService.MIHON, title = "Other name", ids = setOf("mal:manga:1"))))
        assertTrue(MediaMatch.same(ev("a", MediaService.ANILIST), ev("b", MediaService.MAL, title = "solo-leveling!")))
        assertFalse(MediaMatch.same(ev("a", MediaService.ANILIST), ev("b", MediaService.MAL, title = "Tower of God")))
    }

    @Test
    fun differentNumbersOrFarApartTimesOrTheSameServiceAreNotTheSame() {
        assertFalse(MediaMatch.same(ev("a", MediaService.ANILIST), ev("b", MediaService.MIHON, from = 47.0, to = 47.0)))
        assertFalse(MediaMatch.same(ev("a", MediaService.ANILIST), ev("b", MediaService.MIHON, at = t0 + 13 * 60 * min)))
        assertFalse(MediaMatch.same(ev("a", MediaService.ANILIST), ev("b", MediaService.ANILIST)))
        assertFalse(MediaMatch.same(ev("a", MediaService.ANILIST), ev("b", MediaService.MAL, kind = MediaKind.ANIME)))
    }

    @Test
    fun theBestRankedPlaceWinsInABatch() {
        val plan = MediaMatch.plan(listOf(ev("a", MediaService.ANILIST), ev("m", MediaService.MAL), ev("s", MediaService.SIMKL, kind = MediaKind.MANGA)), emptyList(), order)
        assertEquals(listOf("s"), plan.place.map { it.id })
        assertEquals(2, plan.drop.size)
    }

    @Test
    fun yourOrderChangesTheWinner() {
        val flipped = listOf(MediaService.MAL, MediaService.ANILIST, MediaService.SIMKL, MediaService.MIHON, MediaService.KOTATSU)
        val plan = MediaMatch.plan(listOf(ev("a", MediaService.ANILIST), ev("m", MediaService.MAL)), emptyList(), flipped)
        assertEquals(listOf("m"), plan.place.map { it.id })
    }

    @Test
    fun aWinnersWholeRunIsKeptNotJustOne() {
        val range = ev("a", MediaService.ANILIST, from = 46.0, to = 47.0)
        val p1 = ev("m1", MediaService.MIHON, from = 46.0, to = 46.0)
        val p2 = ev("m2", MediaService.MIHON, from = 47.0, to = 47.0, at = t0 + min)
        val plan = MediaMatch.plan(listOf(range, p1, p2), emptyList(), order)
        assertEquals(setOf("m1", "m2"), plan.place.map { it.id }.toSet())
        assertEquals(listOf("a"), plan.drop.map { it.first.id })
    }

    @Test
    fun somethingAlreadyAddedFromABetterPlaceMakesTheNewOneADuplicate() {
        val old = MediaMatch.Logged(ev("m", MediaService.MIHON), true)
        val plan = MediaMatch.plan(listOf(ev("a", MediaService.ANILIST)), listOf(old), order)
        assertTrue(plan.place.isEmpty())
        assertEquals(1, plan.drop.size)
        assertTrue(plan.replace.isEmpty())
    }

    @Test
    fun aBetterRecordReplacesAnEarlierGuessUnlessYouChangedIt() {
        val oldEvent = ev("a", MediaService.ANILIST, from = 46.0, to = 47.0)
        val fresh = listOf(ev("m1", MediaService.MIHON, from = 46.0, to = 46.0), ev("m2", MediaService.MIHON, from = 47.0, to = 47.0))
        val replaced = MediaMatch.plan(fresh, listOf(MediaMatch.Logged(oldEvent, true)), order)
        assertEquals(2, replaced.place.size)
        assertEquals(listOf(oldEvent), replaced.replace)
        val kept = MediaMatch.plan(fresh, listOf(MediaMatch.Logged(oldEvent, false)), order)
        assertTrue(kept.replace.isEmpty())
        assertTrue(kept.place.isEmpty())
    }

    @Test
    fun aPartialBetterRecordDoesNotThrowAwayWhatItDoesNotCover() {
        val oldEvent = ev("a", MediaService.ANILIST, from = 46.0, to = 47.0)
        val plan = MediaMatch.plan(listOf(ev("m1", MediaService.MIHON, from = 46.0, to = 46.0)), listOf(MediaMatch.Logged(oldEvent, true)), order)
        assertTrue(plan.replace.isEmpty())
        assertEquals(1, plan.place.size)
    }

    @Test
    fun aSavedOrderMissingAServiceIsCompleted() {
        assertEquals(MediaService.entries.toSet(), MediaMatch.completeOrder(listOf(MediaService.MAL)).toSet())
        assertEquals(MediaService.MAL, MediaMatch.completeOrder(listOf(MediaService.MAL)).first())
    }
}
