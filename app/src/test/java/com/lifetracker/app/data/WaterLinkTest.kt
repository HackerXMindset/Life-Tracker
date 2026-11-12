package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterLinkTest {
    @Test
    fun onlyVolumeUnitsCanBeLinked() {
        assertEquals(1.0, WaterUnits.mlPer("ml")!!, 1e-9)
        assertEquals(1000.0, WaterUnits.mlPer(" Litres ")!!, 1e-9)
        assertEquals(250.0, WaterUnits.mlPer("glasses")!!, 1e-9)
        assertNull(WaterUnits.mlPer("times"))
        assertNull(WaterUnits.mlPer(""))
    }

    @Test
    fun unitsGoToMillilitresAndBack() {
        assertEquals(750, WaterUnits.toMl(3.0, "glasses"))
        assertEquals(3.0, WaterUnits.fromMl(750, "glasses")!!, 1e-9)
        assertEquals(2500, WaterUnits.toMl(2.5, "l"))
        assertNull(WaterUnits.toMl(2.0, "times"))
        val oz = WaterUnits.fromMl(250, "oz")!!
        assertEquals(250, WaterUnits.toMl(oz, "oz"))
    }

    @Test
    fun theFirstTimeTheLargerSideWins() {
        val plan = WaterSync.step(null, mapOf("d1" to 500), mapOf("d1" to 750, "d2" to 250))
        assertEquals(mapOf("d1" to 750, "d2" to 250), plan.water)
        assertTrue(plan.habit.isEmpty())
        assertEquals(mapOf("d1" to 750, "d2" to 250), plan.agreed)
    }

    @Test
    fun aChangeOnTheFoodSideReachesTheHabit() {
        val prev = mapOf("d1" to 500)
        val plan = WaterSync.step(prev, mapOf("d1" to 750), mapOf("d1" to 500))
        assertEquals(mapOf("d1" to 750), plan.habit)
        assertTrue(plan.water.isEmpty())
    }

    @Test
    fun aChangeOnTheHabitSideReachesFood() {
        val prev = mapOf("d1" to 500)
        val plan = WaterSync.step(prev, mapOf("d1" to 500), mapOf("d1" to 250))
        assertEquals(mapOf("d1" to 250), plan.water)
        assertTrue(plan.habit.isEmpty())
    }

    @Test
    fun clearingOneSideClearsTheOther() {
        val prev = mapOf("d1" to 500)
        val plan = WaterSync.step(prev, emptyMap(), mapOf("d1" to 500))
        assertEquals(mapOf("d1" to 0), plan.habit)
        assertTrue(plan.agreed.isEmpty())
    }

    @Test
    fun ifBothChangedTheLargerWins() {
        val plan = WaterSync.step(mapOf("d1" to 500), mapOf("d1" to 750), mapOf("d1" to 1000))
        assertEquals(mapOf("d1" to 1000), plan.water)
        assertTrue(plan.habit.isEmpty())
    }

    @Test
    fun roundingOfAMillilitreIsIgnored() {
        val plan = WaterSync.step(mapOf("d1" to 250), mapOf("d1" to 250), mapOf("d1" to 249))
        assertTrue(plan.water.isEmpty() && plan.habit.isEmpty())
        assertEquals(mapOf("d1" to 250), plan.agreed)
    }

    @Test
    fun whenBothAgreeNothingIsWritten() {
        val plan = WaterSync.step(mapOf("d1" to 500), mapOf("d1" to 500), mapOf("d1" to 500))
        assertTrue(plan.water.isEmpty() && plan.habit.isEmpty())
    }

    private fun habit(id: String, name: String, unit: String, kind: Int = HabitStats.KIND_COUNTED, archived: Boolean = false) = HabitEntity(
        id = id, name = name, icon = "", category = "", color = 0, kind = kind, quantKind = 0, unitLabel = unit, target = 1.0, step = 1.0,
        anyAmount = false, startMinute = -1, durationMinutes = 0, startedOn = "2026-01-01", archived = archived, sortOrder = 0, raw = "{}",
    )

    @Test
    fun theSavedHabitIsKept() {
        val habits = listOf(habit("a", "Water", "ml"), habit("b", "Hydrate", "glasses"))
        assertEquals("b", WaterLink.pick(habits, "b")?.id)
    }

    @Test
    fun aHabitCalledWaterIsFoundWhenItsUnitIsAVolume() {
        assertEquals("a", WaterLink.pick(listOf(habit("a", " water ", "glasses")), "")?.id)
        assertNull(WaterLink.pick(listOf(habit("a", "Water", "times")), ""))
        assertNull(WaterLink.pick(listOf(habit("a", "Water", "ml", kind = HabitStats.KIND_YES_NO)), ""))
        assertNull(WaterLink.pick(listOf(habit("a", "Water", "ml", archived = true)), ""))
    }
}
