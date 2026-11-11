package com.lifetracker.app.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodReminderRulesTest {
    private fun at(h: Int, m: Int = 0, day: Int = 10) = LocalDateTime.of(2026, 11, day, h, m)

    @Test
    fun aMealRingsTodayIfItsTimeIsLaterElseTomorrow() {
        assertEquals(at(13), FoodReminderRules.nextDaily(13 * 60, at(9)))
        assertEquals(at(13, day = 11), FoodReminderRules.nextDaily(13 * 60, at(13)))
        assertEquals(at(13, day = 11), FoodReminderRules.nextDaily(13 * 60, at(15)))
    }

    @Test
    fun waterSlotsRunFromStartToEnd() {
        assertEquals(listOf(480, 600, 720, 840, 960, 1080, 1200, 1320), FoodReminderRules.waterSlots(480, 1320, 120))
        // Never closer than 30 minutes, whatever is asked.
        assertEquals(listOf(480, 510, 540), FoodReminderRules.waterSlots(480, 540, 5))
    }

    @Test
    fun theNextWaterRingIsTheNextSlotOrTomorrowMorning() {
        assertEquals(at(12), FoodReminderRules.nextWater(at(11, 5), 480, 1320, 120))
        assertEquals(at(8, day = 11), FoodReminderRules.nextWater(at(22), 480, 1320, 120))
        assertEquals(at(8, day = 11), FoodReminderRules.nextWater(at(23, 30), 480, 1320, 120))
        assertEquals(at(8), FoodReminderRules.nextWater(at(6), 480, 1320, 120))
    }

    private fun meal(type: String, date: String) = MealEntity("id$type$date", date, 700, type, "Dal", 100.0, 100.0, 10.0, 5.0, 10.0, "app", "{}")

    @Test
    fun aMealReminderStaysQuietWhenThatMealIsLogged() {
        val meals = listOf(meal("lunch", "2026-11-10"), meal("dinner", "2026-11-09"))
        assertTrue(FoodReminderRules.mealLogged(meals, "2026-11-10", "lunch"))
        assertFalse(FoodReminderRules.mealLogged(meals, "2026-11-10", "dinner"))
        assertFalse(FoodReminderRules.mealLogged(emptyList(), "2026-11-10", "lunch"))
    }

    @Test
    fun waterIsOnTrackWhenYouDrankAsMuchAsTheDayHasUsedUp() {
        // Window 8:00 to 22:00 (840 minutes), goal 2000 ml: at 15:00 half the window is gone, so 1000 ml is on track.
        assertTrue(FoodReminderRules.waterOnTrack(1000, 2000, 15 * 60, 480, 1320))
        assertFalse(FoodReminderRules.waterOnTrack(900, 2000, 15 * 60, 480, 1320))
        // Before the window starts nothing is owed, and a met goal is always on track.
        assertTrue(FoodReminderRules.waterOnTrack(0, 2000, 7 * 60, 480, 1320))
        assertTrue(FoodReminderRules.waterOnTrack(2000, 2000, 21 * 60, 480, 1320))
        assertFalse(FoodReminderRules.waterOnTrack(1500, 2000, 22 * 60, 480, 1320))
    }

    @Test
    fun theWeekReportRingsOnTheNextSunday() {
        // 8 November 2026 is a Sunday, the 10th a Tuesday.
        val sunday = 19 * 60
        assertEquals(at(19, day = 8), FoodReminderRules.nextWeekly(sunday, at(10, day = 8)))
        assertEquals(at(19, day = 15), FoodReminderRules.nextWeekly(sunday, at(20, day = 8)))
        assertEquals(at(19, day = 15), FoodReminderRules.nextWeekly(sunday, at(9, day = 10)))
        assertEquals(at(19, day = 15), FoodReminderRules.nextWeekly(sunday, at(19, day = 8)))
    }
}
