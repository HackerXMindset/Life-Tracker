package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExportFormatsTest {
    private val t0 = 1_760_000_000_000L
    private fun fix(i: Int, gapAfter: Int = 0) = RawFixEntity(t0 + i * 30_000L + gapAfter * 3_600_000L, 10.0 + i * 0.001, 20.0 + i * 0.002, 8f, 1.5f)

    private fun write(format: ExportFormat, fixes: List<RawFixEntity>, chunks: Int = 1): String {
        val sb = StringBuilder()
        val w = ReadingsWriter(sb, format, "Test \"run\"")
        w.begin()
        fixes.chunked(maxOf(1, fixes.size / chunks)).forEach { w.add(it) }
        w.end()
        return sb.toString()
    }

    @Test
    fun aDayIsFromMidnightToMidnight() {
        val zone = ZoneId.of("Asia/Kolkata")
        val (a, b) = ExportRange.day(LocalDate.of(2026, 10, 26), zone)
        assertEquals(24 * 3_600_000L, b - a)
        assertEquals(LocalDate.of(2026, 10, 26), ExportRange.days(a, b, zone).first)
        assertEquals(LocalDate.of(2026, 10, 26), ExportRange.days(a, b, zone).second)
    }

    @Test
    fun aRangeIncludesBothDaysAndABackwardsOneIsRefused() {
        val zone = ZoneId.of("UTC")
        val r = ExportRange.range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), zone)!!
        assertEquals(3 * 24 * 3_600_000L, r.second - r.first)
        assertNull(ExportRange.range(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1), zone))
    }

    @Test
    fun fileNamesSayWhatTheyHold() {
        val today = LocalDate.of(2026, 10, 26)
        assertEquals("life-tracker-readings-all-2026-10-26.gpx", ExportRange.fileName(ExportFormat.GPX, null, null, today))
        assertEquals("life-tracker-readings-2026-10-20.kml", ExportRange.fileName(ExportFormat.KML, LocalDate.of(2026, 10, 20), null, today))
        assertEquals("life-tracker-readings-2026-10-20.csv", ExportRange.fileName(ExportFormat.CSV, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), today))
        assertEquals(
            "life-tracker-readings-2026-10-01-to-2026-10-07.geojson",
            ExportRange.fileName(ExportFormat.GEOJSON, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), today),
        )
    }

    @Test
    fun formatsAreFoundByIdAndFallBackToGpx() {
        assertEquals(ExportFormat.KML, ExportFormat.of("kml"))
        assertEquals(ExportFormat.GPX, ExportFormat.of("nonsense"))
    }

    @Test
    fun csvHasAHeaderAndOneRowPerReading() {
        val text = write(ExportFormat.CSV, (0..2).map { fix(it) })
        val lines = text.trim().lines()
        assertEquals(4, lines.size)
        assertTrue(lines[0].startsWith("time_ms,iso_time,lat,lng"))
    }

    @Test
    fun gpxKeepsOneTrackAndAGapMakesANewSegment() {
        val fixes = listOf(fix(0), fix(1), fix(2, gapAfter = 1), fix(3, gapAfter = 1))
        val text = write(ExportFormat.GPX, fixes)
        assertEquals(2, Regex("<trkseg>").findAll(text).count())
        assertEquals(4, Regex("<trkpt ").findAll(text).count())
    }

    @Test
    fun kmlHasATrackWithTimesAndPositionsPerSegment() {
        val fixes = listOf(fix(0), fix(1), fix(2, gapAfter = 1), fix(3, gapAfter = 1))
        val text = write(ExportFormat.KML, fixes)
        assertTrue(text.startsWith("<?xml"))
        assertEquals(2, Regex("<gx:Track>").findAll(text).count())
        assertEquals(4, Regex("<when>").findAll(text).count())
        assertEquals(4, Regex("<gx:coord>").findAll(text).count())
        assertTrue(text.contains("<gx:coord>20.0000000 10.0000000</gx:coord>"))
        assertTrue(text.trimEnd().endsWith("</kml>"))
    }

    @Test
    fun geoJsonHasLongitudeFirstAndTimesAndEscapesTheName() {
        val fixes = listOf(fix(0), fix(1), fix(2, gapAfter = 1))
        val text = write(ExportFormat.GEOJSON, fixes)
        assertTrue(text.startsWith("{\"type\":\"FeatureCollection\""))
        assertTrue(text.contains("\"name\":\"Test \\\"run\\\"\""))
        assertTrue(text.contains("[20.0000000,10.0000000]"))
        assertEquals(2, Regex("\"type\":\"Feature\"").findAll(text).count())
        assertTrue(text.contains("\"type\":\"Point\""))
        assertEquals(text.count { it == '{' }, text.count { it == '}' })
        assertEquals(text.count { it == '[' }, text.count { it == ']' })
    }

    @Test
    fun readingsGivenInPiecesComeOutAsIfGivenAtOnce() {
        val fixes = (0..9).map { fix(it) }
        for (f in ExportFormat.entries) {
            assertEquals(write(f, fixes, 1), write(f, fixes, 5))
        }
    }

    @Test
    fun nothingToWriteStillGivesAValidFile() {
        assertTrue(write(ExportFormat.GEOJSON, emptyList()).contains("\"features\":[]"))
        assertTrue(write(ExportFormat.KML, emptyList()).trimEnd().endsWith("</kml>"))
        assertTrue(write(ExportFormat.GPX, emptyList()).trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun theScheduleCoversFromTheLastRunToNow() {
        assertEquals(24L, ExportSchedule.hours(ExportSchedule.DAILY))
        assertEquals(168L, ExportSchedule.hours(ExportSchedule.WEEKLY))
        assertEquals(0L, ExportSchedule.hours(ExportSchedule.OFF))
        assertEquals(500L to 900L, ExportSchedule.span(500L, 100L, 900L))
        assertEquals(100L to 900L, ExportSchedule.span(0L, 100L, 900L))
        assertNull(ExportSchedule.span(0L, null, 900L))
        assertNull(ExportSchedule.span(900L, 100L, 900L))
    }

    @Test
    fun theExportCardTurnsItsChoiceIntoASpanAndAName() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 26)
        val all = ExportPlans.plan(ExportPlans.ALL, "", "", ExportFormat.GPX, zone, today, 5_000L)
        assertEquals(0L, all.fromMs)
        assertEquals(5_001L, all.toMs)
        assertEquals("life-tracker-readings-all-2026-10-26.gpx", all.fileName)
        val day = ExportPlans.plan(ExportPlans.DAY, " 2026-10-20 ", "", ExportFormat.CSV, zone, today, 0L)
        assertEquals(24 * 3_600_000L, day.toMs - day.fromMs)
        assertEquals("life-tracker-readings-2026-10-20.csv", day.fileName)
        val range = ExportPlans.plan(ExportPlans.RANGE, "2026-10-01", "2026-10-07", ExportFormat.KML, zone, today, 0L)
        assertEquals(7 * 24 * 3_600_000L, range.toMs - range.fromMs)
        assertNull(range.problem)
    }

    @Test
    fun aBadDateIsSaidInPlainWords() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 26)
        assertEquals("Write the day as 2026-10-26.", ExportPlans.plan(ExportPlans.DAY, "yesterday", "", ExportFormat.GPX, zone, today, 0L).problem)
        assertEquals("Write the last day as 2026-10-07.", ExportPlans.plan(ExportPlans.RANGE, "2026-10-01", "", ExportFormat.GPX, zone, today, 0L).problem)
        assertEquals("The last day is before the first day.", ExportPlans.plan(ExportPlans.RANGE, "2026-10-07", "2026-10-01", ExportFormat.GPX, zone, today, 0L).problem)
    }
}
