package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodViewsTest {
    private fun tick(habit: String, date: String, minute: Int = 600) = TrackerEventEntity("$habit|$date|$minute|0", habit, date, minute)

    private fun meal(id: String, minute: Int) =
        MealEntity(id, "2026-11-01", minute, "lunch", id, 100.0, 100.0, 0.0, 0.0, 0.0, "app", "")

    @Test
    fun theViewIsRememberedByItsKeyAndTheWeekIsTheDefault() {
        assertEquals(FoodView.MONTH, FoodView.fromKey("month"))
        assertEquals(FoodView.TIMELINE, FoodView.fromKey(FoodView.TIMELINE.key))
        assertEquals(FoodView.WEEK, FoodView.fromKey(null))
        assertEquals(FoodView.WEEK, FoodView.fromKey("nonsense"))
    }

    @Test
    fun aHabitIsOneTickADayHoweverOftenItWasChecked() {
        val ticks = MonthMath.ticks(listOf(tick("a", "2026-11-01", 100), tick("a", "2026-11-01", 200)), listOf("a"), setOf("a"))
        assertEquals(mapOf("2026-11-01" to listOf("a")), ticks)
    }

    @Test
    fun onlyTheShownHabitsTickAndTheyKeepThePickedOrder() {
        val events = listOf(tick("c", "2026-11-02"), tick("a", "2026-11-02"), tick("b", "2026-11-03"))
        val ticks = MonthMath.ticks(events, listOf("a", "b", "c"), setOf("a", "c"))
        assertEquals(listOf("a", "c"), ticks["2026-11-02"])
        assertTrue("2026-11-03" !in ticks)
    }

    @Test
    fun noShownHabitsMeansNoTicks() {
        assertTrue(MonthMath.ticks(listOf(tick("a", "2026-11-02")), listOf("a"), emptySet()).isEmpty())
    }

    @Test
    fun theMonthSpanAndShiftWork() {
        assertEquals("2026-02-01" to "2026-02-28", MonthMath.span(YearMonth.of(2026, 2)))
        assertEquals(YearMonth.of(2027, 1), MonthMath.shift(YearMonth.of(2026, 12), 1))
        assertEquals(YearMonth.of(2025, 12), MonthMath.shift(YearMonth.of(2026, 1), -1))
        assertEquals(YearMonth.of(2026, 11), MonthMath.of(LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun theTimelineMixesMealsAndChecksInTimeOrder() {
        val day = DiaryDay(
            LocalDate.of(2026, 11, 1),
            listOf(meal("lunch", 780), meal("breakfast", 480)),
            listOf(tick("a", "2026-11-01", 600), tick("a", "2026-11-01", 900)),
        )
        val order = TimelineMath.entries(day).map { e ->
            when (e) {
                is TimelineEntry.Meal -> e.meal.name
                is TimelineEntry.Check -> "check@" + e.event.minuteOfDay
            }
        }
        assertEquals(listOf("breakfast", "check@600", "lunch", "check@900"), order)
    }

    @Test
    fun aCheckWithoutATimeComesFirstAndAMealBeatsACheckAtTheSameMinute() {
        val day = DiaryDay(
            LocalDate.of(2026, 11, 1),
            listOf(meal("tea", 600)),
            listOf(tick("a", "2026-11-01", 600), tick("a", "2026-11-01", HabitMarks.NO_TIME)),
        )
        val entries = TimelineMath.entries(day)
        assertTrue(entries[0] is TimelineEntry.Check && entries[0].minute == HabitMarks.NO_TIME)
        assertTrue(entries[1] is TimelineEntry.Meal)
        assertTrue(entries[2] is TimelineEntry.Check)
    }
}
