package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.ZoneId

class ImportFormatsTest {
    private val t0 = 1_760_000_000_000L
    private val utc = ZoneId.of("UTC")
    private fun fix(i: Int) = RawFixEntity(t0 + i * 30_000L, 10.0 + i * 0.001, 20.0 + i * 0.002, 8f, 1.5f)
    private val fixes = (0..5).map { fix(it) }

    private fun written(format: ExportFormat): String {
        val sb = StringBuilder()
        val w = ReadingsWriter(sb, format, "Test")
        w.begin()
        w.add(fixes)
        w.end()
        return sb.toString()
    }

    private fun read(text: String, zone: ZoneId = utc): ImportParse {
        val kind = ImportFormats.detect(text.take(4000))
        return ImportFormats.parse(kind, text.byteInputStream(Charsets.UTF_8), zone)
    }

    private fun assertSame(p: ImportParse) {
        assertEquals(fixes.size, p.fixes.size)
        fixes.forEachIndexed { i, f ->
            assertEquals(f.ms, p.fixes.ms(i))
            assertEquals(f.lat, p.fixes.lat(i), 1e-6)
            assertEquals(f.lng, p.fixes.lng(i), 1e-6)
        }
        assertEquals(0, p.skipped)
    }

    @Test
    fun theJsonReaderGoesThroughNestedValues() {
        val j = JsonPull(StringReader("""{"a":[1,2,{"b":"x\"yA"}],"c":null,"d":true,"e":{"f":[]}} """))
        val seen = ArrayList<String>()
        j.obj { k ->
            seen += k
            when (k) {
                "a" -> j.arr {
                    when (j.peek()) {
                        JsonPull.Token.NUMBER -> seen += "n" + j.str()
                        else -> j.obj { kk -> seen += kk + "=" + j.str() }
                    }
                }
                else -> j.skip()
            }
        }
        assertEquals(listOf("a", "n1", "n2", "b=x\"yA", "c", "d", "e"), seen)
    }

    @Test
    fun theJsonReaderTakesAValueAsTextOrSkipsAnObject() {
        val j = JsonPull(StringReader("[\"s\", 12.5, false, null, {\"k\":[1]}, \"end\"]"))
        val out = ArrayList<String?>()
        j.arr { out += j.str() }
        assertEquals(listOf("s", "12.5", "false", null, null, "end"), out)
    }

    @Test
    fun theTypeIsFoundFromTheContent() {
        assertEquals(ImportKind.GPX, ImportFormats.detect("﻿<?xml version=\"1.0\"?>\n<gpx version=\"1.1\">"))
        assertEquals(ImportKind.KML, ImportFormats.detect("<?xml?><kml xmlns=\"x\">"))
        assertEquals(ImportKind.GEOJSON, ImportFormats.detect("{\"type\":\"FeatureCollection\",\"features\":[]}"))
        assertEquals(ImportKind.TIMELINE_RECORDS, ImportFormats.detect("{\n \"locations\" : [ {\n \"latitudeE7\" : 123,"))
        assertEquals(ImportKind.TIMELINE_NEW, ImportFormats.detect("{\"semanticSegments\":[{\"startTime\":\"x\"}]}"))
        assertEquals(ImportKind.TIMELINE_NEW, ImportFormats.detect("[{\"startTime\":\"x\",\"endTime\":\"y\",\"timelinePath\":[]}]"))
        assertEquals(ImportKind.TIMELINE_OLD, ImportFormats.detect("{\"timelineObjects\":[{\"placeVisit\":{}}]}"))
        assertEquals(ImportKind.CSV, ImportFormats.detect("time_ms,iso_time,lat,lng,accuracy_m,speed_ms\n1,2,3,4,5,6"))
        assertEquals(ImportKind.CSV, ImportFormats.detect("Timestamp;Latitude;Longitude\n"))
        assertEquals(ImportKind.UNKNOWN, ImportFormats.detect("hello world"))
        assertEquals(ImportKind.UNKNOWN, ImportFormats.detect("<html></html>"))
    }

    @Test
    fun timesComeInAllTheirUsualForms() {
        val expect = 1_760_000_000_000L
        assertEquals(expect, ImportFormats.parseTime("2025-10-09T08:53:20Z"))
        assertEquals(expect, ImportFormats.parseTime("2025-10-09T14:23:20+05:30"))
        assertEquals(expect, ImportFormats.parseTime("1760000000000"))
        assertEquals(expect, ImportFormats.parseTime("1760000000"))
        assertEquals(expect, ImportFormats.parseTime("2025-10-09 14:23:20", ZoneId.of("Asia/Kolkata")))
        assertNull(ImportFormats.parseTime("yesterday"))
        assertNull(ImportFormats.parseTime(""))
        assertNull(ImportFormats.parseTime(null))
    }

