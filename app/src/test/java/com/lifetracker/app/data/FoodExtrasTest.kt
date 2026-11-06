package com.lifetracker.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodExtrasTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun aSupplementIsDueOnItsWeekdaysAndBetweenItsDates() {
        val mwf = SupplementEntity("a", "D3", weekdays = SupplementRules.bit(1) or SupplementRules.bit(3) or SupplementRules.bit(5))
        assertTrue(SupplementRules.dueOn(mwf, monday))
        assertFalse(SupplementRules.dueOn(mwf, monday.plusDays(1)))
        assertTrue(SupplementRules.dueOn(mwf, monday.plusDays(2)))
        assertEquals("Mon, Wed, Fri", SupplementRules.summary(mwf))

        val daily = SupplementEntity("b", "C")
        assertTrue(SupplementRules.dueOn(daily, monday.plusDays(3)))
        assertEquals("Every day", SupplementRules.summary(daily))

        val limited = daily.copy(fromDate = "2026-11-03", untilDate = "2026-11-05")
        assertFalse(SupplementRules.dueOn(limited, monday))
        assertTrue(SupplementRules.dueOn(limited, monday.plusDays(2)))
        assertFalse(SupplementRules.dueOn(limited, monday.plusDays(5)))
        assertFalse(SupplementRules.dueOn(daily.copy(archived = true), monday))
    }

    @Test
    fun everyFewDaysCountsFromTheStartDate() {
        val s = SupplementEntity("c", "Iron", everyDays = 3, fromDate = "2026-11-02")
        assertTrue(SupplementRules.dueOn(s, monday))
        assertFalse(SupplementRules.dueOn(s, monday.plusDays(1)))
        assertTrue(SupplementRules.dueOn(s, monday.plusDays(3)))
        assertEquals("Every 3 days", SupplementRules.summary(s))
    }

    @Test
    fun aFastIsMeasuredAgainstItsGoal() {
        assertEquals(0.5, FastMath.progress(0L + 1, 8 * 3_600_000L + 1, 16), 1e-9)
        assertEquals(0.0, FastMath.progress(0, 5, 16), 1e-9)
        val hit = FastEntity("1", 0, 17 * 3_600_000L, 16)
        val miss = FastEntity("2", 100 * 3_600_000L, 110 * 3_600_000L, 16)
        assertTrue(FastMath.reached(hit))
        assertFalse(FastMath.reached(miss))
        assertEquals(0, FastMath.streak(listOf(hit, miss)))
        assertEquals(1, FastMath.streak(listOf(miss.copy(id = "0", startMs = -50 * 3_600_000L, endMs = -49 * 3_600_000L), hit.copy(startMs = 200 * 3_600_000L, endMs = 220 * 3_600_000L))))
        assertEquals("17:30", FastMath.clock(17 * 3_600_000L + 30 * 60_000L))
        val week = FastMath.week(listOf(hit, miss), 111 * 3_600_000L)
        assertNotNull(week)
        assertEquals(2, week!!.count)
        assertEquals(1, week.reached)
        assertEquals(810L, week.avgMinutes)
        assertNull(FastMath.week(listOf(hit), 1_000 * 3_600_000L))
    }

    @Test
    fun weightPaceNeedsTwoWeighInsAFewDaysApart() {
        val today = LocalDate.of(2026, 11, 6)
        val w = listOf(WeightEntity("2026-10-23", 72.0), WeightEntity("2026-11-06", 71.0))
        assertEquals(-0.5, WeightMath.kgPerWeek(w, today)!!, 1e-9)
        assertNull(WeightMath.kgPerWeek(listOf(WeightEntity("2026-11-05", 71.0), WeightEntity("2026-11-06", 70.9)), today))
        assertNull(WeightMath.kgPerWeek(listOf(WeightEntity("2026-11-06", 71.0)), today))
        assertEquals("2.4 kg to lose", WeightMath.toGoal(72.4, 70.0))
        assertEquals("1 kg to gain", WeightMath.toGoal(69.0, 70.0))
        assertEquals("Goal reached", WeightMath.toGoal(70.02, 70.0))
        assertNull(WeightMath.toGoal(70.0, 0.0))
    }

    private val rice = SavedMealItem("a:rice", "Rice", 200.0, 360.0, 7.0, 80.0, 1.0, extra = "iron=2.7")
    private val chicken = SavedMealItem("a:chicken", "Chicken", 300.0, 165.0, 31.0, 0.0, 3.6)

    @Test
    fun aBatchIsOneFoodSpreadOverThePot() {
        val batch = BatchEntity("b", "Chicken and rice", SavedMealItems.encode(listOf(rice, chicken)), 1L, cookedGrams = 800.0, leftGrams = 800.0, portions = 4)
        // 200 g rice = 720 kcal, 300 g chicken = 495 kcal; the pot weighs 800 g.
        val food = BatchMath.asFood(batch)
        assertEquals(1215.0 / 8, food.per100.kcal, 1e-9)
        assertEquals((14.0 + 93.0) / 8, food.per100.protein, 1e-9)
        assertEquals(200.0, food.portionGrams, 1e-9)
        assertEquals(5.4 / 8, food.micro.getValue("iron"), 1e-9)
        assertEquals(1215.0, BatchMath.kcalTotal(batch), 1e-9)
        assertEquals(1.0, BatchMath.leftShare(batch), 1e-9)
        assertFalse(BatchMath.finished(batch))
        assertTrue(BatchMath.finished(batch.copy(leftGrams = 0.0)))
        // Without a cooked weight the raw total is used.
        assertEquals(500.0, BatchMath.totalGrams(batch.copy(cookedGrams = 0.0)), 1e-9)
    }

    @Test
    fun aPlanOnlyFillsTheMealsThatAreEmpty() {
        val plan = MealPlanEntity("p", "Day").withItems("breakfast", listOf(rice)).withItems("lunch", listOf(chicken, rice))
        var n = 0
        val existing = listOf(MealEntity("x", "2026-11-06", 600, "lunch", "Dal", 100.0, 100.0, 10.0, 2.0, 5.0, "app", ""))
        val added = PlanMath.mealsToAdd(plan, "2026-11-06", existing, { type -> if (type == "breakfast") 480 else 720 }) { "id${n++}" }
        assertEquals(1, added.size)
        assertEquals("breakfast", added.single().mealType)
        assertEquals(480, added.single().minuteOfDay)
        assertEquals(1935.0, PlanMath.kcal(plan), 1e-9)
    }

    @Test
    fun theWeekAveragesTheDaysThatHaveFoodAndCountsDaysOnTarget() {
        fun meal(date: String, kcal: Double, protein: Double) =
            MealEntity("m$date$kcal", date, 600, "lunch", "x", 100.0, kcal, 0.0, 0.0, protein, "app", "")
        val goal = FoodGoalEntity("2026-11-01", 2000.0, 250.0, 70.0, 140.0)
        val meals = listOf(meal("2026-11-02", 2100.0, 130.0), meal("2026-11-03", 1000.0, 50.0), meal("2026-11-04", 2000.0, 100.0))
        val s = WeekMath.summary(meals) { goal }
        assertEquals(3, s.loggedDays)
        assertEquals(1700.0, s.avgKcal, 1e-9)
        assertEquals(93.33, s.avgProtein, 0.01)
        // Day 1: kcal within 10 % and protein 130 >= 126. Day 2: far off. Day 3: protein 100 < 126.
        assertEquals(1, s.daysOnTarget)
        assertEquals(0, WeekMath.summary(emptyList()) { goal }.loggedDays)
        assertEquals(0, WeekMath.summary(meals) { null }.daysOnTarget)
    }

    @Test
    fun aminoAcidsAreComparedWithWhatAnAdultNeeds() {
        val m = MealEntity("m", "2026-11-06", 600, "lunch", "Egg", 200.0, 150.0, 1.0, 10.0, 12.0, "app", "", amino = "0.3,0.6,1.0,0.9,0.7,1.1,0.5,0.15,0.8", animal100 = 12.0, sat100 = 3.0, mono100 = 4.0, poly100 = 1.5)
        val eaten = QualityMath.aminoEaten(listOf(m))
        assertEquals(0.6, eaten[0], 1e-9)
        assertEquals(1.2, eaten[1], 1e-9)
        // 70 kg: histidine need is 10 mg/kg = 700 mg, 600 mg eaten.
        assertEquals(600.0 / 700.0, QualityMath.aminoShares(eaten, 70.0)[0], 1e-9)
        assertEquals(1.0, QualityMath.animalShare(listOf(m))!!, 1e-9)
        assertNull(QualityMath.animalShare(emptyList()))
        val fats = QualityMath.fats(listOf(m))
        assertEquals(6.0, fats.sat, 1e-9)
        assertEquals(8.0, fats.mono, 1e-9)
        assertEquals(3.0, fats.poly, 1e-9)
        assertEquals(9, QualityMath.AMINO_NAMES.size)
        assertEquals(9, QualityMath.NEED_MG_PER_KG.size)
    }

    @Test
    fun tapsAreGroupedPerTrackerInTheOrderOfTheTrackers() {
        val head = FoodTrackerEntity("a", "Headache", "", 1L)
        val tea = FoodTrackerEntity("b", "Tea", "", 2L, sortOrder = 1)
        val events = listOf(
            TrackerEventEntity("1", "b", "2026-11-06", 600), TrackerEventEntity("2", "a", "2026-11-06", 900),
            TrackerEventEntity("3", "a", "2026-11-06", 500), TrackerEventEntity("4", "gone", "2026-11-06", 100),
        )
        val chips = TrackerMath.chips(events, listOf(head, tea))
        assertEquals(listOf("Headache ×2", "Tea"), chips.map { it.text })
        assertEquals(listOf(500, 900), chips[0].minutes)
        val days = (2..8).map { LocalDate.of(2026, 11, it) }
        val counts = TrackerMath.counts(events, days, "a")
        assertEquals(2, counts[4])
        assertEquals(2, counts.sum())
    }

    @Test
    fun theDiaryListsDaysNewestFirstAndSkipsEmptyOnes() {
        fun meal(date: String, minute: Int, kcal: Double) = MealEntity("m$date$minute", date, minute, "lunch", "x", 100.0, kcal, 0.0, 0.0, 0.0, "app", "")
        val meals = listOf(meal("2026-11-04", 800, 100.0), meal("2026-11-04", 600, 200.0), meal("2026-11-02", 700, 50.0))
        val events = listOf(TrackerEventEntity("e", "a", "2026-11-05", 300), TrackerEventEntity("f", "a", "2026-11-04", 900))
        val days = DiaryMath.days(meals, events)
        assertEquals(listOf("2026-11-05", "2026-11-04", "2026-11-02"), days.map { it.date.toString() })
        assertEquals(300.0, days[1].kcal, 1e-9)
        assertEquals(listOf(600, 800), days[1].meals.map { it.minuteOfDay })
        assertTrue(days[0].hasEvents)
        assertFalse(days[2].hasEvents)
        assertTrue(DiaryMath.days(emptyList(), emptyList()).isEmpty())
    }
}
