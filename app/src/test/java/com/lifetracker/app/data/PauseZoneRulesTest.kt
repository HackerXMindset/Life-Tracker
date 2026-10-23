package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseZoneRulesTest {
    private val t0 = 1_760_000_000_000L

    private val home = PlaceEntity(
        "home", "Home", "home", 28.6, 77.2, 100, false, 0L,
        pauseHere = true, pauseWifi = "\"HomeNet\"\nOther Net", pauseMotionless = true, heartbeatMin = 10,
    )
    private val gym = PlaceEntity("gym", "Gym", "gym", 28.62, 77.2, 100, false, 0L, pauseHere = true)
    private val library = PlaceEntity("lib", "Library", "study", 28.63, 77.2, 100, false, 0L)
    private val places = listOf(home, gym, library)

    private fun at(state: PauseState, sec: Int, lat: Double = 28.6, wifi: String? = null, still: Boolean = false, interval: Int = 10) =
        PauseZoneRules.step(state, places, lat, 77.2, wifi, still, t0 + sec * 1000L, interval)

    @Test
    fun outsideEveryZoneEverythingIsRecorded() {
        val s = at(PauseState(), 0, lat = 28.65)
        assertTrue(s.save)
        assertFalse(s.gpsOff)
        assertNull(s.anchor)
        assertEquals(PauseState(), s.state)
    }

    @Test
    fun aPlaceWithoutPauseHereIsNotAZone() {
        assertNull(PauseZoneRules.zoneAt(places, 28.63, 77.2))
        assertEquals("home", PauseZoneRules.zoneAt(places, 28.6, 77.2)?.id)
    }

    @Test
    fun afterArrivingItKeepsRecordingForThreeAndAHalfIntervalsThenPauses() {
        val arrive = at(PauseState(), 0)
        assertTrue(arrive.save)
        assertEquals("home", arrive.state.zoneId)
        assertTrue(at(arrive.state, 34).save)
        val paused = at(arrive.state, 35)
        assertFalse(paused.save)
        assertFalse(paused.gpsOff)
        assertTrue(paused.state.paused)
        assertEquals("Paused: inside Home", paused.status)
    }

    @Test
    fun anEntryDelayOfYourOwnReplacesTheAutomaticOne() {
        val quick = home.copy(entryDelaySec = 5)
        assertEquals(5_000L, PauseZoneRules.entryDelayMs(quick, 10))
        assertEquals(35_000L, PauseZoneRules.entryDelayMs(home, 10))
        assertEquals(0L, PauseZoneRules.entryDelayMs(home.copy(entryDelaySec = 0), 10))
    }

    @Test
    fun aListedWifiTurnsTheReadingsOffAndShowsWhy() {
        val arrive = at(PauseState(), 0)
        val s = at(arrive.state, 40, wifi = "\"homenet\"")
        assertTrue(s.gpsOff)
        assertFalse(s.save)
        assertEquals("Paused: Wi-Fi", s.status)
        assertFalse(at(arrive.state, 40, wifi = "Cafe").gpsOff)
    }

    @Test
    fun noMovementTurnsTheReadingsOffOnlyWhenThePlaceAsksForIt() {
        val arrive = at(PauseState(), 0)
        val s = at(arrive.state, 40, still = true)
        assertTrue(s.gpsOff)
        assertEquals("Paused: no movement", s.status)
        val noAsk = places.map { if (it.id == "home") it.copy(pauseMotionless = false) else it }
        val t = PauseZoneRules.step(arrive.state, noAsk, 28.6, 77.2, null, true, t0 + 40_000L, 10)
        assertFalse(t.gpsOff)
    }

    @Test
    fun withBothOnTheReadingsComeBackOnlyWhenBothHaveCleared() {
        val arrive = at(PauseState(), 0)
        // On the Wi-Fi and still: off. Then the Wi-Fi goes but you are still: still off. Then you move: back on.
        assertTrue(at(arrive.state, 40, wifi = "HomeNet", still = true).gpsOff)
        assertTrue(at(arrive.state, 50, wifi = null, still = true).gpsOff)
        assertFalse(at(arrive.state, 60, wifi = null, still = false).gpsOff)
    }

    @Test
    fun aHeartbeatIsDueEveryNMinutesWhilePaused() {
        val arrive = at(PauseState(), 0)
        val paused = at(arrive.state, 40)
        assertFalse(paused.heartbeatNow)
        val beat = at(paused.state, 600)
        assertTrue(beat.heartbeatNow)
        assertEquals(t0 + 600_000L, beat.state.lastHeartbeatMs)
        assertFalse(at(beat.state, 660).heartbeatNow)
        assertTrue(at(beat.state, 1200).heartbeatNow)
        // A place with no heartbeat never asks.
        val quiet = places.map { if (it.id == "home") it.copy(heartbeatMin = 0) else it }
        val q = PauseZoneRules.step(paused.state, quiet, 28.6, 77.2, null, false, t0 + 3_600_000L, 10)
        assertFalse(q.heartbeatNow)
    }

    @Test
    fun leavingAZoneYouWerePausedInGivesAnAnchorAtItsCentre() {
        val arrive = at(PauseState(), 0)
        val paused = at(arrive.state, 40)
        val out = at(paused.state, 100, lat = 28.65)
        assertEquals("home", out.anchor?.id)
        assertEquals(PauseState(), out.state)
        assertTrue(out.save)
    }

    @Test
    fun leavingBeforeTheEntryDelayIsOverCancelsEverythingQuietly() {
        val arrive = at(PauseState(), 0)
        val out = at(arrive.state, 20, lat = 28.65)
        assertNull(out.anchor)
        assertEquals(PauseState(), out.state)
        assertTrue(out.save)
    }

    @Test
    fun goingStraightFromOnePausedZoneToAnotherAnchorsTheFirst() {
        val arrive = at(PauseState(), 0)
        val paused = at(arrive.state, 40)
        val next = at(paused.state, 100, lat = 28.62)
        assertEquals("home", next.anchor?.id)
        assertEquals("gym", next.state.zoneId)
        assertTrue(next.save)
    }
}
