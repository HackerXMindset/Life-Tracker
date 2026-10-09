package com.lifetracker.app.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateSpanTest {
    private val today = LocalDate.of(2026, 10, 9)
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun singleDayCountsAsOneDay() {
        val s = DateSpan(LocalDate.of(2024, 5, 26), LocalDate.of(2024, 5, 26))
        assertTrue(s.isSingleDay)
        assertEquals(1L, s.dayCount)
        assertEquals(24L * 60 * 60 * 1000, s.endMs(zone) - s.startMs(zone))
    }

    @Test fun rangeIncludesBothEnds() {
        val s = DateSpan(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 26))
        assertEquals(43L, s.dayCount)
    }

    @Test fun ofDaysSwapsBackwardsDates() {
        val s = DateSpan.ofDays(LocalDate.of(2026, 4, 26), LocalDate.of(2026, 3, 15))
        assertEquals(LocalDate.of(2026, 3, 15), s.from)
        assertEquals(LocalDate.of(2026, 4, 26), s.to)
    }

    @Test fun movingFromPastToDragsToAlong() {
        val s = DateSpan(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5)).withFrom(LocalDate.of(2026, 3, 9))
        assertEquals(LocalDate.of(2026, 3, 9), s.from)
        assertEquals(LocalDate.of(2026, 3, 9), s.to)
    }

    @Test fun movingToBeforeFromDragsFromAlong() {
        val s = DateSpan(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 9)).withTo(LocalDate.of(2026, 3, 1))
        assertEquals(LocalDate.of(2026, 3, 1), s.from)
        assertEquals(LocalDate.of(2026, 3, 1), s.to)
    }

    @Test fun arrowsStepByTheLengthOfTheSpan() {
        val day = DateSpan(LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 26)).shifted(-1)
        assertEquals(LocalDate.of(2026, 5, 25), day.from)
        val week = DateSpan(LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 10)).shifted(1)
        assertEquals(LocalDate.of(2026, 5, 11), week.from)
        assertEquals(LocalDate.of(2026, 5, 17), week.to)
    }

    @Test fun labelsLeaveOutTheYearOnlyForThisYear() {
        assertEquals("15 Mar to 26 Apr", DateSpan(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 26)).label(today))
        assertEquals("26 May 2024", DateSpan(LocalDate.of(2024, 5, 26), LocalDate.of(2024, 5, 26)).label(today))
        assertEquals("24 Mar to 9 Oct", DateSpan(LocalDate.of(2026, 3, 24), LocalDate.of(2026, 10, 9)).label(today))
        assertEquals("1 Dec 2025 to 9 Oct 2026", DateSpan(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 10, 9)).label(today))
    }

    @Test fun presetsEndToday() {
        assertEquals(DateSpan(today, today), RangePresets.span("today", today))
        assertEquals(DateSpan(today.minusDays(1), today.minusDays(1)), RangePresets.span("yesterday", today))
        assertEquals(7L, RangePresets.span("7", today).dayCount)
        assertEquals(30L, RangePresets.span("30", today).dayCount)
        assertEquals(90L, RangePresets.span("90", today).dayCount)
        assertEquals(LocalDate.of(2026, 1, 1), RangePresets.span("year", today).from)
        assertEquals(RangePresets.EARLIEST, RangePresets.span("all", today).from)
        assertEquals(today, RangePresets.span("all", today).to)
    }
}
