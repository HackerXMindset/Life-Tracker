package com.lifetracker.app.data

import com.lifetracker.app.data.PlaceNames.Source
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNamesTest {
    private val lat = 25.4358
    private val lng = 81.8463

    private fun place(id: String, name: String, dLat: Double, radius: Int = 50, archived: Boolean = false) =
        PlaceEntity(id, name, "other", lat + dLat, lng, radius, archived, 0L)

    @Test fun realPlacesComeBeforePlainBuildingsEvenWhenTheBuildingIsCloser() {
        val json = """{"elements":[
            {"type":"way","id":1,"center":{"lat":${lat + 0.00005},"lon":$lng},"tags":{"name":"Block C","building":"yes"}},
            {"type":"node","id":2,"lat":${lat + 0.0003},"lon":$lng,"tags":{"name":"Sharma Sweets","shop":"confectionery"}}
        ]}"""
        val out = PlaceNames.parseOverpass(json, lat, lng, "en")
        assertEquals(listOf("Sharma Sweets", "Block C"), out.map { it.name })
        assertEquals("Confectionery", out[0].kind)
    }

    @Test fun theEnglishNameIsUsedWhenTheMapHasOne() {
        val tags = JSONObject("""{"name":"प्रयागराज जंक्शन","name:en":"Prayagraj Junction","railway":"station"}""")
        assertEquals("Prayagraj Junction", PlaceNames.pickName(tags, "en"))
        assertEquals("प्रयागराज जंक्शन", PlaceNames.pickName(tags, ""))
    }

    @Test fun theInternationalNameIsUsedWhenThereIsNoEnglishOne() {
        val tags = JSONObject("""{"name":"پریاگ راج","int_name":"Prayag Raj"}""")
        assertEquals("Prayag Raj", PlaceNames.pickName(tags, "en"))
        val noLatin = JSONObject("""{"name":"پریاگ راج","int_name":"پریاگ"}""")
        assertEquals("پریاگ راج", PlaceNames.pickName(noLatin, "en"))
    }

    @Test fun repeatsAreDroppedAndOnlyFiveAreKept() {
        val items = (0 until 8).joinToString(",") {
            """{"type":"node","id":$it,"lat":${lat + it * 0.00002},"lon":$lng,"tags":{"name":"Shop ${it % 7}","shop":"yes"}}"""
        }
        val out = PlaceNames.parseOverpass("""{"elements":[$items]}""", lat, lng)
        assertEquals(5, out.size)
        assertEquals(out.map { it.name.lowercase() }.toSet().size, out.size)
    }

    @Test fun unnamedThingsAndGarbageGiveNothing() {
        assertTrue(PlaceNames.parseOverpass("""{"elements":[{"type":"node","id":1,"lat":1,"lon":1,"tags":{"amenity":"bench"}}]}""", 1.0, 1.0).isEmpty())
        assertTrue(PlaceNames.parseOverpass("oops", 0.0, 0.0).isEmpty())
    }

    @Test fun namesYouGaveBeforeAreRememberedEvenWhenNotWatched() {
        val places = listOf(place("a", "Library", 0.0002, archived = true), place("b", "Gym", 0.01), place("c", "Home", 0.0005, radius = 30))
        val out = PlaceNames.remembered(places, lat, lng)
        assertEquals(listOf("Library", "Home"), out.map { it.name })
        assertTrue(out.all { it.source == Source.YOURS })
    }

    @Test fun yoursComeFirstThenNearbyThenTheAddress() {
        val yours = listOf(PlaceNames.Suggestion("Library", Source.YOURS, 10.0))
        val nearby = listOf(PlaceNames.Suggestion("library", Source.NEARBY, 12.0, "Library"), PlaceNames.Suggestion("Cafe Z", Source.NEARBY, 30.0, "Cafe"))
        val address = PlaceNames.Suggestion("MG Road, Civil Lines", Source.ADDRESS, 0.0)
        val out = PlaceNames.combine(yours, nearby, address)
        assertEquals(listOf("Library", "Cafe Z", "MG Road, Civil Lines"), out.map { it.name })
        assertEquals("Cafe Z (Cafe)", out[1].label())
        assertEquals("Library", out[0].label())
    }

    @Test fun suggestionsSurviveBeingSavedAsText() {
        val list = listOf(PlaceNames.Suggestion("Chai\tCorner", Source.NEARBY, 12.7, "Cafe"), PlaceNames.Suggestion("Home", Source.YOURS, 3.0))
        val back = PlaceNames.decode(PlaceNames.encode(list))
        assertEquals(listOf("Chai Corner", "Home"), back.map { it.name })
        assertEquals(Source.YOURS, back[1].source)
        assertEquals("Cafe", back[0].kind)
        assertTrue(PlaceNames.decode("").isEmpty())
    }

    @Test fun quietHoursCanCrossMidnight() {
        assertTrue(NameAskRules.inQuietHours(23 * 60 + 30, 22 * 60, 7 * 60))
        assertTrue(NameAskRules.inQuietHours(2 * 60, 22 * 60, 7 * 60))
        assertTrue(!NameAskRules.inQuietHours(12 * 60, 22 * 60, 7 * 60))
        assertTrue(!NameAskRules.inQuietHours(12 * 60, 9 * 60, 9 * 60))
        assertTrue(NameAskRules.inQuietHours(13 * 60, 12 * 60, 14 * 60))
    }

    private val cfg = NameAskRules.Config(true, 10, 22 * 60, 7 * 60)
    private val now = 1_760_000_000_000L

    private fun ask(
        cfg: NameAskRules.Config = this.cfg,
        unnamed: Boolean = true,
        ignored: Boolean = false,
        stayMin: Int = 12,
        minute: Int = 12 * 60,
        asked: Set<String> = emptySet(),
        spotAsked: Long = 0L,
        snooze: Long = 0L,
    ) = NameAskRules.shouldAsk(cfg, unnamed, ignored, "v1", stayMin * 60_000L, minute, now, asked, spotAsked, snooze)

    @Test fun asksOnlyForALongEnoughStopAtAnUnnamedSpot() {
        assertTrue(ask())
        assertTrue(!ask(stayMin = 9))
        assertTrue(!ask(unnamed = false))
        assertTrue(!ask(ignored = true))
        assertTrue(!ask(cfg = NameAskRules.Config(false, 10, 0, 0)))
    }

    @Test fun staysQuietAtNightAndWhileSnoozed() {
        assertTrue(!ask(minute = 23 * 60))
        assertTrue(!ask(snooze = now + 60_000L))
        assertTrue(ask(snooze = now - 1L))
    }

    @Test fun asksOnceForEachStopAndNotAgainForTheSameSpotSoon() {
        assertTrue(!ask(asked = setOf("v1")))
        assertTrue(!ask(spotAsked = now - 60_000L))
        assertTrue(ask(spotAsked = now - NameAskRules.SPOT_AGAIN_MS - 1L))
    }
}
