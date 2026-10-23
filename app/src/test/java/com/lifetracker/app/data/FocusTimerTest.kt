package com.lifetracker.app.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerTest {
    private val zone = ZoneId.of("UTC")
    private val t0 = 1_000_000_000_000L
    private fun sec(n: Int) = n * 1000L
    private var counter = 0
    private val id: () -> String = { "s${++counter}" }

    private fun countdown(minutes: Int = 25) = FocusTimer.start("h", "Read", minutes, 0, 0, false, t0)
    private fun pomodoro(focus: Int = 25, brk: Int = 5, long: Int = 15, whole: Boolean = false) =
        FocusTimer.start("h", "", focus, brk, long, whole, t0)

    @Test
    fun aCountdownCountsDownAndStopsAtItsTarget() {
        val s = countdown(25)
        assertEquals(25 * 60, FocusTimer.displaySeconds(s, t0))
        assertEquals(24 * 60 + 50, FocusTimer.displaySeconds(s, t0 + sec(10)))
        assertEquals(0, FocusTimer.displaySeconds(s, t0 + sec(99999)))
        assertTrue(FocusTimer.reachedTarget(s, t0 + sec(25 * 60)))
        assertEquals(t0 + sec(25 * 60), FocusTimer.phaseEndMillis(s))
    }

    @Test
    fun aStopwatchCountsUpAndNeverEnds() {
        val s = FocusTimer.start("h", "", 0, 5, 15, false, t0)
        assertTrue(FocusTimer.isFlow(s))
        assertFalse(FocusTimer.isPomodoro(s)) // breaks make no sense without a target
        assertEquals(90, FocusTimer.displaySeconds(s, t0 + sec(90)))
        assertNull(FocusTimer.phaseEndMillis(s))
        assertFalse(FocusTimer.phaseDue(s, t0 + sec(999999)))
    }

    @Test
    fun pauseAndResumeKeepWhatWasDone() {
        val s = FocusTimer.pause(countdown(10), t0 + sec(100))
        assertFalse(FocusTimer.isRunning(s))
        assertEquals(100, s.accumulatedSeconds)
        assertEquals(100, FocusTimer.elapsedSeconds(s, t0 + sec(5000))) // time passes, nothing counts
        assertNull(FocusTimer.phaseEndMillis(s))
        val again = FocusTimer.resume(s, t0 + sec(5000))
        assertEquals(t0 + sec(5000) + sec(500), FocusTimer.phaseEndMillis(again))
    }

    @Test
    fun addMinuteMovesTheEnd() {
        val s = FocusTimer.addMinute(countdown(1), t0 + sec(10))
        assertEquals(2, s.targetMinutes)
        assertEquals(t0 + sec(120), FocusTimer.phaseEndMillis(s))
    }

    @Test
    fun aCountdownThatEndsIsSavedAtOnce() {
        val s = countdown(25)
        assertTrue(FocusTimer.phaseDue(s, t0 + sec(25 * 60)))
        val end = FocusTimer.onPhaseEnd(s, t0 + sec(25 * 60 + 3), id)
        assertEquals(PhaseEnd.Alert.CountdownDone, end.alert)
        assertTrue(end.state.finished)
        val saved = end.saved!!
        assertEquals(25 * 60, saved.seconds)
        assertTrue(saved.completed)
        assertEquals(t0, saved.startedAtMillis)
        assertEquals("Read", saved.label)
        // Ending it afterwards keeps nothing more; +1 minute does nothing.
        assertNull(FocusTimer.stop(end.state, t0 + sec(9999), true, id))
        assertEquals(end.state, FocusTimer.addMinute(end.state, t0 + sec(9999)))
        assertNull(FocusTimer.phaseEndMillis(end.state))
    }

    @Test
    fun aPomodoroRoundIsSavedAndTheBreakStartsByItself() {
        val s = pomodoro()
        val end = FocusTimer.onPhaseEnd(s, t0 + sec(25 * 60 + 2), id)
        assertEquals(PhaseEnd.Alert.BreakStarts, end.alert)
        assertEquals(25 * 60, end.saved!!.seconds)
        assertTrue(end.state.isBreak)
        assertEquals(5, end.state.targetMinutes)
        assertEquals(t0 + sec(25 * 60 + 2), end.state.sinceMillis)
        assertEquals(1, end.state.round)
    }

    @Test
    fun aFinishedBreakWaitsForContinueThenTheNextRoundStarts() {
        val inBreak = FocusTimer.onPhaseEnd(pomodoro(), t0 + sec(1500), id).state
        val over = FocusTimer.onPhaseEnd(inBreak, t0 + sec(1500 + 300), id)
        assertEquals(PhaseEnd.Alert.BreakOver, over.alert)
        assertNull(over.saved)
        assertTrue(over.state.awaiting)
        assertNull(FocusTimer.phaseEndMillis(over.state))
        val next = FocusTimer.advance(over.state, t0 + sec(2000), id)
        assertFalse(next.state.isBreak)
        assertEquals(2, next.state.round)
        assertEquals(25, next.state.targetMinutes)
        assertFalse(next.state.awaiting)
        assertEquals(t0 + sec(2000), next.state.sinceMillis)
    }

    @Test
    fun everyFourthBreakIsALongOne() {
        var s = pomodoro(25, 5, 15)
        val lengths = ArrayList<Int>()
        var now = t0
        repeat(4) {
            now += sec(25 * 60)
            s = FocusTimer.onPhaseEnd(s, now, id).state // focus ends, break starts
            lengths += s.targetMinutes
            now += sec(s.targetMinutes * 60)
            s = FocusTimer.advance(FocusTimer.onPhaseEnd(s, now, id).state, now, id).state // break ends, Continue
        }
        assertEquals(listOf(5, 5, 5, 15), lengths)
        assertEquals(5, s.round)
        // With no long break set, all breaks are short.
        assertFalse(FocusTimer.isLongBreak(pomodoro(25, 5, 0).copy(isBreak = true, round = 4)))
    }

    @Test
    fun skipBreakGoesStraightToTheNextRound() {
        val inBreak = FocusTimer.onPhaseEnd(pomodoro(), t0 + sec(1500), id).state
        val next = FocusTimer.advance(inBreak, t0 + sec(1600), id)
        assertNull(next.saved)
        assertFalse(next.state.isBreak)
        assertEquals(2, next.state.round)
    }

    @Test
    fun continueEarlyInAFocusRoundSavesWhatWasDone() {
        val s = pomodoro()
        val next = FocusTimer.advance(s, t0 + sec(600), id)
        val saved = next.saved!!
        assertEquals(600, saved.seconds)
        assertFalse(saved.completed)
        assertTrue(next.state.isBreak)
    }

    @Test
    fun stoppingKeepsWhatWasDoneUnlessItIsTooShort() {
        val s = countdown(25)
        val half = FocusTimer.stop(s, t0 + sec(600), completed = false, newId = id)!!
        assertEquals(600, half.seconds)
        assertFalse(half.completed)
        assertEquals(t0, half.startedAtMillis)
        assertNull(FocusTimer.stop(s, t0 + sec(29), false, id))
        assertNotNull(FocusTimer.stop(s, t0 + sec(30), false, id))
        assertTrue(FocusTimer.metGoal(FocusTimer.start("h", "", 0, 0, 0, false, t0), t0 + sec(1)))
        assertFalse(FocusTimer.metGoal(s, t0 + sec(600)))
        assertTrue(FocusTimer.metGoal(s, t0 + sec(1500)))
    }

    @Test
    fun stoppingDuringABreakKeepsNothingAndWholeRoundsDropAnEarlyRound() {
        val inBreak = FocusTimer.onPhaseEnd(pomodoro(), t0 + sec(1500), id).state
        assertNull(FocusTimer.stop(inBreak, t0 + sec(1600), true, id))
        val whole = pomodoro(whole = true)
        assertNull(FocusTimer.stop(whole, t0 + sec(600), completed = false, newId = id))
        assertNotNull(FocusTimer.stop(whole, t0 + sec(1500), completed = true, newId = id))
        assertNotNull(FocusTimer.stop(pomodoro(whole = false), t0 + sec(600), completed = false, newId = id))
    }

    @Test
    fun resetStartsThePhaseAgain() {
        val s = FocusTimer.reset(countdown(10), t0 + sec(300))
        assertEquals(0, s.accumulatedSeconds)
        assertEquals(t0 + sec(300 + 600), FocusTimer.phaseEndMillis(s))
        val paused = FocusTimer.reset(FocusTimer.pause(countdown(10), t0 + sec(50)), t0 + sec(300))
        assertFalse(FocusTimer.isRunning(paused))
        assertEquals(0, paused.accumulatedSeconds)
    }

    @Test
    fun aSessionAcrossMidnightIsSplitInTwo() {
        val start = LocalDateTime.of(2026, 10, 14, 23, 30).atZone(zone).toInstant().toEpochMilli()
        val one = FocusSessionData("a", "h", 60, 3600, true, start, "x")
        val pieces = FocusTimer.split(one, zone)
        assertEquals(2, pieces.size)
        assertEquals(listOf("a", "a-2"), pieces.map { it.id })
        assertEquals(listOf(1800, 1800), pieces.map { it.seconds })
        assertEquals(listOf(false, true), pieces.map { it.completed })
        assertEquals(LocalDateTime.of(2026, 10, 15, 0, 0).atZone(zone).toInstant().toEpochMilli(), pieces[1].startedAtMillis)
    }

    @Test
    fun aSessionInsideOneDayOrWithATinyPieceStaysWhole() {
        val noon = LocalDateTime.of(2026, 10, 14, 12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(1, FocusTimer.split(FocusSessionData("a", "h", 25, 1500, true, noon), zone).size)
        val late = LocalDateTime.of(2026, 10, 14, 23, 59, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(1, FocusTimer.split(FocusSessionData("b", "h", 25, 1500, true, late), zone).size) // first piece is 30 s
    }

    @Test
    fun theStateSurvivesBeingSavedAndRead() {
        val s = FocusTimer.pause(pomodoro(25, 5, 15, whole = true), t0 + sec(40)).copy(round = 3, label = "Deep work")
        assertEquals(s, FocusTimer.fromJson(FocusTimer.toJson(s)))
        val running = pomodoro()
        assertEquals(running, FocusTimer.fromJson(FocusTimer.toJson(running)))
        assertNull(FocusTimer.fromJson(null))
        assertNull(FocusTimer.fromJson("nonsense"))
    }

    @Test
    fun clockText() {
        assertEquals("25:00", FocusTimer.clock(1500))
        assertEquals("1:05:09", FocusTimer.clock(3909))
        assertEquals("00:00", FocusTimer.clock(-5))
    }
}
