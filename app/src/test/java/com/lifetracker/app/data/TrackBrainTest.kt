package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackBrainTest {
    private val t0 = 1_760_000_000_000L

    private val home = PlaceEntity(
        "home", "Home", "home", 28.6, 77.2, 100, false, 0L,
        pauseHere = true, pauseWifi = "HomeNet", pauseMotionless = true, heartbeatMin = 10,
    )

    private fun fix(sec: Int, lat: Double = 28.7, lng: Double = 77.2, acc: Float = 10f, speed: Float = -1f) =
        RawFixEntity(t0 + sec * 1000L, lat, lng, acc, speed, source = FixSource.GPS)

    private fun ctx(
        sec: Int,
        settings: TrackSettings = TrackSettings(intervalSec = 10),
        profiles: List<TrackingProfileEntity> = emptyList(),
        places: List<PlaceEntity> = emptyList(),
        base: Situation = Situation(),
        motionless: Boolean = false,
        paused: Boolean = false,
    ) = BrainContext(settings, profiles, places, base, motionless, paused, t0 + sec * 1000L)

    private fun profile(id: String, condition: String, interval: Int, minMove: Int = 0, priority: Int = 0) =
        TrackingProfileEntity(id, id.replaceFirstChar { it.uppercase() }, condition, intervalSec = interval, minMoveM = minMove, priority = priority, createdMs = 0L)

    @Test
    fun aGoodReadingIsSavedAndTheStatusSaysRecording() {
        val step = TrackBrain.onFix(BrainState(), fix(0), ctx(0))
        assertEquals(listOf(fix(0)), step.saves)
        assertNull(step.rejected)
        assertEquals("Recording", step.plan.status)
        assertEquals(10, step.plan.intervalSec)
        assertFalse(step.plan.gpsOff)
        assertEquals(listOf(fix(0)), step.state.recent)
    }

    @Test
    fun aRefusedReadingIsNotSavedAndTheDiaryGetsTheReason() {
        val start = TrackBrain.onFix(BrainState(), fix(0), ctx(0)).state
        val poor = TrackBrain.onFix(start, fix(10, acc = 400f), ctx(10))
        assertTrue(poor.saves.isEmpty())
        assertEquals(Reject.TOO_INACCURATE, poor.rejected)
        // It is not remembered as a recent reading either.
        assertEquals(1, poor.state.recent.size)
    }

    @Test
    fun aProfileChangesHowTheNextReadingsAreAskedForAndTagsTheOnesItTook() {
        val charging = profile("charge", ProfileCondition.CHARGING, interval = 2)
        val step = TrackBrain.onFix(BrainState(), fix(0), ctx(0, profiles = listOf(charging), base = Situation(charging = true)))
        assertEquals(2, step.plan.intervalSec)
        assertEquals("charge", step.plan.profileId)
        assertEquals("charge", step.saves.single().profileId)
        assertEquals("Recording · Charge", step.plan.status)
        // Unplugged (no delay): back to your own settings.
        val after = TrackBrain.onFix(step.state, fix(10), ctx(10, profiles = listOf(charging)))
        assertEquals(10, after.plan.intervalSec)
        assertEquals("", after.saves.single().profileId)
    }

    @Test
    fun thePhoneBeingStillSwitchesAStillProfileOnEvenWhenTheMovementLimitKeepsTheReadings() {
        val still = profile("still", ProfileCondition.STATIONARY, interval = 120)
        val settings = TrackSettings(intervalSec = 10, minMoveM = 10)
        var state = BrainState()
        var plan: Plan? = null
        for (i in 0..8) {
            // 4 m apart: refused as too close (movement limit 10 m) but still counted as the phone sitting there.
            val step = TrackBrain.onFix(state, fix(i * 10, lat = 28.7 + (i % 2) * 0.00004, speed = 0f), ctx(i * 10, settings, listOf(still)))
            state = step.state
            plan = step.plan
        }
        assertEquals("still", plan!!.profileId)
        assertEquals(120, plan.intervalSec)
    }

    @Test
    fun aReadingTooCloseToTheLastIsNotSaved() {
        val settings = TrackSettings(intervalSec = 10, minMoveM = 20)
        val first = TrackBrain.onFix(BrainState(), fix(0), ctx(0, settings))
        val near = TrackBrain.onFix(first.state, fix(10, lat = 28.70004), ctx(10, settings))
        assertTrue(near.saves.isEmpty())
        assertEquals(Reject.TOO_CLOSE, near.rejected)
        assertEquals(2, near.state.recent.size)
    }

    @Test
    fun insideAPauseZoneItKeepsRecordingForAMomentThenStopsSaving() {
        val places = listOf(home)
        val arrive = TrackBrain.onFix(BrainState(), fix(0, lat = 28.6), ctx(0, places = places))
        assertEquals(1, arrive.saves.size)
        val soon = TrackBrain.onFix(arrive.state, fix(10, lat = 28.60001), ctx(10, places = places))
        assertEquals(1, soon.saves.size)
        // 3.5 intervals of 10 s is 35 s.
        val paused = TrackBrain.onFix(soon.state, fix(40, lat = 28.60002), ctx(40, places = places))
        assertTrue(paused.saves.isEmpty())
        assertNull(paused.rejected)
        assertEquals("Paused: inside Home", paused.plan.status)
        assertFalse(paused.plan.gpsOff)
        // Ten minutes after it began, a heartbeat is due.
        assertEquals(560_000L, paused.plan.heartbeatInMs)
    }

    @Test
    fun leavingASpotItWasPausedAtSavesTheAnchorFirst() {
        val places = listOf(home)
        var state = TrackBrain.onFix(BrainState(), fix(0, lat = 28.6), ctx(0, places = places)).state
        state = TrackBrain.onFix(state, fix(40, lat = 28.6), ctx(40, places = places)).state
        val out = TrackBrain.onFix(state, fix(100, lat = 28.6015), ctx(100, places = places))
        assertEquals(2, out.saves.size)
        assertEquals(FixSource.ANCHOR, out.saves[0].source)
        assertEquals(28.6, out.saves[0].lat, 0.0)
        assertEquals(t0 + 100_000L - 1, out.saves[0].ms)
        assertEquals(fix(100, lat = 28.6015), out.saves[1])
        assertEquals("Recording", out.plan.status)
    }

    @Test
    fun aListedWifiAtAPauseZoneSaysTheReadingsCanStop() {
        val places = listOf(home)
        val wifi = Situation(wifiConnected = true, wifiName = "\"HomeNet\"")
        var state = TrackBrain.onFix(BrainState(), fix(0, lat = 28.6), ctx(0, places = places, base = wifi)).state
        val paused = TrackBrain.onFix(state, fix(40, lat = 28.6), ctx(40, places = places, base = wifi))
        assertTrue(paused.plan.gpsOff)
        assertEquals("Paused: Wi-Fi", paused.plan.status)
        state = paused.state
        // The Wi-Fi goes: looking again (no new reading) turns the readings back on.
        val again = TrackBrain.reevaluate(state, ctx(60, places = places, base = Situation()))
        assertFalse(again.plan.gpsOff)
        assertEquals("Paused: inside Home", again.plan.status)
    }

    @Test
    fun noMovementSwitchesTheReadingsOffUntilYouMoveAgain() {
        val places = listOf(home)
        var state = TrackBrain.onFix(BrainState(), fix(0, lat = 28.6), ctx(0, places = places)).state
        state = TrackBrain.onFix(state, fix(40, lat = 28.6), ctx(40, places = places)).state
        val still = TrackBrain.reevaluate(state, ctx(60, places = places, motionless = true))
        assertTrue(still.plan.gpsOff)
        assertEquals("Paused: no movement", still.plan.status)
        val moving = TrackBrain.reevaluate(still.state, ctx(80, places = places, motionless = false))
        assertFalse(moving.plan.gpsOff)
    }

    @Test
    fun aHeartbeatIsDueWhenTheTimeComes() {
        val places = listOf(home)
        var state = TrackBrain.onFix(BrainState(), fix(0, lat = 28.6), ctx(0, places = places)).state
        state = TrackBrain.onFix(state, fix(40, lat = 28.6), ctx(40, places = places)).state
        val early = TrackBrain.reevaluate(state, ctx(300, places = places))
        assertFalse(early.heartbeatNow)
        assertEquals(300_000L, early.plan.heartbeatInMs)
        val due = TrackBrain.reevaluate(early.state, ctx(600, places = places))
        assertTrue(due.heartbeatNow)
    }

    @Test
    fun pausingByYouSavesNothingAndTurnsTheReadingsOff() {
        val step = TrackBrain.onFix(BrainState(), fix(0), ctx(0, paused = true))
        assertTrue(step.saves.isEmpty())
        assertTrue(step.plan.gpsOff)
        assertEquals("Paused by you", step.plan.status)
        val look = TrackBrain.reevaluate(BrainState(), ctx(5, paused = true))
        assertTrue(look.plan.gpsOff)
    }

    @Test
    fun lookingAgainWithNoReadingYetJustGivesYourOwnPlan() {
        val plan = TrackBrain.reevaluate(BrainState(), ctx(0)).plan
        assertEquals(10, plan.intervalSec)
        assertEquals(AccuracyMode.HIGH, plan.accuracyMode)
        assertFalse(plan.gpsOff)
    }

    @Test
    fun onlyTheLastTwelveReadingsAreRemembered() {
        var state = BrainState()
        for (i in 0 until 20) state = TrackBrain.onFix(state, fix(i * 10, lat = 28.7 + i * 0.001), ctx(i * 10)).state
        assertEquals(TrackBrain.RECENT, state.recent.size)
        assertEquals(t0 + 190_000L, state.recent.last().ms)
    }

    @Test
    fun theWaitIsTheNearestOfAHeartbeatAndAProfileDelay() {
        val plan = Plan(10, AccuracyMode.HIGH, false, 60_000L, t0 + 20_000L, "", "Recording")
        assertEquals(20_000L, TrackBrain.waitMs(plan, t0))
        assertEquals(60_000L, TrackBrain.waitMs(plan.copy(nextCheckMs = null), t0))
        assertEquals(5_000L, TrackBrain.waitMs(plan.copy(heartbeatInMs = null, nextCheckMs = t0 + 5_000L), t0))
        assertNull(TrackBrain.waitMs(plan.copy(heartbeatInMs = null, nextCheckMs = null), t0))
        // Never faster than once a second.
        assertEquals(1_000L, TrackBrain.waitMs(plan.copy(heartbeatInMs = 0L), t0))
    }
}
