package com.lifetracker.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripsRangeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val none = DateSpan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
    private val day = LocalDate.of(2026, 10, 23)

    private fun trip(id: String, startMin: Long, endMin: Long, m: Int, mode: String = "walk", top: Int = 6) =
        TripEntity(id, startMin * 60_000L, endMin * 60_000L, "", "", m, 5, top, "", mode, "auto")

    @Test fun aWeekRunsFromMondayToSunday() {
        val w = TripsRange.window(TripsRange.Span.WEEK, day, none)
        assertEquals(LocalDate.of(2026, 10, 19), w.from)
        assertEquals(LocalDate.of(2026, 10, 25), w.to)
        assertEquals(DayOfWeek.MONDAY, w.from.dayOfWeek)
    }

    @Test fun monthYearAndCustom() {
        val m = TripsRange.window(TripsRange.Span.MONTH, day, none)
        assertEquals(LocalDate.of(2026, 10, 1), m.from)
        assertEquals(LocalDate.of(2026, 10, 31), m.to)
        val y = TripsRange.window(TripsRange.Span.YEAR, day, none)
        assertEquals(LocalDate.of(2026, 12, 31), y.to)
        val c = DateSpan(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9))
        assertEquals(c, TripsRange.window(TripsRange.Span.CUSTOM, day, c))
    }

    @Test fun boundsCoverWholeDaysInTheZone() {
        val (from, to) = TripsRange.bounds(DateSpan(day, day), zone)
        assertEquals(24 * 3_600_000L, to - from)
        assertEquals(LocalDate.of(2026, 10, 23).atStartOfDay(zone).toInstant().toEpochMilli(), from)
    }

    @Test fun shiftingMovesByTheStretch() {
        assertEquals(LocalDate.of(2026, 10, 16), TripsRange.shift(TripsRange.Span.WEEK, day, -1))
        assertEquals(LocalDate.of(2026, 11, 23), TripsRange.shift(TripsRange.Span.MONTH, day, 1))
        assertEquals(LocalDate.of(2025, 10, 23), TripsRange.shift(TripsRange.Span.YEAR, day, -1))
        assertEquals(day, TripsRange.shift(TripsRange.Span.CUSTOM, day, 3))
    }

    @Test fun titlesReadPlainly() {
        val en = Locale.ENGLISH
        val today = LocalDate.of(2026, 10, 29)
        assertEquals("Fri, 23 Oct", TripsRange.title(TripsRange.Span.DAY, DateSpan(day, day), today, en))
        assertEquals("19 - 25 Oct", TripsRange.title(TripsRange.Span.WEEK, DateSpan(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)), today, en))
        assertEquals("28 Sep - 4 Oct", TripsRange.title(TripsRange.Span.WEEK, DateSpan(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)), today, en))
        assertEquals("October 2026", TripsRange.title(TripsRange.Span.MONTH, TripsRange.window(TripsRange.Span.MONTH, day, none), today, en))
        assertEquals("2026", TripsRange.title(TripsRange.Span.YEAR, TripsRange.window(TripsRange.Span.YEAR, day, none), today, en))
        assertEquals("Mon, 23 Oct 2023", TripsRange.title(TripsRange.Span.DAY, DateSpan(LocalDate.of(2023, 10, 23), LocalDate.of(2023, 10, 23)), today, en))
    }

    @Test fun summaryAddsUpAndFindsTheRecords() {
        val trips = listOf(trip("a", 0, 60, 4000, "walk", 7), trip("b", 120, 150, 30000, "drive", 80), trip("c", 200, 230, 20000, "drive", 60))
        val s = TripsRange.summary(trips)
        assertEquals(3, s.trips)
        assertEquals(54000L, s.distanceM)
        assertEquals(120 * 60_000L, s.movingMs)
        assertEquals(27, s.avgKmh)
        assertEquals("b", s.longest!!.id)
        assertEquals("b", s.fastest!!.id)
        assertEquals(listOf("drive", "walk"), s.byMode.map { it.mode })
        assertEquals(2, s.byMode[0].count)
        assertEquals(50000L, s.byMode[0].distanceM)
    }

    @Test fun anEmptyStretchHasNoRecords() {
        val s = TripsRange.summary(emptyList())
        assertEquals(0, s.trips)
        assertEquals(0, s.avgKmh)
        assertNull(s.longest)
    }

    @Test fun hiddenTripsAndOtherDaysAreLeftOut() {
        val trips = listOf(trip("a", 10, 20, 1), trip("b", 30, 40, 1), trip("c", 2000, 2010, 1))
        val shown = TripsRange.inRange(trips, 0L, 100 * 60_000L, setOf("b"))
        assertEquals(listOf("a"), shown.map { it.id })
    }

    @Test fun tripsAreGroupedByTheDayTheyBegan() {
        val oneDay = 24 * 60L
        val trips = listOf(trip("late", 3 * oneDay + 600, 3 * oneDay + 620, 1), trip("early", 600, 620, 1), trip("same", 700, 720, 1))
        val groups = TripsRange.byDay(trips, ZoneId.of("UTC"))
        assertEquals(2, groups.size)
        assertEquals(listOf("early", "same"), groups[0].second.map { it.id })
    }

    @Test fun coloursRepeatAfterTheLastOne() {
        assertEquals(TripsRange.colorOf(0), TripsRange.colorOf(TripsRange.palette.size))
        assertEquals(TripsRange.colorOf(1), TripsRange.colorOf(-TripsRange.palette.size + 1))
    }

    @Test fun thinningKeepsTheEndsAndDropsCloseReadings() {
        val path = (0..100).map { 28.0 + it * 0.00001 to 77.0 }
        val thin = TripsRange.thin(path, 20.0)
        assertTrue(thin.size < path.size / 5)
        assertEquals(path.first(), thin.first())
        assertEquals(path.last(), thin.last())
        assertEquals(path, TripsRange.thin(path, 0.0))
    }

    @Test fun readingsAreSlicedByTimeAndTakenOnce() {
        fun pt(id: String, min: Long) = TripPointEntity(id, min * 60_000L, 28.0, 77.0, 5f)
        val sorted = TripsRange.prepare(listOf(pt("b", 12), pt("a", 10), pt("a", 11), pt("b", 11), pt("a", 30)))
        assertEquals(listOf(10L, 11L, 12L, 30L).map { it * 60_000L }, sorted.map { it.ms })
        val own = TripsRange.pointsOf(trip("t", 11, 12, 1), sorted)
        assertEquals(listOf(11L, 12L).map { it * 60_000L }, own.map { it.ms })
        assertTrue(TripsRange.pointsOf(trip("t", 40, 50, 1), sorted).isEmpty())
    }
}
