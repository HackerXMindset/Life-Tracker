package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth

/** The month grid on the Food page, free of the screen so it can be tested. */
object FoodCalendar {
    /**
     * The cells of [month], Monday first: blanks (null) up to the first day, then every day of the month.
     * The list is not padded at the end.
     */
    fun cells(month: YearMonth): List<LocalDate?> {
        val lead = month.atDay(1).dayOfWeek.value - 1
        return List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    }
}
