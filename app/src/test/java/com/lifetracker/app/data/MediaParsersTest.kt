package com.lifetracker.app.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MediaParsersTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun aniListListsAreRead() {
        val body = """{"data":{"Page":{"mediaList":[
            {"id":1,"progress":47,"status":"CURRENT","updatedAt":1763150000,"media":{"id":105398,"type":"MANGA","duration":null,"title":{"userPreferred":"Solo Leveling"}}},
            {"id":2,"progress":3,"status":"CURRENT","updatedAt":1763140000,"media":{"id":16498,"type":"ANIME","duration":24,"title":{"userPreferred":"Attack on Titan"}}}
        ]}}}"""
        val items = MediaParsers.aniListItems(body)
        assertEquals(2, items.size)
        assertEquals("105398", items[0].key)
        assertEquals(MediaKind.MANGA, items[0].kind)
        assertEquals(47, items[0].progress)
        assertEquals(1763150000L * 1000, items[0].updatedMs)
        assertEquals(null, items[0].minutesPerUnit)
        assertEquals(MediaKind.ANIME, items[1].kind)
        assertEquals(24, items[1].minutesPerUnit)
    }

    @Test
    fun aniListUsersAndErrorsAreHandled() {
        assertEquals(5248, MediaParsers.aniListUserId("""{"data":{"User":{"id":5248,"name":"Bob"}}}"""))
        try {
            MediaParsers.aniListUserId("""{"errors":[{"message":"User not found","status":404}],"data":{"User":null}}""")
            fail("expected an error")
        } catch (e: MediaException) {
            assertTrue(e.message!!.contains("no user"))
        }
        try {
            MediaParsers.aniListItems("""{"errors":[{"message":"Too Many Requests.","status":429}]}""")
            fail("expected an error")
        } catch (e: MediaException) {
            assertTrue(e.message!!.contains("too many"))
        }
    }

    @Test
    fun jikanHistoryGivesEventsAtTheTimesMyAnimeListNoted() {
        val body = """{"data":[
            {"entry":{"mal_id":2,"type":"manga","name":"Berserk"},"increment":364,"date":"2026-11-14T14:30:00+00:00"},
            {"entry":{"mal_id":20,"type":"anime","name":"Naruto"},"increment":5,"date":"2026-01-01T00:00:00+00:00"}
        ]}"""
        val events = MediaParsers.jikanHistory(body, ms("2026-11-01T00:00:00Z"))
        assertEquals(1, events.size)
        assertEquals("Berserk", events[0].title)
        assertEquals("ch 364", events[0].label)
        assertEquals(MediaKind.MANGA, events[0].kind)
        assertEquals(MediaService.MAL, events[0].service)
        assertEquals(ms("2026-11-14T14:30:00Z"), events[0].atMs)
        val all = MediaParsers.jikanHistory(body, 0)
        assertEquals(2, all.size)
        assertEquals("ep 5", all[1].label)
        assertEquals(all.map { it.id }.toSet().size, all.size)
    }

    @Test
    fun jikanUsersAreRecognised() {
        assertTrue(MediaParsers.jikanUserExists("""{"data":{"username":"Neko"}}"""))
        assertFalse(MediaParsers.jikanUserExists("""{"status":404}"""))
    }

    private val simkl = """{
        "shows":[{"last_watched_at":"2026-11-14T15:00:00Z","status":"watching","last_watched":"S01E02",
            "show":{"title":"Severance","runtime":50,"ids":{"simkl":111}},
            "seasons":[{"number":1,"episodes":[
                {"number":1,"watched_at":"2026-11-13T15:00:00Z","runtime":55},
                {"number":2,"watched_at":"2026-11-14T15:00:00Z"},
                {"number":3,"watched_at":"1970-01-01T00:00:01Z"}]}]}],
        "movies":[
            {"last_watched_at":"2026-11-12T18:00:00Z","status":"completed","movie":{"title":"Dune","runtime":155,"ids":{"simkl":222}}},
            {"last_watched_at":"2026-11-12T18:00:00Z","status":"plantowatch","movie":{"title":"Skip","ids":{"simkl":333}}}],
        "anime":[{"last_watched_at":"2026-11-14T10:00:00Z","status":"watching","last_watched":"S01E07",
            "show":{"title":"Frieren","runtime":24,"ids":{"simkl":444}}}]
    }"""

    @Test
    fun simklGivesOneEventPerEpisodeAndPerFinishedMovie() {
        val events = MediaParsers.simklEvents(simkl, ms("2026-11-01T00:00:00Z"))
        assertEquals(4, events.size)
        val one = events.first { it.label == "S1E1" }
        assertEquals(55, one.minutesPerUnit)
        assertEquals(ms("2026-11-13T15:00:00Z"), one.atMs)
        assertEquals(50, events.first { it.label == "S1E2" }.minutesPerUnit)
        val movie = events.first { it.kind == MediaKind.MOVIE }
        assertEquals("Dune", movie.title)
        assertEquals(155, movie.minutesPerUnit)
        val anime = events.first { it.kind == MediaKind.ANIME }
        assertEquals("S01E07", anime.label)
        assertEquals(24, anime.minutesPerUnit)
        assertEquals(events.map { it.id }.toSet().size, events.size)
    }

    @Test
    fun simklEntriesAddedForADayInThePastAreKeptWhileTheyAreInsideTheLookBack() {
        assertEquals(4, MediaParsers.simklEvents(simkl, ms("2026-11-01T00:00:00Z")).size)
        assertEquals(2, MediaParsers.simklEvents(simkl, ms("2026-11-14T00:00:00Z")).size)
    }

    @Test
    fun simklSignInAnswersAreRead() {
        val code = MediaParsers.simklDeviceCode("""{"user_code":"ABCD","verification_uri":"https://simkl.com/pin","device_code":"dc","interval":5}""")
        assertEquals("ABCD", code.userCode)
        assertEquals("dc", code.deviceCode)
        assertEquals(5, code.intervalSeconds)
        assertTrue(MediaParsers.simklToken("""{"access_token":"abc"}""") is MediaParsers.TokenPoll.Token)
        assertTrue(MediaParsers.simklToken("""{"error":"authorization_pending"}""") is MediaParsers.TokenPoll.Pending)
        assertTrue(MediaParsers.simklToken("""{"error":"slow_down"}""") is MediaParsers.TokenPoll.SlowDown)
        assertTrue(MediaParsers.simklToken("""{"error":"expired_token"}""") is MediaParsers.TokenPoll.Failed)
        assertEquals("2026-11-14T15:00:00Z", MediaParsers.simklActivitiesStamp("""{"all":"2026-11-14T15:00:00Z"}"""))
    }

    @Test
    fun signInsAndStateStayOutOfBackups() {
        assertFalse(SettingsBackup.included("media_accounts"))
        assertFalse(SettingsBackup.included("media_sync"))
        assertTrue(SettingsBackup.included("media_settings"))
    }
}
