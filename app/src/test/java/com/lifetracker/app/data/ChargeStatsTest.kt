package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeStatsTest {
    private val min = 60_000L
    private val t0 = 1_760_000_000_000L

    private fun sample(at: Long, charging: Boolean = true, level: Int = 50, plug: Int = ChargeStats.PLUG_AC, ma: Int? = 2000, mv: Int? = 4000) =
        ChargeSessions.Sample(at, charging, plug, level, ma, mv)

    @Test fun pluggingInStartsASession() {
        val r = ChargeSessions.apply(null, sample(t0, level = 30))
        val s = r.started!!
        assertTrue(s.ongoing)
        assertEquals(30, s.startLevel)
        assertEquals(t0, s.startMs)
        assertEquals(listOf(s), r.changed)
    }

    @Test fun nothingHappensWhenNotChargingAndNothingOpen() {
        val r = ChargeSessions.apply(null, sample(t0, charging = false))
        assertNull(r.started)
        assertTrue(r.changed.isEmpty())
    }

    @Test fun nextCheckWhileChargingExtendsTheSession() {
        val open = ChargeSessions.apply(null, sample(t0, level = 30)).started!!
        val r = ChargeSessions.apply(open, sample(t0 + 15 * min, level = 38, ma = 1800))
        assertNull(r.started)
        val s = r.changed.single()
        assertEquals(open.id, s.id)
        assertTrue(s.ongoing)
        assertEquals(38, s.endLevel)
        assertEquals(t0 + 15 * min, s.endMs)
        assertEquals(2, s.samples)
        assertEquals(1900, ChargeStats.avgMilliamps(s))
    }

    @Test fun unplugSeenLaterEndsAtLastCheck() {
        val open = ChargeSessions.apply(null, sample(t0, level = 30)).started!!
        val mid = ChargeSessions.apply(open, sample(t0 + 15 * min, level = 40)).changed.single()
        val r = ChargeSessions.apply(mid, sample(t0 + 3 * 60 * min, charging = false, level = 35))
        val s = r.changed.single()
        assertFalse(s.ongoing)
        assertEquals(t0 + 15 * min, s.endMs)
        assertEquals(40, s.endLevel)
    }

    @Test fun unplugSeenLiveEndsExactly() {
        val open = ChargeSessions.apply(null, sample(t0, level = 30)).started!!
        val r = ChargeSessions.apply(open, sample(t0 + 7 * min, charging = false, level = 33), sawUnplug = true)
        val s = r.changed.single()
        assertFalse(s.ongoing)
        assertEquals(t0 + 7 * min, s.endMs)
        assertEquals(33, s.endLevel)
    }

    @Test fun longSilenceMeansItWasUnpluggedAndPluggedAgain() {
        val open = ChargeSessions.apply(null, sample(t0, level = 30)).started!!
        val r = ChargeSessions.apply(open, sample(t0 + 5 * 60 * min, level = 31))
        assertEquals(2, r.changed.size)
        assertFalse(r.changed[0].ongoing)
        assertEquals(open.id, r.changed[0].id)
        assertNotNull(r.started)
        assertEquals((t0 + 5 * 60 * min).toString(), r.started!!.id)
    }

    @Test fun aBigDropMeansItWasUnplugged() {
        val open = ChargeSessions.apply(null, sample(t0, level = 80)).started!!
        val r = ChargeSessions.apply(open, sample(t0 + 15 * min, level = 60))
        assertNotNull(r.started)
        assertFalse(r.changed[0].ongoing)
    }

    @Test fun aDifferentPlugMeansANewSession() {
        val open = ChargeSessions.apply(null, sample(t0, level = 30, plug = ChargeStats.PLUG_AC)).started!!
        val r = ChargeSessions.apply(open, sample(t0 + 10 * min, level = 31, plug = ChargeStats.PLUG_USB))
        assertNotNull(r.started)
        assertEquals(ChargeStats.PLUG_USB, r.started!!.plugType)
    }

    @Test fun currentReadingsAreMilliampsOrMicroamps() {
        assertEquals(1500, ChargeStats.toMilliamps(1_500_000))
        assertEquals(1500, ChargeStats.toMilliamps(-1_500_000))
        assertEquals(1500, ChargeStats.toMilliamps(1500))
        assertNull(ChargeStats.toMilliamps(0))
        assertNull(ChargeStats.toMilliamps(Long.MIN_VALUE))
    }

    @Test fun wattsComeFromCurrentAndVoltage() {
        val s = session(source = "", ma = 2000, mv = 4000)
        assertEquals(8.0, ChargeStats.avgWatts(s)!!, 0.01)
        assertEquals("≈ 8 W", ChargeStats.wattsText(8.0))
        assertEquals("≈ 7.5 W", ChargeStats.wattsText(7.46))
        assertNull(ChargeStats.avgWatts(session(source = "", ma = null, mv = 4000)))
    }

    @Test fun phoneUseCountsOnlyTheOverlap() {
        val s = session(source = "").copy(startMs = t0, endMs = t0 + 60 * min, ongoing = false)
        val usage = listOf(
            UsageSessionEntity("a", "com.a", t0 - 10 * min, t0 + 5 * min), // 5 min inside
            UsageSessionEntity("b", "com.b", t0 + 20 * min, t0 + 30 * min), // 10 min inside
            UsageSessionEntity("c", "com.c", t0 + 55 * min, t0 + 80 * min), // 5 min inside
            UsageSessionEntity("d", "com.d", t0 + 90 * min, t0 + 95 * min), // outside
        )
        assertEquals(20 * min, ChargeStats.phoneUseMs(s, usage, t0 + 200 * min))
    }

    @Test fun guessNeedsEnoughAgreeingHistory() {
        val two = listOf(session("powerbank", plug = ChargeStats.PLUG_USB), session("powerbank", plug = ChargeStats.PLUG_USB))
        assertNull(ChargeStats.guessSource(ChargeStats.PLUG_USB, null, two))

        val four = two + listOf(session("powerbank", plug = ChargeStats.PLUG_USB), session("powerbank", plug = ChargeStats.PLUG_USB))
        assertEquals("powerbank", ChargeStats.guessSource(ChargeStats.PLUG_USB, null, four))
        // A different kind of plug has no history.
        assertNull(ChargeStats.guessSource(ChargeStats.PLUG_AC, null, four))

        val split = four.take(2) + listOf(session("laptop", plug = ChargeStats.PLUG_USB), session("laptop", plug = ChargeStats.PLUG_USB))
        assertNull(ChargeStats.guessSource(ChargeStats.PLUG_USB, null, split))
    }

    @Test fun guessUsesSpeedWhenKnown() {
        val slowBank = List(3) { session("powerbank", plug = ChargeStats.PLUG_USB, ma = 1000) } // 4 W
        val fastWall = List(3) { session("wall", plug = ChargeStats.PLUG_USB, ma = 4000) } // 16 W
        val history = slowBank + fastWall
        assertEquals("powerbank", ChargeStats.guessSource(ChargeStats.PLUG_USB, 4.2, history))
        assertEquals("wall", ChargeStats.guessSource(ChargeStats.PLUG_USB, 15.0, history))
        // Without a speed the two answers disagree, so no guess.
        assertNull(ChargeStats.guessSource(ChargeStats.PLUG_USB, null, history))
    }

    @Test fun untaggedSessionsNeverTeachTheGuess() {
        val untagged = List(5) { session("", plug = ChargeStats.PLUG_AC) }
        assertNull(ChargeStats.guessSource(ChargeStats.PLUG_AC, null, untagged))
    }

    @Test fun titleAndDetailReadWell() {
        val tagged = session("wall", plug = ChargeStats.PLUG_AC).copy(startLevel = 34, endLevel = 88, startMs = t0, endMs = t0 + 130 * min, ongoing = false)
        assertEquals("Wall charger", ChargeStats.title(tagged))
        assertEquals("34% → 88% · 2h 10m · ≈ 8 W", ChargeStats.detail(tagged))
        val plain = tagged.copy(source = "")
        assertEquals("Charger", ChargeStats.title(plain))
        assertEquals("Power bank (guess)", ChargeStats.title(plain, "powerbank"))
        assertTrue(ChargeStats.detail(tagged.copy(ongoing = true)).contains("still charging"))
    }

    @Test fun totalsAddUp() {
        val a = session("wall").copy(startLevel = 20, endLevel = 60, startMs = t0, endMs = t0 + 60 * min)
        val b = session("wall").copy(startLevel = 50, endLevel = 90, startMs = t0 + 5 * 60 * min, endMs = t0 + 6 * 60 * min)
        val t = ChargeStats.totals(listOf(a, b))
        assertEquals(2, t.sessions)
        assertEquals(120 * min, t.chargingMs)
        assertEquals(80, t.gainPoints)
    }

    private var counter = 0
    private fun session(source: String, plug: Int = ChargeStats.PLUG_AC, ma: Int? = 2000, mv: Int? = 4000): ChargeSessionEntity {
        counter++
        return ChargeSessionEntity(
            id = "s$counter",
            startMs = t0 + counter * 1000L,
            endMs = t0 + counter * 1000L,
            startLevel = 20,
            endLevel = 80,
            plugType = plug,
            source = source,
            samples = 2,
            currentSamples = if (ma != null) 2 else 0,
            sumMa = (ma ?: 0) * 2L,
            sumMv = (mv ?: 0) * 2L,
            ongoing = false,
        )
    }
}
