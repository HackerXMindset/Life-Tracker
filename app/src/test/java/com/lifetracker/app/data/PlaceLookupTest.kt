package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceLookupTest {
    @Test
    fun overpassNamesComeNearestFirst() {
        val json = """{"elements":[
            {"type":"way","id":1,"center":{"lat":28.6105,"lon":77.2100},"tags":{"name":"City Library","amenity":"library"}},
            {"type":"node","id":2,"lat":28.61002,"lon":77.2100,"tags":{"name":"Chai Corner","amenity":"cafe"}},
            {"type":"node","id":3,"lat":28.6100,"lon":77.2100,"tags":{"amenity":"bench"}}
        ]}"""
        val out = PlaceLookup.parseOverpass(json, 28.6100, 77.2100)
        assertEquals(listOf("Chai Corner", "City Library"), out.map { it.name })
        assertTrue(out[0].distanceM < out[1].distanceM)
    }

    @Test
    fun overpassGarbageGivesNothing() {
        assertTrue(PlaceLookup.parseOverpass("oops", 0.0, 0.0).isEmpty())
        assertTrue(PlaceLookup.parseOverpass("""{"elements":[]}""", 0.0, 0.0).isEmpty())
    }

    @Test
    fun nominatimPrefersTheNameThenTheRoad() {
        assertEquals("Sunrise Hostel", PlaceLookup.parseNominatim("""{"name":"Sunrise Hostel","address":{"road":"MG Road"}}"""))
        assertEquals("MG Road, Indiranagar", PlaceLookup.parseNominatim("""{"name":"","address":{"road":"MG Road","suburb":"Indiranagar"}}"""))
        assertEquals("MG Road", PlaceLookup.parseNominatim("""{"address":{"road":"MG Road"}}"""))
        assertEquals("12, Some Street", PlaceLookup.parseNominatim("""{"display_name":"12, Some Street, City, India","address":{}}"""))
        assertNull(PlaceLookup.parseNominatim("not json"))
    }

    @Test
    fun keysGroupSpotsAboutElevenMetresWide() {
        assertEquals("28.6100_77.2100", PlaceLookup.key(28.61001, 77.21002))
        assertEquals(PlaceLookup.key(28.61001, 77.21002), PlaceLookup.key(28.61004, 77.20996))
    }
}