    @Test
    fun aPositionCanBeWrittenInTheGoogleWays() {
        assertEquals(12.5 to -45.25, ImportFormats.parseLatLng("12.5°, -45.25°"))
        assertEquals(12.5 to 45.25, ImportFormats.parseLatLng("geo:12.5,45.25"))
        assertNull(ImportFormats.parseLatLng("nothing"))
    }

    @Test
    fun ourOwnGpxComesBackTheSame() = assertSame(read(written(ExportFormat.GPX)))

    @Test
    fun ourOwnKmlComesBackTheSame() = assertSame(read(written(ExportFormat.KML)))

    @Test
    fun ourOwnGeoJsonComesBackTheSame() {
        val p = read(written(ExportFormat.GEOJSON))
        assertEquals(ImportKind.GEOJSON, p.kind)
        assertSame(p)
        assertEquals(8f, p.fixes.acc(0), 0.01f)
    }

    @Test
    fun ourOwnCsvComesBackTheSame() {
        val p = read(written(ExportFormat.CSV))
        assertEquals(ImportKind.CSV, p.kind)
        assertSame(p)
        assertEquals(8f, p.fixes.acc(0), 0.01f)
    }

    @Test
    fun aCsvFromAnotherAppIsFoundByItsColumnNames() {
        val csv = "Date;Latitude;Longitude;Accuracy\n2025-10-09T08:53:20Z;28.6;77.2;12\n2025-10-09T08:54:20Z;28.601;77.201;\n;28.6;77.2;5\n"
        val p = read(csv)
        assertEquals(2, p.fixes.size)
        assertEquals(1, p.skipped)
        assertEquals(12f, p.fixes.acc(0), 0.01f)
        assertEquals(ImportFormats.DEFAULT_ACC, p.fixes.acc(1), 0.01f)
    }

    @Test
    fun aCsvWithoutTheColumnsSaysSo() {
        val p = ImportFormats.parse(ImportKind.CSV, "a,b,c\n1,2,3".byteInputStream(), utc)
        assertEquals(0, p.fixes.size)
        assertTrue(p.note.contains("latitude, longitude and time"))
    }

    @Test
    fun aGeoJsonLineStringWithTimesAndAPointWithATimeAreBothRead() {
        val text = """{"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{"coordTimes":["2025-10-09T08:53:20Z","2025-10-09T08:53:50Z"]},
             "geometry":{"type":"LineString","coordinates":[[20.0,10.0,5],[20.001,10.001,5]]}},
            {"type":"Feature","properties":{"time":"2025-10-09T09:00:00Z","accuracy":9},
             "geometry":{"type":"Point","coordinates":[21.0,11.0]}},
            {"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[[1.0,2.0],[1.1,2.1]]}}
        ]}"""
        val p = read(text)
        assertEquals(3, p.fixes.size)
        assertEquals(2, p.skipped)
        assertEquals(10.0, p.fixes.lat(0), 1e-9)
        assertEquals(20.0, p.fixes.lng(0), 1e-9)
        assertEquals(9f, p.fixes.acc(2), 0.01f)
    }

    @Test
    fun aKmlPointWithATimeStampIsReadAndALineWithoutTimesIsSkipped() {
        val text = """<?xml version="1.0"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document>
            <Placemark><TimeStamp><when>2025-10-09T08:53:20Z</when></TimeStamp><Point><coordinates>77.2,28.6,0</coordinates></Point></Placemark>
            <Placemark><LineString><coordinates>1,2,0 3,4,0 5,6,0</coordinates></LineString></Placemark>
            </Document></kml>"""
        val p = read(text)
        assertEquals(1, p.fixes.size)
        assertEquals(3, p.skipped)
        assertEquals(28.6, p.fixes.lat(0), 1e-9)
    }

