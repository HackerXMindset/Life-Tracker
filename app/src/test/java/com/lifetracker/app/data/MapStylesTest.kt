package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStylesTest {
    @Test
    fun builtInStylesAreOpenFreeMapAndCarryItsLimit() {
        assertEquals("ofm-liberty", MapStyles.DEFAULT_ID)
        assertTrue(MapStyles.builtIn.all { it.url.startsWith("https://tiles.openfreemap.org/styles/") })
        val limit = MapStyles.limitFor(MapStyles.builtIn.first().url)
        assertNotNull(limit)
        assertEquals(3_000, limit!!.maxTiles)
        assertEquals(50L * 1024 * 1024, limit.maxBytes)
        assertEquals(4, limit.parallel)
    }

    @Test
    fun yourOwnStyleHasNoLimit() {
        assertNull(MapStyles.limitFor("https://maps.example.com/style.json"))
    }

    @Test
    fun anAddressIsAStyleOrATileAddressOrNothing() {
        assertEquals(MapStyles.Kind.STYLE, MapStyles.kind("https://maps.example.com/style.json?key={key}"))
        assertEquals(MapStyles.Kind.TILES, MapStyles.kind("https://t.example.com/{z}/{x}/{y}.png"))
        assertEquals(MapStyles.Kind.INVALID, MapStyles.kind("http://maps.example.com/style.json"))
        assertEquals(MapStyles.Kind.INVALID, MapStyles.kind("https://t.example.com/{z}/{x}.png"))
        assertEquals(MapStyles.Kind.INVALID, MapStyles.kind("https://exa mple.com/style.json"))
        assertEquals(MapStyles.Kind.INVALID, MapStyles.kind(""))
    }

    @Test
    fun theKeyIsPutIntoTheAddress() {
        assertEquals("https://m.example.com/s.json?key=abc", MapStyles.resolve(" https://m.example.com/s.json?key={key} ", " abc "))
        assertEquals("https://m.example.com/s.json", MapStyles.resolve("https://m.example.com/s.json", ""))
    }

    @Test
    fun problemsAreSaidInPlainWords() {
        assertEquals("Give the style a name.", MapStyles.problem(" ", "https://m.example.com/s.json", ""))
        assertNotNull(MapStyles.problem("Mine", "m.example.com", ""))
        assertEquals("This address has {key} in it, so a key is needed.", MapStyles.problem("Mine", "https://m.example.com/s.json?key={key}", ""))
        assertNull(MapStyles.problem("Mine", "https://m.example.com/s.json?key={key}", "abc"))
    }

    @Test
    fun aTileAddressBecomesAStyleFile() {
        val json = MapStyles.rasterStyleJson("My \"sat\"", "https://t.example.com/{z}/{x}/{y}.jpg", "© me")
        assertTrue(json.startsWith("{\"version\":8"))
        assertTrue(json.contains("\"name\":\"My \\\"sat\\\"\""))
        assertTrue(json.contains("\"tiles\":[\"https://t.example.com/{z}/{x}/{y}.jpg\"]"))
        assertTrue(json.contains("\"attribution\":\"© me\""))
        assertTrue(json.endsWith("\"source\":\"tiles\"}]}"))
    }

    @Test
    fun colourHexHasSixDigits() {
        assertEquals("#2E9E5B", PathSpeeds.hex(0x2E9E5B))
        assertEquals("#00000A", PathSpeeds.hex(0xA))
    }
}
