package com.lifetracker.app.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRulesTest {
    private val min = 60_000L
    private val t0 = Instant.parse("2026-11-14T20:00:00Z").toEpochMilli()
    private val utc = ZoneId.of("UTC")

    private fun event(id: String, at: Long, units: Int = 1, kind: MediaKind = MediaKind.MANGA, service: MediaService = MediaService.ANILIST, minutes: Int? = null) =
        MediaEvent(id, service, kind, "Title $id", at, units, "ch $units", minutes)

    private fun place(
        events: List<MediaEvent>,
        sessions: List<MediaSession> = emptyList(),
        userBusy: List<MediaSpan> = emptyList(),
        now: Long = t0 + 60 * min,
    ) = MediaPlacer.place(events, { s -> if (s == MediaService.ANILIST) sessions else emptyList() }, userBusy, emptyList(), 8, now, utc)

    // ---- names ----

    @Test
    fun namesAreTakenFromLinksAndPlainText() {
        assertEquals("Bob", MediaNames.aniList("https://anilist.co/user/Bob/"))
        assertEquals("Bob", MediaNames.aniList("@Bob"))
        assertEquals("Bob", MediaNames.aniList("https://anilist.co/user/Bob/animelist"))
        assertEquals("Neko_1", MediaNames.myAnimeList("https://myanimelist.net/profile/Neko_1"))
        assertEquals("Neko_1", MediaNames.myAnimeList(" Neko_1 "))
        assertNull(MediaNames.aniList("not a name!"))
        assertNull(MediaNames.myAnimeList(""))
    }

    @Test
    fun aClientIdMustLookLikeOne() {
        assertEquals("abcdefghijklmnop1234", MediaNames.simklClientId("  abcdefghijklmnop1234 "))
        assertNull(MediaNames.simklClientId("short"))
        assertNull(MediaNames.simklClientId("has spaces in it and more than sixteen"))
    }

    @Test
    fun readerAppsAreSuggestedButNeverAssumed() {
        assertEquals(MediaService.ANILIST, MediaSuggest.forPackage("app.mihon"))
        assertEquals(MediaService.MAL, MediaSuggest.forPackage("org.koitharu.kotatsu"))
        assertEquals(MediaService.SIMKL, MediaSuggest.forPackage("com.lagradost.cloudstream3"))
        assertNull(MediaSuggest.forPackage("com.instagram.android"))
    }

    // ---- diff ----

    private fun item(key: String, progress: Int, updatedMs: Long) = ListItem(key, "Title $key", MediaKind.MANGA, progress, updatedMs)

    @Test
    fun aTitleSeenForTheFirstTimeCountsOnlyIfItChangedRecently() {
        val items = listOf(item("1", 47, t0), item("2", 10, t0 - 100 * 60 * min))
        val result = MediaDiff.events(MediaService.ANILIST, items, emptyMap(), sinceMs = t0 - 10 * min)
        assertEquals(1, result.events.size)
        assertEquals("ch 47", result.events[0].label)
        assertEquals(1, result.events[0].units)
        assertEquals(mapOf("1" to 47, "2" to 10), result.snapshot)
    }

    @Test
    fun aTitleThatMovedForwardGivesTheChaptersItMoved() {
        val result = MediaDiff.events(MediaService.ANILIST, listOf(item("1", 47, t0)), mapOf("1" to 45), sinceMs = 0)
        assertEquals(1, result.events.size)
        assertEquals(2, result.events[0].units)
        assertEquals("ch 46–47", result.events[0].label)
        assertEquals(t0, result.events[0].atMs)
    }

    @Test
    fun noMoveOrABackwardMoveGivesNothingButIsRemembered() {
        val same = MediaDiff.events(MediaService.ANILIST, listOf(item("1", 45, t0)), mapOf("1" to 45), sinceMs = 0)
        assertTrue(same.events.isEmpty())
        val back = MediaDiff.events(MediaService.ANILIST, listOf(item("1", 3, t0)), mapOf("1" to 45), sinceMs = 0)
        assertTrue(back.events.isEmpty())
        assertEquals(3, back.snapshot["1"])
    }

    @Test
    fun aHugeJumpIsCapped() {
        val result = MediaDiff.events(MediaService.ANILIST, listOf(item("1", 100, t0)), mapOf("1" to 45), sinceMs = 0)
        assertEquals(MediaDiff.MAX_UNITS, result.events[0].units)
        assertEquals("ch 71–100", result.events[0].label)
    }

    @Test
    fun theSameChangeAlwaysGetsTheSameId() {
        val a = MediaDiff.events(MediaService.ANILIST, listOf(item("1", 47, t0)), mapOf("1" to 45), sinceMs = 0).events[0].id
        val b = MediaDiff.events(MediaService.ANILIST, listOf(item("1", 47, t0)), mapOf("1" to 45), sinceMs = 0).events[0].id
        assertEquals(a, b)
    }

    // ---- placing ----

    @Test
    fun anEventWithNoAppTimeEndsWhenItWasNotedAndTakesAsLongAsItShould() {
        val r = place(listOf(event("a", t0, units = 2)))
        val p = r.placed.single()
        assertEquals(t0 - 16 * min, p.startMs)
        assertEquals(t0, p.endMs)
        assertFalse(p.fromSession)
    }

    @Test
    fun episodesAndMoviesUseTheLengthTheServiceKnows() {
        val ep = place(listOf(event("a", t0, kind = MediaKind.ANIME, minutes = 24))).placed.single()
        assertEquals(24 * min, ep.endMs - ep.startMs)
        val movie = place(listOf(event("b", t0, kind = MediaKind.MOVIE, minutes = 155))).placed.single()
        assertEquals(155 * min, movie.endMs - movie.startMs)
        val long = place(listOf(event("c", t0, kind = MediaKind.MOVIE, minutes = 600))).placed.single()
        assertEquals(MediaPlacer.MAX_BLOCK_MS, long.endMs - long.startMs)
    }

    @Test
    fun twoEventsAtTheSameMomentAreStackedNotPiledUp() {
        val r = place(listOf(event("a", t0), event("b", t0)))
        assertEquals(2, r.placed.size)
        val a = r.placed.first { it.event.id == "a" }
        val b = r.placed.first { it.event.id == "b" }
        assertEquals(t0, a.endMs)
        assertEquals(a.startMs, b.endMs)
        assertEquals(t0 - 16 * min, b.startMs)
    }

    @Test
    fun timeYouWroteDownIsNeverCovered() {
        val busy = listOf(MediaSpan(t0 - 10 * min, t0 + 10 * min))
        val inside = place(listOf(event("a", t0)), userBusy = busy)
        assertTrue(inside.placed.isEmpty())
        assertEquals(1, inside.skipped.size)
        val after = place(listOf(event("b", t0 + 20 * min, units = 2)), userBusy = busy).placed.single()
        assertEquals(t0 + 10 * min, after.startMs)
        assertEquals(t0 + 20 * min, after.endMs)
    }

    @Test
    fun anAppSessionGivesTheTimeOfTheEvent() {
        val session = MediaSession("app.mihon", t0 - 30 * min, t0 + 10 * min)
        val early = place(listOf(event("a", t0)), listOf(session), now = t0 + 5 * min).placed.single()
        assertTrue(early.fromSession)
        assertEquals(t0 - 30 * min, early.startMs)
        assertEquals(t0, early.endMs)
        val finished = place(listOf(event("a", t0)), listOf(session), now = t0 + 2 * 60 * min).placed.single()
        assertEquals(t0 + 10 * min, finished.endMs)
    }

    @Test
    fun eventsInOneSessionTakeTurns() {
        val session = MediaSession("app.mihon", t0 - 30 * min, t0 + 10 * min)
        val r = place(listOf(event("a", t0 - 10 * min), event("b", t0)), listOf(session), now = t0 + 5 * min)
        val a = r.placed.first { it.event.id == "a" }
        val b = r.placed.first { it.event.id == "b" }
        assertEquals(t0 - 30 * min, a.startMs)
        assertEquals(t0 - 10 * min, a.endMs)
        assertEquals(t0 - 10 * min, b.startMs)
        assertEquals(t0, b.endMs)
    }

    @Test
    fun aServiceNotedAfterTheAppClosedStillMatchesWithinTheSlack() {
        val session = MediaSession("app.mihon", t0 - 30 * min, t0)
        val near = place(listOf(event("a", t0 + 10 * min)), listOf(session), now = t0 + 10 * min).placed.single()
        assertTrue(near.fromSession)
        assertEquals(t0, near.endMs)
        val far = place(listOf(event("b", t0 + 40 * min)), listOf(session), now = t0 + 40 * min).placed.single()
        assertFalse(far.fromSession)
    }

    @Test
    fun anAppForAnotherServiceIsIgnored() {
        val session = MediaSession("app.mihon", t0 - 30 * min, t0 + 10 * min)
        val mal = MediaPlacer.place(
            listOf(event("a", t0, service = MediaService.MAL)),
            { s -> if (s == MediaService.ANILIST) listOf(session) else emptyList() },
            emptyList(), emptyList(), 8, t0 + 60 * min, utc,
        ).placed.single()
        assertFalse(mal.fromSession)
    }

    @Test
    fun anEntryNeverCrossesMidnight() {
        val at = Instant.parse("2026-11-15T00:05:00Z").toEpochMilli()
        val p = place(listOf(event("a", at, units = 2)), now = at + 60 * min).placed.single()
        assertEquals(Instant.parse("2026-11-15T00:00:00Z").toEpochMilli(), p.startMs)
        assertEquals(at, p.endMs)
    }
}
