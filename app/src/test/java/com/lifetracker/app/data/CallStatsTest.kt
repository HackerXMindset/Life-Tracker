package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStatsTest {
    private fun call(type: Int, seconds: Int, number: String = "98765", name: String = "", start: Long = 1_000L) =
        CallEntity(CallStats.idFor(start, number), number, name, type, start, seconds)

    @Test
    fun whoPrefersNameThenNumberThenUnknown() {
        assertEquals("Mom", CallStats.who(call(1, 10, name = "Mom")))
        assertEquals("98765", CallStats.who(call(1, 10)))
        assertEquals("Unknown number", CallStats.who(call(1, 10, number = "")))
        assertEquals("Unknown number", CallStats.who(call(3, 0, number = "-1")))
    }

    @Test
    fun titlesReadNaturally() {
        assertEquals("Call with Mom", CallStats.title(call(CallStats.INCOMING, 60, name = "Mom")))
        assertEquals("Call with Mom", CallStats.title(call(CallStats.OUTGOING, 60, name = "Mom")))
        assertEquals("Called Mom, no answer", CallStats.title(call(CallStats.OUTGOING, 0, name = "Mom")))
        assertEquals("Missed call from Mom", CallStats.title(call(CallStats.MISSED, 0, name = "Mom")))
        assertEquals("Declined call from Mom", CallStats.title(call(CallStats.DECLINED, 0, name = "Mom")))
        assertEquals("Blocked call from Mom", CallStats.title(call(CallStats.BLOCKED, 0, name = "Mom")))
        assertEquals("Voicemail from Mom", CallStats.title(call(CallStats.VOICEMAIL, 20, name = "Mom")))
    }

    @Test
    fun detailShowsDirectionAndLengthOnlyWhenSpoken() {
        assertEquals("Incoming · 12m 05s", CallStats.detail(call(CallStats.INCOMING, 12 * 60 + 5)))
        assertEquals("Outgoing · 45s", CallStats.detail(call(CallStats.OUTGOING, 45)))
        assertEquals("Outgoing", CallStats.detail(call(CallStats.OUTGOING, 0)))
        assertEquals("Incoming", CallStats.detail(call(CallStats.MISSED, 0)))
    }

    @Test
    fun durationText() {
        assertEquals("0s", CallStats.durationText(0))
        assertEquals("59s", CallStats.durationText(59))
        assertEquals("1m", CallStats.durationText(60))
        assertEquals("2m 30s", CallStats.durationText(150))
        assertEquals("59m 59s", CallStats.durationText(3599))
        assertEquals("1h", CallStats.durationText(3600))
        assertEquals("1h 05m", CallStats.durationText(3600 + 5 * 60 + 40))
        assertEquals("0s", CallStats.durationText(-5))
    }

    @Test
    fun onlySpokenCallsCountAsTalkTime() {
        val list = listOf(
            call(CallStats.INCOMING, 100, start = 1),
            call(CallStats.OUTGOING, 50, start = 2),
            call(CallStats.OUTGOING, 0, start = 3),
            call(CallStats.MISSED, 0, start = 4),
            call(CallStats.VOICEMAIL, 30, start = 5),
        )
        val t = CallStats.totals(list)
        assertEquals(5, t.calls)
        assertEquals(150, t.talkSec)
        assertEquals(1, t.missed)
        assertTrue(CallStats.connected(list[0]))
        assertFalse(CallStats.connected(list[4]))
    }

    @Test
    fun peopleAreGroupedByNumberAndRankedByTalkTime() {
        val list = listOf(
            call(CallStats.INCOMING, 600, number = "111", name = "Old name", start = 1),
            call(CallStats.OUTGOING, 300, number = "111", name = "Mom", start = 5),
            call(CallStats.OUTGOING, 800, number = "222", name = "Friend", start = 3),
            call(CallStats.MISSED, 0, number = "333", start = 4),
            call(CallStats.MISSED, 0, number = "", start = 6),
        )
        val people = CallStats.perPerson(list)
        assertEquals(listOf("Mom", "Friend"), people.take(2).map { it.name })
        assertEquals(2, people[0].calls)
        assertEquals(900, people[0].talkSec)
        assertEquals(5L, people[0].lastMs)
        assertEquals(4, people.size)
    }

    @Test
    fun idsAreStableSoCopyingTwiceNeverDoubles() {
        assertEquals("1234|98765", CallStats.idFor(1234, "98765"))
        assertEquals(call(1, 5, start = 1234).id, call(1, 5, start = 1234).id)
    }
}
