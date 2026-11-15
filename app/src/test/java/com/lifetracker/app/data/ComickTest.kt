package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComickTest {
    private val now = 1_800_000_000_000L
    private fun attempt() = ComickAuth.Attempt("stateABC", "verifierXYZ", now)

    @Test
    fun theChallengeMatchesTheStandardExample() {
        // The example in RFC 7636 appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ComickAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun newAttemptsAreRandomAndLongEnough() {
        val a = ComickAuth.newAttempt(now)
        val b = ComickAuth.newAttempt(now)
        assertTrue(a.verifier.length in 43..128)
        assertTrue(a.state != b.state && a.verifier != b.verifier)
        assertTrue(a.verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun theSignInAddressCarriesEverythingComickAsksFor() {
        val q = ComickAuth.queryOf(ComickAuth.authorizeUrl("client123", attempt()))
        assertEquals(listOf("code"), q["response_type"])
        assertEquals(listOf("client123"), q["client_id"])
        assertEquals(listOf("com.lifetracker.app:/oauth/comick"), q["redirect_uri"])
        assertEquals(listOf("library:read offline_access"), q["scope"])
        assertEquals(listOf("https://api.comick.dev/integrations/v1"), q["resource"])
        assertEquals(listOf("stateABC"), q["state"])
        assertEquals(listOf("S256"), q["code_challenge_method"])
        assertEquals(listOf(ComickAuth.challenge("verifierXYZ")), q["code_challenge"])
        assertTrue(ComickAuth.authorizeUrl("client123", attempt()).startsWith("https://comick.dev/api/auth/oauth2/authorize?"))
    }

    @Test
    fun aGoodCallbackGivesTheCodeAndVerifier() {
        val r = ComickAuth.parseCallback("com.lifetracker.app:/oauth/comick?code=abc%20d&state=stateABC", attempt(), now + 1000)
        assertTrue(r is ComickAuth.Callback.Code)
        r as ComickAuth.Callback.Code
        assertEquals("abc d", r.code)
        assertEquals("verifierXYZ", r.verifier)
    }

    @Test
    fun aCallbackThatDoesNotCheckOutIsRefused() {
        fun bad(url: String, att: ComickAuth.Attempt? = attempt(), at: Long = now + 1000) =
            ComickAuth.parseCallback(url, att, at) is ComickAuth.Callback.Invalid
        assertTrue(bad("com.lifetracker.app:/oauth/comick?code=c&state=other"))
        assertTrue(bad("com.lifetracker.app:/oauth/comick?code=c"))
        assertTrue(bad("com.lifetracker.app:/oauth/comick?code=c&state=stateABC&state=stateABC"))
        assertTrue(bad("com.lifetracker.app:/oauth/comick?code=a&code=b&state=stateABC"))
        assertTrue(bad("com.lifetracker.app:/oauth/comick?state=stateABC"))
        assertTrue(bad("com.lifetracker.app:/oauth/comick?code=c&state=stateABC", null))
        assertTrue(bad("com.lifetracker.app:/oauth/comick?code=c&state=stateABC", attempt(), now + 11 * 60_000L))
    }

    @Test
    fun declinedIsNotAnError() {
        val r = ComickAuth.parseCallback("com.lifetracker.app:/oauth/comick?error=access_denied&state=stateABC", attempt(), now)
        assertTrue(r is ComickAuth.Callback.Denied)
    }

    @Test
    fun tokensAreReadWithOrWithoutARefreshToken() {
        val t = MediaParsers.comickTokens("""{"access_token":"A","token_type":"Bearer","expires_in":900,"scope":"library:read offline_access","refresh_token":"R"}""")
        assertEquals("A", t.access); assertEquals("R", t.refresh); assertEquals(900L, t.expiresInSeconds)
        val n = MediaParsers.comickTokens("""{"access_token":"A","expires_in":900,"scope":"library:read"}""")
        assertNull(n.refresh)
    }

    @Test
    fun anExpiredGrantAsksToConnectAgain() {
        try {
            MediaParsers.comickTokens("""{"error":"invalid_grant","error_description":"x"}""")
            throw AssertionError("should have thrown")
        } catch (e: MediaException) {
            assertEquals(ComickAuth.RECONNECT, e.message)
        }
    }

    private val library = """
        {"data":[
          {"hid":"aB7kP2xQ","title":"Solo Leveling","media_type":"manga","status":1,
           "progress":{"unit":"chapter","hid":null,"number":"51","volume":null},
           "progressed_at":"2026-11-15T10:00:00.000Z","updated_at":"2026-11-14T10:00:00.000Z",
           "titles":[{"title":"Solo Leveling","lang":"en","is_default":true},{"title":"Na Honjaman Level-Up","lang":"ko","is_default":false}]},
          {"hid":"rT4mN8vW","title":"Some Anime","media_type":"anime","status":1,
           "progress":{"unit":"episode","hid":"x","number":"3","volume":null},
           "progressed_at":null,"updated_at":"2026-11-14T10:00:00.000Z","titles":[]},
          {"hid":"noProg","title":"Planned","media_type":"manga","status":5,"progress":null,"progressed_at":null,"updated_at":"2026-11-14T10:00:00.000Z","titles":[]},
          {"hid":"dec","title":"Decimal","media_type":"manga","status":1,"progress":{"unit":"chapter","hid":null,"number":"12.5","volume":null},
           "progressed_at":"2026-11-15T09:00:00.000Z","titles":[]},
          {"hid":"word","title":"Word","media_type":"manga","status":1,"progress":{"unit":"chapter","hid":null,"number":"extra","volume":null},"titles":[]}
        ],"next_cursor":"CURSOR=="}
    """.trimIndent()

    @Test
    fun aLibraryPageGivesItemsWithProgressAndTimes() {
        val page = MediaParsers.comickPage(library)
        assertEquals("CURSOR==", page.next)
        assertEquals(listOf("Solo Leveling", "Some Anime", "Decimal"), page.items.map { it.title })
        val solo = page.items[0]
        assertEquals(MediaKind.MANGA, solo.kind)
        assertEquals(51, solo.progress)
        assertEquals(MediaParsers.parseInstant("2026-11-15T10:00:00.000Z"), solo.updatedMs)
        assertTrue("comick:aB7kP2xQ" in solo.ids)
        assertTrue("title:nahonjamanlevelup" in solo.ids)
        assertEquals(MediaKind.ANIME, page.items[1].kind)
        assertEquals(MediaParsers.parseInstant("2026-11-14T10:00:00.000Z"), page.items[1].updatedMs)
        assertEquals(12, page.items[2].progress)
    }

    @Test
    fun theLastPageHasNoCursor() {
        assertNull(MediaParsers.comickPage("""{"data":[],"next_cursor":null}""").next)
    }

    @Test
    fun comickProgressBecomesEventsLikeAnyOtherList() {
        val items = MediaParsers.comickPage(library).items
        val since = MediaParsers.parseInstant("2026-11-15T00:00:00Z")!!
        val first = MediaDiff.events(MediaService.COMICK, items, emptyMap(), since)
        assertEquals(listOf("Solo Leveling", "Decimal"), first.events.map { it.title })
        assertEquals("ch 51", first.events[0].label)
        val moved = MediaDiff.events(MediaService.COMICK, items.map { if (it.title == "Solo Leveling") ListItem(it.key, it.title, it.kind, 53, it.updatedMs, null, it.ids) else it }, first.snapshot, since)
        assertEquals(1, moved.events.size)
        assertEquals("ch 52–53", moved.events.single().label)
    }

    @Test
    fun theSameTitleUnderAnotherNameIsRecognisedThroughComicksNames() {
        val comick = MediaParsers.comickPage(library).items[0].let {
            MediaEvent("c", MediaService.COMICK, MediaKind.MANGA, it.title, 1_800_000_000_000L, 1, "", null, it.ids, 51.0, 51.0)
        }
        val other = MediaEvent("a", MediaService.ANILIST, MediaKind.MANGA, "Na Honjaman Level-Up", 1_800_000_000_000L, 1, "", null, emptySet(), 51.0, 51.0)
        assertTrue(MediaMatch.same(comick, other))
    }
}
