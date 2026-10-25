package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class LocationDaysTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val min = 60_000L
    private val day = LocalDate.of(2026, 10, 24)
    private val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun at(hour: Int, minute: Int = 0) = from + hour * 60 * min + minute * min

    private fun trip(id: String, start: Long, minutes: Int, metres: Int) =
        TripEntity(id, start, start + minutes * min, "", "", metres, 5, 7, "", "walk", "auto")

    private fun visit(id: String, place: String, start: Long, end: Long, ongoing: Boolean = false) =
        PlaceVisitEntity(id, place, start, end, 28.6, 77.2, "gps", ongoing, false)

    @Test
    fun aDayAddsUpItsTripsAndPlaces() {
        val trips = listOf(trip("a", at(8), 20, 1500), trip("b", at(18), 40, 5200))
        val visits = listOf(visit("v1", "home", from, at(8)), visit("v2", "work", at(9), at(17)), visit("v3", "home", at(19), to))
        val s = LocationDays.summary(trips, visits, from, to, to)
        assertEquals(6700, s.distanceM)
        assertEquals(60 * min, s.movingMs)
        assertEquals(2, s.trips)
        assertEquals(2, s.places)
        assertEquals(8 * 60 * min + 8 * 60 * min + 5 * 60 * min, s.atPlacesMs)
    }

    @Test
    fun aTripBelongsToTheDayItBegan() {
        val late = trip("late", at(23, 50), 30, 800)
        val early = trip("early", from - 10 * min, 30, 900)
        val s = LocationDays.summary(listOf(late, early), emptyList(), from, to, to)
        assertEquals(1, s.trips)
        assertEquals(800, s.distanceM)
    }

    @Test
    fun anUnnamedStopIsNotAPlace() {
        val s = LocationDays.summary(emptyList(), listOf(visit("u", "", at(9), at(10))), from, to, to)
        assertEquals(0, s.places)
        assertEquals(0L, s.atPlacesMs)
    }

    @Test
    fun theSummaryReadsInPlainWords() {
        val s = LocationDays.Summary(12_400, 48 * min, 3, 4, 0L)
        assertEquals("12.4 km · 48 min moving · 3 trips · 4 places", LocationDays.summaryText(s))
        assertEquals("850 m · 1 min moving · 1 trip · 1 place", LocationDays.summaryText(LocationDays.Summary(850, min, 1, 1, 0L)))
    }

    @Test
    fun aDayWithNothingSaysSo() {
        assertEquals("No trips or places on this day.", LocationDays.summaryText(LocationDays.Summary(0, 0L, 0, 0, 0L)))
    }

    @Test
    fun aDayWithOnlyAStayLeavesOutTheTravelNumbers() {
        assertEquals("0 trips · 1 place", LocationDays.summaryText(LocationDays.Summary(0, 0L, 0, 1, 3 * 60 * min)))
    }

    @Test
    fun movementDotsFollowTheDayEachTripBegan() {
        val trips = listOf(trip("a", at(8), 20, 1500), trip("b", at(9), 20, 500), trip("c", at(8) + 24 * 60 * min, 10, 300))
        assertEquals(setOf(day, day.plusDays(1)), LocationDays.movementDays(trips, zone))
    }

    @Test
    fun theMonthStartsOnTheRightWeekdayAndFillsEveryRow() {
        // 1 October 2026 is a Thursday: three empty cells first with Monday as the first day.
        val grid = LocationDays.monthGrid(YearMonth.of(2026, 10))
        assertEquals(0, grid.size % 7)
        assertEquals(3, grid.indexOfFirst { it != null })
        assertEquals(LocalDate.of(2026, 10, 1), grid[3])
        assertEquals(LocalDate.of(2026, 10, 31), grid.last { it != null })
        assertEquals(31, grid.count { it != null })
        assertNull(grid.last())
    }

    @Test
    fun aSundayWeekStartMovesTheFirstCell() {
        val grid = LocationDays.monthGrid(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(4, grid.indexOfFirst { it != null })
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), LocationDays.weekdayLetters(DayOfWeek.SUNDAY))
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), LocationDays.weekdayLetters())
    }

    @Test
    fun aMonthThatStartsOnTheFirstColumnHasNoEmptyCellsBefore() {
        // 1 June 2026 is a Monday.
        val grid = LocationDays.monthGrid(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), grid.first())
    }

    @Test
    fun monthBoundsRunFromTheFirstMidnightToTheNext() {
        val (a, b) = LocationDays.monthBounds(YearMonth.of(2026, 10), zone)
        assertEquals(31L * 24 * 60 * min, b - a)
        assertTrue(a <= from && from < b)
    }
}
