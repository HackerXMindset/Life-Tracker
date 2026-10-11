package com.lifetracker.app.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GpxTest {
    private val t0 = 1_760_000_000_000L
    private val min = 60_000L

    private fun write(fixes: List<RawFixEntity>): String {
        val sb = StringBuilder()
        val w = Gpx.Writer(sb, "Readings & more")
        w.begin()
        w.add(fixes)
        w.end()
        return sb.toString()
    }

    @Test
    fun whatIsWrittenComesBackExactlyAndASilenceStartsANewSegment() {
        val fixes = listOf(
            RawFixEntity(t0, 28.6139123, 77.2090456, 12f, 1.25f),
            RawFixEntity(t0 + min, 28.6140123, 77.2091456, 9f),
            // 40 minutes of silence.
            RawFixEntity(t0 + 41 * min, -33.8688, 151.2093, 5f, 0f),
        )
        val text = write(fixes)
        assertEquals(2, Regex("<trkseg>").findAll(text).count())
        assertTrue(text.contains("Readings &amp; more"))
        val back = Gpx.parse(text)
        assertEquals(0, back.skipped)
        assertEquals(3, back.fixes.size)
        for (i in fixes.indices) {
            assertEquals(fixes[i].ms, back.fixes[i].ms)
            assertEquals(fixes[i].lat, back.fixes[i].lat, 1e-7)
            assertEquals(fixes[i].lng, back.fixes[i].lng, 1e-7)
            assertEquals(fixes[i].acc, back.fixes[i].acc, 0f)
            assertEquals(fixes[i].speed, back.fixes[i].speed, 0.001f)
        }
    }

    @Test
    fun aGpsLoggerFileIsRead() {
        val file = """<?xml version="1.0" encoding="UTF-8" ?><gpx version="1.0" creator="GPSLogger 137 - http://gpslogger.mendhak.com/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.topografix.com/GPX/1/0"><time>2026-10-11T10:03:54.856Z</time><trk><name>20261011</name><trkseg><trkpt lat="28.5614644" lon="77.2121964"><ele>179.70001220703125</ele><time>2026-10-11T10:03:54.856Z</time><src>network</src></trkpt>
<trkpt lat="28.5614837" lon="77.2122096"><ele>179.70001220703125</ele><time>2026-10-11T10:04:56.590Z</time><src>network</src></trkpt>
</trkseg></trk></gpx>"""
        val parsed = Gpx.parse(file)
        assertEquals(2, parsed.fixes.size)
        assertEquals(Instant.parse("2026-10-11T10:03:54.856Z").toEpochMilli(), parsed.fixes[0].ms)
        assertEquals(28.5614644, parsed.fixes[0].lat, 1e-9)
        assertEquals(77.2121964, parsed.fixes[0].lng, 1e-9)
        // No accuracy in the file: a network position is assumed to be good to about 50 m, and there is no speed.
        assertEquals(50f, parsed.fixes[0].acc, 0f)
        assertEquals(-1f, parsed.fixes[0].speed, 0f)
    }

    @Test
    fun accuracyIsGuessedFromHdopOrTheSource() {
        val file = """<gpx xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>
<trkpt lat="1" lon="2"><time>2026-01-01T00:00:00Z</time><hdop>3</hdop></trkpt>
<trkpt lat="1" lon="2"><time>2026-01-01T00:01:00Z</time><src>gps</src></trkpt>
<trkpt lat="1" lon="2"><time>2026-01-01T00:02:00Z</time><src>cell</src></trkpt>
<trkpt lat="1" lon="2"><time>2026-01-01T00:03:00+05:30</time><extensions><acc>7</acc><speed>3.5</speed></extensions></trkpt>
</trkseg></trk></gpx>"""
        val fixes = Gpx.parse(file).fixes
        // +05:30 is earlier in UTC than the same clock time at Z, so that point comes first (readings are oldest first).
        assertEquals(Instant.parse("2025-12-31T18:33:00Z").toEpochMilli(), fixes[0].ms)
        assertEquals(listOf(7f, 15f, 20f, 150f), fixes.map { it.acc })
        assertEquals(3.5f, fixes[0].speed, 0f)
    }

    @Test
    fun pointsWithoutAPositionOrATimeAreSkippedAndCounted() {
        val file = """<gpx><trk><trkseg>
<trkpt lat="1" lon="2"><time>2026-01-01T00:00:00Z</time></trkpt>
<trkpt lat="1" lon="2"></trkpt>
<trkpt lon="2"><time>2026-01-01T00:01:00Z</time></trkpt>
<trkpt lat="95" lon="2"><time>2026-01-01T00:02:00Z</time></trkpt>
<trkpt lat="1" lon="2"><time>not a time</time></trkpt>
</trkseg></trk></gpx>"""
        val parsed = Gpx.parse(file)
        assertEquals(1, parsed.fixes.size)
        assertEquals(4, parsed.skipped)
    }

    @Test
    fun aPrefixedFileAndRepeatedTimesAreHandled() {
        val file = """<g:gpx xmlns:g="http://www.topografix.com/GPX/1/1"><g:trk><g:trkseg>
<g:trkpt lat="1" lon="2"><g:time>2026-01-01T00:01:00Z</g:time></g:trkpt>
<g:trkpt lat="1.5" lon="2"><g:time>2026-01-01T00:00:00Z</g:time></g:trkpt>
<g:trkpt lat="1" lon="2"><g:time>2026-01-01T00:01:00Z</g:time></g:trkpt>
</g:trkseg></g:trk></g:gpx>"""
        val fixes = Gpx.parse(file).fixes
        // Oldest first, and a second reading at the same moment is dropped.
        assertEquals(2, fixes.size)
        assertEquals(1.5, fixes[0].lat, 0.0)
    }

    @Test
    fun aFileWithOutsideEntitiesIsRefused() {
        val evil = """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]><gpx><trk>&x;</trk></gpx>"""
        try {
            Gpx.parse(evil)
            fail("should have been refused")
        } catch (e: Exception) {
            // expected
        }
        try {
            Gpx.parse("this is not xml")
            fail("should have been refused")
        } catch (e: Exception) {
            // expected
        }
    }

    @Test
    fun csvHasOneLinePerReading() {
        val sb = StringBuilder(Gpx.csvHeader())
        Gpx.csvRows(sb, listOf(RawFixEntity(t0, 28.61, 77.21, 10f), RawFixEntity(t0 + 1000, 28.62, 77.22, 8f, 2.5f)))
        val lines = sb.toString().trim().split("\n")
        assertEquals("time_ms,iso_time,lat,lng,accuracy_m,speed_ms", lines[0])
        assertEquals("$t0,${Instant.ofEpochMilli(t0)},28.6100000,77.2100000,10,", lines[1])
        assertEquals("${t0 + 1000},${Instant.ofEpochMilli(t0 + 1000)},28.6200000,77.2200000,8,2.50", lines[2])
    }

    @Test
    fun tripsAreWrittenOneTrackEach() {
        val a = listOf(TripPointEntity("t1", t0, 28.6, 77.2, 10f), TripPointEntity("t1", t0 + 30_000L, 28.601, 77.2, 10f))
        val b = listOf(TripPointEntity("t2", t0 + 3_600_000L, 28.7, 77.3, 12f))
        val sb = StringBuilder()
        Gpx.writeTrips(sb, listOf("2026-10-11 Walk 100 m" to a, "2026-10-11 Car 4 km" to b))
        val text = sb.toString()
        assertEquals(2, Regex("<trk>").findAll(text).count())
        assertTrue(text.contains("<name>2026-10-11 Car 4 km</name>"))
        assertEquals(3, Gpx.parse(text).fixes.size)
    }
}
