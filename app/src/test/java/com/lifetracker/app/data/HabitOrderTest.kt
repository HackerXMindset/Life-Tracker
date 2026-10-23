package com.lifetracker.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitOrderTest {
    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun movesUpAndDownByOnePlace() {
        assertEquals(listOf("a", "c", "b", "d"), HabitOrder.move(ids, "b", 1))
        assertEquals(listOf("b", "a", "c", "d"), HabitOrder.move(ids, "b", -1))
    }

    @Test
    fun theEndsAndSmallListsStayPut() {
        assertEquals(ids, HabitOrder.move(ids, "a", -1))
        assertEquals(ids, HabitOrder.move(ids, "d", 1))
        assertEquals(listOf("x"), HabitOrder.move(listOf("x"), "x", 1))
        assertEquals(ids, HabitOrder.move(ids, "zzz", 1))
        assertEquals(ids, HabitOrder.move(ids, "b", 0))
    }

    @Test
    fun hiddenHabitsAreJumpedOverAndKeepTheirPlace() {
        assertEquals(listOf("a", "c", "b", "d"), HabitOrder.move(ids, "b", 1))
        assertEquals(listOf("a", "d", "c", "b"), HabitOrder.move(ids, "b", 1, skip = setOf("c")))
        assertEquals(ids, HabitOrder.move(ids, "b", 1, skip = setOf("c", "d")))
    }

    @Test
    fun withOrderWritesSortOrderAndStreaksOrderField() {
        val h = HabitEntity("h", "x", "t", "", 0, 0, 0, "", 1.0, 1.0, false, -1, 0, "2026-10-01", false, 9, """{"name":"x"}""")
        val out = HabitOrder.withOrder(h, 3)
        assertEquals(3, out.sortOrder)
        assertEquals(3, JSONObject(out.raw).getInt("order"))
        assertEquals("x", JSONObject(out.raw).getString("name"))
        val broken = HabitOrder.withOrder(h.copy(raw = "not json"), 2)
        assertEquals(2, JSONObject(broken.raw).getInt("order"))
    }

    private data class Item(val name: String, val due: Boolean, val done: Boolean, val tracking: Boolean = false)

    private val items = listOf(
        Item("due-open", due = true, done = false),
        Item("due-done", due = true, done = true),
        Item("off-open", due = false, done = false),
        Item("track", due = true, done = false, tracking = true),
    )

    private fun names(f: HabitOrder.Filter) =
        HabitOrder.apply(items, f) { HabitOrder.Facts(it.due, it.done, it.tracking) }.map { it.name }

    @Test
    fun defaultFilterShowsEverythingInOrder() {
        assertEquals(items.map { it.name }, names(HabitOrder.Filter()))
        assertTrue(!HabitOrder.Filter().active)
    }

    @Test
    fun eachSwitchHidesItsOwnKind() {
        assertEquals(listOf("due-open", "due-done", "track"), names(HabitOrder.Filter(dueToday = true)))
        assertEquals(listOf("due-open", "off-open", "track"), names(HabitOrder.Filter(hideCompleted = true)))
        assertEquals(listOf("due-open", "due-done", "off-open"), names(HabitOrder.Filter(hideTracking = true)))
    }

    @Test
    fun completedLastMovesAndNeverHides() {
        val f = HabitOrder.Filter(completedLast = true)
        assertEquals(listOf("due-open", "off-open", "track", "due-done"), names(f))
        assertTrue(f.active)
        assertTrue(!f.hides)
    }

    @Test
    fun switchesCombine() {
        val f = HabitOrder.Filter(dueToday = true, hideTracking = true, completedLast = true)
        assertEquals(listOf("due-open", "due-done"), names(f))
        assertEquals(listOf("due-open"), names(HabitOrder.Filter(dueToday = true, hideCompleted = true, hideTracking = true)))
    }
}
