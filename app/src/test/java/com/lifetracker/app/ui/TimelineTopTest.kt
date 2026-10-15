package com.lifetracker.app.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTopTest {
    private val today = LocalDate.of(2026, 10, 11)

    @Test
    fun titleNamesTheNearDays() {
        assertEquals("Today", TimelineTop.title(today, today))
        assertEquals("Yesterday", TimelineTop.title(today.minusDays(1), today))
        assertEquals("Tomorrow", TimelineTop.title(today.plusDays(1), today))
    }

    @Test
    fun otherDaysUseTheWeekday() {
        assertEquals("Friday", TimelineTop.title(LocalDate.of(2026, 10, 9), today))
        assertEquals("Wednesday", TimelineTop.title(LocalDate.of(2026, 10, 14), today))
    }

    @Test
    fun subtitleIsTheFullDate() {
        assertEquals("Sunday, 11 October 2026", TimelineTop.subtitle(today))
    }

    @Test
    fun sunArcFractionRunsFromMidnightToMidnight() {
        assertEquals(0f, TimelineTop.dayFraction(0), 0.0001f)
        assertEquals(0.5f, TimelineTop.dayFraction(720), 0.0001f)
        assertEquals(1f, TimelineTop.dayFraction(1440), 0.0001f)
        assertEquals(1f, TimelineTop.dayFraction(5000), 0.0001f)
        assertEquals(0f, TimelineTop.dayFraction(-5), 0.0001f)
    }

    @Test
    fun timeLeftTodayIsTheRestOfTheDay() {
        assertEquals("5h 36m left today", TimelineTop.leftText(18 * 60 + 24))
        assertEquals("24h left today", TimelineTop.leftText(0))
        assertEquals("0m left today", TimelineTop.leftText(1440))
    }
}
