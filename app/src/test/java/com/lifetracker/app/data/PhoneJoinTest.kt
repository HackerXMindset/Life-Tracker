package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneJoinTest {
    private val min = 60_000L

    private fun block(pkg: String, start: Long, end: Long, vararg uses: Pair<String, Long>): UsageDays.Block {
        val apps = uses.map { (p, ms) -> UsageDays.AppUse(p, p.uppercase(), "", ms, 1) }
        return UsageDays.Block(
            pkg, apps.joinToString(" + ") { it.label }, "", start, end, apps.sumOf { it.activeMs },
            spans = listOf(start to end), apps = apps,
            appSpans = apps.associate { it.pkg to listOf(start to end) },
        )
    }

    @Test
    fun theLaterPeriodIsHiddenIntoTheEarlierOne() {
        val out = PhoneJoin.join("a@0", "b@1000", emptyList(), 5L)
        val dropped = out.single()
        assertTrue(dropped.hidden)
        assertEquals("b@1000", dropped.refId)
        assertEquals("a@0", PhoneJoin.keeperOf(dropped))
        assertTrue(PhoneJoin.isJoined(dropped))
    }

    @Test
    fun aPeriodAlreadyFoldedIntoTheDroppedOneFollowsIt() {
        val earlier = PhoneJoin.join("b@1000", "c@2000", emptyList(), 1L).single()
        val out = PhoneJoin.join("a@0", "b@1000", listOf(earlier), 2L)
        assertEquals(2, out.size)
        assertEquals("a@0", PhoneJoin.keeperOf(out.first { it.refId == "c@2000" }))
        assertEquals("a@0", PhoneJoin.keeperOf(out.first { it.refId == "b@1000" }))
    }

    @Test
    fun joiningAPeriodToItselfDoesNothing() {
        assertTrue(PhoneJoin.join("a@0", "a@0", emptyList(), 1L).isEmpty())
    }

    @Test
    fun separatingBringsEveryFoldedPeriodBack() {
        val hidden = PhoneJoin.join("a@0", "b@1000", emptyList(), 1L)
        val back = PhoneJoin.separate("a@0", hidden, 2L)
        assertEquals(1, back.size)
        assertFalse(back.single().hidden)
        assertNull(PhoneJoin.keeperOf(back.single()))
        assertTrue(PhoneJoin.separate("zzz", hidden, 2L).isEmpty())
    }

    @Test
    fun anotherKindOfHiddenEditIsNotAJoin() {
        val plain = TimelineEdits.edit(TimelineEdits.APP, "a@0", hidden = true, now = 1L)
        assertFalse(PhoneJoin.isJoined(plain))
        assertNull(PhoneJoin.keeperOf(plain))
    }

    @Test
    fun mergedPeriodAddsTheSameAppTogetherAndKeepsEveryOpening() {
        val a = block("ig", 0, 10 * min, "ig" to 8 * min, "br" to 2 * min)
        val b = block("br", 20 * min, 30 * min, "br" to 6 * min, "ig" to 2 * min)
        val m = PhoneJoin.merge(a, listOf(b))
        assertEquals("ig", m.pkg)
        assertEquals(0L, m.startMs)
        assertEquals(30 * min, m.endMs)
        assertEquals(18 * min, m.activeMs)
        assertEquals(listOf("ig", "br"), m.apps.map { it.pkg })
        assertEquals(10 * min, m.apps[0].activeMs)
        assertEquals(8 * min, m.apps[1].activeMs)
        assertEquals(2, m.apps[0].opens)
        assertEquals(2, m.appSpans["ig"]?.size)
        assertEquals(listOf("br@${20 * min}"), m.joined)
    }
}
