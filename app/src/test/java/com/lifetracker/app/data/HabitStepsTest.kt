package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitStepsTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val now = LocalDateTime.of(2026, 10, 14, 9, 0)
    private val a = HabitStep("a", "Wake up")
    private val b = HabitStep("b", "Stretch")
    private val c = HabitStep("c", "Shower")
    private val steps = listOf(a, b, c)

    private fun habit(any: Boolean = false, needed: Int = 1, form: HabitForm = HabitForm(name = "Morning", steps = steps, anySteps = any, stepsNeeded = needed)) =
        HabitEditor.build(form, null, "h", 0, today, now)

    private fun day(ids: String, marks: String = "", count: Double = ids.split(',').filter { it.isNotEmpty() }.size.toDouble()) =
        CompletionEntity("h", "2026-10-14", count, marks.split(',').firstOrNull { it.isNotEmpty() }?.toInt(), marks, ids)

    @Test
    fun stepsAreWrittenIntoTheHabitRecordAndReadBack() {
        val h = habit()
        assertEquals(steps, HabitSteps.read(h.raw))
        assertTrue(HabitSteps.isChecklist(h))
        assertEquals(steps, HabitEditor.formFrom(h).steps)
        // A habit with no steps is not a checklist, and neither is an Avoid habit.
        assertFalse(HabitSteps.isChecklist(HabitEditor.build(HabitForm(name = "x"), null, "x", 0, today, now)))
        assertEquals(emptyList<HabitStep>(), HabitSteps.read("not json"))
    }

    @Test
    fun blankStepsAreDropped() {
        val h = habit(form = HabitForm(name = "x", steps = listOf(a, HabitStep("z", "  "), b)))
        assertEquals(listOf(a, b), HabitSteps.read(h.raw))
    }

    @Test
    fun howManyStepsMakeTheDayDone() {
        assertEquals(3, HabitSteps.needed(habit().raw))
        assertEquals(2, HabitSteps.needed(habit(any = true, needed = 2).raw))
        assertEquals(0, HabitSteps.needed(HabitEditor.build(HabitForm(name = "x"), null, "x", 0, today, now).raw))
        // Streak can hold a number bigger than the steps: it is capped.
        assertEquals(3, HabitSteps.needed("""{"substeps":[{"id":"a","title":"A"},{"id":"b","title":"B"},{"id":"c","title":"C"}],"anySteps":true,"stepsNeeded":9}"""))
    }

    @Test
    fun validationChecksTheNumberNeededAndTracking() {
        assertNull(HabitEditor.validate(HabitForm(name = "x", steps = steps, anySteps = true, stepsNeeded = 3)))
        assertNotNull(HabitEditor.validate(HabitForm(name = "x", steps = steps, anySteps = true, stepsNeeded = 4)))
        assertNotNull(HabitEditor.validate(HabitForm(name = "x", steps = steps, anySteps = true, stepsNeeded = 0)))
        assertNotNull(HabitEditor.validate(HabitForm(name = "x", steps = steps, tracking = true)))
        // The number needed does not matter when every step is needed.
        assertNull(HabitEditor.validate(HabitForm(name = "x", steps = steps, anySteps = false, stepsNeeded = 7)))
    }

    @Test
    fun tickingOneStepAtATimeKeepsTheTimesAndUntickingTheLastClearsTheDay() {
        val one = HabitSteps.toggle(null, "h", "2026-10-14", "b", steps, 480)!!
        assertEquals(1.0, one.count, 0.0)
        assertEquals("b", one.steps)
        assertEquals("480", one.marks)
        val two = HabitSteps.toggle(one, "h", "2026-10-14", "a", steps, 500)!!
        assertEquals("a,b", two.steps) // kept in the order of the steps
        assertEquals(2.0, two.count, 0.0)
        assertEquals("480,500", two.marks)
        val back = HabitSteps.toggle(two, "h", "2026-10-14", "a", steps, 520)!!
        assertEquals("b", back.steps)
        assertNull(HabitSteps.toggle(back, "h", "2026-10-14", "b", steps, 530))
    }

    @Test
    fun aPastDayTicksWithNoTime() {
        val one = HabitSteps.toggle(null, "h", "2026-10-10", "a", steps, null)!!
        assertEquals("", one.marks)
        assertNull(one.minuteOfDay)
    }

    @Test
    fun theCardTapTicksEverythingOrClearsADoneDay() {
        val h = habit()
        val all = HabitSteps.toggleAll(null, "h", "2026-10-14", h, steps, 700)!!
        assertEquals(3.0, all.count, 0.0)
        assertEquals("a,b,c", all.steps)
        assertNull(HabitSteps.toggleAll(all, "h", "2026-10-14", h, steps, 710))
        // Part way: the tap finishes the day instead of clearing it.
        val partial = day("a")
        assertEquals("a,b,c", HabitSteps.toggleAll(partial, "h", "2026-10-14", h, steps, null)!!.steps)
    }

    @Test
    fun aRemovedStepNoLongerCountsButStaysInTheDay() {
        val d = day("a,b,gone")
        assertEquals(setOf("a", "b"), HabitSteps.ticked(d, steps))
        assertEquals(2.0, HabitSteps.dayCount(d, steps), 0.0)
        // A day from before steps were kept keeps its stored amount.
        assertEquals(3.0, HabitSteps.dayCount(CompletionEntity("h", "2026-10-13", 3.0, null), steps), 0.0)
    }

    @Test
    fun theRulesUseTheStepsNeededAsTheGoal() {
        val all = habit()
        val some = habit(any = true, needed = 2)
        val counts = mapOf(today to 2.0, today.minusDays(1) to 3.0)
        val rAll = HabitRules.of(all, counts, today)
        assertFalse(rAll.isCompleted(today))
        assertTrue(rAll.isCompleted(today.minusDays(1)))
        assertEquals(DayKind.PARTIAL, rAll.cell(today).kind)
        val rSome = HabitRules.of(some, counts, today)
        assertTrue(rSome.isCompleted(today))
        assertEquals(DayKind.DONE, rSome.cell(today).kind)
        assertEquals(2, rSome.currentStreak())
    }

    @Test
    fun otherChangesToADayKeepItsSteps() {
        val d = day("a,b", "480,500")
        assertEquals("a,b", HabitTicks.retime(d, 480, 600).steps)
        assertEquals("a,b", HabitTicks.add(d, "h", "2026-10-14", 1.0, 700).steps)
    }

    @Test
    fun anotherKindOfHabitKeepsItsStepsWhenEditedHere() {
        val imported = HabitEditor.build(HabitForm(name = "x", steps = steps), null, "h", 0, today, now)
            .copy(kind = HabitStats.KIND_COUNTED)
        val edited = HabitEditor.build(
            HabitForm(name = "x2", kind = HabitStats.KIND_COUNTED, target = 3.0, steps = emptyList()), imported, "h", 0, today, now,
        )
        assertEquals(steps, HabitSteps.read(edited.raw))
    }
}
