package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPatternsTest {
    private val start = LocalDate.of(2026, 10, 1)
    private fun day(n: Int) = start.plusDays(n - 1L).toString()

    private fun meal(d: Int, minute: Int, name: String) =
        MealEntity("$d$minute$name", day(d), minute, "lunch", name, 100.0, 100.0, 0.0, 0.0, 0.0, "app", "")

    private fun check(d: Int, minute: Int) = TrackerEventEntity("h|${day(d)}|$minute|0", "h", day(d), minute)

    private fun build(meals: List<MealEntity>, checks: List<TrackerEventEntity>, hours: Int = 6, days: Int = 10) =
        FoodPatterns.build(meals, checks, hours, start, start.plusDays(days - 1L))

    @Test
    fun closeChecksAreOneEpisode() {
        val e = FoodPatterns.episodes(listOf(check(1, 600), check(1, 660), check(1, 700), check(1, 900), check(2, 600)))
        assertEquals(3, e.size) // 10:00-11:40 is one, 15:00 another, then the next day
    }

    @Test
    fun checksWithoutATimeAreLeftOut() {
        assertTrue(FoodPatterns.episodes(listOf(check(1, HabitMarks.NO_TIME))).isEmpty())
    }

    @Test
    fun aFoodBeforeEveryHeadacheThatIsRareOtherwiseStandsOut() {
        val meals = ArrayList<MealEntity>()
        for (d in 1..10) { meals += meal(d, 780, "Rice"); meals += meal(d, 1260, "Rice") }
        for (d in listOf(3, 6, 9)) meals += meal(d, 1200, "Curd")
        val checks = listOf(3, 6, 9).map { check(it, 1380) }
        val r = build(meals, checks)
        assertTrue(r.enough)
        assertEquals(3, r.withFood)
        // rice is eaten before everything, so it is no signal; curd only before the headaches
        assertEquals(listOf("Curd"), r.rows.map { it.name })
        assertEquals(3, r.rows[0].before)
        assertEquals(1.0, r.rows[0].share, 1e-9)
        assertTrue(r.rows[0].baseline < 0.3)
        assertEquals(3.0, r.rows[0].medianGapHours, 1e-9)
    }

    @Test
    fun aFoodSeenOnlyOnceIsNotReported() {
        val meals = listOf(meal(3, 1200, "Curd"), meal(3, 1260, "Rice"), meal(6, 1260, "Rice"), meal(9, 1260, "Rice"))
        val r = build(meals, listOf(3, 6, 9).map { check(it, 1380) })
        assertFalse(r.rows.any { it.name == "Curd" })
    }

    @Test
    fun episodesWithNoFoodLoggedBeforeThemAreNotCounted() {
        val meals = listOf(meal(3, 1200, "Curd"), meal(6, 1200, "Curd"))
        val r = build(meals, listOf(3, 6, 9, 10).map { check(it, 1380) })
        assertEquals(4, r.episodes)
        assertEquals(2, r.withFood)
        assertFalse(r.enough)
        assertTrue(r.rows.isEmpty())
    }

    @Test
    fun theWindowDecidesWhatCounts() {
        // curd 5 hours before each check
        val meals = listOf(3, 6, 9).map { meal(it, 1080, "Curd") } + listOf(3, 6, 9).map { meal(it, 1370, "Tea") }
        val checks = listOf(3, 6, 9).map { check(it, 1380) }
        val short = build(meals, checks, hours = 3)
        assertFalse(short.rows.any { it.name == "Curd" })
        val long = build(meals, checks, hours = 6)
        assertTrue(long.rows.any { it.name == "Curd" })
    }

    @Test
    fun theFoodsNameIsMatchedIgnoringCase() {
        val meals = listOf(meal(3, 1200, "Curd"), meal(6, 1200, "curd "), meal(9, 1200, "CURD")) + (1..10).map { meal(it, 780, "Rice") }
        val r = build(meals, listOf(3, 6, 9).map { check(it, 1380) })
        assertEquals(1, r.rows.size)
        assertEquals(3, r.rows[0].before)
    }

    @Test
    fun mealsLoggedJustBeforeTheSpanStillCountForTheFirstCheck() {
        val s = LocalDate.of(2026, 10, 1)
        val meals = listOf(
            MealEntity("a", "2026-09-30", 1380, "dinner", "Curd", 100.0, 100.0, 0.0, 0.0, 0.0, "app", ""),
            MealEntity("b", "2026-10-02", 1380, "dinner", "Curd", 100.0, 100.0, 0.0, 0.0, 0.0, "app", ""),
            MealEntity("c", "2026-10-03", 1380, "dinner", "Curd", 100.0, 100.0, 0.0, 0.0, 0.0, "app", ""),
        ) + (1..10).map { meal(it, 780, "Rice") }
        val checks = listOf(
            TrackerEventEntity("h|2026-10-01|120|0", "h", "2026-10-01", 120),
            TrackerEventEntity("h|2026-10-03|120|0", "h", "2026-10-03", 120),
            TrackerEventEntity("h|2026-10-04|120|0", "h", "2026-10-04", 120),
        )
        val r = FoodPatterns.build(meals, checks, 6, s, s.plusDays(9))
        assertEquals(3, r.withFood)
        assertEquals(3, r.rows[0].before)
    }
}
