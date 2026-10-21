package com.lifetracker.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesHeartbeatTest {
    private val now = 10_000_000L

    @Test fun dueWhenEverythingIsOnAndNothingIsRunning() {
        assertTrue(PlacesHeartbeat.due(true, true, true, false, now, now - 15 * 60_000L))
    }

    @Test fun notDueWhenOffOrNoPermissionOrReadingsRunning() {
        val last = now - 15 * 60_000L
        assertFalse(PlacesHeartbeat.due(false, true, true, false, now, last))
        assertFalse(PlacesHeartbeat.due(true, false, true, false, now, last))
        assertFalse(PlacesHeartbeat.due(true, true, false, false, now, last))
        assertFalse(PlacesHeartbeat.due(true, true, true, true, now, last))
    }

    @Test fun notDueTwiceWithinTenMinutes() {
        assertFalse(PlacesHeartbeat.due(true, true, true, false, now, now - 9 * 60_000L))
        assertTrue(PlacesHeartbeat.due(true, true, true, false, now, now - 10 * 60_000L))
    }

    @Test fun onlyFreshPositionsAreUsed() {
        assertTrue(PlacesHeartbeat.usable(now - 4 * 60_000L, now))
        assertTrue(PlacesHeartbeat.usable(now + 30_000L, now))
        assertFalse(PlacesHeartbeat.usable(now - 6 * 60_000L, now))
        assertFalse(PlacesHeartbeat.usable(now + 5 * 60_000L, now))
    }
}
