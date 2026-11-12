package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AgoTextTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun recentDaysAreSaidInWords() {
        assertEquals("today", AgoText.text(today, today))
        assertEquals("yesterday", AgoText.text(today.minusDays(1), today))
        assertEquals("3 days ago", AgoText.text(today.minusDays(3), today))
        assertEquals("13 days ago", AgoText.text(today.minusDays(13), today))
    }

    @Test
    fun olderOnesAreWeeks() {
        assertEquals("2 weeks ago", AgoText.text(today.minusDays(14), today))
        assertEquals("8 weeks ago", AgoText.text(today.minusDays(59), today))
    }

    @Test
    fun aDayInTheFutureSaysSo() {
        assertEquals("in 1 day", AgoText.text(today.plusDays(1), today))
        assertEquals("in 3 days", AgoText.text(today.plusDays(3), today))
    }
}
