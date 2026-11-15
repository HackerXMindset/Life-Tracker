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

    private val malManga = """<?xml version="1.0"?><rss version="2.0"><channel><title>dmmrx&apos;s Manga from MyAnimeList.net</title><link>https://myanimelist.net/</link>
        <item><title>'Til Debt Do Us Part - Manhwa</title><link>https://myanimelist.net/manga/147736/Til_Debt_Do_Us_Part</link><description>Reading - 17 of 72 chapters</description><pubDate>Fri, 06 Nov 2026 07:45:14 -0800</pubDate></item>
        <item><title>Fight Class 3 - Manhwa</title><link>https://myanimelist.net/manga/118805/Fight_Class_3</link><description>Plan to Read - 0 of ? chapters</description><pubDate>Fri, 06 Nov 2026 07:45:28 -0800</pubDate></item>
        </channel></rss>"""

    private val malAnime = """<?xml version="1.0"?><rss version="2.0"><channel><title>dmmrx&apos;s Recently Watched Anime from MyAnimeList.net</title>
        <item><title>Jujutsu Kaisen: Shimetsu Kaiyuu - Zenpen</title><link>https://myanimelist.net/anime/57658/Jujutsu_Kaisen</link><description>Completed - 12 of 12 episodes</description><pubDate>Fri, 06 Nov 2026 07:40:32 -0800</pubDate></item>
        <item><title>Kusuriya no Hitorigoto</title><link>https://myanimelist.net/anime/54492/Kusuriya_no_Hitorigoto</link><description>Watching - 4 of 24 episodes</description><pubDate>Tue, 27 Oct 2026 04:11:39 -0700</pubDate></item>
        <item><title>One Piece</title><link>https://myanimelist.net/anime/21/One_Piece</link><description>Watching - 1180 of ? episodes</description><pubDate>Mon, 28 Sep 2026 03:43:36 -0700</pubDate></item>
        <item><title>Prism Rondo - ONA</title><link>https://myanimelist.net/anime/63019/Prism_Rondo</link><description>Plan to Watch - 0 of 20 episodes</description><pubDate>Fri, 06 Nov 2026 07:44:09 -0800</pubDate></item>
        </channel></rss>"""

    @Test
    fun aMyAnimeListFeedIsRead() {
        val feed = MediaParsers.malFeed(malManga)
        assertEquals("dmmrx's Manga from MyAnimeList.net", feed.channelTitle)
        assertEquals(2, feed.items.size)
        assertEquals(ms("2026-11-06T15:45:14Z"), feed.items[0].pubMs)
        assertEquals("Reading - 17 of 72 chapters", feed.items[0].description)
        assertTrue(MediaParsers.malFeedHasUser(malManga))
        assertFalse(MediaParsers.malFeedHasUser("<rss><channel><title></title></channel></rss>"))
    }

    @Test
    fun myAnimeListMangaIsReadAsAListWithCleanTitles() {
        val items = MediaParsers.malMangaItems(MediaParsers.malFeed(malManga).items)
        assertEquals(2, items.size)
        assertEquals("manga:147736", items[0].key)
        assertEquals("'Til Debt Do Us Part", items[0].title)
        assertEquals(MediaKind.MANGA, items[0].kind)
        assertEquals(17, items[0].progress)
        assertEquals(0, items[1].progress)
    }

    @Test
    fun myAnimeListAnimeGivesOneEventPerUpdateAtItsTime() {
        val items = MediaParsers.malFeed(malAnime).items
        val events = MediaParsers.malAnimeEvents(items, ms("2026-10-01T00:00:00Z"))
        assertEquals(2, events.size)
        val kusuriya = events.first { it.title == "Kusuriya no Hitorigoto" }
        assertEquals("ep 4", kusuriya.label)
        assertEquals(ms("2026-10-27T11:11:39Z"), kusuriya.atMs)
        assertEquals(MediaService.MAL, kusuriya.service)
        assertEquals("Jujutsu Kaisen: Shimetsu Kaiyuu - Zenpen", events.first { it.label == "ep 12" }.title)
        // A plan-to-watch row (0 episodes) and anything before the look-back give nothing.
        assertEquals(3, MediaParsers.malAnimeEvents(items, 0).size)
        assertEquals(events.map { it.id }.toSet().size, events.size)
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
