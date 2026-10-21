package com.lifetracker.app.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogFormatTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun lineSurvivesTheFile() {
        val line = LogLine(1_760_000_000_000L, LogLevel.Warn, "Places", "fix ignored\taccuracy 85 m\nnext")
        val back = LogFormat.decode(LogFormat.encode(line))!!
        assertEquals(1_760_000_000_000L, back.ms)
        assertEquals(LogLevel.Warn, back.level)
        assertEquals("Places", back.tag)
        assertEquals("fix ignored accuracy 85 m ⏎ next", back.text)
    }

    @Test fun halfWrittenLinesAreSkipped() {
        assertNull(LogFormat.decode("1760000000000\tI"))
        assertNull(LogFormat.decode("abc\tI\tPlaces\ttext"))
        assertNull(LogFormat.decode(""))
    }

    @Test fun unknownLevelLetterIsInfo() {
        assertEquals(LogLevel.Info, LogFormat.decode("5\tX\tApp\tx")!!.level)
    }

    @Test fun fileNamesRoundTrip() {
        val day = LocalDate.of(2026, 10, 21)
        assertEquals("2026-10-21.log", LogFormat.fileName(day))
        assertEquals(day, LogFormat.dayOfFile("2026-10-21.log"))
        assertNull(LogFormat.dayOfFile("notes.txt"))
        assertNull(LogFormat.dayOfFile("hello.log"))
    }

    @Test fun onlyDaysOlderThanThirtyAreStale() {
        val today = LocalDate.of(2026, 10, 21)
        val days = listOf(today, today.minusDays(29), today.minusDays(30), today.minusDays(90))
        assertEquals(listOf(today.minusDays(30), today.minusDays(90)), LogFormat.staleDays(days, today))
    }

    @Test fun filtersByLevelTagAndText() {
        val lines = listOf(
            LogLine(1, LogLevel.Info, "Places", "geofence EXIT home"),
            LogLine(2, LogLevel.Warn, "Service", "Android refused to start the readings"),
            LogLine(3, LogLevel.Error, "Service", "crash"),
            LogLine(4, LogLevel.Info, "Trips", "Trip dropped: 38 m"),
        )
        assertEquals(4, LogFormat.filter(lines, LogLevel.Info, null, "").size)
        assertEquals(listOf(2L, 3L), LogFormat.filter(lines, LogLevel.Warn, null, "").map { it.ms })
        assertEquals(listOf(3L), LogFormat.filter(lines, LogLevel.Error, "Service", "").map { it.ms })
        assertEquals(listOf(4L), LogFormat.filter(lines, LogLevel.Info, null, "DROPPED").map { it.ms })
        assertEquals(listOf(2L, 3L), LogFormat.filter(lines, LogLevel.Info, null, "service").map { it.ms })
    }

    @Test fun tagsMostUsedFirst() {
        val lines = listOf("B", "A", "B", "C", "A", "B").map { LogLine(0, LogLevel.Info, it, "") }
        assertEquals(listOf("B", "A", "C"), LogFormat.tags(lines))
    }

    @Test fun clockUsesThePhoneZone() {
        // 2026-10-21 04:46:04 UTC is 10:16:04 in India.
        val ms = java.time.ZonedDateTime.of(2026, 10, 21, 4, 46, 4, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("10:16:04", LogFormat.clock(ms, zone))
        assertEquals(LocalDate.of(2026, 10, 21), LogFormat.dayOf(ms, zone))
        assertEquals("10:16:04 W Places  hi", LogFormat.readable(LogLine(ms, LogLevel.Warn, "Places", "hi"), zone))
    }
}
