package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionClassifierTest {
    private val step = 200L

    /** Feeds one reading every 200 ms from [fromMs] up to (not including) [toMs] and returns every change that was announced. */
    private fun feed(c: MotionClassifier, fromMs: Long, toMs: Long, magnitude: (Int) -> Double): List<MotionClassifier.Change> {
        val out = ArrayList<MotionClassifier.Change>()
        var i = 0
        var t = fromMs
        while (t < toMs) {
            c.onSample(t, magnitude(i))?.let { out.add(it) }
            t += step
            i++
        }
        return out
    }

    private fun calm(i: Int) = if (i % 2 == 0) 0.02 else -0.02
    private fun shaking(i: Int) = if (i % 2 == 0) 2.0 else -2.0

    @Test
    fun aPhoneOnATableIsStillAfterAMinute() {
        val c = MotionClassifier()
        val changes = feed(c, 0L, 70_000L) { calm(it) }
        assertEquals(1, changes.size)
        assertTrue(changes[0].still)
        assertEquals(true, c.still)
        // Nothing more is said while it stays calm.
        assertTrue(feed(c, 70_000L, 200_000L) { calm(it) }.isEmpty())
    }

    @Test
    fun nothingIsDecidedFromAFewReadings() {
        val c = MotionClassifier()
        val first = (0 until 7).mapNotNull { c.onSample(it * step, 0.0) }
        assertTrue(first.isEmpty())
        assertNull(c.still)
    }

    @Test
    fun carryingItIsMovingAfterAFewSeconds() {
        val c = MotionClassifier()
        val changes = feed(c, 0L, 20_000L) { shaking(it) }
        assertEquals(1, changes.size)
        assertFalse(changes[0].still)
        assertEquals(false, c.still)
        // It began with the first stretch of shaking, not at the end of the wait.
        assertTrue(changes[0].sinceMs < 3_000L)
    }

    @Test
    fun theMiddleGroundChangesNothing() {
        // Alternating plus and minus 0.45 has a variance of about 0.2: between the two limits.
        val c = MotionClassifier()
        val changes = feed(c, 0L, 180_000L) { if (it % 2 == 0) 0.45 else -0.45 }
        assertTrue(changes.isEmpty())
        assertNull(c.still)
    }

    @Test
    fun stoppingIsStillSinceTheLastShakeNotWhenTheMinuteOfCalmWasOver() {
        val c = MotionClassifier()
        val moving = feed(c, 0L, 10_000L) { shaking(it) }
        assertEquals(1, moving.size)
        assertFalse(moving[0].still)
        val after = feed(c, 10_000L, 200_000L) { calm(it) }
        assertEquals(1, after.size)
        assertTrue(after[0].still)
        assertTrue(after[0].sinceMs in 9_000L..10_000L)
    }

    @Test
    fun theSystemsMotionAlertMakesItMovingSoTheNextCalmIsAnnounced() {
        val c = MotionClassifier()
        assertEquals(1, feed(c, 0L, 70_000L) { calm(it) }.size)
        // Without the alert, more calm says nothing.
        assertTrue(feed(c, 70_000L, 140_000L) { calm(it) }.isEmpty())
        c.markMoving()
        assertEquals(false, c.still)
        val again = feed(c, 140_000L, 220_000L) { calm(it) }
        assertEquals(1, again.size)
        assertNotNull(again[0])
        assertTrue(again[0].still)
    }
}
