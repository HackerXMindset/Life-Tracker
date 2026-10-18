package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodCalendarTest {
    @Test
    fun aMonthStartingOnAMondayHasNoBlanks() {
        // 1 June 2026 is a Monday.
        val cells = FoodCalendar.cells(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), cells.first())
        assertEquals(30, cells.size)
    }

    @Test
    fun blanksFillTheDaysBeforeTheFirst() {
        // 1 October 2026 is a Thursday: Monday, Tuesday and Wednesday are blank.
        val cells = FoodCalendar.cells(YearMonth.of(2026, 10))
        assertNull(cells[0]); assertNull(cells[1]); assertNull(cells[2])
        assertEquals(LocalDate.of(2026, 10, 1), cells[3])
        assertEquals(3 + 31, cells.size)
        assertEquals(LocalDate.of(2026, 10, 31), cells.last())
    }

    @Test
    fun aSundayStartLeavesSixBlanks() {
        // 1 February 2026 is a Sunday.
        val cells = FoodCalendar.cells(YearMonth.of(2026, 2))
        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(6 + 28, cells.size)
    }

    @Test
    fun fuelPiecesAreSizedAgainstTheGoal() {
        val shares = FoodStats.fuelShares(listOf(500.0, 250.0), goalKcal = 2000.0)
        assertEquals(0.25, shares[0], 1e-9)
        assertEquals(0.125, shares[1], 1e-9)
    }

    @Test
    fun goingOverTheGoalMakesTheBarFullAndSharesAddToOne() {
        val shares = FoodStats.fuelShares(listOf(1500.0, 1500.0), goalKcal = 2000.0)
        assertEquals(1.0, shares.sum(), 1e-9)
        assertEquals(0.5, shares[0], 1e-9)
    }

    @Test
    fun withNoGoalThePiecesShareWhatWasEaten() {
        val shares = FoodStats.fuelShares(listOf(300.0, 100.0), goalKcal = null)
        assertEquals(0.75, shares[0], 1e-9)
        assertEquals(listOf(0.0), FoodStats.fuelShares(listOf(0.0), null))
    }
}