    @Test
    fun googleRecordsJsonIsRead() {
        val text = """{ "locations" : [ {
            "latitudeE7" : 286000000, "longitudeE7" : 772000000, "accuracy" : 15, "timestamp" : "2025-10-09T08:53:20.123Z", "source":"WIFI"
          }, { "timestampMs" : "1760000060000", "latitudeE7" : 286010000, "longitudeE7" : 772010000, "accuracy" : 40,
               "activity" : [ { "activity" : [ { "type" : "STILL" } ] } ] },
          { "latitudeE7" : 0, "longitudeE7" : 0, "timestampMs" : "1760000090000" } ] }"""
        val p = read(text)
        assertEquals(ImportKind.TIMELINE_RECORDS, p.kind)
        assertEquals(2, p.fixes.size)
        assertEquals(1, p.skipped)
        assertEquals(28.6, p.fixes.lat(0), 1e-6)
        assertEquals(77.2, p.fixes.lng(0), 1e-6)
        assertEquals(15f, p.fixes.acc(0), 0.01f)
        assertEquals(1_760_000_060_000L, p.fixes.ms(1))
    }

    @Test
    fun theNewGoogleTimelineExportGivesItsPathPointsAndRawSignals() {
        val text = """{"semanticSegments":[
          {"startTime":"2025-10-09T08:00:00Z","endTime":"2025-10-09T09:00:00Z",
           "visit":{"topCandidate":{"placeLocation":{"latLng":"28.6°, 77.2°"}}}},
          {"startTime":"2025-10-09T09:00:00Z","endTime":"2025-10-09T09:10:00Z",
           "timelinePath":[{"point":"28.6°, 77.2°","time":"2025-10-09T09:00:00Z"},{"point":"28.61°, 77.21°","time":"2025-10-09T09:05:00Z"}]}],
         "rawSignals":[{"position":{"LatLng":"28.62°, 77.22°","accuracyMeters":25,"timestamp":"2025-10-09T09:06:00Z","speedMetersPerSecond":1.5}},
                       {"wifiScan":{"deliveryTime":"x"}}],
         "userLocationProfile":{"frequentPlaces":[]}}"""
        val p = read(text)
        assertEquals(ImportKind.TIMELINE_NEW, p.kind)
        assertEquals(3, p.fixes.size)
        assertEquals(0, p.skipped)
        assertEquals(25f, p.fixes.acc(2), 0.01f)
        assertEquals(28.62, p.fixes.lat(2), 1e-6)
    }

    @Test
    fun theOldMonthlyFilesAreNamedAsNotSupported() {
        val p = read("{\"timelineObjects\":[{\"placeVisit\":{}}]}")
        assertEquals(ImportKind.TIMELINE_OLD, p.kind)
        assertEquals(0, p.fixes.size)
        assertTrue(p.note.contains("not read yet"))
    }

    @Test
    fun thePreviewCountsNewAlreadyHereAndInaccurate() {
        val buf = FixBuffer()
        buf.add(t0, 10.0, 20.0, 10f, -1f)
        buf.add(t0 + 500, 10.0, 20.0, 10f, -1f) // same second as the first: seen twice in the file
        buf.add(t0 + 60_000, 10.0, 20.0, 10f, -1f) // already stored
        buf.add(t0 + 120_000, 10.0, 20.0, 500f, -1f) // too inaccurate
        buf.add(t0 + 3 * 86_400_000L, 10.0, 20.0, 10f, -1f) // new, three days later
        val parse = ImportParse(ImportKind.GPX, buf, 2)
        val plan = ImportPlanner.plan(parse, setOf((t0 + 60_000) / 1000), 100f, utc)
        assertEquals(5, plan.preview.found)
        assertEquals(2, plan.preview.added)
        assertEquals(2, plan.preview.alreadyHere)
        assertEquals(1, plan.preview.tooInaccurate)
        assertEquals(2, plan.preview.skipped)
        assertEquals(2, plan.preview.days)
        assertEquals(listOf(0, 4), plan.indexes.toList())
        val text = plan.preview.text(utc)
        assertTrue(text, text.contains("5 readings on 2 days"))
        assertTrue(text, text.contains("2 are new, 2 are already here"))
    }

    @Test
    fun anEmptyFileGivesItsNoteAsThePreview() {
        val p = ImportFormats.parse(ImportKind.UNKNOWN, "x".byteInputStream(), utc)
        val plan = ImportPlanner.plan(p, emptySet(), 100f, utc)
        assertEquals(0, plan.preview.found)
        assertNotNull(plan.preview.text(utc))
        assertTrue(plan.preview.text(utc).contains("could not tell"))
    }

    @Test
    fun importedReadingsAreMarkedAsImported() {
        val p = read(written(ExportFormat.CSV))
        assertEquals(FixSource.IMPORTED, p.fixes.fix(0).source)
    }
}
